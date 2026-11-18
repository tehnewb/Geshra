/**
 * Filesystem access for persisted correctness evidence.
 */
const fs = require('node:fs');
/**
 * Platform-specific evidence path construction.
 */
const path = require('node:path');
/**
 * Strict assertion support for independently observable outcomes.
 */
const assert = require('node:assert/strict');
/**
 * Existing project browser dependency; no added installation is required.
 */
const {chromium, firefox} = require('../browser-tests/node_modules/playwright');
/**
 * Verifies actual build artifact and recorded launch identity for every execution.
 */
const {readBuildIdentity} = require('./build-identity.cjs');

/**
 * Exercises the shared showcase in an isolated browser context and saves each result, including
 * failures, without changing the server or maintained demonstration sources.
 */
async function main() {
    /*
     * Continue independent cases after a failure so one defect cannot conceal unrelated coverage.
     */
    const engine = process.env.BROWSER || 'chromium';
    const identity = readBuildIdentity();
    const browser = await (engine === 'firefox' ? firefox : chromium).launch({headless: true, ...(engine === 'firefox' ? {} : {channel: 'chrome'})});
    const outputRoot = path.join(__dirname, 'final', engine);
    fs.mkdirSync(outputRoot, {recursive: true});
    const context = await browser.newContext({viewport: {width: 1440, height: 1000}});
    const page = await context.newPage();
    page.setDefaultTimeout(5000);
    const results = [];
    const diagnostics = [];
    page.on('pageerror', (error) => diagnostics.push({kind: 'pageerror', message: error.message}));
    page.on('console', (message) => {
        if (message.type() === 'error') diagnostics.push({kind: 'console', message: message.text()});
    });
    page.on('requestfailed', (request) => diagnostics.push({kind: 'requestfailed', url: request.url(), error: request.failure()}));
    await context.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    const base = process.env.BASE_URL || 'http://localhost:4050';
    const button = (name) => page.getByRole('button', {name, exact: true});

    /**
     * Records one independently reviewable case and captures its failing viewport when necessary.
     */
    async function check(name, action) {
        try {
            await action();
            results.push({name, status: 'PASS'});
        } catch (error) {
            const screenshot = path.join(outputRoot, name.replace(/[^a-z0-9]+/gi, '-') + '.png');
            let screenshotFailure = null; // Capture errors remain visible in the case evidence.
            try {
                await page.screenshot({path: screenshot});
            } catch (captureError) {
                screenshotFailure = captureError.message;
            }
            results.push({name, status: 'FAIL', error: error.message, screenshot, screenshotFailure});
        }
        console.log(JSON.stringify(results.at(-1)));
    }

    try {
        await page.goto(base + '/showcase');
        await page.locator('[data-showcase-ready="true"]').waitFor();
        await page.waitForFunction(() => document.querySelectorAll('.geshra-virtual-row').length > 0);
        await page.screenshot({path: path.join(outputRoot, 'showcase-initial.png'), fullPage: true});
        const samples = await page
            .locator('[data-types]')
            .evaluateAll((nodes) => nodes.map((node) => ({demo: node.dataset.demo, types: node.dataset.types, text: node.innerText})));
        fs.writeFileSync(path.join(outputRoot, 'rendered-samples.json'), JSON.stringify(samples, null, 2));
        await check('correct document title', async () => assert.equal(await page.title(), 'Geshra UI showcase'));
        await check('source inventory coverage', async () => {
            const inventory = JSON.parse(fs.readFileSync(path.join(__dirname, 'component-inventory.json'), 'utf8').replace(/^\uFEFF/, ''));
            const represented = new Set(samples.flatMap((sample) => sample.types.split(',').map((type) => type.trim())));
            assert.deepEqual([...represented].sort(), inventory.map((item) => item.Name).sort());
        });
        await check('image constructor accessible description', async () => {
            const image = page.locator('[data-demo="Image"] img');
            await image.evaluate((element) => element.decode());
            assert.equal(await image.getAttribute('alt'), 'Blue Geshra sample illustration');
        });
        await check('tree expansion selection and rename retains descendants', async () => {
            const sample = page.locator('[data-demo="Tree"]');
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Sources', {exact: true}).waitFor({state: 'visible'});
            await sample.getByText('+', {exact: true}).first().click();
            await sample.getByText('Main.java', {exact: true}).waitFor({state: 'visible'});
            await sample.getByText('Main.java', {exact: true}).click();
            await button('Read tree selection').click();
            await sample.getByText('Selected nodes: 1', {exact: true}).waitFor();
            await button('Rename tree branch').click();
            await sample.getByText('Renamed sources', {exact: true}).waitFor();
            assert.equal(await sample.getByText('Main.java', {exact: true}).isVisible(), true);
        });
        const values = {
            TextField: 'Edited 日本語',
            NativeTextField: 'Native edited',
            InputField: 'Generic edited',
            SearchField: 'needle',
            EmailField: 'test@example.org',
            PasswordField: 'secret-test',
            UrlField: 'https://example.org/path',
            TelephoneField: '+1 555 0199',
            DateField: '2026-12-31',
            DateTimeField: '2026-12-31T23:59',
            TimeField: '23:59',
            MonthField: '2026-12',
            WeekField: '2026-W52',
            ColorField: '#123456',
            NumberField: '0',
            TextArea: 'Line A\n日本語'
        };
        for (const [name, value] of Object.entries(values)) {
            await check('field ' + name + ' trusted edit and Java callback', async () => {
                await page.getByLabel(name, {exact: true}).fill(value);
                if (name === 'PasswordField') {
                    const feedback = page.locator('[data-feedback="PasswordField"]');
                    await feedback.filter({hasText: 'Password updated (' + value.length + ' characters)'}).waitFor();
                    assert.equal(await feedback.textContent(), 'Password updated (' + value.length + ' characters)');
                    assert.equal((await page.locator('body').innerText()).includes(value), false, 'Password plaintext must not appear in visible page text');
                    assert.equal((await feedback.textContent()).includes(value), false);
                    return;
                }
                await page
                    .locator('[data-feedback="' + name + '"]')
                    .filter({hasText: (name === 'InputField' ? 'Native input event: ' : 'Java value: ') + value})
                    .waitFor();
            });
        }
        await check('checkbox switch radio range and multiselect', async () => {
            await page.getByLabel('Checkbox', {exact: true}).uncheck();
            await page.locator('[data-feedback="Checkbox"]').filter({hasText: 'Java value: false'}).waitFor();
            await page.getByLabel('Switch', {exact: true}).uncheck();
            await page.locator('[data-feedback="Switch"]').filter({hasText: 'Java value: false'}).waitFor();
            await page.getByLabel('Beta', {exact: true}).check();
            await page.getByText('Alpha=false; Beta=true', {exact: true}).waitFor();
            await page.getByLabel('RangeField', {exact: true}).focus();
            await page.keyboard.press('ArrowRight');
            await page.locator('[data-feedback="RangeField"]').filter({hasText: 'Java value: 41'}).waitFor();
            await page.getByLabel('MultiSelect', {exact: true}).selectOption(['a,b', '日本語']);
            const multiFeedback = page.locator('[data-feedback="MultiSelect"]');
            await multiFeedback.filter({hasText: 'Java value: [a,b, 日本語]'}).waitFor();
            assert.equal(await multiFeedback.textContent(), 'Java value: [a,b, 日本語]');
            assert.deepEqual(await page.getByLabel('MultiSelect', {exact: true}).evaluate((element) => [...element.selectedOptions].map((option) => option.value)), [
                'a,b',
                '日本語'
            ]);
            assert.equal(await page.getByLabel('MultiSelect', {exact: true}).evaluate((element) => element.selectedOptions.length), 2);
        });
        await check('disabled readonly invalid and form submit validation', async () => {
            assert.equal(await page.getByLabel('Disabled text', {exact: true}).isDisabled(), true);
            assert.equal(await page.getByLabel('Read-only text', {exact: true}).isEditable(), false);
            assert.equal(await page.getByLabel('Invalid email example', {exact: true}).evaluate((element) => element.validity.valid), false);
            await page.getByLabel('Form name', {exact: true}).fill('Grace');
            await button('Submit showcase form').click();
            await page.getByText(/Submitted:.*Grace/).waitFor();
            await page.getByLabel('Form name', {exact: true}).fill('');
            await button('Submit showcase form').click();
            assert.equal(await page.getByLabel('Form name', {exact: true}).evaluate((element) => element.validity.valueMissing), true);
        });
        await check('file uploader server callback', async () => {
            await page.getByLabel('Upload text files', {exact: true}).setInputFiles({name: 'functional-test.txt', mimeType: 'text/plain', buffer: Buffer.from('Hello 日本語')});
            await page.getByText('Uploaded functional-test.txt: 15 bytes', {exact: true}).waitFor();
        });
        await check('table clear reset and tab switching', async () => {
            await button('Clear table').click();
            await page.getByText('Empty table', {exact: true}).waitFor();
            assert.equal(await page.locator('[data-demo="Table"] tbody tr').count(), 0);
            await button('Reset table').click();
            await page.getByText('2 rows restored', {exact: true}).waitFor();
            assert.equal(await page.locator('[data-demo="Table"] tbody tr').count(), 2);
            await button('Settings').click();
            await page.getByText('Settings tab content', {exact: true}).waitFor();
            await button('Overview').click();
            await page.getByText('Overview tab content', {exact: true}).waitFor();
        });
        await check('disclosure modal popover tooltip', async () => {
            await page.getByText('Disclosure', {exact: true}).click();
            await page.getByText('Disclosure open', {exact: true}).waitFor();
            await button('Open dialog').click();
            await page.locator('dialog[open]').waitFor();
            await button('Close dialog').click();
            await page.getByText('Dialog result: accepted', {exact: true}).waitFor();
            await button('Open dialog').click();
            await page.locator('dialog[open]').waitFor();
            await page.keyboard.press('Escape');
            await page.locator('dialog[open]').waitFor({state: 'detached'});
            await button('Show popover').click();
            await page.locator(':popover-open').waitFor();
            await page.keyboard.press('Escape');
            await page.locator(':popover-open').waitFor({state: 'detached'});
            await button('Show tooltip').click();
            await page.getByRole('tooltip').waitFor();
            await button('Hide tooltip').click();
            await page.getByRole('tooltip').waitFor({state: 'hidden'});
        });
        await check('progress toolbar canvas svg and media load', async () => {
            await button('Complete progress').click();
            await page.getByText('Complete: 100 percent', {exact: true}).waitFor();
            assert.equal(await page.getByLabel('Determinate progress', {exact: true}).evaluate((element) => element.value), 100);
            await button('Reset progress').click();
            await page.getByText('Ready: 40 percent', {exact: true}).waitFor();
            await button('Toolbar action').click();
            await page.getByText('Toolbar clicked', {exact: true}).waitFor();
            await button('Draw canvas circle').click();
            await page.waitForFunction(() => document.querySelector('canvas').getContext('2d').getImageData(170, 50, 1, 1).data[1] === 163);
            assert.equal(await page.locator('svg *').evaluateAll((nodes) => nodes.every((node) => node.namespaceURI === 'http://www.w3.org/2000/svg')), true);
            await page.waitForFunction(() => [...document.querySelectorAll('audio,video')].every((element) => element.readyState > 0 && !element.error), null, {timeout: 5000});
        });
        await check('virtual list last refresh trusted wheel', async () => {
            await button('Jump to last item').click();
            await page.getByText('Item 999999', {exact: true}).waitFor();
            await button('Refresh virtual list').click();
            await page.getByText('Item 999999', {exact: true}).waitFor();
            const list = page.getByLabel('Virtual dataset');
            assert.ok((await list.locator('.geshra-virtual-row').count()) < 30);
            await list.hover();
            await page.mouse.wheel(0, -256);
            await page.waitForFunction(() => document.querySelector('[aria-label="Virtual dataset"]').scrollTop < 31999744);
        });
        await check('retained component move detach restore dispose', async () => {
            const input = page.getByLabel('Retained input');
            await input.fill('retained 日本語');
            const original = await input.elementHandle();
            await button('Move input').click();
            assert.equal(await input.evaluate((element, prior) => element === prior, original), true);
            await button('Detach input').click();
            await input.waitFor({state: 'detached'});
            await button('Restore input').click();
            await input.waitFor();
            assert.equal(await input.inputValue(), 'retained 日本語');
            await button('Dispose input').click();
            await input.waitFor({state: 'detached'});
        });
    } finally {
        try {
            fs.writeFileSync(
                path.join(outputRoot, 'showcase-results.json'),
                JSON.stringify(
                    {
                        date: new Date().toISOString(),
                        base,
                        identity,
                        engine,
                        browser: browser.version(),
                        results,
                        diagnostics
                    },
                    null,
                    2
                )
            );
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
