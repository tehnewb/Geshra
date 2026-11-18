/**
 * Writes independent first-build observations without changing production files.
 */
const fs = require('node:fs');
/**
 * Existing project browser automation dependency.
 */
const {chromium} = require('../browser-tests/node_modules/playwright');
/**
 * Captures independent boundary and graphics observations omitted after an earlier grouped failure.
 */
async function main() {
    /*
     * Each result is an observation rather than an inferred pass for an entire component family.
     */
    const browser = await chromium.launch({headless: true, channel: 'chrome'});
    const page = await browser.newPage();
    const out = {};
    try {
        await page.goto('http://localhost:4050/showcase');
        await page.locator('[data-showcase-ready]').waitFor();
        await page.getByLabel('RangeField', {exact: true}).focus();
        await page.keyboard.press('End');
        await page.waitForTimeout(200);
        out.range = {value: await page.getByLabel('RangeField', {exact: true}).inputValue(), feedback: await page.locator('[data-feedback="RangeField"]').innerText()};
        await page.getByLabel('MultiSelect', {exact: true}).selectOption(['a,b', '日本語']);
        await page.waitForTimeout(200);
        out.multi = await page.locator('[data-feedback="MultiSelect"]').innerText();
        await page.getByLabel('Select,Option,OptionGroup', {exact: true}).selectOption('');
        await page.waitForTimeout(200);
        out.emptySelect = await page.locator('[data-feedback="Select,Option,OptionGroup"]').innerText();
        await page.getByRole('button', {name: 'Draw canvas circle', exact: true}).click();
        await page.waitForTimeout(200);
        out.canvas = await page.locator('canvas').evaluate((element) => Array.from(element.getContext('2d').getImageData(170, 50, 1, 1).data));
        out.svg = await page.locator('svg *').evaluateAll((elements) => elements.map((element) => ({tag: element.tagName, namespace: element.namespaceURI})));
        out.media = await page
            .locator('audio,video')
            .evaluateAll((elements) => elements.map((element) => ({tag: element.tagName, ready: element.readyState, error: element.error?.message})));
        await page.getByRole('button', {name: 'Complete progress', exact: true}).click();
        await page.waitForTimeout(100);
        out.progress = await page.getByLabel('Determinate progress', {exact: true}).evaluate((element) => element.value);
        await page.getByRole('button', {name: 'Reset progress', exact: true}).click();
        await page.waitForTimeout(100);
        out.progressReset = await page.getByLabel('Determinate progress', {exact: true}).evaluate((element) => element.value);
        out.tabSemantics = await page.locator('[data-demo="Tabbed"] button').evaluateAll((elements) =>
            elements.map((element) => ({
                text: element.textContent,
                role: element.getAttribute('role'),
                tabIndex: element.tabIndex,
                selected: element.getAttribute('aria-selected')
            }))
        );
        fs.writeFileSync(require('node:path').join(__dirname, 'followup-results.json'), JSON.stringify(out, null, 2));
        console.log(JSON.stringify(out));
    } finally {
        await browser.close();
    }
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
