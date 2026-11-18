/**
 * Legacy action codecs retained for old servers; negotiated compact actions use the dedicated encoder.
 */
import {
    SEND_NAVIGATE,
    SEND_CLICK,
    SEND_KEY_UP,
    SEND_KEY_DOWN,
    SEND_VALUE_CHANGE,
    SEND_SUBMIT,
    SEND_FILE,
    SEND_VIEWPORT,
    SEND_DOM_EVENT,
    SEND_VALUE_SYNC,
    SEND_HEARTBEAT
} from './protocol.js';
import {encodeCompact} from './compact-encode.js';
/**
 * Shared UTF-8 encoder.
 */
const enc = new TextEncoder();
/**
 * Legacy action serializers indexed by opcode.
 */
const packets_out = [];
let compact = false; // Capability of the current connection.
/**
 * Serialize a navigation packet.
 * @param {string} path - The URL or route to navigate to.
 * @returns {Uint8Array|null} Packet: [opcode, lengthHigh, lengthLow, UTF-8 bytes...] or null if invalid.
 */
packets_out[SEND_NAVIGATE] = (path) => {
    if (typeof path !== 'string') return null;
    const bytes = enc.encode(path);
    const len = bytes.length;
    if (len > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    const buf = new Uint8Array(3 + len);
    buf[0] = SEND_NAVIGATE;
    buf[1] = (len >> 8) & 0xff;
    buf[2] = len & 0xff;
    buf.set(bytes, 3);
    return buf;
};

/**
 * Serialize a mouse-click packet.
 * @param {number} id       - Element identifier.
 * @param {number} btn      - Button (0=left,1=middle,2=right).
 * @param {number} cx       - Click X coordinate relative to element.
 * @param {number} cy       - Click Y coordinate relative to element.
 * @param {number} px       - Page X coordinate.
 * @param {number} py       - Page Y coordinate.
 * @param {number} sx       - Screen X coordinate.
 * @param {number} sy       - Screen Y coordinate.
 * @param {boolean} ctrl    - Ctrl key pressed.
 * @param {boolean} shift   - Shift key pressed.
 * @param {boolean} alt     - Alt key pressed.
 * @param {boolean} meta    - Meta key pressed.
 * @returns {Uint8Array} Packet: [opcode, id(4B), btn, cx(2B), cy(2B), px(2B), py(2B), sx(2B), sy(2B), modifiers].
 */
packets_out[SEND_CLICK] = (id, btn, cx, cy, px, py, sx, sy, ctrl, shift, alt, meta) => {
    const buf = new Uint8Array(19);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_CLICK;
    dv.setUint32(1, id);
    buf[5] = btn;
    dv.setInt16(6, cx);
    dv.setInt16(8, cy);
    dv.setInt16(10, px);
    dv.setInt16(12, py);
    dv.setInt16(14, sx);
    dv.setInt16(16, sy);
    buf[18] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
    return buf;
};

/**
 * Serialize a key-up packet.
 * @param {number} id       - Element identifier.
 * @param {string} key      - Key value (e.g., "Enter").
 * @param {boolean} repeat  - Whether key is repeating.
 * @param {boolean} ctrl    - Ctrl key pressed.
 * @param {boolean} shift   - Shift key pressed.
 * @param {boolean} alt     - Alt key pressed.
 * @param {boolean} meta    - Meta key pressed.
 * @returns {Uint8Array} Packet: [opcode, id(4B), repeatFlag, modifiers, keyLengthHigh, keyLengthLow, UTF-8 key bytes...].
 */
packets_out[SEND_KEY_UP] = (id, key, repeat, ctrl, shift, alt, meta) => {
    const keyBytes = enc.encode(key);
    const len = keyBytes.length;
    if (len > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    const buf = new Uint8Array(9 + len);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_KEY_UP;
    dv.setUint32(1, id);
    buf[5] = repeat ? 1 : 0;
    buf[6] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
    buf[7] = (len >> 8) & 0xff;
    buf[8] = len & 0xff;
    buf.set(keyBytes, 9);
    return buf;
};

/**
 * Serialize a key-down packet (identical to key-up).
 */

packets_out[SEND_KEY_DOWN] = (id, key, repeat, ctrl, shift, alt, meta) => {
    const keyBytes = enc.encode(key);
    const len = keyBytes.length;
    if (len > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    const buf = new Uint8Array(9 + len);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_KEY_DOWN;
    dv.setUint32(1, id);
    buf[5] = repeat ? 1 : 0;
    buf[6] = (ctrl ? 1 : 0) | (shift ? 2 : 0) | (alt ? 4 : 0) | (meta ? 8 : 0);
    buf[7] = (len >> 8) & 0xff;
    buf[8] = len & 0xff;
    buf.set(keyBytes, 9);
    return buf;
};
/**
 * Serialize an input/change-value packet.
 * @param {number} id       - Element identifier.
 * @param {string} value    - New input value.
 * @returns {Uint8Array} Packet: [opcode, id(4B), valueLenHigh, valueLenLow, UTF-8 value bytes...].
 */
packets_out[SEND_VALUE_CHANGE] = (id, value) => {
    const valueBytes = enc.encode(value);
    const len = valueBytes.length;
    if (len > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    const buf = new Uint8Array(7 + len);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_VALUE_CHANGE;
    dv.setUint32(1, id);
    dv.setUint16(5, len);
    buf.set(valueBytes, 7);
    return buf;
};

/**
 * Serialize a form-submit packet.
 * @param {number} id - Form element identifier.
 * @returns {Uint8Array} Packet: [opcode, id(4B)].
 */
packets_out[SEND_SUBMIT] = (id) => {
    const buf = new Uint8Array(5);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_SUBMIT;
    dv.setUint32(1, id);
    return buf;
};

/**
 * Serialize a file-upload packet.
 * @param {number} id            - Input element identifier.
 * @param {string} fileName      - Name of the file.
 * @param {ArrayBuffer} arrayBuffer - File data.
 * @returns {Uint8Array} Packet: [opcode, id(4B), nameLenHigh, nameLenLow, fileLenHigh(4B)..., nameBytes..., fileBytes...].
 */
packets_out[SEND_FILE] = (id, fileName, arrayBuffer) => {
    const nameBytes = enc.encode(fileName);
    const nameLen = nameBytes.length;
    if (nameLen > 65535) throw new RangeError('Text exceeds 65535 UTF-8 bytes');
    if (arrayBuffer.byteLength > 65536) throw new RangeError('Upload exceeds the WebSocket frame limit');
    const fileBytes = new Uint8Array(arrayBuffer);
    const fileLen = fileBytes.length;
    const buf = new Uint8Array(11 + nameLen + fileLen);
    const dv = new DataView(buf.buffer);
    buf[0] = SEND_FILE;
    dv.setUint32(1, id);
    dv.setUint16(5, nameLen);
    dv.setUint32(7, fileLen);
    buf.set(nameBytes, 11);
    buf.set(fileBytes, 11 + nameLen);
    return buf;
};

/**
 * Serialize a heartbeat packet.
 * The server only uses this to touch the session and keep the WebSocket active.
 * @returns {Uint8Array} Packet: [opcode].
 */
packets_out[SEND_HEARTBEAT] = () => new Uint8Array([SEND_HEARTBEAT]);
packets_out[11] = (request) => {
    const bytes = new Uint8Array(5);
    bytes[0] = 11;
    new DataView(bytes.buffer).setInt32(1, request);
    return bytes;
};
packets_out[SEND_VALUE_SYNC] = packets_out[SEND_VALUE_CHANGE];
packets_out[SEND_VIEWPORT] = (id, revision, first, end, token) => {
    const bytes = new Uint8Array(token === undefined ? 17 : 21);
    const view = new DataView(bytes.buffer);
    view.setUint8(0, SEND_VIEWPORT);
    view.setInt32(1, id);
    view.setInt32(5, revision);
    view.setInt32(9, first);
    view.setInt32(13, end);
    if (token !== undefined) view.setInt32(17, token);
    return bytes;
};
// The common scalar encoder writes its original opcode; patch it for silent synchronization.
/**
 * Scalar encoder reused for silent browser normalization.
 */
const scalarSyncEncoder = packets_out[SEND_VALUE_SYNC];
packets_out[SEND_VALUE_SYNC] = (...args) => {
    const bytes = scalarSyncEncoder(...args);
    if (bytes) bytes[0] = SEND_VALUE_SYNC;
    return bytes;
};
packets_out[SEND_DOM_EVENT] = (id, name, value) => {
    const event = enc.encode(name);
    const snapshot = enc.encode(value || '');
    if (event.length > 65535 || snapshot.length > 65535) return null;
    const bytes = new Uint8Array(9 + event.length + snapshot.length);
    const view = new DataView(bytes.buffer);
    view.setUint8(0, SEND_DOM_EVENT);
    view.setInt32(1, id);
    view.setUint16(5, event.length);
    bytes.set(event, 7);
    view.setUint16(7 + event.length, snapshot.length);
    bytes.set(snapshot, 9 + event.length);
    return bytes;
};

/**
 * Returns an encoded action for the negotiated representation.
 */
export function encodePacket(op, args) {
    if (compact) return encodeCompact(op, args);
    const fn = packets_out[op];
    const packet = fn ? fn(...args) : null;
    if (packet && packet.byteLength > 65536) throw new RangeError('Action exceeds the WebSocket frame limit');
    return packet;
}
/**
 * Changes encoding only after receiving this connection's server capability.
 */
export function setCompactEncoding(enabled) {
    compact = enabled;
}
