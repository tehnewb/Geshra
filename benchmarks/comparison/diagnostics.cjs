/**
 * Separate diagnostic contexts keep forced GC and screenshots out of timing comparisons.
 */
const {chromium} = require('../../browser-tests/node_modules/playwright');
/**
 * Local diagnostic storage; previous Geshra lab artifacts remain separate.
 */
const {mkdirSync, writeFileSync, readFileSync} = require('node:fs');
/**
 * Native output path resolution.
 */
const {resolve} = require('node:path');
/**
 * Identical readiness and genuine server-operation fixtures used in the matrix.
 */
const {implementations, ready, action} = require('./run.cjs');

/**
 * Captures identical native layout, screenshots, styles and six create/clear GC cycles per app.
 * Browser heap and DOM counters cannot establish backend memory usage or absence of leaks.
 */
async function diagnostics() {
    const directory = resolve(__dirname, '../../build/comparison-artifacts');
    mkdirSync(directory, {recursive: true});
    const expectedCss = readFileSync(resolve(__dirname, 'common/bench.css'), 'utf8').replace(/\r\n/g, '\n');
    const result = {recordedAt: new Date().toISOString(), samples: []};
    await fetch('http://127.0.0.1:4199/profile', {method: 'POST', body: JSON.stringify({delayMs: 0})});
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        result.browser = browser.version();
        for (const implementation of implementations) {
            const context = await browser.newContext({viewport: {width: 1440, height: 900}});
            try {
                const page = await context.newPage();
                const cdp = await context.newCDPSession(page);
                await cdp.send('Network.enable');
                const websocketExtensions = [];
                cdp.on('Network.webSocketHandshakeResponseReceived', (event) => {
                    const headers = event.response.headers;
                    const extension = Object.entries(headers).find(([name]) => name.toLowerCase() === 'sec-websocket-extensions');
                    websocketExtensions.push(extension ? extension[1] : 'none');
                });
                await page.goto('http://127.0.0.1:' + implementation.port + '/');
                await ready(page);
                const css = await (await page.request.get('http://127.0.0.1:' + implementation.port + '/bench.css')).text();
                if (css.replace(/\r\n/g, '\n') !== expectedCss) throw new Error('Shared stylesheet mismatch');
                const layout = await page.evaluate(() => {
                    const root = document.querySelector('main');
                    const bounds = root.getBoundingClientRect();
                    const style = getComputedStyle(root);
                    return {
                        title: document.title,
                        children: Array.from(root.children).map((element) => element.tagName),
                        buttons: Array.from(root.querySelectorAll('button')).map((element) => element.textContent),
                        mainBounds: {x: bounds.x, y: bounds.y, width: bounds.width, height: bounds.height},
                        font: style.font,
                        inputType: root.querySelector('input').type,
                        overflow: document.documentElement.scrollWidth > innerWidth
                    };
                });
                await page.screenshot({path: resolve(directory, implementation.name + '-initial.png')});
                const snapshots = [];
                for (let cycle = 0; cycle <= 6; cycle++) {
                    if (cycle) {
                        await action(page, 'Create 1000');
                        await action(page, 'Clear');
                    }
                    await page.waitForTimeout(100);
                    await cdp.send('HeapProfiler.collectGarbage');
                    snapshots.push({cycle, heap: await cdp.send('Runtime.getHeapUsage'), dom: await cdp.send('Memory.getDOMCounters'), attached: await page.locator('*').count()});
                }
                await action(page, 'Create 1000');
                await page.screenshot({path: resolve(directory, implementation.name + '-table.png')});
                result.samples.push({implementation: implementation.name, status: 'passed', layout, snapshots, websocketExtensions});
            } catch (error) {
                result.samples.push({implementation: implementation.name, status: 'failed', error: error.stack});
            } finally {
                await context.close();
            }
        }
    } finally {
        try {
            writeFileSync(resolve(__dirname, '../../performance-results/comparison-diagnostics.json'), JSON.stringify(result, null, 2) + '\n');
        } finally {
            await browser.close();
        }
    }
    if (result.samples.some((sample) => sample.status === 'failed')) process.exitCode = 1;
    console.log(JSON.stringify(result.samples.map((sample) => ({implementation: sample.implementation, status: sample.status, error: sample.error}))));
}

diagnostics().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
