/**
 * Focused harness regression checks; these do not supply comparative performance timings.
 */
const assert = require('node:assert/strict');
/**
 * Real local TCP peers expose abort/half-close behavior without touching application servers.
 */
const net = require('node:net');
/**
 * Browser-backed row-identity checks use the existing pinned development browser.
 */
const {chromium} = require('../../browser-tests/node_modules/playwright');
/**
 * The actual harness validators under test.
 */
const {verify, selectProfile} = require('./run.cjs');
/**
 * The actual relay implementation; importing it never starts the four benchmark servers.
 */
const {relay, setDelay} = require('./proxy.cjs');

/**
 * Bounds an asynchronous resource test and clears its timer on every outcome.
 */
async function deadline(promise) {
    let timer;
    try {
        return await Promise.race([
            promise,
            new Promise((_, reject) => {
                timer = setTimeout(() => reject(new Error('TCP check deadline')), 1000);
            })
        ]);
    } finally {
        clearTimeout(timer);
    }
}

/**
 * Waits for one TCP listener to bind its ephemeral port.
 */
async function listening(server) {
    if (!server.listening)
        await new Promise((resolve, reject) => {
            server.once('listening', resolve);
            server.once('error', reject);
        });
    return server.address().port;
}

/**
 * Closes an owned listener after test sockets have been released.
 */
async function close(server) {
    await new Promise((resolve, reject) => server.close((error) => (error ? reject(error) : resolve())));
}

/**
 * Confirms refused peers close clients, graceful FIN follows delayed bytes, and client aborts
 * cancel pending response delivery and promptly release backend sockets.
 */
async function checkRelay() {
    const unused = net.createServer();
    unused.listen(0, '127.0.0.1');
    const refusedPort = await listening(unused);
    await close(unused);
    setDelay(50);
    const refused = relay('geshra', 0, refusedPort);
    const refusedClient = net.connect(await listening(refused), '127.0.0.1');
    const clientErrors = [];
    refusedClient.on('error', (error) => clientErrors.push(error.code));
    try {
        await deadline(new Promise((resolve) => refusedClient.once('close', resolve)));
    } finally {
        refusedClient.destroy();
        await close(refused);
    }

    const gracefulBackend = net.createServer({allowHalfOpen: true}, (socket) => socket.on('data', () => socket.end('delayed tail')));
    gracefulBackend.listen(0, '127.0.0.1');
    const graceful = relay('geshra', 0, await listening(gracefulBackend));
    const gracefulClient = net.connect({port: await listening(graceful), host: '127.0.0.1', allowHalfOpen: true});
    const received = [];
    gracefulClient.on('data', (chunk) => received.push(chunk));
    gracefulClient.end('request');
    try {
        await deadline(
            new Promise((resolve, reject) => {
                gracefulClient.once('close', resolve);
                gracefulClient.once('error', reject);
            })
        );
        assert.equal(Buffer.concat(received).toString(), 'delayed tail');
    } finally {
        gracefulClient.destroy();
        await close(graceful);
        await close(gracefulBackend);
    }

    let backendSocket;
    let releaseBackend;
    const released = new Promise((resolve) => {
        releaseBackend = resolve;
    });
    const abortBackend = net.createServer((socket) => {
        backendSocket = socket;
        socket.on('close', releaseBackend);
        socket.write('must be canceled');
    });
    abortBackend.listen(0, '127.0.0.1');
    const abort = relay('geshra', 0, await listening(abortBackend));
    const abortClient = net.connect(await listening(abort), '127.0.0.1');
    try {
        await new Promise((resolve) => abortClient.once('connect', resolve));
        await new Promise((resolve) => setTimeout(resolve, 10));
        abortClient.destroy();
        await deadline(released);
        assert.equal(backendSocket.destroyed, true);
    } finally {
        abortClient.destroy();
        backendSocket?.destroy();
        await close(abort);
        await close(abortBackend);
    }
}

/**
 * Checks rejected and mismatched profile responses fail rather than silently labeling timings.
 */
async function checkProfile() {
    const nativeFetch = global.fetch;
    try {
        global.fetch = async () => ({ok: false, status: 503});
        await assert.rejects(selectProfile({delayEachWayMs: 75}), /Relay profile rejected/);
        global.fetch = async () => ({ok: true, json: async () => ({delayMs: 0})});
        await assert.rejects(selectProfile({delayEachWayMs: 75}), /does not match/);
    } finally {
        global.fetch = nativeFetch;
    }
}

/**
 * A duplicate ID with otherwise correct range/labels must fail the real browser validator.
 */
async function checkRows() {
    const browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    try {
        const page = await browser.newPage();
        await page.setContent('<p data-counter>Counter: 0</p><p data-echo>Echo: </p><table><tbody></tbody></table>');
        await page.evaluate(() => {
            document.querySelector('tbody').innerHTML = Array.from(
                {length: 1000},
                (_, index) => '<tr data-row="' + (index + 1) + '"><td>' + (index + 1) + '</td><td>Row ' + (index + 1) + '</td></tr>'
            ).join('');
        });
        await verify(page, 'Create 1000', 0);
        await page.evaluate(() => {
            const row = document.querySelector('[data-row="500"]');
            row.dataset.row = '501';
            row.firstElementChild.textContent = '501';
            row.lastElementChild.textContent = 'Row 501';
        });
        await assert.rejects(verify(page, 'Create 1000', 0), /Exact row ID\/content mismatch/);
        await page.evaluate(() => {
            document.querySelector('tbody').innerHTML = Array.from(
                {length: 999},
                (_, index) =>
                    '<tr data-row="' +
                    (index + 1) +
                    '"' +
                    (index === 499 ? ' class="selected"' : '') +
                    '><td>' +
                    (index + 1) +
                    '</td><td>Row ' +
                    (index + 1) +
                    (index % 10 === 0 ? ' !!!' : '') +
                    '</td></tr>'
            ).join('');
        });
        await assert.rejects(verify(page, 'Select 500', 0), /Selection mismatch/);
    } finally {
        await browser.close();
    }
}

/**
 * Runs failure-oriented checks sequentially, separate from measured comparison evidence.
 */
async function checks() {
    await checkRelay();
    await checkProfile();
    await checkRows();
    console.log('Passed relay peer refusal/FIN/abort cleanup, profile rejection/mismatch, and exact row-ID checks.');
}

checks().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
