/**
 * Owns one outgoing action buffer. TextEncoder writes directly into sized storage, avoiding
 * a temporary UTF-8 array per string. The finished view is immutable after WebSocket.send.
 */
import {textBytes} from './wire-size.js';

/**
 * Encodes one action into caller-sized storage without retaining oversized backing arrays.
 */
export class PacketWriter {
    bytes; // Owned storage, sized exactly for normal compact action payloads.
    offset = 0; // Number of encoded bytes.

    /**
     * Creates one action buffer with its compact envelope and original action opcode.
     */
    constructor(opcode, capacity = 64) {
        this.bytes = new Uint8Array(capacity);
        this.bytes[this.offset++] = 12;
        this.bytes[this.offset++] = opcode;
    }

    /**
     * Appends one unsigned byte.
     */
    byte(value) {
        this.bytes[this.offset++] = value;
    }

    /**
     * Appends a canonical non-negative signed-32-bit integer in base-128 form.
     */
    unsigned(value) {
        if (!Number.isInteger(value) || value < 0 || value > 2147483647) throw new RangeError('Invalid compact integer');
        while (value > 127) {
            this.bytes[this.offset++] = (value & 127) | 128;
            value >>>= 7;
        }
        this.bytes[this.offset++] = value;
    }

    /**
     * Preserves the existing signed-short coordinate semantics using ZigZag encoding.
     */
    coordinate(value) {
        const signed = (value << 16) >> 16;
        this.unsigned((signed << 1) ^ (signed >> 31));
    }

    /**
     * Appends bounded UTF-8 text directly after its known variable-length byte count.
     */
    text(value, length = textBytes(value)) {
        this.unsigned(length);
        const destination = this.bytes.subarray(this.offset);
        const encoded = encoder.encodeInto(value, destination);
        if (encoded.read !== value.length || encoded.written !== length) throw new RangeError('Packet text capacity mismatch');
        this.offset += encoded.written;
    }

    /**
     * Appends an already owned upload byte view without intermediate copies.
     */
    raw(bytes) {
        this.bytes.set(bytes, this.offset);
        this.offset += bytes.length;
    }

    /**
     * Returns only the initialized payload; callers must not mutate it after sending.
     */
    finish() {
        if (this.offset > 65536) throw new RangeError('Action exceeds the 65536-byte WebSocket frame limit');
        return this.offset === this.bytes.length ? this.bytes : this.bytes.subarray(0, this.offset);
    }
}

/**
 * Shared stateless UTF-8 encoder; browser execution is confined to its document thread.
 */
const encoder = new TextEncoder();
