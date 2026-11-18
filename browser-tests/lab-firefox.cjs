/**
 * Unthrottled Firefox subset; Chromium-only CDP results are never implied to cover this browser.
 */
const {firefox} = require('playwright');
/**
 * Persistent raw observations for supported and unavailable Firefox metrics.
 */
const {writeFileSync} = require('node:fs');
/**
 * Native output path resolution.
 */
const {resolve} = require('node:path');
/**
 * The same document-local metric semantics used by Chromium.
 */
const {installMetrics, readMetrics} = require('./lab-metrics.cjs');
/**
 * Shared actual-server interaction/readiness fixtures, without any CDP emulation.
 */
const {ready, callbackSample, provenance} = require('./browser-lab.cjs');

/**
 * Measures two repetitions of home/gallery in desktop/mobile-sized Firefox viewports.
 * Each fresh context is cold; no touch/CPU/network emulation or CDP counters are claimed.
 */
async function runFirefox() {
    const result = {
        recordedAt: new Date().toISOString(),
        browser: null,
        engine: 'firefox',
        conditions: 'unthrottled, fresh context, desktop user agent, viewport size only',
        source: provenance(),
        samples: []
    };
    const browser = await firefox.launch({headless: true});
    try {
        result.browser = browser.version();
        for (const route of ['/', '/components'])
            for (const viewport of [
                {width: 1440, height: 900},
                {width: 390, height: 844}
            ])
                for (let repeat = 1; repeat <= 2; repeat++) {
                    const context = await browser.newContext({viewport});
                    const page = await context.newPage();
                    const errors = [];
                    page.on('pageerror', (error) => errors.push(error.message));
                    try {
                        await page.addInitScript(installMetrics);
                        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + route);
                        await ready(page, route);
                        await page.waitForTimeout(750);
                        const load = await page.evaluate(readMetrics);
                        const callback = await callbackSample(page, route);
                        if (route !== '/') {
                            await page.getByLabel('Retained input').click();
                            await page.getByLabel('Retained input').pressSequentially('hello', {delay: 30});
                        }
                        await page.waitForTimeout(200);
                        result.samples.push({route, viewport, repeat, status: 'passed', load, metrics: await page.evaluate(readMetrics), callback, errors});
                    } catch (error) {
                        result.samples.push({route, viewport, repeat, status: 'failed', error: error.stack, errors});
                    } finally {
                        await context.close();
                    }
                    console.log(JSON.stringify({route, viewport, repeat, status: result.samples.at(-1).status}));
                }
    } finally {
        try {
            writeFileSync(resolve(process.env.LAB_FIREFOX || '../performance-results/browser-lab-firefox.json'), JSON.stringify(result, null, 2) + '\n');
        } finally {
            await browser.close();
        }
    }
    if (result.samples.some((sample) => sample.status === 'failed')) process.exitCode = 1;
}

runFirefox().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
