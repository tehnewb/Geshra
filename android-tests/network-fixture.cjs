/**
 * Loopback-only HTTP reverse proxy and controlled native-host acceptance endpoints.
 */
const http = require('node:http');
/**
 * Raw upgrade forwarding preserves the real Geshra WebSocket protocol and headers.
 */
const net = require('node:net');
/**
 * Stores launch metadata without credentials or request cookie/header dumps.
 */
const fs = require('node:fs');
/**
 * Repository-relative evidence paths.
 */
const path = require('node:path');
/**
 * Dedicated backend Geshra server; the Android app still uses the proxy's origin on port4060.
 */
const backendPort = Number(process.env.ANDROID_BACKEND_PORT || 4062);
/**
 * Main app origin and intentionally different iframe origin, both host-loopback only.
 */
const ports = [Number(process.env.ANDROID_PROXY_PORT || 4060), Number(process.env.ANDROID_FRAME_PORT || 4061)];
/**
 * Reused stream chunk is test data, not a per-download whole-file allocation.
 */
const chunk = Buffer.alloc(16384, 71);
/**
 * Large enough for deliberate mid-stream cancellation, without completing during the picker.
 */
const slowLength = 64 * 1024 * 1024;
/**
 * Only synthetic counts/status are recorded; no authentication or user file data is retained.
 */
const observations = [];
/**
 * Socket and timer ownership for deterministic fixture cleanup.
 */
const sockets = new Set();
/**
 * Both listening servers are closed when the task finishes.
 */
const servers = [];
let sequence = 0; // Monotonic synthetic request identity, unrelated to Geshra session identifiers.

/**
 * Streams ordinary requests through the real Geshra server without changing cookies or routes.
 */
function proxy(request, response) {
    const upstream = http.request({host: '127.0.0.1', port: backendPort, method: request.method, path: request.url, headers: request.headers}, (received) => {
        response.writeHead(received.statusCode, received.headers);
        received.pipe(response);
    });
    upstream.on('error', () => {
        if (!response.headersSent) response.writeHead(502, {'Content-Type': 'text/plain'});
        response.end('Test backend unavailable');
    });
    request.on('aborted', () => upstream.destroy());
    response.on('close', () => upstream.destroy());
    request.pipe(upstream);
}

/**
 * Uses the real protected route to check authentication; cookie spelling alone is not enough.
 */
function authorized(request, response, accept) {
    const check = http.get({host: '127.0.0.1', port: backendPort, path: '/account', headers: {Host: request.headers.host, Cookie: request.headers.cookie || ''}}, (result) => {
        const accepted = result.statusCode === 200;
        result.resume();
        if (response.destroyed || response.writableEnded) return;
        if (accepted) accept();
        else {
            response.writeHead(401, {'Content-Type': 'text/plain'});
            response.end('Sign in before downloading');
        }
    });
    check.on('error', () => {
        if (response.destroyed || response.writableEnded) return;
        response.writeHead(502, {'Content-Type': 'text/plain'});
        response.end('Test authorization unavailable');
    });
}

/**
 * Serves bounded native fixtures; the toolkit showcase and login remain real Geshra pages.
 */
function serve(request, response) {
    const route = new URL(request.url, 'http://127.0.0.1').pathname;
    if (route === '/__android_test__/stats') {
        response.writeHead(200, {'Content-Type': 'application/json', 'Cache-Control': 'no-store'});
        response.end(JSON.stringify(observations));
        return;
    }
    if (route === '/__android_test__/error') {
        response.writeHead(503, {'Content-Type': 'text/plain', 'Cache-Control': 'no-store'});
        response.end('Controlled test failure');
        return;
    }
    if (route === '/__android_test__/file' || route === '/__android_test__/slow') {
        authorized(request, response, () => {
            const state = {id: ++sequence, route, bytes: 0, completed: false, closed: false};
            observations.push(state);
            if (observations.length > 64) observations.shift();
            const slow = route.endsWith('/slow');
            const body = Buffer.from('Geshra authenticated Android download\n');
            response.writeHead(200, {'Content-Type': 'application/octet-stream', 'Content-Disposition': 'attachment; filename="geshra-android-test.txt"', 'Content-Length': slow ? slowLength : body.length, 'Cache-Control': 'no-store'});
            if (!slow) {
                state.bytes = body.length;
                state.completed = true;
                response.end(body);
                return;
            }
            const timer = setInterval(() => {
                if (response.destroyed) {
                    clearInterval(timer);
                    state.closed = true;
                    return;
                }
                state.bytes += chunk.length;
                response.write(chunk);
                if (state.bytes >= slowLength) {
                    state.completed = true;
                    clearInterval(timer);
                    response.end();
                }
            }, 50);
            response.on('close', () => {
                clearInterval(timer);
                state.closed = true;
            });
        });
        return;
    }
    if (route === '/__android_test__/host' || route === '/__android_test__/frame') {
        response.writeHead(200, {'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store'});
        response.end(`<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Android host acceptance fixture</title><style>body{font:18px sans-serif;padding:16px}button,a,input{display:block;margin:16px 0;padding:12px}iframe{height:180px;width:95%}</style></head><body>
<h1>Native host fixture</h1><a href="/showcase">Real Geshra showcase</a><a href="/login">Real Geshra sign in</a>
<a id="download" download="geshra.txt" href="/__android_test__/file">Authenticated download</a>
<a id="slow" download="geshra-slow.txt" href="/__android_test__/slow">Cancelable slow download</a>
<a id="popup" target="_blank" href="/showcase">New-window Geshra link</a>
<a id="external" href="https://example.com/">External browser link</a>
<a id="http-error" href="/__android_test__/error">Controlled HTTP error</a>
<button id="alert" onclick="alert('Android alert')">Alert</button>
<button id="confirm" onclick="document.getElementById('result').textContent='Confirm: '+confirm('Android confirm')">Confirm</button>
<button id="prompt" onclick="document.getElementById('result').textContent='Prompt: '+prompt('Android prompt','test')">Prompt</button>
<p id="result">No native result yet</p>
${route.endsWith('/host') ? `<iframe title="Cross-origin test frame" src="http://10.0.2.2:${ports[1]}/__android_test__/frame"></iframe>` : ''}
</body></html>`);
        return;
    }
    proxy(request, response);
}

/**
 * Pipes the upgrade byte stream without decoding or re-encoding application packets.
 */
function upgrade(request, socket, head) {
    const backend = net.connect(backendPort, '127.0.0.1', () => {
        const headers = [];
        for (let index = 0; index < request.rawHeaders.length; index += 2) headers.push(request.rawHeaders[index] + ': ' + request.rawHeaders[index + 1]);
        backend.write(request.method + ' ' + request.url + ' HTTP/' + request.httpVersion + '\r\n' + headers.join('\r\n') + '\r\n\r\n');
        if (head.length) backend.write(head);
        socket.pipe(backend);
        backend.pipe(socket);
    });
    backend.on('error', () => socket.destroy());
    socket.on('error', () => backend.destroy());
    socket.on('close', () => backend.destroy());
    backend.on('close', () => socket.destroy());
}

/**
 * Stops only this fixture's sockets and listeners; it never touches another project's server.
 */
function close() {
    for (const socket of sockets) socket.destroy();
    for (const server of servers) server.close();
}

for (const port of ports) {
    const server = http.createServer(serve);
    server.on('upgrade', upgrade);
    server.on('connection', (socket) => {
        sockets.add(socket);
        socket.on('close', () => sockets.delete(socket));
    });
    server.listen(port, '127.0.0.1');
    servers.push(server);
}
process.on('SIGINT', close);
process.on('SIGTERM', close);
const output = path.resolve(__dirname, '../build/android-test-results');
fs.mkdirSync(output, {recursive: true});
fs.writeFileSync(path.join(output, 'network-fixture.json'), JSON.stringify({pid: process.pid, ports, backendPort}, null, 2));
console.log(JSON.stringify({ready: true, ports, backendPort}));
