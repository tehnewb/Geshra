/**
 * One socket, bounded reconnect buffering, heartbeat ownership, and navigation hooks.
 */
import {SEND_NAVIGATE, SEND_HEARTBEAT, SEND_VIEWPORT} from './protocol.js';
import {resumeVirtualLists} from './virtual-list.js';
import {encodePacket, setCompactEncoding} from './encode.js';
import {receivePacket} from './decode.js';
import {getHeartbeatInterval, renewSessionCookie} from './session.js';

/**
 * Reconnect delay bounds for a dropped WebSocket.
 */
const RECONNECT_BASE_DELAY_MS = 500;
/**
 * Maximum delay between reconnect attempts.
 */
const RECONNECT_MAX_DELAY_MS = 10_000;

/**
 * If the page has been disconnected for this long, server-side component IDs may be stale.
 * In that case the runtime drops queued UI events and reloads the current route instead.
 */
const STALE_RECONNECT_MS = 25 * 60_000;

/**
 * Maximum number of user packets buffered while the socket is reconnecting.
 */
const MAX_QUEUED_PACKETS = 128;
/**
 * Retained reconnect payload budget, independent of packet count.
 */
const MAX_QUEUED_BYTES = 1048576;
/**
 * Maximum wait for capability state before recovering with the legacy route request.
 */
const SESSION_STATE_TIMEOUT_MS = 1000;

let reconnectTimer = null; // Scheduled reconnect timer, or null.
let heartbeatTimer = null; // Active heartbeat timer, or null.
let reconnectAttempt = 0; // Consecutive failed connection attempts.
let connecting = false; // Whether socket construction is in progress.
let firstConnection = true; // Whether the first route render is required.
let sessionStateTimer = null; // Bounded capability wait owned by the current connection.
let queuedBytes = 0; // Total payload retained by the reconnect queue.
let previousCompact = null; // Previous connection's format; changes require a route rebuild.
let awaitingSession = false; // Gates replay on the server capability.
let reloadOnReady = true; // Whether the next session state must rebuild the route.
let socketClosedAt = 0; // Last disconnect timestamp, or zero.
let bootstrapEnabled = !!document.querySelector('meta[name="geshra-bootstrap"][content="2"]'); // HTTP-advertised initial-route support; disabled after an incompatible handshake.
let bootstrapPath = ''; // Exact path requested by the current connection, or empty when not bootstrapping.
let routedPath = location.pathname; // Last server route; native fragment history does not rebuild its DOM.
/**
 * Reconnect payloads retained within the configured count and byte limits.
 */
const queuedPackets = [];

/**
 * Reports whether a socket accepts outgoing messages.
 */
export function socketIsOpen(socket) {
    return socket && socket.readyState === WebSocket.OPEN;
}

/**
 * Reports whether a connection attempt is in progress.
 */
function socketIsConnecting(socket) {
    return socket && socket.readyState === WebSocket.CONNECTING;
}

/**
 * Reports whether an old connection must finish closing.
 */
function socketIsClosing(socket) {
    return socket && socket.readyState === WebSocket.CLOSING;
}

/**
 * Retains user actions within both packet-count and byte budgets.
 */
function queuePacket(packet) {
    if (!packet) {
        return;
    }

    queuedPackets.push(packet);
    queuedBytes += packet.byteLength;

    while (queuedPackets.length > MAX_QUEUED_PACKETS || queuedBytes > MAX_QUEUED_BYTES) {
        queuedBytes -= queuedPackets.shift().byteLength;
    }
}

/**
 * Replays retained actions in their original order.
 */
function flushQueuedPackets() {
    const socket = window.domSocket;

    while (socketIsOpen(socket) && queuedPackets.length > 0) {
        const packet = queuedPackets[0];
        socket.send(packet);
        queuedPackets.shift();
        queuedBytes -= packet.byteLength;
    }
}

/**
 * Cancels an outstanding reconnect timer.
 */
function clearReconnectTimer() {
    if (reconnectTimer !== null) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
}

/**
 * Releases the current connection's capability wait before completion or disconnection.
 */
function clearSessionStateTimer() {
    if (sessionStateTimer !== null) {
        clearTimeout(sessionStateTimer);
        sessionStateTimer = null;
    }
}

/**
 * Releases the active heartbeat timer.
 */
function stopHeartbeat() {
    if (heartbeatTimer !== null) {
        clearInterval(heartbeatTimer);
        heartbeatTimer = null;
    }
}

/**
 * Keeps the session active and renews its HTTP cookie.
 */
export function startHeartbeat() {
    stopHeartbeat();

    heartbeatTimer = setInterval(() => {
        sendPacket(SEND_HEARTBEAT);
        renewSessionCookie();
    }, getHeartbeatInterval());
}

/**
 * Schedules bounded exponential reconnect attempts.
 */
function scheduleReconnect(delayOverride) {
    const socket = window.domSocket;

    if (socketIsOpen(socket) || socketIsConnecting(socket) || connecting || reconnectTimer !== null) {
        return;
    }

    if (socketIsClosing(socket) && delayOverride === undefined) {
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            scheduleReconnect(0);
        }, 300);
        return;
    }

    const delay = delayOverride !== undefined ? delayOverride : Math.min(RECONNECT_MAX_DELAY_MS, RECONNECT_BASE_DELAY_MS * Math.pow(2, reconnectAttempt++));

    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        connectSocket();
    }, delay);
}

/**
 * Creates one connection and binds ordered server and lifecycle callbacks.
 */
function connectSocket() {
    const existing = window.domSocket;

    if (socketIsOpen(existing) || socketIsConnecting(existing) || connecting) {
        return;
    }

    if (socketIsClosing(existing)) {
        scheduleReconnect(300);
        return;
    }

    connecting = true;
    clearReconnectTimer();

    const socketProtocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const staleReconnect = !firstConnection && socketClosedAt > 0 && Date.now() - socketClosedAt > STALE_RECONNECT_MS;
    const route = bootstrapEnabled && (firstConnection || staleReconnect) ? encodeURIComponent(location.pathname) : '';
    const bootstrapping = route !== '' && route.length <= 2048;
    bootstrapPath = bootstrapping ? location.pathname : '';
    const socket = new WebSocket(`${socketProtocol}//${location.host}/ws${bootstrapping ? '?geshra-bootstrap=' + route + '&geshra-protocol=2' : ''}`);
    let opened = false;
    socket.binaryType = 'arraybuffer';
    window.domSocket = socket;

    socket.onopen = () => {
        if (window.domSocket !== socket) return;
        opened = true;

        connecting = false;
        reconnectAttempt = 0;
        socketClosedAt = 0;
        clearReconnectTimer();
        startHeartbeat();

        setCompactEncoding(false);
        awaitingSession = true;
        reloadOnReady = firstConnection || staleReconnect;
        clearSessionStateTimer();
        sessionStateTimer = setTimeout(() => {
            sessionStateTimer = null;
            if (window.domSocket !== socket || !awaitingSession || !socketIsOpen(socket)) return;
            setCompactEncoding(false);
            sessionReady(false);
        }, SESSION_STATE_TIMEOUT_MS);
    };

    socket.onmessage = (event) => {
        if (window.domSocket === socket) receivePacket(event.data);
    };

    socket.onclose = (event) => {
        if (window.domSocket !== socket) return;
        const failedBootstrap = bootstrapping && !opened;
        if (failedBootstrap) bootstrapEnabled = false;
        if (window.domSocket === socket) {
            if (event?.code === 1011 && event.reason === 'bootstrap failed') {
                firstConnection = true;
                bootstrapEnabled = false;
            }
            socketClosedAt = Date.now();
            clearSessionStateTimer();
            awaitingSession = false;
        }

        connecting = false;
        stopHeartbeat();
        scheduleReconnect(failedBootstrap ? 0 : undefined);
    };

    socket.onerror = () => {
        if (window.domSocket !== socket) return;
        if (window.domSocket === socket && socketClosedAt === 0) {
            socketClosedAt = Date.now();
        }

        connecting = false;
        stopHeartbeat();
        if (bootstrapping && !opened) {
            bootstrapEnabled = false;
            scheduleReconnect(0);
        } else scheduleReconnect(500);
    };
}

/**
 * Returns connection availability or starts reconnecting.
 */
function ensureSocket() {
    const socket = window.domSocket;

    if (socketIsOpen(socket)) {
        return true;
    }

    if (!socketIsConnecting(socket)) {
        connectSocket();
    }

    return false;
}

/**
 * Send a serialized packet to the server.
 * If the WebSocket has gone idle/closed, queue the user action and reconnect.
 * @param {number} op       - Opcode of the packet to send (e.g., SEND_CLICK).
 * @param {...*} args       - Arguments for the corresponding packets_out[op] serializer.
 */
export function sendPacket(op, ...args) {
    if (op === SEND_NAVIGATE) routedPath = args[0];
    const packet = encodePacket(op, args);

    if (!packet) {
        return false;
    }

    const socket = window.domSocket;
    if (socketIsOpen(socket)) {
        try {
            socket.send(packet);
            return true;
        } catch (ignored) {
            if (window.domSocket === socket && socketClosedAt === 0) {
                socketClosedAt = Date.now();
            }
        }
    }

    if (op !== SEND_HEARTBEAT && op !== SEND_VIEWPORT) {
        queuePacket(packet);
    }

    ensureSocket();
    return false;
}

/**
 * Sends replaceable viewport state only on a ready connection, never to the reconnect event queue.
 * Acknowledged session recovery explicitly requests the latest viewport instead.
 */
export function sendViewportPacket(id, revision, first, end, token) {
    if (!viewportConnectionReady()) return false;
    return sendPacket(SEND_VIEWPORT, id, revision, first, end, token);
}

/**
 * Reports whether replaceable viewport state may be sent without entering reconnect buffering.
 */
export function viewportConnectionReady() {
    return !awaitingSession && socketIsOpen(window.domSocket);
}

/**
 * Set up the WebSocket connection and global event hooks once the DOM is ready.
 */
export function startRuntime() {
    connectSocket();

    /**
     * Intercept in-page link clicks to drive client-side routing.
     * Preserve downloads, new windows, modified clicks and fragment/query navigation so the
     * browser or Android host can implement their native semantics. Only plain same-origin
     * route links use the lightweight WebSocket navigation path.
     */
    document.body.addEventListener('click', (e) => {
        if (e.defaultPrevented || e.button !== 0 || e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
        const link = e.target instanceof Element ? e.target.closest('a[href]') : null;
        if (
            link &&
            link.origin === location.origin &&
            !link.hasAttribute('download') &&
            (!link.target || link.target.toLowerCase() === '_self') &&
            !link.href.includes('#') &&
            !link.search
        ) {
            e.preventDefault();
            history.pushState({}, '', link.pathname);
            sendPacket(SEND_NAVIGATE, link.pathname);
        }
    });

    /**
     * Listen for browser back/forward navigation and notify the server.
     */
    window.addEventListener('popstate', () => {
        if (location.pathname !== routedPath) sendPacket(SEND_NAVIGATE, location.pathname);
    });

    /**
     * When a sleeping tab wakes up, immediately reconnect/touch the session.
     */
    window.addEventListener('focus', () => {
        if (ensureSocket()) {
            sendPacket(SEND_HEARTBEAT);
        }
    });

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden && ensureSocket()) {
            sendPacket(SEND_HEARTBEAT);
        }
    });

    window.addEventListener('pageshow', () => {
        if (ensureSocket()) {
            sendPacket(SEND_HEARTBEAT);
        }
    });
}
/**
 * Starts navigation or replay only after receiving this socket's encoding capability.
 * An acknowledged bootstrap is reused only while its requested path still matches the browser.
 * @param {boolean} compact whether the server accepts compact client actions
 * @param {boolean} bootstrapped whether the server is already sending this connection's initial route
 */
export function sessionReady(compact, bootstrapped = false) {
    if (!awaitingSession) {
        previousCompact = compact;
        return;
    }
    clearSessionStateTimer();
    awaitingSession = false;
    reloadOnReady ||= previousCompact !== null && previousCompact !== compact;
    previousCompact = compact;
    if (reloadOnReady) {
        firstConnection = false;
        queuedPackets.length = 0;
        queuedBytes = 0;
        if (!bootstrapped || bootstrapPath !== location.pathname) sendPacket(SEND_NAVIGATE, location.pathname);
    } else {
        sendPacket(SEND_HEARTBEAT);
        flushQueuedPackets();
        resumeVirtualLists();
    }
}
