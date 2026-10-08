package geshra.audit;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.packets.FileUploadPacketHandler;
import geshra.net.web.ui.DOMDispatcher;
import geshra.net.web.ui.DOMUpdate;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.css.Style;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises browser-visible protocol limits, ordering, and style invariants found during the audit.
 * Embedded transports expose complete wire messages without sockets; every owned buffer and
 * thread-local session is released in a finally block.
 */
class ProtocolRegressionTest {

    /**
     * Verifies that structural updates precede property changes without overflow or unstable ties.
     */
    @Test
    void ordersStructuralUpdatesBeforeStablePropertyUpdates() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            DOMDispatcher dispatcher = new DOMDispatcher();
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 11));
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 12));
            dispatcher.queue(new DOMUpdate(DOMUpdateType.APPEND_CHILD, 10));
            dispatcher.flush(channel);
            BinaryWebSocketFrame frame = channel.readOutbound();
            try {
                ByteBuf bytes = frame.content();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(1);
                assertThat(bytes.readUnsignedShort())
                        .isEqualTo(3);
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(DOMUpdateType.APPEND_CHILD.getCode());
                assertThat(bytes.readInt())
                        .isEqualTo(10);
                assertThat((int) bytes.readUnsignedByte())
                        .isZero();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(DOMUpdateType.SET_TEXT.getCode());
                assertThat(bytes.readInt())
                        .isEqualTo(11);
                assertThat((int) bytes.readUnsignedByte())
                        .isZero();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(DOMUpdateType.SET_TEXT.getCode());
                assertThat(bytes.readInt())
                        .isEqualTo(12);
                assertThat((int) bytes.readUnsignedByte())
                        .isZero();
                assertThat(bytes.isReadable())
                        .isFalse();
            } finally {
                frame.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Verifies that non-ASCII titles carry their encoded byte count rather than Java string length.
     */
    @Test
    void encodesTitleLengthInUtf8Bytes() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "title-audit", channel);
        SessionContext.set(context);
        try {
            String title = "Café 東京";
            context.getUI()
                    .setTitle(title);
            BinaryWebSocketFrame frame = channel.readOutbound();
            try {
                ByteBuf bytes = frame.content();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(3);
                int length = bytes.readUnsignedShort();
                assertThat(length)
                        .isEqualTo(title.getBytes(StandardCharsets.UTF_8).length);
                assertThat(bytes.readCharSequence(length, StandardCharsets.UTF_8).toString())
                        .isEqualTo(title);
                assertThat(bytes.isReadable())
                        .isFalse();
            } finally {
                frame.release();
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Verifies that values too large for the unsigned-short parameter length fail explicitly.
     */
    @Test
    void rejectsOversizedUtf8ValuesAndTitles() {
        DOMUpdate update = new DOMUpdate(DOMUpdateType.SET_TEXT, 1)
                .param(DOMUpdateParam.TEXT, "é".repeat(32768));
        assertThatThrownBy(update::encode)
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UI().setTitle("x".repeat(65536)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Verifies that property replacement, self-mapping, and lock checks preserve style invariants.
     */
    @Test
    void replacesStylePropertiesAndRejectsLockedMutation() {
        Style style = new Style(null, ".example");
        style.set("color", "red");
        style.set("color", "blue");
        assertThat(style.get("color"))
                .isEqualTo("blue");
        assertThat(style.inline())
                .isEqualTo("color: blue;");
        style.map(style);
        assertThat(style.get("color"))
                .isEqualTo("blue");
        style.combinator(">", ".child");
        assertThat(style.toCSS(false))
                .contains(".example > .child");
        assertThat(style.getSelector())
                .isEqualTo(".example > .child");
        style.lock();
        assertThatThrownBy(() -> style.map(new Style(null, ".other")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> style.combinator(" ", ".leaf"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(style.get("color"))
                .isEqualTo("blue");
    }

    /**
     * Verifies constructor boundary validation and malformed upload rejection before allocation.
     */
    @Test
    void rejectsInvalidSelectorsAndUploadLengths() {
        assertThatThrownBy(() -> new Style(null, " "))
                .isInstanceOf(IllegalArgumentException.class);
        ByteBuf packet = Unpooled.buffer();
        try {
            packet.writeInt(1);
            packet.writeShort(0);
            packet.writeInt(-1);
            assertThatThrownBy(() -> new FileUploadPacketHandler().handlePacket(null, packet))
                    .isInstanceOf(IllegalArgumentException.class);
        } finally {
            packet.release();
        }
    }
}
