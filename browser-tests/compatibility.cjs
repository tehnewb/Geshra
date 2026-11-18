const assert = require('node:assert/strict');
const {chromium} = require('playwright');
const {resolve} = require('node:path');

/**
 * Runs the current bundled browser against a separately started legacy example server.
 * BASELINE_URL must identify an unmodified previous-revision server. Only the runtime response
 * is replaced; routing, event decoding, authentication, and server DOM packets remain legacy.
 */
async function verifyCompatibility() {
    if (!process.env.BASELINE_URL) throw new Error('Set BASELINE_URL to a running previous-revision example server');
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    const page = await browser.newPage();
    const sent = [];
    const errors = [];
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('websocket', (socket) => socket.on('framesent', (event) => sent.push(Buffer.from(event.payload))));
    await page.route('**/js/runtime.js', (route) => route.fulfill({path: resolve(__dirname, '../src/main/resources/web/js/runtime.js'), contentType: 'application/javascript'}));
    await page.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    try {
        await page.goto(process.env.BASELINE_URL + '/');
        await page.getByRole('button', {name: 'Click me', exact: true}).click();
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
        assert.ok(sent.some((bytes) => bytes[0] === 1));
        assert.equal(
            sent.some((bytes) => bytes[0] === 12),
            false
        );
        await page.goto(process.env.BASELINE_URL + '/login');
        await page.getByLabel('Username', {exact: true}).fill('demo');
        await page.getByLabel('Password', {exact: true}).fill(process.env.DEMO_PASSWORD || 'browser-test-password');
        await page.getByRole('button', {name: 'Sign in', exact: true}).click();
        await page.getByRole('heading', {name: 'Welcome demo', exact: true}).waitFor();
        assert.deepEqual(errors, []);
        console.log(JSON.stringify({passed: true, currentBrowserWithLegacyServer: true}));
    } finally {
        await browser.close();
    }
}

verifyCompatibility().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
