/**
 * Real production-fixture browser sessions, with all timing and correctness observations retained.
 */
const {chromium, firefox} = require('../../browser-tests/node_modules/playwright');
/**
 * Exact assertions are independent of timing scores.
 */
const assert = require('node:assert/strict');
/**
 * Persistent raw output and artifact provenance.
 */
const {writeFileSync, readFileSync} = require('node:fs');
/**
 * Absolute artifact paths.
 */
const {resolve} = require('node:path');
/**
 * Byte-level actual packaged binary identity.
 */
const {createHash} = require('node:crypto');
/**
 * Identical document-local socket observations.
 */
const {installMetrics} = require('./metrics.cjs');
/**
 * Execution environment is recorded with raw results, rather than inferred from timings.
 */
const os = require('node:os');

/**
 * Waits for a requested logical row to be actually inside the viewport, then two paint opportunities.
 */
async function visible(page, index) {
    await page.waitForFunction((index) => {
        const content = document.querySelector('[data-index="' + index + '"]');
        const list = document.querySelector('.geshra-virtual-list');
        if (!content || !list) return false;
        const row = content.getBoundingClientRect();
        const viewport = list.getBoundingClientRect();
        return row.top >= viewport.top - 1 && row.bottom <= viewport.bottom + 1;
    }, index);
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

/**
 * Advances actual native scrolling once per animation frame and observes coverage, gaps and DOM.
 * This measures scripted sustained scroll/jumps, not human wheel acceleration or compositor FPS.
 */
async function scrollWork(page, count, jumps) {
    return page.evaluate(
        async ({count, jumps}) => {
            const list = document.querySelector('.geshra-virtual-list');
            const samples = [];
            let previous = performance.now();
            for (let step = 0; step < (jumps ? 24 : 60); step++) {
                list.scrollTop = jumps ? ((step * 1237) % (count - 10)) * 32 : step * 3 * 32;
                await new Promise(requestAnimationFrame);
                const now = performance.now();
                const viewport = list.getBoundingClientRect();
                const rows = list.querySelectorAll('.geshra-virtual-row');
                const first = rows.length ? rows[0].getBoundingClientRect() : null;
                const last = rows.length ? rows[rows.length - 1].getBoundingClientRect() : null;
                const overlap = first && last ? Math.max(0, Math.min(last.bottom, viewport.bottom - 1) - Math.max(first.top, viewport.top + 1)) : 0;
                samples.push({at: now, gap: now - previous, mounted: rows.length, uncoveredPixels: Math.max(0, list.clientHeight - overlap)});
                previous = now;
            }
            const target = jumps ? count - 10 : 177;
            list.scrollTop = target * 32;
            return {samples, target, endedAt: performance.now()};
        },
        {count, jumps}
    );
}

/**
 * Executes one isolated cold session with the requested real backend, profile and logical size.
 */
async function sample(browser, engine, version, count, profile, repeat) {
    const context = await browser.newContext({viewport: {width: 1024, height: 768}});
    const page = await context.newPage();
    page.setDefaultTimeout(15000);
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    const result = {engine, browser: browser.version(), version, count, profile, repeat, errors};
    try {
        await page.addInitScript(installMetrics);
        const cdp = engine === 'chrome' ? await context.newCDPSession(page) : null;
        if (cdp) await cdp.send('Emulation.setCPUThrottlingRate', {rate: profile === 'delayed' ? 4 : 1});
        result.cpuRate = cdp && profile === 'delayed' ? 4 : 1;
        if (cdp) await cdp.send('Performance.enable');
        await page.goto('http://localhost:4201/');
        await visible(page, 0);
        result.initial = await page.evaluate(() => ({
            readyMs: performance.now(),
            mounted: document.querySelectorAll('.geshra-virtual-row').length,
            resources: performance
                .getEntriesByType('resource')
                .map((entry) => ({name: new URL(entry.name).pathname, transferSize: entry.transferSize, encodedBodySize: entry.encodedBodySize})),
            receivedBytes: window.virtualMetrics.receivedBytes,
            sentBytes: window.virtualMetrics.sentBytes
        }));
        if (cdp) {
            await cdp.send('HeapProfiler.collectGarbage');
            result.heapInitial = await cdp.send('Runtime.getHeapUsage');
            result.domInitial = await cdp.send('Memory.getDOMCounters');
            result.browserCountersInitial = await cdp.send('Performance.getMetrics');
        }
        result.sustained = await scrollWork(page, count, false);
        await visible(page, result.sustained.target);
        result.sustained.settledAt = await page.evaluate(() => performance.now());
        result.jumps = await scrollWork(page, count, true);
        await visible(page, count - 1);
        result.jumps.settledAt = await page.evaluate(() => performance.now());
        await page.getByRole('button', {name: 'Select ' + (count - 1), exact: true}).click();
        await page
            .getByLabel('Result', {exact: true})
            .filter({hasText: 'selected=' + (count - 1)})
            .waitFor();
        await page.getByLabel('Edit ' + (count - 1), {exact: true}).fill('persisted');
        await page.getByRole('button', {name: 'First', exact: true}).click();
        await visible(page, 0);
        await page.getByRole('button', {name: 'Last', exact: true}).click();
        await visible(page, count - 1);
        assert.equal(await page.getByLabel('Edit ' + (count - 1), {exact: true}).inputValue(), 'persisted');
        await page.getByRole('button', {name: 'Refresh', exact: true}).click();
        await visible(page, count - 1);
        await page.getByRole('button', {name: 'Stats', exact: true}).click();
        await page.getByLabel('Result', {exact: true}).filter({hasText: 'reads='}).waitFor();
        result.serverStats = await page.getByLabel('Result', {exact: true}).innerText();
        result.metrics = await page.evaluate(() => window.virtualMetrics);
        const mounted = await page.locator('.geshra-virtual-row').count();
        assert.ok(mounted <= 18, 'bounded mounted rows');
        assert.ok(Number(result.serverStats.match(/reads=(\d+)/)[1]) < 4096, 'lazy bounded provider reads');
        assert.deepEqual(errors, []);
        if (cdp) {
            await page.evaluate(() => {
                window.virtualMetrics.requests.length = 0;
                window.virtualMetrics.acknowledgments.length = 0;
                window.virtualMetrics.longTasks.length = 0;
            });
            await cdp.send('HeapProfiler.collectGarbage');
            result.heapFinal = await cdp.send('Runtime.getHeapUsage');
            result.domFinal = await cdp.send('Memory.getDOMCounters');
            result.browserCountersFinal = await cdp.send('Performance.getMetrics');
        }
        result.status = 'passed';
    } catch (error) {
        result.status = 'failed';
        result.error = error.stack;
    } finally {
        await context.close();
    }
    return result;
}

/**
 * Counterbalances version order within every browser/count/profile/repetition block. Backend JARs
 * remain idle except the selected one; the same TCP relay delays HTTP and WS in both directions.
 */
function orderVersions(versions, repeat) {
    if (versions.length === 2) return repeat % 2 ? [...versions].reverse() : versions;
    const offset = repeat % versions.length;
    const order = [...versions.slice(offset), ...versions.slice(0, offset)];
    return Math.floor(repeat / versions.length) % 2 ? order.reverse() : order;
}

/**
 * Records the full matrix without overlapping sessions or changing browser measurement semantics.
 */
async function main() {
    const versions = (process.env.VIRTUAL_VERSIONS || 'before,after').split(',');
    const counts = (process.env.VIRTUAL_COUNTS || '10000,100000').split(',').map(Number);
    const repeats = Number(process.env.VIRTUAL_REPEATS || 4);
    const output = {
        recordedAt: new Date().toISOString(),
        environment: {
            node: process.version,
            platform: process.platform,
            arch: process.arch,
            os: os.release(),
            cpu: os.cpus()[0].model,
            logicalCpus: os.cpus().length,
            memoryBytes: os.totalmem()
        },
        viewport: {width: 1024, height: 768},
        artifacts: Object.fromEntries(
            versions.map((version) => [
                version,
                createHash('sha256')
                    .update(readFileSync(resolve(__dirname, '../../build/virtual-list-' + version + '.jar')))
                    .digest('hex')
            ])
        ),
        warmups: [],
        samples: []
    };
    for (const [engine, launcher] of [
        ['chrome', chromium],
        ['firefox', firefox]
    ]) {
        const browser = await launcher.launch({headless: true, executablePath: engine === 'chrome' ? 'C:/Program Files/Google/Chrome/Application/chrome.exe' : undefined});
        try {
            for (const count of counts)
                for (const profile of ['local', 'delayed'])
                    for (let repeat = 0; repeat < repeats; repeat++)
                        for (const version of orderVersions(versions, repeat)) {
                            const port = {before: 4300, after: 4400, 'two-flight': 4500, priority: 4600, bounded: 4700}[version] + (count === 10000 ? 1 : 2);
                            const response = await fetch('http://localhost:4199/config', {
                                method: 'POST',
                                headers: {'content-type': 'application/json'},
                                body: JSON.stringify({delayMs: profile === 'delayed' ? 75 : 0, backendPort: port})
                            });
                            assert.ok(response.ok);
                            const expected = {delayMs: profile === 'delayed' ? 75 : 0, backendPort: port};
                            assert.deepEqual(await response.json(), expected);
                            if (repeat === 0) {
                                const warmup = await sample(browser, engine, version, count, profile, -1);
                                output.warmups.push(warmup);
                                assert.equal(warmup.status, 'passed', warmup.error);
                            }
                            const observation = await sample(browser, engine, version, count, profile, repeat);
                            assert.deepEqual(await (await fetch('http://localhost:4199/config')).json(), expected);
                            output.samples.push(observation);
                            writeFileSync(
                                resolve(__dirname, '../../performance-results/virtual-list/' + (process.env.VIRTUAL_OUTPUT || 'browser.json')),
                                JSON.stringify(output, null, 2) + '\n'
                            );
                            console.log(JSON.stringify({engine, version, count, profile, repeat, status: observation.status, error: observation.error}));
                            if (observation.status !== 'passed') throw new Error('Correctness failure; raw observation retained');
                        }
        } finally {
            await browser.close();
        }
    }
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
