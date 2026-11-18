const assert = require('node:assert/strict');
const {chromium, firefox, webkit} = require('playwright');

/**
 * Verifies compact codec boundaries in a real browser and exercises both server decoders.
 * Direct codec checks do not initialize a second transport or alter the page's session helpers.
 */
async function verifyProtocol() {
    const engine = {chromium, firefox, webkit}[process.env.BROWSER || 'chromium'];
    const browser = await engine.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE || undefined});
    const page = await browser.newPage();
    const warnings = [];
    const sentOpcodes = new Set();
    const receivedOpcodes = new Set();
    page.on('pageerror', (error) => warnings.push(error.message));
    page.on('websocket', (socket) => {
        socket.on('framesent', (event) => sentOpcodes.add(Buffer.from(event.payload)[0]));
        socket.on('framereceived', (event) => receivedOpcodes.add(Buffer.from(event.payload)[0]));
    });
    await page.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    try {
        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + '/');
        await page.getByRole('button', {name: 'Click me', exact: true}).waitFor();
        const result = await page.evaluate(async () => {
            const {PacketReader} = await import('/js/runtime/packet-reader.js');
            const {PacketWriter} = await import('/js/runtime/packet-writer.js');
            const {encodeCompact} = await import('/js/runtime/compact-encode.js');
            const boundaries = [0, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, 2147483647];
            for (const value of boundaries) {
                const writer = new PacketWriter(5);
                writer.unsigned(value);
                const packet = writer.finish();
                const reader = new PacketReader(packet.slice(2).buffer);
                if (reader.unsigned() !== value) throw new Error('Integer round trip failed');
            }
            const strings = ['', 'ASCII', 'Café 東京 😀', '\ud800', 'x'.repeat(127), 'x'.repeat(128)];
            for (const text of strings) {
                const packet = encodeCompact(4, [42, text]);
                const reader = new PacketReader(packet.slice(2).buffer);
                if (reader.unsigned() !== 42 || reader.text() !== text.replace(/\ud800/g, '\ufffd')) throw new Error('Text round trip failed');
            }
            let rejected = 0;
            for (const bytes of [[128], [128, 0], [255, 255, 255, 255, 8], [255, 255, 255, 255, 255, 0]]) {
                try {
                    new PacketReader(new Uint8Array(bytes).buffer).unsigned();
                } catch (error) {
                    if (!(error instanceof RangeError)) throw error;
                    rejected++;
                }
            }
            for (const text of ['é'.repeat(32768), 'x'.repeat(65536)]) {
                try {
                    encodeCompact(4, [1, text]);
                } catch (error) {
                    if (!(error instanceof RangeError)) throw error;
                    rejected++;
                }
            }
            const click = encodeCompact(1, [42, 0, -12, 0, 0, 0, 0, 0, true, false, false, false]);
            const clickReader = new PacketReader(click.slice(2).buffer);
            if (clickReader.unsigned() !== 42 || clickReader.byte() !== 8 || clickReader.unsigned() !== 23) throw new Error('Packed click fields changed');
            const key = encodeCompact(3, [42, 'Enter', true, true, false, true, false]);
            const keyReader = new PacketReader(key.slice(2).buffer);
            if (keyReader.unsigned() !== 42 || keyReader.byte() !== 21 || keyReader.text() !== 'Enter') throw new Error('Packed key flags changed');
            const upload = encodeCompact(6, [42, '日本.txt', new Uint8Array([1, 2, 3]).buffer]);
            const uploadReader = new PacketReader(upload.slice(2).buffer);
            if (uploadReader.unsigned() !== 42 || uploadReader.unsigned() !== 10 || uploadReader.unsigned() !== 3) throw new Error('Upload header changed');
            // Opcode four must execute trusted server scripts, independent of DOM batch encoding.
            const script = new TextEncoder().encode('window.protocolScriptExecuted = true;');
            const command = new Uint8Array(3 + script.length);
            command[0] = 4;
            new DataView(command.buffer).setUint16(1, script.length);
            command.set(script, 3);
            window.domSocket.onmessage({data: command.buffer});
            return {rejected, clickBytes: click.length, scriptExecuted: window.protocolScriptExecuted === true};
        });
        assert.equal(result.rejected, 6);
        assert.equal(result.clickBytes, 10);
        assert.equal(result.scriptExecuted, true);
        await page.getByRole('button', {name: 'Click me', exact: true}).click();
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
        assert.ok(sentOpcodes.has(12), 'the packaged server must negotiate compact client actions');
        assert.ok(receivedOpcodes.has(7), 'the packaged server must emit compact DOM batches');
        assert.deepEqual(warnings, []);
        await verifyMissingState(browser);
        await verifyLateState(browser);
        console.log(JSON.stringify({passed: true, protocol: result}));
    } finally {
        await browser.close();
    }
}

/**
 * Withholds the first server capability message and verifies bounded legacy recovery.
 * This exercises the real browser connection rather than advancing a synthetic timer alone.
 */
async function verifyMissingState(browser) {
    const page = await browser.newPage();
    const sent = [];
    page.on('websocket', (socket) => socket.on('framesent', (event) => sent.push(Buffer.from(event.payload)[0])));
    await page.addInitScript(() => {
        const NativeWebSocket = window.WebSocket;
        window.WebSocket = class extends NativeWebSocket {
            /**
             * Installs the message filter before the runtime assigns its onmessage handler.
             */
            constructor(...args) {
                super(...args);
                let withheld = false;
                this.addEventListener('message', (event) => {
                    if (!withheld && event.data instanceof ArrayBuffer && new Uint8Array(event.data)[0] === 6) {
                        withheld = true;
                        window.withheldSessionState = true;
                        event.stopImmediatePropagation();
                    }
                });
            }
        };
    });
    await page.route('**/favicon.ico', (route) => route.fulfill({status: 204}));
    try {
        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + '/');
        const button = page.getByRole('button', {name: 'Click me', exact: true});
        await button.waitFor({timeout: 5000});
        assert.equal(await page.evaluate(() => window.withheldSessionState), true);
        // The new server can render before state arrives; the legacy recovery timer still runs.
        await page.waitForTimeout(1100);
        assert.ok(sent.includes(0), 'missing capability state must recover with a legacy route request');
        assert.equal(sent.includes(12), false);
        await button.click();
        await page.getByRole('heading', {name: 'The Java listener handled your click', exact: true}).waitFor();
    } finally {
        await page.close();
    }
}

/**
 * Exercises timeout, late capability arrival, disconnected action queueing, and downgrade replay.
 * Native DOM and timers run in the browser; a socket double makes the server-version transition
 * deterministic. Source-module behavior is tied to the shipped bundle by its reproducibility check.
 */
async function verifyLateState(browser) {
    const page = await browser.newPage();
    await page.route('**/js/runtime.js', (route) => route.fulfill({body: '// Source-driven connection lifecycle check', contentType: 'application/javascript'}));
    await page.addInitScript(() => {
        window.mockSockets = [];
        window.WebSocket = class {
            /**
             * Native ready-state constant required by the transport contract.
             */
            static OPEN = 1;
            /**
             * Native ready-state constant required by the transport contract.
             */
            static CONNECTING = 0;
            /**
             * Native ready-state constant required by the transport contract.
             */
            static CLOSING = 2;
            readyState = 0; // Starts connecting until the constructor's microtask completes.
            sent = []; // Copied payloads observed by the fake server.

            /**
             * Records this connection and asynchronously completes its native open callback.
             */
            constructor() {
                window.mockSockets.push(this);
                queueMicrotask(() => {
                    this.readyState = 1;
                    this.onopen?.();
                });
            }

            /**
             * Copies sent bytes so later codec allocations cannot change observed traffic.
             */
            send(packet) {
                this.sent.push(Array.from(packet));
            }

            /**
             * Disconnects this transport and notifies its reconnect policy immediately.
             */
            close() {
                this.readyState = 3;
                this.onclose?.();
            }
        };
    });
    try {
        await page.goto((process.env.BASE_URL || 'http://localhost:4040') + '/');
        await page.evaluate(async () => {
            window.testTransport = await import('/js/runtime/transport.js');
            window.testTransport.startRuntime();
        });
        await page.waitForFunction(() => window.mockSockets[0]?.sent.some((packet) => packet[0] === 0), null, {timeout: 5000});
        await page.evaluate(() => {
            const payload = new TextEncoder().encode(JSON.stringify({action: 'state', protocol: 2, csrfToken: 'test', cookieMaxAge: 28800}));
            const packet = new Uint8Array(3 + payload.length);
            packet[0] = 6;
            new DataView(packet.buffer).setUint16(1, payload.length);
            packet.set(payload, 3);
            window.mockSockets[0].onmessage({data: packet.buffer});
            window.mockSockets[0].close();
            window.testTransport.sendPacket(5, 42);
        });
        await page.waitForFunction(() => window.mockSockets.length === 2 && window.mockSockets[1].readyState === 1);
        const sent = await page.evaluate(() => {
            const payload = new TextEncoder().encode(JSON.stringify({action: 'state', protocol: 1, csrfToken: 'test', cookieMaxAge: 28800}));
            const packet = new Uint8Array(3 + payload.length);
            packet[0] = 6;
            new DataView(packet.buffer).setUint16(1, payload.length);
            packet.set(payload, 3);
            window.mockSockets[1].onmessage({data: packet.buffer});
            return window.mockSockets[1].sent;
        });
        assert.ok(
            sent.some((packet) => packet[0] === 0),
            'late capability changes must cause route recovery after a downgrade'
        );
        assert.equal(
            sent.some((packet) => packet[0] === 12),
            false,
            'queued compact bytes must never replay to a legacy server'
        );
    } finally {
        await page.close();
    }
}

verifyProtocol().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
