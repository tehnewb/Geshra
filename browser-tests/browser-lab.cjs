/**
 * Development-only Chromium driver; no browser dependency enters Geshra's published library.
 */
const {chromium} = require('playwright');
/**
 * Artifact I/O and source provenance support.
 */
const {mkdirSync, readFileSync, writeFileSync} = require('node:fs');
/**
 * Native path operations for portable output locations.
 */
const {resolve} = require('node:path');
/**
 * Source hashing distinguishes uncommitted candidates without relying on human labels.
 */
const {createHash} = require('node:crypto');
/**
 * Read-only Git metadata collector.
 */
const {execFileSync} = require('node:child_process');
/**
 * Machine metadata; renderer throttling is an emulation, not a physical mobile CPU.
 */
const os = require('node:os');
/**
 * Document-local observation functions serialized into the browser.
 */
const {installMetrics, readMetrics} = require('./lab-metrics.cjs');
/**
 * HTTP and WS telemetry captured through the installed browser's CDP implementation.
 */
const {trackNetwork, applyProfile, networkSnapshot} = require('./lab-network.cjs');
/**
 * Finite explicit profiles, expressed as milliseconds and bytes/second rather than preset names.
 */
const profiles = [
    {name: 'local', latencyMs: 0, downloadBytesPerSecond: -1, uploadBytesPerSecond: -1, cpuRate: 1},
    {name: 'fast', latencyMs: 40, downloadBytesPerSecond: 1250000, uploadBytesPerSecond: 125000, cpuRate: 2},
    {name: 'slow', latencyMs: 150, downloadBytesPerSecond: 200000, uploadBytesPerSecond: 93750, cpuRate: 4}
];
/**
 * Desktop and mobile-sized touch viewports; user agent remains the recorded desktop browser.
 */
const viewports = [
    {name: 'desktop', viewport: {width: 1440, height: 900}, isMobile: false, hasTouch: false, deviceScaleFactor: 1},
    {name: 'mobile', viewport: {width: 390, height: 844}, isMobile: true, hasTouch: true, deviceScaleFactor: 2}
];

/**
 * Waits for the real WS-rendered route rather than treating document load as application ready.
 */
async function ready(page, route) {
    await page.getByRole('button', {name: route === '/' ? 'Click me' : 'Read Java values', exact: true}).waitFor();
    if (route !== '/') await page.waitForFunction(() => document.querySelectorAll('.geshra-virtual-row').length === 12);
}

/**
 * Arms a trusted-click-to-DOM-update measurement, then waits two animation frames as a visual
 * presentation proxy. This explicitly measures the asynchronous server response, not INP.
 */
async function callbackSample(page, route) {
    await page.evaluate((gallery) => {
        const button = Array.from(document.querySelectorAll('button')).find((element) => element.textContent === (gallery ? 'Read Java values' : 'Click me'));
        const output = gallery ? document.querySelector('[aria-label="Java values"]') : document.querySelector('h1');
        window.labCallbackPromise = new Promise((resolve, reject) => {
            let start;
            const deadline = setTimeout(() => {
                observer.disconnect();
                reject(new Error('Server callback did not produce a visual update within 20 seconds'));
            }, 20000);
            button.addEventListener(
                'click',
                () => {
                    start = performance.now();
                },
                {once: true, capture: true}
            );
            const observer = new MutationObserver(() => {
                observer.disconnect();
                const mutationMs = performance.now() - start;
                requestAnimationFrame(() =>
                    requestAnimationFrame(() => {
                        clearTimeout(deadline);
                        resolve({mutationMs, visualProxyMs: performance.now() - start});
                    })
                );
            });
            observer.observe(output, {childList: true, characterData: true, subtree: true});
        });
    }, route !== '/');
    await page.getByRole('button', {name: route === '/' ? 'Click me' : 'Read Java values', exact: true}).click();
    return page.evaluate(() => window.labCallbackPromise);
}

/**
 * Hashes maintained runtime, Java, example, and benchmark sources, including untracked scripts.
 * Results, documentation, and generated build directories are deliberately excluded.
 */
function provenance() {
    const root = resolve(__dirname, '..');
    const files = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z'], {cwd: root, encoding: 'utf8'})
        .split('\0')
        .filter((name) => name && (/^src\//.test(name) || /^browser-tests\//.test(name) || /^examples\/spring-app\/src\//.test(name) || name === 'build.gradle.kts'))
        .sort();
    const hash = createHash('sha256');
    for (const name of files)
        hash.update(name + '\0')
            .update(readFileSync(resolve(root, name)))
            .update('\0');
    return {head: execFileSync('git', ['rev-parse', 'HEAD'], {cwd: root, encoding: 'utf8'}).trim(), sourceSha256: hash.digest('hex'), files: files.length};
}

/**
 * Computes nearest-rank percentiles; with three repeats p95 is simply the maximum sample.
 */
function statistics(values) {
    const finite = values.filter((value) => Number.isFinite(value)).sort((left, right) => left - right);
    return {n: finite.length, median: finite.length ? finite[Math.ceil(finite.length * 0.5) - 1] : null, p95: finite.length ? finite[Math.ceil(finite.length * 0.95) - 1] : null};
}

/**
 * Measures one cold document or one document after an unmeasured same-context priming visit.
 * No request interception, HAR, tracing, screenshots, or GC are used in timed matrix samples.
 */
async function measure(browser, base, route, viewport, profile, cache, repeat) {
    const context = await browser.newContext({
        viewport: viewport.viewport,
        isMobile: viewport.isMobile,
        hasTouch: viewport.hasTouch,
        deviceScaleFactor: viewport.deviceScaleFactor
    });
    const errors = [];
    try {
        const page = await context.newPage();
        page.setDefaultTimeout(20000);
        page.on('pageerror', (error) => errors.push(error.message));
        page.on('console', (message) => {
            if (message.type() === 'error') errors.push(message.text());
        });
        const cdp = await context.newCDPSession(page);
        await cdp.send('Network.enable');
        await applyProfile(cdp, profile);
        await cdp.send('Network.clearBrowserCache');
        if (cache === 'warm') {
            await page.goto(base + route);
            await ready(page, route);
            await page.goto('about:blank');
        }
        errors.length = 0;
        await page.addInitScript(installMetrics);
        const network = trackNetwork(cdp);
        await cdp.send('Performance.enable');
        const beforeCpu = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((metric) => [metric.name, metric.value]));
        await page.goto(base + route);
        await ready(page, route);
        const readyMs = await page.evaluate(() => performance.now());
        await page.waitForTimeout(750);
        const load = await page.evaluate(readMetrics);
        const loadCpu = await cdp.send('Performance.getMetrics');
        const callbacks = [];
        for (let index = 0; index < 3; index++) callbacks.push(await callbackSample(page, route));
        if (route !== '/') {
            await page.getByLabel('Retained input').click();
            await page.getByLabel('Retained input').pressSequentially('hello', {delay: 30});
        }
        await page.waitForTimeout(200);
        const metrics = await page.evaluate(readMetrics);
        const layout = await page.evaluate(() => ({
            viewportWidth: innerWidth,
            clientWidth: document.documentElement.clientWidth,
            scrollWidth: document.documentElement.scrollWidth,
            horizontalOverflow: document.documentElement.scrollWidth > innerWidth + 1,
            nodes: document.querySelectorAll('*').length
        }));
        return {
            route,
            viewport: viewport.name,
            profile: profile.name,
            cache,
            repeat,
            status: 'passed',
            readyMs,
            load,
            metrics,
            callbacks,
            layout,
            loadCpu: Object.fromEntries(
                loadCpu.metrics.map((metric) => [metric.name, metric.name.endsWith('Duration') ? metric.value - (beforeCpu[metric.name] || 0) : metric.value])
            ),
            network: networkSnapshot(network),
            errors
        };
    } catch (error) {
        return {route, viewport: viewport.name, profile: profile.name, cache, repeat, status: 'failed', error: error.stack, errors};
    } finally {
        await context.close();
    }
}

/**
 * Runs the finite sequential matrix and persists every raw sample after each completion.
 * LAB_REPEATS and LAB_PROFILES support explicitly labeled smoke runs without replacing full data.
 */
async function runLab() {
    const output = resolve(process.env.LAB_OUTPUT || '../performance-results/browser-lab.json');
    mkdirSync(resolve(output, '..'), {recursive: true});
    const selected = process.env.LAB_PROFILES ? profiles.filter((profile) => process.env.LAB_PROFILES.split(',').includes(profile.name)) : profiles;
    const repeats = Number(process.env.LAB_REPEATS || 3);
    if (!Number.isInteger(repeats) || repeats < 1 || !selected.length) throw new Error('Invalid matrix configuration');
    const result = {
        schema: 1,
        consoleCaptureIncludesWarmPrime: false,
        recordedAt: new Date().toISOString(),
        source: provenance(),
        environment: {
            browser: null,
            executable: process.env.BROWSER_EXECUTABLE || 'playwright chromium',
            node: process.version,
            platform: os.platform(),
            arch: os.arch(),
            cpu: os.cpus()[0].model,
            headless: true
        },
        profiles: selected,
        viewports,
        repeats,
        routes: ['/', '/components'],
        samples: [],
        summary: []
    };
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        result.environment.browser = browser.version();
        for (const route of result.routes)
            for (const viewport of viewports)
                for (const profile of selected)
                    for (const cache of ['cold', 'warm']) {
                        const group = [];
                        for (let repeat = 1; repeat <= repeats; repeat++) {
                            const sample = await measure(browser, process.env.BASE_URL || 'http://localhost:4040', route, viewport, profile, cache, repeat);
                            result.samples.push(sample);
                            group.push(sample);
                            writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
                            console.log(
                                JSON.stringify({
                                    route,
                                    viewport: viewport.name,
                                    profile: profile.name,
                                    cache,
                                    repeat,
                                    status: sample.status,
                                    lcpMs: sample.load?.lcpMs,
                                    error: sample.error
                                })
                            );
                        }
                        const passed = group.filter((sample) => sample.status === 'passed');
                        result.summary.push({
                            route,
                            viewport: viewport.name,
                            profile: profile.name,
                            cache,
                            failures: group.length - passed.length,
                            ttfbMs: statistics(passed.map((sample) => sample.load.navigation.ttfbMs)),
                            lcpMs: statistics(passed.map((sample) => sample.load.lcpMs)),
                            cls: statistics(passed.map((sample) => sample.metrics.cls)),
                            labInpMs: statistics(passed.map((sample) => sample.metrics.labInpMs)),
                            callbackVisualMs: statistics(passed.flatMap((sample) => sample.callbacks.map((callback) => callback.visualProxyMs))),
                            overflowSamples: passed.filter((sample) => sample.layout.horizontalOverflow).length
                        });
                    }
    } finally {
        try {
            result.completedAt = new Date().toISOString();
            writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
        } finally {
            await browser.close();
        }
    }
    if (result.samples.some((sample) => sample.status === 'failed')) process.exitCode = 1;
}

if (require.main === module)
    runLab().catch((error) => {
        console.error(error);
        process.exitCode = 1;
    });

module.exports = {profiles, viewports, ready, callbackSample, statistics, provenance};
