/**
 * Installs identical observation in both versions before their actual native WebSocket exists.
 * Message payload bytes exclude framing/TCP/TLS; HTTP transferSize is recorded separately.
 */
function installMetrics() {
    const metrics = {
        sentBytes: 0,
        receivedBytes: 0,
        requests: [],
        acknowledgments: [],
        pending: [],
        maxPending: 0,
        frames: [],
        longTasks: [],
        longTaskSupported: PerformanceObserver.supportedEntryTypes.includes('longtask'),
        sockets: 0
    };
    window.virtualMetrics = metrics;
    const NativeWebSocket = window.WebSocket;
    const decoder = new TextDecoder();
    /**
     * Reads protocol integers without importing or modifying the application decoder.
     */
    function unsigned(bytes, cursor) {
        let value = 0;
        for (let shift = 0; shift <= 28; shift += 7) {
            const byte = bytes[cursor.offset++];
            value |= (byte & 127) << shift;
            if (!(byte & 128)) return value;
        }
        throw new Error('Invalid observed integer');
    }
    /**
     * Records committed range acknowledgments while leaving application messages untouched.
     */
    function observeMessage(buffer) {
        const bytes = new Uint8Array(buffer);
        metrics.receivedBytes += bytes.length;
        const compact = bytes[0] === 7;
        if (!compact && bytes[0] !== 1) return;
        const view = new DataView(buffer);
        const cursor = {offset: 3};
        const count = view.getUint16(1);
        for (let index = 0; index < count; index++) {
            const type = bytes[cursor.offset++];
            const id = compact ? unsigned(bytes, cursor) : view.getInt32(cursor.offset);
            if (!compact) cursor.offset += 4;
            const fields = bytes[cursor.offset++];
            let value;
            for (let field = 0; field < fields; field++) {
                const header = bytes[cursor.offset++];
                const key = compact ? header & 31 : header;
                const kind = compact ? header >>> 5 : 0;
                let text;
                if (kind === 0) {
                    const length = compact ? unsigned(bytes, cursor) : view.getUint16(cursor.offset);
                    if (!compact) cursor.offset += 2;
                    if (type === 28 && key === 3) text = decoder.decode(bytes.subarray(cursor.offset, cursor.offset + length));
                    cursor.offset += length;
                } else if (kind === 4) unsigned(bytes, cursor);
                if (type === 28 && key === 3) value = text;
            }
            if (type === 28 && value) {
                const ack = JSON.parse(value);
                const match = metrics.pending.findIndex(
                    (request) =>
                        request.id === id &&
                        request.revision === ack.revision &&
                        request.first === (ack.requestFirst ?? ack.first) &&
                        request.end === (ack.requestEnd ?? ack.end) &&
                        (request.token === undefined || request.token === ack.token)
                );
                const request = match >= 0 ? metrics.pending.splice(match, 1)[0] : null;
                metrics.acknowledgments.push({...ack, id, at: performance.now(), latencyMs: request ? performance.now() - request.at : null});
            }
        }
    }
    /**
     * Instruments genuine native socket construction once, including in Firefox.
     */
    window.WebSocket = class extends NativeWebSocket {
        /**
         * Observes the incoming payload before the application listener consumes it.
         */
        constructor(...arguments_) {
            super(...arguments_);
            metrics.sockets++;
            this.addEventListener('message', (event) => observeMessage(event.data));
            this.addEventListener('close', () => {
                metrics.pending.length = 0;
            });
        }
        /**
         * Records each actual viewport send and lets the native implementation transmit it.
         */
        send(buffer) {
            const bytes = new Uint8Array(buffer.buffer || buffer, buffer.byteOffset || 0, buffer.byteLength);
            metrics.sentBytes += bytes.length;
            const compact = bytes[0] === 12;
            if ((compact ? bytes[1] : bytes[0]) === 8) {
                const cursor = {offset: 2};
                const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
                const values = compact ? Array.from({length: 4}, () => unsigned(bytes, cursor)) : [view.getInt32(1), view.getInt32(5), view.getInt32(9), view.getInt32(13)];
                const token = compact ? (cursor.offset < bytes.length ? unsigned(bytes, cursor) : undefined) : bytes.length > 17 ? view.getInt32(17) : undefined;
                const request = {id: values[0], revision: values[1], first: values[2], end: values[3], token, at: performance.now()};
                metrics.requests.push(request);
                metrics.pending.push(request);
                metrics.maxPending = Math.max(metrics.maxPending, metrics.pending.length);
            }
            return super.send(buffer);
        }
    };
    if (PerformanceObserver.supportedEntryTypes.includes('longtask'))
        new PerformanceObserver((entries) => metrics.longTasks.push(...entries.getEntries().map((entry) => ({start: entry.startTime, duration: entry.duration})))).observe({
            type: 'longtask',
            buffered: true
        });
}

module.exports = {installMetrics};
