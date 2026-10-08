package geshra.net.web;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;

import java.util.Objects;

/**
 * Encodes UTF-8 title, navigation, and script control messages into pooled buffers. Length prefixes
 * count encoded bytes rather than Java characters. The caller transfers ownership to SessionContext
 * or releases the returned buffer; failures release partial allocations internally.
 */
public final class TextPacketEncoder {
    /**
     * Largest control-message payload allowed by the unsigned 16-bit length prefix.
     */
    private static final int MAX_TEXT_BYTES = 65535;

    /**
     * Prevents instances of this stateless wire encoder.
     */
    private TextPacketEncoder() { }

    /**
     * Encodes one opcode and length-prefixed string without a temporary UTF-8 byte array.
     * @param channel allocator owner, or null for detached sessions
     * @param opcode control-message opcode
     * @param text string payload
     * @return owned pooled buffer
     * @throws IllegalArgumentException if the encoded payload exceeds 65535 bytes
     */
    public static ByteBuf encode(Channel channel, int opcode, String text) {
        /*
         * Reserve the length and patch it after direct encoding, avoiding a separate byte array
         * or a second UTF-8 scan. Detached sessions still use the shared pooled allocator.
         */
        Objects.requireNonNull(text, "text");
        if (text.length() > MAX_TEXT_BYTES) throw new IllegalArgumentException("Control message exceeds 65535 UTF-8 bytes");
        ByteBuf buffer = channel == null ? PooledByteBufAllocator.DEFAULT.buffer(3 + text.length()) : channel.alloc().buffer(3 + text.length());
        try {
            buffer.writeByte(opcode);
            buffer.writeShort(0);
            int length = ByteBufUtil.writeUtf8(buffer, text);
            if (length > MAX_TEXT_BYTES) throw new IllegalArgumentException("Control message exceeds 65535 UTF-8 bytes");
            buffer.setShort(1, length);
            return buffer;
        } catch (RuntimeException | Error failure) {
            buffer.release();
            throw failure;
        }
    }
}
