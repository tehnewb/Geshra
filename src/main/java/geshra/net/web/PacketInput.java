package geshra.net.web;

import io.netty.buffer.ByteBuf;

/**
 * Reads built-in action fields in either the legacy or negotiated compact representation.
 * The dispatch boundary owns the session's temporary encoding flag; custom packet handlers
 * continue receiving their original payload unchanged. No reader is allocated per action.
 */
public final class PacketInput {
    /**
     * Prevents instances of the action field reader.
     */
    private PacketInput() { }

    /**
     * Reads a component ID, revision, nonce, or viewport index.
     * @param context dispatch session
     * @param packet action payload
     * @return decoded non-negative compact value or legacy signed int
     */
    public static int integer(SessionContext context, ByteBuf packet) {
        /*
         * Legacy clients retain fixed-width fields; compact clients pay no object conversion cost.
         */
        return context != null && context.isCompactInput() ? Wire.readUnsigned(packet) : packet.readInt();
    }

    /**
     * Reads a bounded UTF-8 byte length.
     * @param context dispatch session
     * @param packet action payload
     * @return byte length from zero to 65535
     */
    public static int length(SessionContext context, ByteBuf packet) {
        /*
         * Keep the existing text limit even though the varint representation can encode more.
         */
        int length = context != null && context.isCompactInput() ? Wire.readUnsigned(packet) : packet.readUnsignedShort();
        if (length > 65535) throw new IllegalArgumentException("Text exceeds 65535 UTF-8 bytes");
        return length;
    }

    /**
     * Reads a signed coordinate using compact ZigZag or the legacy signed short.
     * @param context dispatch session
     * @param packet action payload
     * @return signed 16-bit browser coordinate
     */
    public static int coordinate(SessionContext context, ByteBuf packet) {
        /*
         * ZigZag preserves negative coordinates while zero and small offsets use one byte.
         */
        if (context == null || !context.isCompactInput()) return packet.readShort();
        int value = Wire.readUnsigned(packet);
        if (value > 65535) throw new IllegalArgumentException("Coordinate exceeds signed short range");
        return value >>> 1 ^ -(value & 1);
    }
}
