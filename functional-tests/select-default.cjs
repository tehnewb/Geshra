/**
 * Existing browser dependency for a read-only Select default snapshot.
 */
const {chromium, firefox} = require('../browser-tests/node_modules/playwright');
/**
 * Writes exact DOM observations for independent review.
 */
const fs = require('node:fs');
/**
 * Records grouped-option selection before any trusted user edit in both engines.
 */
async function main() {
    /*
     * The sample does not expose an initial Java getter, so never infer that value from DOM state.
     */
    const observations = [];
    for (const engine of ['chromium', 'firefox']) {
        const browser = await (engine === 'firefox' ? firefox : chromium).launch({headless: true, ...(engine === 'firefox' ? {} : {channel: 'chrome'})});
        try {
            const page = await browser.newPage();
            await page.goto('http://localhost:4050/showcase');
            await page.locator('[data-showcase-ready]').waitFor();
            observations.push({
                engine,
                browser: browser.version(),
                javaInitialValue: 'NOT OBSERVABLE: no initial getter/read action in this sample',
                dom: await page.getByLabel('Select,Option,OptionGroup', {exact: true}).evaluate((element) => ({
                    value: element.value,
                    selectedIndex: element.selectedIndex,
                    options: [...element.options].map((option) => ({value: option.value, text: option.text, selected: option.selected, parent: option.parentElement.tagName})),
                    outerHTML: element.outerHTML
                })),
                feedback: await page.locator('[data-feedback="Select,Option,OptionGroup"]').innerText()
            });
        } finally {
            await browser.close();
        }
    }
    fs.writeFileSync(require('node:path').join(__dirname, 'corrected/select-default.json'), JSON.stringify(observations, null, 2));
    console.log(JSON.stringify(observations));
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
