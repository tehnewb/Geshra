/**
 * Collects HTTP waterfall and WebSocket diagnostics from one Chromium target. Payload counts
 * exclude WS framing/TLS; CDP encodedDataLength and Resource Timing are reported separately.
 */
function trackNetwork(cdp) {
    const requests = new Map();
    const sockets = new Map();
    cdp.on('Network.requestWillBeSent', (event) => requests.set(event.requestId, {url: event.request.url, type: event.type, start: event.timestamp, method: event.request.method}));
    cdp.on('Network.requestServedFromCache', (event) => {
        if (requests.has(event.requestId)) requests.get(event.requestId).cache = true;
    });
    cdp.on('Network.responseReceived', (event) => {
        const request = requests.get(event.requestId);
        if (request)
            Object.assign(request, {
                status: event.response.status,
                protocol: event.response.protocol,
                mimeType: event.response.mimeType,
                cache: request.cache || event.response.fromDiskCache || event.response.fromServiceWorker || false,
                encoding: event.response.headers['content-encoding'] || event.response.headers['Content-Encoding'] || null,
                timing: event.response.timing
            });
    });
    cdp.on('Network.loadingFinished', (event) => {
        if (requests.has(event.requestId)) Object.assign(requests.get(event.requestId), {end: event.timestamp, wireBytes: event.encodedDataLength});
    });
    cdp.on('Network.loadingFailed', (event) => {
        if (requests.has(event.requestId)) Object.assign(requests.get(event.requestId), {error: event.errorText, canceled: event.canceled || false});
    });
    cdp.on('Network.webSocketCreated', (event) => sockets.set(event.requestId, {url: event.url, sent: [], received: []}));
    cdp.on('Network.webSocketWillSendHandshakeRequest', (event) => {
        if (sockets.has(event.requestId)) sockets.get(event.requestId).handshakeStart = event.timestamp;
    });
    cdp.on('Network.webSocketHandshakeResponseReceived', (event) => {
        if (sockets.has(event.requestId)) Object.assign(sockets.get(event.requestId), {handshakeEnd: event.timestamp, status: event.response.status});
    });
    for (const [name, direction] of [
        ['Network.webSocketFrameSent', 'sent'],
        ['Network.webSocketFrameReceived', 'received']
    ])
        cdp.on(name, (event) => {
            const socket = sockets.get(event.requestId);
            if (socket)
                socket[direction].push({
                    at: event.timestamp,
                    opcode: event.response.opcode,
                    bytes: event.response.opcode === 2 ? Buffer.from(event.response.payloadData, 'base64').length : Buffer.byteLength(event.response.payloadData)
                });
        });
    return {requests, sockets};
}

/**
 * Applies explicit HTTP network conditions and a renderer CPU multiplier. The legacy CDP
 * command is retained for installed-browser compatibility; WS effects are measured separately.
 */
async function applyProfile(cdp, profile) {
    await cdp.send('Network.emulateNetworkConditions', {
        offline: false,
        latency: profile.latencyMs,
        downloadThroughput: profile.downloadBytesPerSecond,
        uploadThroughput: profile.uploadBytesPerSecond
    });
    await cdp.send('Emulation.setCPUThrottlingRate', {rate: profile.cpuRate});
}

/**
 * Produces serializable network evidence with exact packet totals and handshake durations.
 */
function networkSnapshot(network) {
    return {
        requests: Array.from(network.requests.values()),
        sockets: Array.from(network.sockets.values()).map((socket) => ({
            ...socket,
            handshakeMs: socket.handshakeEnd && socket.handshakeStart ? (socket.handshakeEnd - socket.handshakeStart) * 1000 : null,
            sentBytes: socket.sent.reduce((sum, frame) => sum + frame.bytes, 0),
            receivedBytes: socket.received.reduce((sum, frame) => sum + frame.bytes, 0)
        }))
    };
}

module.exports = {trackNetwork, applyProfile, networkSnapshot};
