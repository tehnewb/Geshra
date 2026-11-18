/**
 * Production optimization regressions use genuine HTTP/WS and native callbacks, not timed scores.
 */
const assert = require('node:assert/strict');
/**
 * Existing pinned development browsers; the production library has no frontend test dependency.
 */
const {chromium, firefox} = require('playwright');
/**
 * Current generated bundle path for real old-server compatibility checks.
 */
const {resolve} = require('node:path');
/**
 * Intercepts only the runtime response, preserving the document's actual local network origin.
 */
const {readFileSync} = require('node:fs');

/**
 * Verifies private validators and absence of validators on page/session responses.
 */
async function checkValidators(context, base) {
    const asset = await context.request.get(base + '/js/runtime.js');
    assert.equal(asset.status(), 200);
    const tag = asset.headers().etag;
    assert.match(tag, /^W\/"[a-f0-9]{64}"$/);
    assert.equal(asset.headers()['cache-control'], 'private, no-cache');
    const unchanged = await context.request.get(base + '/js/runtime.js', {headers: {'If-None-Match': '"other,tag", ' + tag}});
    assert.equal(unchanged.status(), 304);
    assert.equal((await unchanged.body()).length, 0);
    assert.equal((await context.request.get(base + '/js/runtime.js', {headers: {'If-None-Match': 'W/"previous-deployment"'}})).status(), 200);
    for (const path of ['/', '/_geshra/session']) {
        const response = await context.request.get(base + path);
        assert.equal(response.headers()['cache-control'], 'no-store');
        assert.equal(response.headers().etag, undefined);
    }
}

/**
 * Confirms a modern document requests initial bootstrapping without sending a duplicate route.
 */
async function checkBootstrap(context, base) {
    const page = await context.newPage();
    const routes = [];
    const sockets = [];
    page.on('websocket', (socket) => {
        sockets.push(socket.url());
        socket.on('framesent', (event) => {
            const bytes = Buffer.from(event.payload);
            if (bytes[0] === 0 || (bytes[0] === 12 && bytes[1] === 0)) routes.push(bytes.length);
        });
    });
    try {
        await page.goto(base + '/');
        await page.getByRole('button', {name: 'Click me', exact: true}).waitFor();
        assert.match(sockets[0], /geshra-bootstrap=%2F/);
        assert.match(sockets[0], /geshra-protocol=2/);
        await page.waitForTimeout(100);
        assert.equal(routes.length, 0);
        await page.getByRole('button', {name: 'Click me', exact: true}).click();
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
    } finally {
        await page.close();
    }
}

/**
 * Runs the new bundle against the actual preserved old matched server, with and without a stale
 * modern capability marker. A rejected query handshake must fall back to plain /ws exactly once.
 */
async function checkOldServer(browser, base, forceMarker) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const sockets = [];
    const errors = [];
    await page.addInitScript(() => {
        window.optimizerSocketAttempts = [];
        const NativeWebSocket = window.WebSocket;
        window.WebSocket = class extends NativeWebSocket {
            /**
             * Counts actual runtime constructions; Firefox may emit duplicate network observations.
             */
            constructor(...args) {
                super(...args);
                window.optimizerSocketAttempts.push(String(args[0]));
            }
        };
    });
    page.on('pageerror', (error) => errors.push(error.message));
    page.on('websocket', (socket) => sockets.push(socket.url()));
    const bundle = readFileSync(resolve(__dirname, '../src/main/resources/web/js/runtime.js'), 'utf8');
    const marker = forceMarker ? 'document.head.insertAdjacentHTML("beforeend", \'<meta name="geshra-bootstrap" content="2">\');\n' : '';
    await page.route('**/js/runtime.js', (route) => route.fulfill({body: marker + bundle, contentType: 'application/javascript'}));
    try {
        await page.goto(base + '/');
        await page.getByRole('button', {name: 'Increment', exact: true}).click();
        await page.waitForFunction(() => document.querySelector('[data-counter]').textContent === 'Counter: 1');
        if (forceMarker) assert.ok(sockets.some((url) => url.includes('geshra-bootstrap=')));
        assert.ok(sockets.some((url) => new URL(url).search === ''));
        const attempts = await page.evaluate(() => window.optimizerSocketAttempts);
        assert.equal(attempts.length, forceMarker ? 2 : 1);
        assert.deepEqual(errors, []);
    } finally {
        await context.close();
    }
}

/**
 * Holds server output while the URL changes, then requires navigation to the current path rather
 * than treating an acknowledged older bootstrap path as the final destination.
 */
async function checkNavigationDuringBootstrap(browser, base) {
    const context = await browser.newContext();
    const page = await context.newPage();
    let release = null;
    let observedState = false;
    await page.routeWebSocket('**/ws**', (route) => {
        const server = route.connectToServer();
        const held = [];
        let released = false;
        release = () => {
            released = true;
            for (const packet of held) route.send(packet);
            held.length = 0;
        };
        server.onMessage((message) => {
            if (Buffer.from(message)[0] === 6) observedState = true;
            if (released) route.send(message);
            else held.push(message);
        });
    });
    try {
        await page.goto(base + '/');
        for (let attempt = 0; attempt < 100 && !observedState; attempt++) await page.waitForTimeout(50);
        assert.equal(observedState, true);
        await page.evaluate(() => history.pushState({}, '', '/components'));
        release();
        await page.getByRole('heading', {name: 'Component gallery', exact: true}).waitFor();
    } finally {
        await context.close();
    }
}

/**
 * Drops initial DOM output after the real server acknowledges bootstrap, then signals the same
 * close code as an actual render failure. The next connection must rebuild through plain /ws.
 */
async function checkBootstrapFailureRecovery(browser, base) {
    const context = await browser.newContext();
    const page = await context.newPage();
    const urls = [];
    await page.routeWebSocket('**/ws**', (route) => {
        urls.push(route.url());
        const server = route.connectToServer();
        if (urls.length === 1)
            server.onMessage(async (message) => {
                if (Buffer.from(message)[0] !== 6) return;
                route.send(message);
                await route.close({code: 1011, reason: 'bootstrap failed'});
            });
    });
    try {
        await page.goto(base + '/');
        await page.getByRole('button', {name: 'Click me', exact: true}).click();
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
        assert.equal(urls.length, 2);
        assert.equal(new URL(urls[1]).search, '');
    } finally {
        await context.close();
    }
}

/**
 * Runs API/cache/startup compatibility checks separately from performance measurements.
 */
async function verifyOptimizations() {
    const browser = await {chromium, firefox}[process.env.BROWSER || 'chromium'].launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        const context = await browser.newContext();
        try {
            await checkValidators(context, process.env.BASE_URL || 'http://localhost:4040');
            await checkBootstrap(context, process.env.BASE_URL || 'http://localhost:4040');
        } finally {
            await context.close();
        }
        await checkNavigationDuringBootstrap(browser, process.env.BASE_URL || 'http://localhost:4040');
        await checkBootstrapFailureRecovery(browser, process.env.BASE_URL || 'http://localhost:4040');
        if (process.env.BASELINE_URL) {
            await checkOldServer(browser, process.env.BASELINE_URL, false);
            await checkOldServer(browser, process.env.BASELINE_URL, true);
        }
        console.log(JSON.stringify({passed: true, browser: process.env.BROWSER || 'chromium', validators: true, bootstrap: true, oldServer: !!process.env.BASELINE_URL}));
    } finally {
        await browser.close();
    }
}

verifyOptimizations().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
