/**
 * Existing browser automation dependency used for trusted interaction checks.
 */
const {chromium, firefox} = require('../browser-tests/node_modules/playwright');
/**
 * Strict behavioral assertions.
 */
const assert = require('node:assert/strict');
/**
 * Filesystem writer for audit evidence.
 */
const fs = require('node:fs');
/**
 * Portable result path construction.
 */
const path = require('node:path');
/**
 * Verifies the actual current packaged build before collecting evidence.
 */
const {readBuildIdentity} = require('./build-identity.cjs');

/**
 * Validates additional boundaries, semantics and native playback in a fresh isolated session.
 */
async function main() {
    /*
     * Test cases retain independent outcomes so unsupported keyboard behavior stays visible.
     */
    const engine = process.env.BROWSER || 'chromium';
    const identity = readBuildIdentity();
    const browser = await (engine === 'firefox' ? firefox : chromium).launch({headless: true, ...(engine === 'firefox' ? {} : {channel: 'chrome'})});
    const outputRoot = path.join(__dirname, 'final', engine);
    fs.mkdirSync(outputRoot, {recursive: true});
    const page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    page.setDefaultTimeout(5000);
    const results = [];
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('requestfailed', (request) => errors.push(request.url() + ': ' + request.failure()?.errorText));
    await page.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    const button = (name) => page.getByRole('button', {name, exact: true});

    /**
     * Captures a case result without preventing independent later observations.
     */
    async function check(name, action) {
        try {
            const observations = await action();
            results.push({name, status: 'PASS', observations});
        } catch (error) {
            results.push({name, status: 'FAIL', error: error.message});
            await page.screenshot({path: path.join(outputRoot, name.replace(/[^a-z0-9]+/gi, '-') + '.png')});
        }
        console.log(JSON.stringify(results.at(-1)));
    }

    try {
        await page.goto('http://localhost:4050/showcase');
        await page.locator('[data-showcase-ready]').waitFor();
        await check('untouched form defaults and label checkbox activation', async () => {
            await button('Submit showcase form').click();
            const output = page.locator('[data-demo="Form"] output');
            await output.filter({hasText: 'name=Ada'}).waitFor();
            assert.match(await output.innerText(), /updates=false/);
            await page.getByText('Receive updates', {exact: true}).click();
            assert.equal(await page.locator('input[name="updates"]').isChecked(), true);
            await button('Submit showcase form').click();
            await output.filter({hasText: 'updates=true'}).waitFor();
            return output.innerText();
        });
        await check('checkbox label mixed and numeric native default values', async () => {
            const mixed = page.getByLabel('Indeterminate checkbox', {exact: true});
            assert.equal(await mixed.evaluate((element) => element.indeterminate), true);
            await page.getByText('Mixed checkbox', {exact: true}).click();
            assert.equal(await mixed.evaluate((element) => element.indeterminate), false);
            assert.equal(await page.getByLabel('NumberField', {exact: true}).inputValue(), '3.5');
            assert.equal(Number(await page.getByLabel('RangeField', {exact: true}).inputValue()), 40);
        });
        await check('explicit configured Select Java value', async () => {
            assert.equal(await page.getByLabel('Select,Option,OptionGroup', {exact: true}).inputValue(), 'java');
            return {scope: 'Showcase explicit setValue only; untouched grouped-native-default bug remains OPEN'};
        });
        await check('exact media response MIME and byte lengths', async () => {
            const observations = [];
            for (const [file, mime, length] of [
                ['sample.wav', 'audio/wav', 32044],
                ['sample.webm', 'video/webm', 5418]
            ]) {
                const response = await page.request.get('http://localhost:4050/showcase-assets/' + file);
                assert.equal(response.status(), 200);
                assert.equal(response.headers()['content-type'], mime);
                assert.equal((await response.body()).length, length);
                observations.push({file, mime, length});
            }
            return observations;
        });
        await check('tree multiple ctrl shift single none and collapse after rename', async () => {
            const sample = page.locator('[data-demo="Tree"]');
            const mode = page.getByLabel('Tree selection mode');
            const selected = sample.locator('p').last();
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Sources', {exact: true}).waitFor({state: 'visible'});
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Main.java', {exact: true}).waitFor({state: 'visible'});
            await sample.getByText('Sources', {exact: true}).click();
            await sample.getByText('README.md', {exact: true}).click({modifiers: ['Control']});
            await button('Read tree selection').click();
            await selected.filter({hasText: 'Selected nodes: 2'}).waitFor();
            await sample.getByText('Project', {exact: true}).click();
            await sample.getByText('README.md', {exact: true}).click({modifiers: ['Shift']});
            await button('Read tree selection').click();
            await selected.filter({hasText: 'Selected nodes: 4'}).waitFor();
            await mode.selectOption('SINGLE');
            await sample.getByText('Sources', {exact: true}).click();
            await button('Read tree selection').click();
            await selected.filter({hasText: 'Selected nodes: 1'}).waitFor();
            await mode.selectOption('NONE');
            await sample.getByText('README.md', {exact: true}).click();
            await button('Read tree selection').click();
            await selected.filter({hasText: 'Selected nodes: 1'}).waitFor();
            await button('Rename tree branch').click();
            await sample.getByText('Renamed sources', {exact: true}).waitFor();
            await sample.getByText('-', {exact: true}).nth(1).click();
            await sample.getByText('Main.java', {exact: true}).waitFor({state: 'hidden'});
            await sample.getByText('+', {exact: true}).click();
            await sample.getByText('Main.java', {exact: true}).waitFor();
        });
        await check('datalist hidden resource and inert script', async () => {
            const suggestion = page.getByLabel('Language suggestion');
            const associated = await suggestion.evaluate((element) => ({list: element.list?.id, values: [...(element.list?.options || [])].map((option) => option.value)}));
            assert.ok(associated.list);
            assert.deepEqual(associated.values, ['Java', 'JavaScript', 'Kotlin']);
            await suggestion.fill('Custom suggestion');
            assert.equal(await suggestion.inputValue(), 'Custom suggestion');
            await button('Read hidden value').click();
            await page.getByText('HiddenField value: hidden-token', {exact: true}).waitFor();
            assert.equal(await page.locator('input[type="hidden"]').isVisible(), false);
            assert.equal(await page.locator('[data-demo="Script"] script').textContent(), '');
            return page.locator('link[rel="stylesheet"]').evaluateAll((elements) => elements.map((element) => ({href: element.href, loaded: !!element.sheet})));
        });
        await check('native media trusted keyboard play and Java pause', async () => {
            const observations = [];
            for (const type of ['audio', 'video']) {
                const media = page.locator(type);
                await media.scrollIntoViewIfNeeded();
                await media.focus();
                await page.keyboard.press('Space');
                await page.waitForFunction((tag) => !document.querySelector(tag).paused, type, {timeout: 2000});
                await page.getByText(type === 'audio' ? 'Audio playing' : 'Video playing', {exact: true}).waitFor();
                await page.waitForFunction((tag) => document.querySelector(tag).currentTime > 0.05, type, {timeout: 2000});
                await button(type === 'audio' ? 'Pause audio' : 'Pause video').click();
                await page.getByText(type === 'audio' ? 'Audio paused' : 'Video paused', {exact: true}).waitFor();
                observations.push(
                    await media.evaluate((element) => ({
                        tag: element.tagName,
                        paused: element.paused,
                        time: element.currentTime,
                        duration: element.duration,
                        error: element.error?.message
                    }))
                );
            }
            return observations;
        });
        await check('all static semantic component samples', async () => {
            const groups = {
                H1: ['h1', 'h2', 'h3', 'h4', 'h5', 'h6'],
                Paragraph: ['p', 'span', 'strong', 'em', 'small', 'mark', 'code', 'pre', 'blockquote'],
                DefinitionList: ['dl', 'dt', 'dd'],
                OrderedList: ['ol', 'ul', 'li'],
                Main: ['main', 'header', 'nav', 'article', 'section', 'aside', 'footer', 'div'],
                HorizontalLayout: ['.geshra-horizontal', '.geshra-grid', '.geshra-stack', '.geshra-spacer', '.geshra-button-bar', '.geshra-toolbar'],
                Badge: ['.geshra-badge', '.geshra-spinner', 'progress', 'meter', '[role="alert"]', '.geshra-card', '.geshra-panel', 'hr'],
                Table: ['table', 'thead', 'tbody', 'tr', 'th', 'td', 'caption'],
                Form: ['form', 'fieldset', 'legend', 'output', 'label'],
                Svg: ['svg', 'g', 'rect', 'circle', 'line', 'polyline', 'polygon', 'path', 'text']
            };
            const observations = [];
            for (const [demo, selectors] of Object.entries(groups)) {
                const sample = page.locator('[data-demo="' + demo + '"]').first();
                for (const selector of selectors) assert.ok((await sample.locator(selector).count()) > 0, demo + ': missing ' + selector);
                observations.push({demo, selectors});
            }
            assert.equal(await page.locator('[data-demo="Paragraph"] pre').textContent(), 'Line 1\n    Indented line 2');
            const meter = await page
                .locator('meter')
                .evaluate((element) => ({value: element.value, max: element.max, low: element.low, high: element.high, optimum: element.optimum}));
            assert.deepEqual(meter, {value: 7, max: 10, low: 3, high: 8, optimum: 7});
            assert.equal(await page.getByLabel('Indeterminate progress', {exact: true}).getAttribute('value'), null);
            await button('Button bar action').click();
            await page.getByText('Button bar clicked', {exact: true}).waitFor();
            await button('Toolbar action').click();
            await page.getByText('Toolbar clicked', {exact: true}).waitFor();
            return observations;
        });
        await check('tab native keyboard activation and exposed semantics', async () => {
            await button('Settings').focus();
            await page.keyboard.press('Enter');
            await page.getByText('Settings tab content', {exact: true}).waitFor();
            await button('Overview').focus();
            await page.keyboard.press('Space');
            await page.getByText('Overview tab content', {exact: true}).waitFor();
            return page.locator('[data-demo="Tabbed"] button').evaluateAll((elements) =>
                elements.map((element) => ({
                    text: element.textContent,
                    role: element.getAttribute('role'),
                    selected: element.getAttribute('aria-selected'),
                    tabIndex: element.tabIndex
                }))
            );
        });
        await check('native disclosure accordion keyboard and listener removal', async () => {
            const summary = page.getByText('First section', {exact: true});
            await summary.focus();
            await page.keyboard.press('Enter');
            await page.getByText('First', {exact: true}).waitFor();
            await summary.focus();
            await page.keyboard.press('Space');
            await page.getByText('First', {exact: true}).waitFor({state: 'hidden'});
            await button('Removable listener').click();
            await page.getByText('Native calls: 1', {exact: true}).waitFor();
            await button('Remove listener').click();
            await button('Removable listener').click();
            await page.waitForTimeout(100);
            assert.equal(await page.getByText('Native calls: 1', {exact: true}).count(), 1);
        });
        await check('390 pixel mobile overflow and reachable actions', async () => {
            await page.setViewportSize({width: 390, height: 844});
            await page.waitForTimeout(100);
            const sizes = await page.evaluate(() => ({
                viewport: innerWidth,
                width: document.documentElement.scrollWidth,
                overflowing: [...document.querySelectorAll('[data-demo]')]
                    .filter((element) => element.scrollWidth > element.clientWidth + 1)
                    .map((element) => ({demo: element.dataset.demo, client: element.clientWidth, scroll: element.scrollWidth}))
            }));
            await page.screenshot({path: path.join(outputRoot, 'mobile-390.png'), fullPage: true});
            fs.writeFileSync(path.join(outputRoot, 'mobile-390.json'), JSON.stringify(sizes, null, 2));
            assert.ok(sizes.width <= 390, JSON.stringify(sizes));
            await button('Show tooltip').click();
            await page.getByRole('tooltip').waitFor();
            await button('Hide tooltip').click();
            return sizes;
        });
    } finally {
        try {
            fs.writeFileSync(path.join(outputRoot, 'extended-results.json'), JSON.stringify({engine, browser: browser.version(), identity, results, errors}, null, 2));
        } finally {
            await browser.close();
        }
        if (results.some((result) => result.status === 'FAIL')) process.exitCode = 1;
    }
}

main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
