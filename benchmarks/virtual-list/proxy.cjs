/**
 * Reuses the established ordered TCP delay implementation without modifying the four-app fixture.
 */
const {relay, setDelay} = require('./relay.cjs');
/**
 * Local-only configuration for sequential isolated browser sessions.
 */
const http = require('node:http');
let server = null; // Current native relay listener, replaced only after the previous context closes.
let configuration = null; // Actual selected delay and backend returned for verification.

/**
 * Selects a known packaged backend and equal HTTP/WS delay before the next browser session.
 */
async function configure(request, response) {
    try {
        if (request.method === 'POST' && request.url === '/config') {
            let text = '';
            for await (const chunk of request) {
                text += chunk;
                if (text.length > 128) throw new Error('Configuration too large');
            }
            const next = JSON.parse(text);
            if (![4301, 4302, 4401, 4402, 4502, 4602, 4702].includes(next.backendPort)) throw new Error('Invalid backend');
            setDelay(next.delayMs);
            configuration = next;
            if (!server) {
                server = relay('geshra', 4201, () => configuration.backendPort);
                await new Promise((resolve) => server.once('listening', resolve));
            }
        }
        response.setHeader('content-type', 'application/json');
        response.end(JSON.stringify(configuration));
    } catch (error) {
        response.writeHead(400);
        response.end(JSON.stringify({error: error.message}));
    }
}

http.createServer(configure).listen(4199, '127.0.0.1', () => console.log('Virtual relay PID ' + process.pid));
