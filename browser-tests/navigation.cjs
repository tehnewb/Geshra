/**
 * Strict checks for route interception versus native browser/Android navigation behavior.
 */
const assert = require('node:assert/strict');
/**
 * Existing real-browser engines; no new production or test dependency is introduced.
 */
const {chromium, firefox} = require('playwright');

/**
 * Verifies ordinary routes still use one WebSocket navigation while native-intent links do not.
 * A later observer cancels default browser actions only after inspecting the runtime decision,
 * avoiding external windows/download dialogs during this focused desktop regression.
 */
async function verifyNavigation() {
    const engine = process.env.BROWSER === 'firefox' ? firefox : chromium;
    const browser = await engine.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    const page = await browser.newPage();
    let navigationPackets = 0; // Count actual transport frames independently of DOM event checks.
    page.on('websocket', (socket) => {
        socket.on('framesent', ({payload}) => {
            const bytes = Buffer.from(payload);
            if ((bytes[0] === 12 ? bytes[1] : bytes[0]) === 0) navigationPackets++;
        });
    });
    try {
        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + '/showcase');
        await page.locator('[data-showcase-ready="true"]').waitFor();
        const initialPackets = navigationPackets;
        const outcomes = await page.evaluate(() => {
            const cases = [
                {name: 'download', href: '/showcase-assets/sample.svg', attributes: {download: 'sample.svg'}, expected: false},
                {name: 'new window', href: '/showcase', attributes: {target: '_blank'}, expected: false},
                {name: 'parent window', href: '/showcase', attributes: {target: '_parent'}, expected: false},
                {name: 'fragment', href: '/showcase#details', expected: false},
                {name: 'empty fragment', href: '#', expected: false},
                {name: 'query', href: '/showcase?mode=all', expected: false},
                {name: 'control click', href: '/showcase', modifiers: {ctrlKey: true}, expected: false},
                {name: 'meta click', href: '/showcase', modifiers: {metaKey: true}, expected: false},
                {name: 'shift click', href: '/showcase', modifiers: {shiftKey: true}, expected: false},
                {name: 'alt click', href: '/showcase', modifiers: {altKey: true}, expected: false},
                {name: 'non-primary click', href: '/showcase', modifiers: {button: 1}, expected: false},
                {name: 'external origin', href: 'https://outside.example.test/', expected: false},
                {name: 'previously canceled', href: '/showcase', canceled: true, expected: true},
                {name: 'ordinary route', href: '/showcase', attributes: {target: '_SELF'}, expected: true}
            ];
            return cases.map((item) => {
                const anchor = document.createElement('a');
                anchor.href = item.href;
                for (const [key, value] of Object.entries(item.attributes || {})) anchor.setAttribute(key, value);
                document.body.appendChild(anchor);
                const click = new MouseEvent('click', {bubbles: true, cancelable: true, button: 0, ...item.modifiers});
                if (item.canceled) click.preventDefault();
                let intercepted = null; // Captured before the observer prevents native side effects.
                document.body.addEventListener(
                    'click',
                    (event) => {
                        intercepted = event.defaultPrevented;
                        event.preventDefault();
                    },
                    {once: true}
                );
                anchor.dispatchEvent(click);
                anchor.remove();
                return {name: item.name, intercepted, expected: item.expected};
            });
        });
        for (const outcome of outcomes) assert.equal(outcome.intercepted, outcome.expected, outcome.name);
        await page.waitForFunction(() => document.querySelector('[data-showcase-ready="true"]') !== null);
        await page.waitForTimeout(150);
        assert.equal(navigationPackets - initialPackets, 1, 'only the plain route sends a navigation packet');
        await page.evaluate(() => {
            window.fragmentMarker = 'preserved';
            const anchor = document.createElement('a');
            anchor.href = '#native-fragment';
            anchor.id = 'native-fragment-link';
            anchor.textContent = 'Native fragment';
            document.body.appendChild(anchor);
        });
        await page.locator('#native-fragment-link').click();
        await page.waitForURL('**/showcase#native-fragment');
        await page.goBack();
        await page.waitForURL('**/showcase');
        await page.goForward();
        await page.waitForURL('**/showcase#native-fragment');
        assert.equal(await page.evaluate(() => window.fragmentMarker), 'preserved');
        assert.equal(navigationPackets - initialPackets, 1, 'fragment Back/Forward does not rebuild the server route');
        console.log(JSON.stringify({passed: true, browser: process.env.BROWSER || 'chromium', cases: outcomes.length, navigationPackets: navigationPackets - initialPackets}));
    } finally {
        await browser.close();
    }
}

verifyNavigation().catch((failure) => {
    console.error(failure);
    process.exitCode = 1;
});
