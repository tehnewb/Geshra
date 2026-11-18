/**
 * Legacy and compact server packet decoding.
 */
import {
    RECEIVE_NAVIGATE,
    RECEIVE_DOM_UPDATE,
    RECEIVE_COOKIE,
    RECEIVE_TITLE,
    COOKIE_NAME,
    COOKIE_VALUE,
    COOKIE_PATH,
    COOKIE_MAX_AGE,
    COOKIE_EXPIRES,
    COOKIE_DOMAIN,
    COOKIE_SECURE,
    COOKIE_SAME_SITE
} from './protocol.js';
import {updateDOM} from './dom.js';
import {flushValueSynchronization} from './values.js';
import {receiveSession} from './session.js';
import {PacketReader} from './packet-reader.js';
/**
 * Shared UTF-8 decoder.
 */
const dec = new TextDecoder();
/**
 * Opcode-indexed server message handlers.
 */
const packets_in = [];
/**
 * Handle server‐sent title updates.
 * Packet format: [opcode=RECEIVE_TITLE][lenHigh][lenLow][UTF-8 title…]
 * @param {DataView} view - DataView over the received ArrayBuffer.
 */
packets_in[RECEIVE_TITLE] = (view) => {
    const len = view.getUint16(1);
    document.title = dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, len));
};

/**
 * Handle server‐sent cookie instructions.
 * Packet format: [opcode=RECEIVE_COOKIE][paramCount][key][len][value]… repeated
 * @param {DataView} view
 */
packets_in[RECEIVE_COOKIE] = (view) => {
    let offset = 1;
    const count = view.getUint8(offset++);
    const cookieArr = [];
    for (let i = 0; i < count; i++) {
        const key = view.getUint8(offset++);
        const len = view.getUint16(offset);
        offset += 2;
        const val = dec.decode(new Uint8Array(view.buffer, offset, len));
        offset += len;
        cookieArr[key] = val;
    }
    // Build cookie string: name=value; otherAttrs...
    let str = `${cookieArr[COOKIE_NAME]}=${cookieArr[COOKIE_VALUE]}`;
    if (cookieArr[COOKIE_PATH]) str += `; path=${cookieArr[COOKIE_PATH]}`;
    if (cookieArr[COOKIE_DOMAIN]) str += `; domain=${cookieArr[COOKIE_DOMAIN]}`;
    if (cookieArr[COOKIE_MAX_AGE]) str += `; max-age=${cookieArr[COOKIE_MAX_AGE]}`;
    if (cookieArr[COOKIE_EXPIRES]) str += `; expires=${cookieArr[COOKIE_EXPIRES]}`;
    if (cookieArr[COOKIE_SECURE]) str += '; secure';
    if (cookieArr[COOKIE_SAME_SITE]) str += `; SameSite=${cookieArr[COOKIE_SAME_SITE]}`;
    document.cookie = str;
};

/**
 * Handle server‐sent navigation commands.
 * Packet format: [opcode=RECEIVE_NAVIGATE][lenHigh][lenLow][UTF-8 path…]
 * @param {DataView} view
 */
packets_in[RECEIVE_NAVIGATE] = (view) => {
    const len = view.getUint16(1); // read length of path string from byte offset 1
    window.location.href = dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, len));
};

/**
 * Handle server‐sent DOM mutations.
 * Packet format: [opcode=RECEIVE_DOM_UPDATE][countHigh][countLow]
 *   then repeated per mutation:
 *   [type][id(4B)][paramCount][key][lenHigh][lenLow][UTF-8 val…]…
 * @param {DataView} view
 */
packets_in[RECEIVE_DOM_UPDATE] = (view) => {
    let offset = 1;
    const count = view.getUint16(offset);
    offset += 2;
    const params = [];
    const keys = [];
    let previousCount = 0;
    for (let i = 0; i < count; i++) {
        const type = view.getUint8(offset++);
        const id = view.getInt32(offset);
        offset += 4;
        const paramCount = view.getUint8(offset++);
        for (let previous = 0; previous < previousCount; previous++) params[keys[previous]] = undefined;
        for (let j = 0; j < paramCount; j++) {
            const key = view.getUint8(offset++);
            keys[j] = key;
            const len = view.getUint16(offset);
            offset += 2;
            params[key] = dec.decode(new Uint8Array(view.buffer, offset, len));
            offset += len;
        }
        previousCount = paramCount;
        updateDOM(type, id, params);
    }
    flushValueSynchronization();
};

packets_in[6] = receiveSession;
packets_in[7] = receiveCompact;
packets_in[4] = (view) => {
    const length = view.getUint16(1);
    const script = dec.decode(new Uint8Array(view.buffer, view.byteOffset + 3, length));
    (0, eval)(script);
};
/**
 * Applies bounded compact mutations using one reusable parameter array per batch.
 */
function receiveCompact(view) {
    const reader = new PacketReader(view.buffer);
    reader.offset = 3;
    const count = view.getUint16(1);
    const params = new Array(19);
    const keys = new Uint8Array(19);
    let previousCount = 0;
    for (let index = 0; index < count; index++) {
        const type = reader.byte();
        const id = reader.unsigned();
        const fields = reader.byte();
        if (fields > 19) throw new RangeError('Invalid compact parameter count');
        for (let previous = 0; previous < previousCount; previous++) params[keys[previous]] = undefined;
        for (let field = 0; field < fields; field++) {
            const header = reader.byte();
            const key = header & 31;
            const kind = header >>> 5;
            if (key > 18 || kind > 4) throw new RangeError('Invalid compact parameter');
            keys[field] = key;
            params[key] = kind === 0 ? reader.text() : kind === 1 ? 'false' : kind === 2 ? 'true' : kind === 3 ? '' : reader.unsigned();
        }
        previousCount = fields;
        updateDOM(type, id, params);
    }
    if (reader.offset !== reader.bytes.length) throw new RangeError('Trailing compact mutation data');
    flushValueSynchronization();
}
/**
 * Dispatches a complete binary server message to its opcode handler.
 */
export function receivePacket(data) {
    const view = new DataView(data);
    const opcode = view.getUint8(0);
    const handler = packets_in[opcode];
    if (handler) handler(view);
    else console.warn('Unknown server packet opcode:', opcode);
}
