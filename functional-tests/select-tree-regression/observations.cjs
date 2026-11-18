/**
 * Existing browser engines for independent failure-state capture.
 */
const {chromium, firefox} = require('../../browser-tests/node_modules/playwright');
/**
 * Persists focused observations without overwriting assertion-suite evidence.
 */
const fs = require('node:fs');
/**
 * Resolves build-partitioned evidence destinations.
 */
const path = require('node:path');
/**
 * Verifies expected readiness identity before opening browsers.
 */
const assert = require('node:assert/strict');
/**
 * Reads and independently verifies the actual current artifact hash and PID records.
 */
const {readBuildIdentity} = require('../build-identity.cjs');

/**
 * Captures exact Select Java/native mismatches and initial Tree tab stops in both engines.
 * This is an observation probe; its pointer-collapse check is explicitly asserted separately.
 */
async function main() {
    /*
     * Read every failed fixture in one fresh session to expose values omitted by a timeout trace.
     */
    const identity = readBuildIdentity();
    assert.equal(identity.sha256, process.env.EXPECTED_BUILD_SHA?.toUpperCase());
    for (const engine of ['chromium', 'firefox']) {
        const output = path.join(__dirname, 'evidence', identity.sha256, engine);
        fs.mkdirSync(output, {recursive: true});
        const browser = await (engine === 'firefox' ? firefox : chromium).launch({headless: true, ...(engine === 'firefox' ? {} : {channel: 'chrome'})});
        const observations = {identity, engine};
        try {
            const page = await browser.newPage();
            await page.goto(new URL('/showcase-regressions', identity.url).href);
            await page.locator('[data-showcase-regressions-ready]').waitFor();
            for (const name of ['explicit-existing', 'explicit-empty', 'all-disabled', 'listbox-default', 'multiple-default', 'explicit-property']) {
                await page.getByRole('button', {name: 'Read ' + name, exact: true}).click();
                await page
                    .locator('[data-regression-feedback="' + name + '"]')
                    .filter({hasText: /^Java value: .*; callbacks: \d+$/})
                    .waitFor();
            }
            await page.getByLabel('Tree selection mode').focus();
            await page.keyboard.press('Tab');
            await page.waitForTimeout(200);
            observations.initial = await page.evaluate(() => ({
                selects: [...document.querySelectorAll('[data-regression] select')].map((element) => ({
                    name: element.getAttribute('aria-label'),
                    value: element.value,
                    index: element.selectedIndex,
                    size: element.size,
                    multiple: element.multiple,
                    selected: [...element.selectedOptions].map((option) => option.value),
                    java: document.querySelector('[data-regression-feedback="' + element.getAttribute('aria-label') + '"]')?.textContent
                })),
                focus: {tag: document.activeElement?.tagName, text: document.activeElement?.textContent, role: document.activeElement?.getAttribute('role')},
                treeStops: [...document.querySelectorAll('[role="tree"][aria-label="Project files"] [role="treeitem"]')].map((element) => ({
                    name: element.getAttribute('aria-label'),
                    tabIndex: element.tabIndex
                }))
            }));
            const sample = page.locator('[data-demo="Tree"]');
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Sources', {exact: true}).waitFor();
            await sample.getByText('+', {exact: true}).first().click();
            await page.getByRole('treeitem', {name: 'Main.java', exact: true}).focus();
            await page.getByRole('treeitem', {name: 'Sources', exact: true}).locator(':scope > .geshra-tree-label').getByText('-', {exact: true}).click();
            await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === 'Sources' && document.activeElement.getAttribute('tabindex') === '0');
            observations.pointerCollapse = await page.evaluate(() => ({
                active: document.activeElement.getAttribute('aria-label'),
                stops: [...document.querySelectorAll('[role="tree"][aria-label="Project files"] [role="treeitem"][tabindex="0"]')].map((element) =>
                    element.getAttribute('aria-label')
                )
            }));
            assert.deepEqual(observations.pointerCollapse, {active: 'Sources', stops: ['Sources']});
            observations.pointerCollapseAssertion = 'PASS after asynchronous focus and roving state settle';
        } finally {
            try {
                fs.writeFileSync(path.join(output, 'focused-observations.json'), JSON.stringify(observations, null, 2));
            } finally {
                await browser.close();
            }
        }
        console.log(JSON.stringify(observations));
    }
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
