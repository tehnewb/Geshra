/**
 * Shared raw TCP relay applies the same application-byte delay to HTTP and WebSocket traffic.
 */
const net = require('node:net');
/**
 * Separate local-only control channel exposes profile configuration and byte counters.
 */
const http = require('node:http');
/**
 * All applications use one identical relay implementation; ports identify isolated backends.
 */
const applications = ['geshra', 'vaadin', 'react', 'dom'];
/**
 * Cumulative delivered application bytes, including HTTP headers and WS framing, excluding TCP/IP.
 */
const counters = Object.fromEntries(applications.map((name) => [name, {clientToServer: 0, serverToClient: 0, connections: 0, errors: []}]));
let delayMs = 0; // Current per-direction chunk delay; never throughput shaping or artificial server work.

/**
 * Creates a relay whose delayed chunks preserve arrival order and clean up on disconnect.
 */
function relay(name, frontPort, backPort) {
    const server = net.createServer({allowHalfOpen: true}, (client) => {
        const backend = net.connect({port: backPort(), host: '127.0.0.1', allowHalfOpen: true});
        const timers = new Set();
        let closed = false;
        /**
         * Destroys both peers and cancels delayed writes on any abort or I/O error.
         */
        function closeConnection() {
            if (closed) return;
            closed = true;
            for (const timer of timers) clearTimeout(timer);
            timers.clear();
            client.destroy();
            backend.destroy();
        }
        counters[name].connections++;
        client.setNoDelay(true);
        backend.setNoDelay(true);
        for (const [source, target, direction] of [
            [client, backend, 'clientToServer'],
            [backend, client, 'serverToClient']
        ]) {
            let pending = 0;
            let ended = false;
            source.on('data', (bytes) => {
                pending++;
                const deliver = () => {
                    pending--;
                    if (closed) return;
                    if (target.destroyed || target.writableEnded) {
                        closeConnection();
                        return;
                    }
                    try {
                        if (!target.write(bytes)) {
                            source.pause();
                            target.once('drain', () => {
                                if (!closed) source.resume();
                            });
                        }
                        counters[name][direction] += bytes.length;
                        if (ended && pending === 0) target.end();
                    } catch (error) {
                        counters[name].errors.push(error.code || error.message);
                        closeConnection();
                    }
                };
                if (delayMs === 0) deliver();
                else {
                    const timer = setTimeout(() => {
                        timers.delete(timer);
                        deliver();
                    }, delayMs);
                    timers.add(timer);
                }
            });
            source.on('error', (error) => {
                if (!['ECONNRESET', 'EPIPE'].includes(error.code)) counters[name].errors.push(error.code || error.message);
                closeConnection();
            });
            source.on('end', () => {
                ended = true;
                if (!closed && pending === 0) target.end();
            });
            source.on('close', (hadError) => {
                if (hadError || !ended || (client.destroyed && backend.destroyed)) closeConnection();
            });
        }
    });
    server.listen(frontPort, '127.0.0.1');
    return server;
}

/**
 * Applies the same checked per-direction delay to all new session connections.
 */
function setDelay(value) {
    if (!Number.isInteger(value) || value < 0 || value > 500) throw new RangeError('Invalid relay delay');
    delayMs = value;
}

module.exports = {relay, setDelay};
