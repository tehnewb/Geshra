const assert = require('node:assert/strict');
const {chromium, firefox, webkit} = require('playwright');

// Runs against the separately packaged Spring example; no browser dependency enters the library.
(async () => {
    const engines = {chromium, firefox, webkit};
    const engine = engines[process.env.BROWSER || 'chromium'];
    const browser = await engine.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    const page = await browser.newPage();
    page.setDefaultTimeout(10000);
    const errors = [];
    const viewportPackets = [];
    let inputPackets = 0;
    page.on('pageerror', error => errors.push(error.message));
    page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
    page.on('websocket', socket => socket.on('framesent', event => {
        const bytes = event.payload;
        if (Buffer.isBuffer(bytes) && bytes[0] === 4) inputPackets++;
        if (Buffer.isBuffer(bytes) && bytes[0] === 8) viewportPackets.push(bytes);
    }));
    // The library has no favicon, so avoid unrelated browser favicon requests in these checks.
    await page.route('**/favicon.ico', route => route.fulfill({status: 204}));
    try {
        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + '/components');
        await page.getByRole('heading', {name: 'Component gallery', exact: true}).waitFor();
        await page.waitForFunction(() => document.querySelectorAll('.geshra-virtual-row').length === 12);
        const button = name => page.getByRole('button', {name, exact: true});
        const read = async () => {
            await button('Read Java values').click();
            await page.waitForFunction(() => document.querySelector('[aria-label="Java values"]').textContent.startsWith('range='));
            return page.getByLabel('Java values').innerText();
        };
        const initial = await read();
        assert.match(initial, /range=100.0;checkbox=true;date=;color=#000000;select=alpha/);
        assert.match(initial, /multiple=\[a,b, , 日本語\]/);
        assert.equal(await page.getByLabel('Checkbox', {exact: true}).isChecked(), true);
        await page.getByLabel('Checkbox', {exact: true}).uncheck();
        await page.getByText('Changes: 1', {exact: true}).waitFor();
        await button('Assign values').click();
        await page.waitForFunction(() => document.querySelector('[aria-label="Select"]').selectedIndex === -1);
        await page.waitForTimeout(100);
        assert.equal(await page.getByText('Changes: 1', {exact: true}).count(), 1);
        await read();
        await page.waitForFunction(() => document.querySelector('[aria-label="Java values"]').textContent.includes('select=;multiple=[+, ]'));
        await page.getByLabel('Second radio').check();
        await button('Read Java values').click();
        await page.waitForFunction(() => document.querySelector('[aria-label="Java values"]').textContent.includes('first=false;second=true'));
        await page.getByText('Disclosure', {exact: true}).click();
        await page.getByText('Disclosure open', {exact: true}).waitFor();
        await button('Open dialog').click();
        await page.waitForFunction(() => document.querySelector('dialog').open);
        await button('Close dialog').click();
        await page.getByText('Dialog result: accepted', {exact: true}).waitFor();
        await button('Open dialog').click();
        await page.waitForFunction(() => document.querySelector('dialog').open);
        await page.keyboard.press('Escape');
        await page.waitForFunction(() => !document.querySelector('dialog').open);
        await button('Show popover').click();
        await page.waitForFunction(() => document.querySelector('[popover]').matches(':popover-open'));
        await page.keyboard.press('Escape');
        await page.waitForFunction(() => !document.querySelector('[popover]').matches(':popover-open'));

        const input = page.getByLabel('Retained input');
        await input.fill('native state');
        await input.evaluate(element => {window.retainedNode = element; element.setSelectionRange(2, 5);});
        await button('Move input').click();
        assert.equal(await input.evaluate(element => element === window.retainedNode), true);
        await button('Detach input').click();
        await input.waitFor({state: 'detached'});
        await button('Restore input').click();
        await input.waitFor();
        assert.equal(await input.inputValue(), 'native state');
        assert.equal(await input.evaluate(element => element === window.retainedNode), true);
        await button('Dispose input').click();
        await input.waitFor({state: 'detached'});
        const beforeDisposedInput = inputPackets;
        await page.evaluate(() => window.retainedNode.dispatchEvent(new Event('input', {bubbles: true})));
        await page.waitForTimeout(25);
        assert.equal(inputPackets, beforeDisposedInput, 'disposed native listeners must not send stale component IDs');
        await button('Removable listener').click();
        await page.getByText('Native calls: 1', {exact: true}).waitFor();
        await button('Remove listener').click();
        await button('Removable listener').click();
        await page.waitForTimeout(75);
        assert.equal(await page.getByText('Native calls: 1', {exact: true}).count(), 1);

        const list = page.getByLabel('Virtual dataset');
        const firstRow = await list.locator('.geshra-virtual-row').first().getAttribute('id');
        await list.evaluate(element => {window.overlap = element.querySelectorAll('.geshra-virtual-row')[5]; element.scrollTop = 64;});
        await page.waitForFunction(() => document.querySelector('.geshra-virtual-row').getAttribute('aria-posinset') === '5');
        assert.ok(await list.locator('.geshra-virtual-row').count() <= 18);
        assert.equal(await list.evaluate(element => Array.from(element.querySelectorAll('.geshra-virtual-row')).includes(window.overlap)), true);
        await button('Jump to last item').click();
        await list.getByText('Item 999999', {exact: true}).waitFor();
        assert.ok(await list.locator('.geshra-virtual-row').count() <= 16);
        assert.ok(await list.evaluate(element => element.scrollHeight) <= 8000001);
        assert.ok(await list.getByText('Item 999999', {exact: true}).evaluate(element => {
            const row = element.getBoundingClientRect();
            const viewport = element.closest('.geshra-virtual-list').getBoundingClientRect();
            return row.top >= viewport.top - 1 && row.bottom <= viewport.bottom + 1;
        }));
        await button('Refresh virtual list').click();
        await list.getByText('Item 999999', {exact: true}).waitFor();
        assert.ok(await list.locator('.geshra-virtual-row').count() <= 16);
        assert.ok(viewportPackets.length < 20, 'unchanged windows must not continuously send packets');
        assert.equal(await page.getByLabel('SVG example').evaluate(element => element.firstElementChild.namespaceURI), 'http://www.w3.org/2000/svg');
        assert.deepEqual(await page.getByLabel('Canvas example').evaluate(element => Array.from(element.getContext('2d').getImageData(15, 15, 1, 1).data)), [21, 101, 192, 255]);
        assert.deepEqual(errors, []);
        console.log(JSON.stringify({passed: true, browser: process.env.BROWSER || 'chromium', virtualItems: 1000000, liveRows: await list.locator('.geshra-virtual-row').count(), viewportPackets: viewportPackets.length, initialFirstRow: firstRow}));
    } finally {
        await browser.close();
    }
})().catch(error => {console.error(error); process.exitCode = 1;});
