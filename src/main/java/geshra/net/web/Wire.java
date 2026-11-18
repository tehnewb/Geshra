package geshra.net.web;

import io.netty.buffer.ByteBuf;
import io.netty.util.AttributeKey;

/**
 * Bounded unsigned base-128 integers shared by the compact browser protocol. Values are limited
 * to non-negative Java ints, so malformed fifth bytes cannot overflow into negative lengths.
 * Methods allocate no objects and consume at most five bytes. Legacy encoders remain available.
 */
public final class Wire {
    /**
     * Per-channel opt-in after a compact action or explicit compact-reader bootstrap capability.
     */
    public static final AttributeKey<Boolean> COMPACT = AttributeKey.valueOf("geshra.compact");
    /**
     * Optional bounded initial route, accepted only after the HTTP same-origin/session boundary.
     */
    static final AttributeKey<String> BOOTSTRAP_ROUTE = AttributeKey.valueOf("geshra.bootstrapRoute");
    /**
     * Explicit compact-reader capability supplied with an initial-route handshake request.
     */
    static final AttributeKey<Boolean> BOOTSTRAP_COMPACT = AttributeKey.valueOf("geshra.bootstrapCompact");
    /**
     * Browser-to-server compact envelope; the following byte identifies a built-in action.
     */
    public static final int CLIENT_COMPACT = 12;
    /**
     * Server-to-browser compact mutation batch, retaining a two-byte mutation count.
     */
    public static final int SERVER_COMPACT = 7;

    /**
     * Prevents construction of the stateless protocol codec.
     */
    private Wire() { }

    /**
     * Appends a non-negative integer in one to five base-128 bytes.
     * @param buffer caller-owned destination
     * @param value non-negative value
     */
    public static void writeUnsigned(ByteBuf buffer, int value) {
        /*
         * Small IDs and lengths take the single-byte path without temporary storage.
         */
        if (value < 0) throw new IllegalArgumentException("Unsigned value cannot be negative");
        while ((value & ~127) != 0) {
            buffer.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buffer.writeByte(value);
    }

    /**
     * Reads one canonical non-negative int, rejecting truncation, overflow, and overlong forms.
     * @param buffer current packet payload
     * @return decoded integer
     */
    public static int readUnsigned(ByteBuf buffer) {
        /*
         * A fixed five-byte ceiling bounds work even for hostile continuation sequences.
         */
        int result = 0;
        for (int shift = 0; shift <= 28; shift += 7) {
            int value = buffer.readUnsignedByte();
            if (shift == 28 && value > 7) throw new IllegalArgumentException("Compact integer overflow");
            result |= (value & 127) << shift;
            if ((value & 128) == 0) {
                if (shift != 0 && value == 0) throw new IllegalArgumentException("Overlong compact integer");
                return result;
            }
        }
        throw new IllegalArgumentException("Invalid compact integer");
    }
}
