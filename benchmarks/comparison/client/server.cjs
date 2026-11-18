/**
 * Minimal real HTTP transport and authoritative state for both client-rendered comparators.
 */
const http = require('node:http');
/**
 * Reads only the fixed production build output.
 */
const {readFileSync} = require('node:fs');
/**
 * Native production artifact paths.
 */
const {resolve} = require('node:path');
/**
 * Uses the same ordinary gzip content coding available to the Java HTTP servers.
 */
const {gzipSync} = require('node:zlib');
/**
 * Creates opaque context-specific session identifiers, never authentication credentials.
 */
const {randomUUID} = require('node:crypto');

/**
 * Applies one authoritative operation and returns its exact delta, never a speculative result.
 * @param state session-owned dataset and completion state
 * @param action requested operation
 * @param value native form value submitted for echo
 * @returns operation response consumed by both real clients
 */
function operate(state, action, value) {
    const result = {};
    if (action === 'increment') {
        state.counter++;
        result.counter = state.counter;
        state.status = 'Incremented';
    } else if (action === 'echo') {
        state.echo = String(value);
        result.echo = state.echo;
        state.status = 'Echoed';
    } else if (action === 'create') {
        state.rows = Array.from({length: 1000}, (_, index) => ({id: index + 1, label: 'Row ' + (index + 1)}));
        result.rows = state.rows;
        state.status = 'Created 1000';
    } else if (action === 'update') {
        result.patches = [];
        for (const row of state.rows)
            if ((row.id - 1) % 10 === 0) {
                row.label += ' !!!';
                result.patches.push({id: row.id, label: row.label});
            }
        state.status = 'Updated tenth';
    } else if (action === 'select') {
        const row = state.rows.find((row) => row.id === 500);
        if (row) row.selected = true;
        state.status = 'Selected 500';
    } else if (action === 'remove') {
        state.rows = state.rows.filter((row) => row.id !== 500);
        state.status = 'Removed 500';
    } else if (action === 'clear') {
        state.rows = [];
        state.status = 'Cleared';
    } else throw new Error('Unknown benchmark action');
    state.revision++;
    return {...result, status: state.status, revision: state.revision};
}

/**
 * Serves one production client and a bounded set of ephemeral per-context sessions.
 * Each comparator has its own port and session map; there is no shared mutable user state.
 */
function serve(name, port) {
    const sessions = new Map();
    const files = new Map([
        ['/', ['text/html', readFileSync(resolve(__dirname, 'dist', name, 'index.html'))]],
        ['/app.js', ['application/javascript', readFileSync(resolve(__dirname, 'dist', name, 'app.js'))]],
        ['/bench.css', ['text/css', readFileSync(resolve(__dirname, 'dist', name, 'bench.css'))]]
    ]);
    const server = http.createServer(async (request, response) => {
        try {
            const token = request.headers.cookie
                ?.split(';')
                .map((cookie) => cookie.trim())
                .find((cookie) => cookie.startsWith('bench='))
                ?.slice(6);
            let state = sessions.get(token);
            if (!state) {
                const identifier = randomUUID();
                state = {counter: 0, echo: '', status: 'Ready', revision: 0, rows: []};
                sessions.set(identifier, state);
                response.setHeader('Set-Cookie', 'bench=' + identifier + '; HttpOnly; SameSite=Lax; Path=/');
                if (sessions.size > 256) sessions.delete(sessions.keys().next().value);
            }
            let body;
            if (request.url === '/api/action' && request.method === 'POST') {
                let input = '';
                for await (const chunk of request) {
                    input += chunk;
                    if (input.length > 4096) throw new Error('Benchmark input too large');
                }
                const payload = JSON.parse(input);
                body = Buffer.from(JSON.stringify(operate(state, payload.action, payload.value)));
                response.setHeader('Content-Type', 'application/json');
                response.setHeader('Cache-Control', 'no-store');
            } else {
                const file = files.get(request.url);
                if (!file) {
                    response.writeHead(404);
                    response.end();
                    return;
                }
                response.setHeader('Content-Type', file[0]);
                response.setHeader('Cache-Control', request.url === '/' ? 'no-store' : 'public, max-age=3600');
                body = file[1];
            }
            if (request.headers['accept-encoding']?.includes('gzip')) {
                body = gzipSync(body);
                response.setHeader('Content-Encoding', 'gzip');
                response.setHeader('Vary', 'Accept-Encoding');
            }
            response.setHeader('Content-Length', body.length);
            response.end(body);
        } catch (error) {
            console.error(error);
            response.writeHead(400);
            response.end('Invalid benchmark operation');
        }
    });
    server.listen(port, '127.0.0.1', () => console.log(name + ' production fixture listening on ' + port));
}

serve('react', 4103);
serve('dom', 4104);
