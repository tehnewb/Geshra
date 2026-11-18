/**
 * Same-origin session actions, CSRF state, and browser cookie acknowledgment.
 */
import {sendPacket, startHeartbeat, socketIsOpen, sessionReady} from './transport.js';
import {setCompactEncoding} from './encode.js';
/**
 * Shared UTF-8 session JSON decoder.
 */
const dec = new TextDecoder();
let csrfToken = ''; // Current anti-forgery secret.
let sessionActions = Promise.resolve(); // Promise tail preserving HTTP action order.
let cookieRenewAt = Infinity; // Next cookie renewal deadline in milliseconds.
let heartbeatInterval = 25000; // Heartbeat period derived from cookie lifetime.
let renewingCookie = false; // Whether a cookie renewal is in progress.
let nextSessionRequest = 0; // Next authentication acknowledgment identifier.
let pendingSessionAck = null; // Pending cookie acknowledgment, or null.
/**
 * Updates CSRF state and renewal timing without exposing bearer cookies.
 */
function rememberSession(state) {
    csrfToken = state.csrfToken;
    if (state.cookieMaxAge) {
        cookieRenewAt = Date.now() + state.cookieMaxAge * 750;
        heartbeatInterval = Math.max(250, Math.min(25000, state.cookieMaxAge * 250));
        if (socketIsOpen(window.domSocket)) startHeartbeat();
    }
}
/**
 * Fetches safe session state through same-origin HTTP.
 */
async function sessionState() {
    const response = await fetch('/_geshra/session', {credentials: 'same-origin', cache: 'no-store'});
    if (!response.ok) throw new Error('Unable to read the browser session');
    const state = await response.json();
    rememberSession(state);
    return state;
}

/**
 * Performs a protected action and waits for cookie acknowledgment.
 */
async function sessionRequest(action, fields) {
    if (action === 'session') return sessionState();
    if (!csrfToken) await sessionState();
    const authentication = action === 'login' || action === 'logout';
    const request = authentication ? ++nextSessionRequest : 0;
    const response = await fetch('/_geshra/' + action, {
        method: 'POST',
        credentials: 'same-origin',
        cache: 'no-store',
        headers: {'Content-Type': 'application/x-www-form-urlencoded', 'X-CSRF-Token': csrfToken},
        body: new URLSearchParams({...fields, _request: request})
    });
    const state = await response.json();
    if (state.csrfToken) rememberSession(state);
    if (authentication && socketIsOpen(window.domSocket)) {
        await new Promise((resolve, reject) => {
            const timeout = setTimeout(() => {
                if (pendingSessionAck?.request === request) pendingSessionAck = null;
                reject(new Error('Authentication callback timed out'));
            }, 10000);
            pendingSessionAck = {
                request,
                resolve: () => {
                    clearTimeout(timeout);
                    resolve();
                }
            };
            sendPacket(11, request);
        });
    }
    if (!response.ok) throw new Error(state.error || 'Session action failed');
    if (action === 'login') state.success = response.headers.get('X-Authentication-Success') === 'true';
    return state;
}

/**
 * Serializes identity actions while returning failures to their caller.
 */
function queueSessionAction(action, fields) {
    const result = sessionActions.then(() => sessionRequest(action, fields));
    sessionActions = result.catch(() => null);
    return result;
}

window.geshra = Object.freeze({
    session: () => queueSessionAction('session'),
    login: (username, password) => queueSessionAction('login', {username, password}),
    logout: () => queueSessionAction('logout')
});

/**
 * Applies safe session state or completes a matching authentication acknowledgment.
 */
export function receiveSession(view) {
    const length = view.getUint16(1);
    const action = JSON.parse(dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, length)));
    if (action.action === 'ack') {
        if (pendingSessionAck?.request === action.request) {
            const ack = pendingSessionAck;
            pendingSessionAck = null;
            ack.resolve();
        }
        return;
    }
    if (action.action === 'state') {
        setCompactEncoding(action.protocol === 2);
        rememberSession(action);
        sessionReady(action.protocol === 2, action.bootstrap === true);
        return;
    }
    queueSessionAction(action.action, action.action === 'login' ? {username: action.username, password: action.password} : {}).catch((error) =>
        console.error('Session action failed:', error.message)
    );
}
/**
 * Returns the heartbeat period selected by the current session cookie lifetime.
 */
export function getHeartbeatInterval() {
    return heartbeatInterval;
}
/**
 * Renews the HTTP cookie only when its next renewal deadline has elapsed.
 */
export function renewSessionCookie() {
    if (Date.now() < cookieRenewAt || renewingCookie) return;
    renewingCookie = true;
    queueSessionAction('session')
        .catch((error) => console.error('Session renewal failed:', error.message))
        .finally(() => {
            renewingCookie = false;
        });
}
