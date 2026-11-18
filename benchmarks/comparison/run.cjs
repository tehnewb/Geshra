/**
 * Uses the repository's pinned development browser harness; no production app needs Playwright.
 */
const {chromium} = require('../../browser-tests/node_modules/playwright');
/**
 * Raw results and fixture provenance storage.
 */
const {mkdirSync, readFileSync, writeFileSync, readdirSync} = require('node:fs');
/**
 * Portable benchmark paths.
 */
const {resolve, relative} = require('node:path');
/**
 * Fingerprints maintained fixture sources, never generated bundles or downloaded dependencies.
 */
const {createHash} = require('node:crypto');
/**
 * Hardware and runtime metadata.
 */
const os = require('node:os');
/**
 * Shares audited observer semantics; asynchronous result latency is still measured separately.
 */
const {installMetrics, readMetrics} = require('../../browser-tests/lab-metrics.cjs');
/**
 * Shares raw CDP HTTP and WS telemetry without CDP network throttling.
 */
const {trackNetwork, networkSnapshot} = require('../../browser-tests/lab-network.cjs');
/**
 * Finite matched implementations; COMPARISON_APPS can explicitly exclude an unavailable build.
 */
const implementations = [
    {name: 'geshra', port: 4201},
    {name: 'vaadin', port: 4202},
    {name: 'react', port: 4203},
    {name: 'dom', port: 4204}
];
/**
 * Identical latency applies before every backend in both directions, including WS frames.
 */
const profiles = [
    {name: 'local', delayEachWayMs: 0, cpuRate: 1},
    {name: 'latency-cpu', delayEachWayMs: 75, cpuRate: 4}
];
/**
 * Server-dependent operation sequence, with a common native DOM completion contract.
 */
const operations = ['Increment', 'Increment', 'Increment', 'Echo', 'Create 1000', 'Update tenth', 'Select 500', 'Remove 500', 'Clear'];

/**
 * Reads cumulative raw relay bytes before/after one quiescent operation.
 */
async function relayState() {
    const response = await fetch('http://127.0.0.1:4199/');
    if (!response.ok) throw new Error('Relay snapshot failed: ' + response.status);
    return response.json();
}

/**
 * Selects a relay profile and verifies that the control endpoint actually accepted its delay.
 */
async function selectProfile(profile) {
    const response = await fetch('http://127.0.0.1:4199/profile', {method: 'POST', body: JSON.stringify({delayMs: profile.delayEachWayMs})});
    if (!response.ok) throw new Error('Relay profile rejected: ' + response.status);
    const applied = await response.json();
    if (applied.delayMs !== profile.delayEachWayMs) throw new Error('Applied relay delay does not match requested profile');
    return applied;
}

/**
 * Reads cumulative stream counters through the checked control endpoint.
 */
async function bytes(name) {
    return (await relayState()).counters[name];
}

/**
 * Computes delivered application-stream bytes, including actual protocol envelopes/headers.
 */
function difference(before, after) {
    return {clientToServer: after.clientToServer - before.clientToServer, serverToClient: after.serverToClient - before.serverToClient};
}

/**
 * Waits for complete matched content; readiness is not inferred from framework-specific globals.
 */
async function ready(page) {
    await page.waitForFunction(
        () => document.querySelector('main[data-ready="true"] button') && document.querySelectorAll('main button').length === 7 && document.querySelector('tbody')
    );
    await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
}

/**
 * Records the trusted click to revision mutation and two-rAF presentation proxy. A deadline
 * reports missing server output as failure, never hangs or substitutes Event Timing/INP.
 */
async function action(page, name) {
    await page.evaluate((label) => {
        const root = document.querySelector('main');
        const revision = Number(root.dataset.revision);
        const button = Array.from(root.querySelectorAll('button')).find((element) => element.textContent === label);
        window.comparisonOperation = new Promise((resolve, reject) => {
            let start;
            const timer = setTimeout(() => {
                observer.disconnect();
                reject(new Error('Operation deadline: ' + label));
            }, 30000);
            button.addEventListener(
                'click',
                () => {
                    start = performance.now();
                },
                {once: true, capture: true}
            );
            const observer = new MutationObserver(() => {
                if (Number(root.dataset.revision) !== revision + 1) return;
                observer.disconnect();
                const mutationMs = performance.now() - start;
                requestAnimationFrame(() =>
                    requestAnimationFrame(() => {
                        clearTimeout(timer);
                        resolve({mutationMs, visualProxyMs: performance.now() - start, revision: revision + 1});
                    })
                );
            });
            observer.observe(root, {attributes: true, attributeFilter: ['data-revision']});
        });
    }, name);
    await page.getByRole('button', {name, exact: true}).click();
    return page.evaluate(() => window.comparisonOperation);
}

/**
 * Checks full observable semantics after every operation, not just framework callback timing.
 */
async function verify(page, name, increments) {
    const result = await page.evaluate(
        (operation) => ({
            counter: document.querySelector('[data-counter]').textContent,
            echo: document.querySelector('[data-echo]').textContent,
            rows: document.querySelectorAll('tbody tr').length,
            first: document.querySelector('[data-row="1"]')?.lastElementChild.textContent,
            second: document.querySelector('[data-row="2"]')?.lastElementChild.textContent,
            selected: document.querySelector('[data-row="500"]')?.classList.contains('selected'),
            removed: !document.querySelector('[data-row="500"]'),
            invalidRows: Array.from(document.querySelectorAll('tbody tr')).filter((row) => {
                const id = Number(row.dataset.row);
                const updated = ['Update tenth', 'Select 500', 'Remove 500'].includes(operation) && (id - 1) % 10 === 0;
                return (
                    id < 1 ||
                    id > 1000 ||
                    row.children.length !== 2 ||
                    row.firstElementChild.textContent !== String(id) ||
                    row.lastElementChild.textContent !== 'Row ' + id + (updated ? ' !!!' : '')
                );
            }).length,
            idSequenceCorrect: Array.from(document.querySelectorAll('tbody tr')).every(
                (row, index) => Number(row.dataset.row) === index + 1 + (operation === 'Remove 500' && index >= 499 ? 1 : 0)
            ),
            uniqueIds: new Set(Array.from(document.querySelectorAll('tbody tr'), (row) => row.dataset.row)).size
        }),
        name
    );
    if (result.invalidRows || !result.idSequenceCorrect || result.uniqueIds !== result.rows) throw new Error('Exact row ID/content mismatch: ' + JSON.stringify(result));
    if (result.counter !== 'Counter: ' + increments) throw new Error('Counter mismatch: ' + JSON.stringify(result));
    if (name === 'Echo' && result.echo !== 'Echo: Ada') throw new Error('Echo mismatch');
    if (name === 'Create 1000' && (result.rows !== 1000 || result.first !== 'Row 1' || result.second !== 'Row 2')) throw new Error('Create mismatch');
    if (name === 'Update tenth' && (result.rows !== 1000 || result.first !== 'Row 1 !!!' || result.second !== 'Row 2')) throw new Error('Update mismatch');
    if (name === 'Select 500' && (result.rows !== 1000 || !result.selected)) throw new Error('Selection mismatch');
    if (name === 'Remove 500' && (result.rows !== 999 || !result.removed)) throw new Error('Removal mismatch');
    if (name === 'Clear' && result.rows !== 0) throw new Error('Clear mismatch');
    return result;
}

/**
 * Captures one fresh or primed-cache document and all matched server operations sequentially.
 */
async function measure(browser, implementation, profile, cache, repeat) {
    const context = await browser.newContext({viewport: {width: 1440, height: 900}, deviceScaleFactor: 1});
    const errors = [];
    try {
        const applied = await relayState();
        if (applied.delayMs !== profile.delayEachWayMs) throw new Error('Relay delay changed before sample');
        const page = await context.newPage();
        page.setDefaultTimeout(30000);
        page.on('pageerror', (error) => errors.push(error.message));
        const cdp = await context.newCDPSession(page);
        await cdp.send('Network.enable');
        await cdp.send('Network.clearBrowserCache');
        await cdp.send('Emulation.setCPUThrottlingRate', {rate: profile.cpuRate});
        const url = 'http://127.0.0.1:' + implementation.port + '/';
        if (cache === 'warm') {
            await page.goto(url);
            await ready(page);
            await page.waitForTimeout(300);
            await page.goto('about:blank');
        }
        errors.length = 0;
        await page.addInitScript(installMetrics);
        const network = trackNetwork(cdp);
        await cdp.send('Performance.enable');
        const beforeCpu = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((metric) => [metric.name, metric.value]));
        const beforeLoadBytes = await bytes(implementation.name);
        await page.goto(url);
        await ready(page);
        const readyMs = await page.evaluate(() => performance.now());
        await page.waitForTimeout(750);
        const load = await page.evaluate(readMetrics);
        const loadBytes = difference(beforeLoadBytes, await bytes(implementation.name));
        const loadNetwork = structuredClone(networkSnapshot(network));
        const loadCpu = Object.fromEntries(
            (await cdp.send('Performance.getMetrics')).metrics.map((metric) => [
                metric.name,
                metric.name.endsWith('Duration') ? metric.value - (beforeCpu[metric.name] || 0) : metric.value
            ])
        );
        const samples = [];
        const beforeOperationsBytes = await bytes(implementation.name);
        let increments = 0;
        for (const name of operations) {
            if (name === 'Echo') {
                const beforeTyping = await bytes(implementation.name);
                await page.getByLabel('Name', {exact: true}).click();
                await page.getByLabel('Name', {exact: true}).pressSequentially('Ada', {delay: 50});
                await page.waitForTimeout(300);
                samples.push({
                    operation: 'Typing Ada',
                    bytes: difference(beforeTyping, await bytes(implementation.name)),
                    note: 'Native typing; server-driven frameworks may synchronize eagerly. No result-latency comparison is claimed.'
                });
            }
            const before = await bytes(implementation.name);
            const cpuBefore = Object.fromEntries((await cdp.send('Performance.getMetrics')).metrics.map((metric) => [metric.name, metric.value]));
            const timing = await action(page, name);
            const transferred = difference(before, await bytes(implementation.name));
            const cpu = Object.fromEntries(
                (await cdp.send('Performance.getMetrics')).metrics
                    .filter((metric) => metric.name.endsWith('Duration'))
                    .map((metric) => [metric.name, metric.value - (cpuBefore[metric.name] || 0)])
            );
            if (name === 'Increment') increments++;
            const correctness = await verify(page, name, increments);
            samples.push({operation: name, ...timing, bytes: transferred, cpu, correctness});
        }
        await page.waitForTimeout(500);
        const operationsBytes = difference(beforeOperationsBytes, await bytes(implementation.name));
        const metrics = await page.evaluate(readMetrics);
        if ((await relayState()).delayMs !== profile.delayEachWayMs) throw new Error('Relay delay changed during sample');
        return {
            implementation: implementation.name,
            profile: profile.name,
            appliedDelayMs: applied.delayMs,
            cache,
            repeat,
            status: 'passed',
            readyMs,
            load,
            loadBytes,
            loadCpu,
            loadNetwork,
            operations: samples,
            operationsBytes,
            metrics,
            network: networkSnapshot(network),
            errors
        };
    } catch (error) {
        return {implementation: implementation.name, profile: profile.name, cache, repeat, status: 'failed', error: error.stack, errors};
    } finally {
        await context.close();
    }
}

/**
 * Fingerprints fixture source files recursively, omitting generated/downloaded build products.
 */
function fingerprint() {
    const hash = createHash('sha256');
    /**
     * Hashes stable relative names and bytes in deterministic directory order.
     */
    function visit(directory) {
        for (const entry of readdirSync(directory, {withFileTypes: true}).sort((left, right) => left.name.localeCompare(right.name))) {
            if (['build', '.gradle', 'node_modules', 'dist', 'target', 'generated'].includes(entry.name)) continue;
            const path = resolve(directory, entry.name);
            if (entry.isDirectory()) visit(path);
            else hash.update(relative(__dirname, path)).update('\0').update(readFileSync(path)).update('\0');
        }
    }
    visit(__dirname);
    return hash.digest('hex');
}

/**
 * Runs counterbalanced complete-app visits, preserving raw samples after each completion.
 * Each repetition rotates implementation order; all timing runs are sequential on one machine.
 */
async function runComparison() {
    const selected = process.env.COMPARISON_APPS ? implementations.filter((app) => process.env.COMPARISON_APPS.split(',').includes(app.name)) : implementations;
    const repeats = Number(process.env.COMPARISON_REPEATS || 4);
    const selectedProfiles = process.env.COMPARISON_PROFILES ? profiles.filter((profile) => process.env.COMPARISON_PROFILES.split(',').includes(profile.name)) : profiles;
    if (!selected.length || !Number.isInteger(repeats) || repeats < 1 || !selectedProfiles.length) throw new Error('Invalid comparison configuration');
    const output = resolve(process.env.COMPARISON_OUTPUT || '../../performance-results/comparison.json');
    mkdirSync(resolve(output, '..'), {recursive: true});
    const result = {
        recordedAt: new Date().toISOString(),
        label: process.env.COMPARISON_LABEL || 'matched-comparison',
        runtimeArtifactSha256: process.env.COMPARISON_ARTIFACT
            ? createHash('sha256')
                  .update(readFileSync(resolve(process.env.COMPARISON_ARTIFACT)))
                  .digest('hex')
            : null,
        sourceSha256: fingerprint(),
        environment: {browser: null, node: process.version, cpu: os.cpus()[0].model, platform: os.platform(), arch: os.arch()},
        implementations: selected,
        profiles: selectedProfiles,
        repeats,
        viewport: {width: 1440, height: 900},
        samples: [],
        warmups: []
    };
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        result.environment.browser = browser.version();
        for (const profile of selectedProfiles) {
            await selectProfile(profile);
            for (const implementation of selected) {
                const warmup = await measure(browser, implementation, profile, 'cold', 0);
                result.warmups.push(warmup);
                if (warmup.status !== 'passed') throw new Error('Warmup failed: ' + JSON.stringify(warmup));
            }
            for (const cache of ['cold', 'warm'])
                for (let repeat = 0; repeat < repeats; repeat++) {
                    const order = selected.map((_, index) => selected[(index + repeat) % selected.length]);
                    for (const implementation of order) {
                        const sample = await measure(browser, implementation, profile, cache, repeat + 1);
                        result.samples.push(sample);
                        writeFileSync(output, JSON.stringify(result, null, 2) + '\n');
                        console.log(
                            JSON.stringify({
                                implementation: implementation.name,
                                profile: profile.name,
                                cache,
                                repeat: repeat + 1,
                                status: sample.status,
                                readyMs: sample.readyMs,
                                error: sample.error
                            })
                        );
                    }
                }
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
    runComparison().catch((error) => {
        console.error(error);
        process.exitCode = 1;
    });

module.exports = {implementations, action, ready, measure, verify, selectProfile};
