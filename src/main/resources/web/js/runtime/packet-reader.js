/**
 * Reads bounded compact fields from one server message. One reader belongs to one decode call;
 * malformed lengths and integer overflow fail before constructing an out-of-bounds text view.
 */
export class PacketReader {
    bytes; // Complete incoming WebSocket payload.
    offset = 0; // Next unread byte.

    /**
     * Wraps a complete binary message without copying its storage.
     */
    constructor(buffer) {
        this.bytes = new Uint8Array(buffer);
    }

    /**
     * Reads one byte, rejecting truncated mutations.
     */
    byte() {
        if (this.offset === this.bytes.length) throw new RangeError('Truncated compact packet');
        return this.bytes[this.offset++];
    }

    /**
     * Reads one canonical non-negative signed-32-bit base-128 integer.
     */
    unsigned() {
        let result = 0;
        for (let shift = 0; shift <= 28; shift += 7) {
            const value = this.byte();
            if (shift === 28 && value > 7) throw new RangeError('Compact integer overflow');
            result |= (value & 127) << shift;
            if ((value & 128) === 0) {
                if (shift !== 0 && value === 0) throw new RangeError('Overlong compact integer');
                return result;
            }
        }
        throw new RangeError('Invalid compact integer');
    }

    /**
     * Decodes a bounded UTF-8 parameter using the shared decoder.
     */
    text() {
        const length = this.unsigned();
        if (length > 65535 || length > this.bytes.length - this.offset) throw new RangeError('Invalid compact text length');
        const text = decoder.decode(this.bytes.subarray(this.offset, this.offset + length));
        this.offset += length;
        return text;
    }
}

/**
 * Shared stateless UTF-8 decoder, used only on the document thread.
 */
const decoder = new TextDecoder();
