package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.TextComponent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies batch visibility, control-message ordering, and wire serialization through an owned
 * transport. Every test releases frames and thread-local state even when assertions fail.
 */
class UIBatchTest {
    /**
     * A nested update emits no intermediate packets and preserves all mutations in one DOM frame.
     */
    @Test
    void groupsNestedUpdatesIntoOneOrderedPacket() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "batch", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            ui.beginUpdate();
            TextComponent component = new TextComponent("initial", "p");
            ui.add(component);
            component.setText("updated");
            ui.endUpdate();
            assertThat((Object) channel.readOutbound())
                    .isNull();
            ui.endUpdate();
            BinaryWebSocketFrame frame = channel.readOutbound();
            try {
                ByteBuf bytes = frame.content();
                assertThat((int) bytes.readUnsignedByte())
                    .isEqualTo(1);
                assertThat(bytes.readUnsignedShort())
                    .isEqualTo(3);
                assertThat((int) bytes.getUnsignedByte(bytes.readerIndex()))
                    .isEqualTo(DOMUpdateType.APPEND_CHILD.getCode());
                assertThat(bytes.toString(StandardCharsets.UTF_8))
                    .contains("initial", "updated");
            } finally {
                frame.release();
            }
            assertThat((Object) channel.readOutbound())
                    .isNull();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * A group containing only a title still flushes its pending control packet.
     */
    @Test
    void flushesControlOnlyGroups() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "title-group", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            ui.setTitle("東京");
            assertThat((Object) channel.readOutbound())
                    .isNull();
            ui.endUpdate();
            BinaryWebSocketFrame frame = channel.readOutbound();
            try {
                assertThat((int) frame.content().readUnsignedByte())
                    .isEqualTo(3);
                assertThat(frame.content().readUnsignedShort())
                    .isEqualTo(6);
            } finally {
                frame.release();
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Script execution establishes a DOM ordering barrier inside an open group.
     */
    @Test
    void sendsRequiredDomBeforeExecutingScripts() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "script-group", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            TextComponent component = new TextComponent("content", "p");
            ui.add(component);
            component.executeJS("window.example = 'é';");
            ui.endUpdate();
            BinaryWebSocketFrame dom = channel.readOutbound();
            BinaryWebSocketFrame script = channel.readOutbound();
            try {
                assertThat((int) dom.content().getUnsignedByte(0))
                    .isEqualTo(1);
                assertThat((int) script.content().readUnsignedByte())
                    .isEqualTo(4);
                int length = script.content()
                        .readUnsignedShort();
                assertThat(script.content().readCharSequence(length, StandardCharsets.UTF_8).toString())
                    .contains("é");
            } finally {
                dom.release();
                script.release();
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Inline and spilled parameter storage keep insertion order while replacing existing values.
     */
    @Test
    void encodesSpilledParametersAndRestoresTheWriterAfterFailure() {
        DOMUpdate update = new DOMUpdate(DOMUpdateType.SET_STYLE, 42);
        DOMUpdateParam[] keys = { DOMUpdateParam.TEXT, DOMUpdateParam.HTML, DOMUpdateParam.KEY, DOMUpdateParam.VALUE, DOMUpdateParam.CLASS_NAME, DOMUpdateParam.STYLE_PROPERTY, DOMUpdateParam.STYLE_VALUE };
        for (DOMUpdateParam key : keys) update.param(key, "é");
        update.param(DOMUpdateParam.TEXT, "東京");
        ByteBuf bytes = update.encode();
        try {
            bytes.skipBytes(5);
            assertThat((int) bytes.readUnsignedByte())
                    .isEqualTo(keys.length);
            for (DOMUpdateParam key : keys) {
                assertThat((int) bytes.readUnsignedByte())
                    .isEqualTo(key.getCode());
                int length = bytes.readUnsignedShort();
                assertThat(bytes.readCharSequence(length, StandardCharsets.UTF_8).toString())
                    .isEqualTo(key == DOMUpdateParam.TEXT ? "東京" : "é");
            }
        } finally {
            bytes.release();
        }
        ByteBuf destination = Unpooled.buffer();
        try {
            destination.writeByte(99);
            DOMUpdate oversized = new DOMUpdate(DOMUpdateType.SET_TEXT, 1)
                    .param(DOMUpdateParam.TEXT, "é".repeat(32768));
            assertThatThrownBy(() -> oversized.writeTo(destination))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(destination.writerIndex())
                    .isEqualTo(1);
            assertThat((int) destination.getUnsignedByte(0))
                    .isEqualTo(99);
        } finally {
            destination.release();
        }
    }

    /**
     * Network backpressure retains mutations until writes can proceed.
     */
    @Test
    void retainsUpdatesUnderBackpressure() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            channel.config()
                    .setWriteBufferWaterMark(new WriteBufferWaterMark(32, 64));
            channel.write(Unpooled.buffer(128).writeZero(128));
            assertThat(channel.isWritable())
                    .isFalse();
            DOMDispatcher dispatcher = new DOMDispatcher();
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 1).param(DOMUpdateParam.TEXT, "retained"));
            dispatcher.flush(channel);
            assertThat(dispatcher.hasUpdates())
                    .isTrue();
            channel.flush();
            ByteBuf previous = channel.readOutbound();
            previous.release();
            dispatcher.flush(channel);
            BinaryWebSocketFrame frame = channel.readOutbound();
            try {
                assertThat(frame.content().toString(StandardCharsets.UTF_8))
                        .contains("retained");
            } finally {
                frame.release();
            }
            assertThat(dispatcher.hasUpdates())
                    .isFalse();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Batches larger than the unsigned-short wire count split into complete ordered packets.
     */
    @Test
    void splitsLargeBatchesAtTheWireCountLimit() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            DOMDispatcher dispatcher = new DOMDispatcher();
            for (int i = 0; i < 65536; i++) dispatcher.queue(new DOMUpdate(DOMUpdateType.CLEAR_CHILDREN, i));
            dispatcher.flush(channel);
            int expectedID = 0;
            int packets = 0;
            BinaryWebSocketFrame frame;
            while ((frame = channel.readOutbound()) != null) {
                try {
                    ByteBuf bytes = frame.content();
                    assertThat(bytes.readableBytes())
                            .isLessThanOrEqualTo(65536 + 6);
                    bytes.skipBytes(1);
                    int count = bytes.readUnsignedShort();
                    assertThat(count)
                            .isBetween(1, 65535);
                    for (int i = 0; i < count; i++) {
                        bytes.skipBytes(1);
                        assertThat(bytes.readInt())
                                .isEqualTo(expectedID++);
                        bytes.skipBytes(1);
                    }
                    packets++;
                } finally {
                    frame.release();
                }
            }
            assertThat(expectedID)
                    .isEqualTo(65536);
            assertThat(packets)
                    .isGreaterThan(1);
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
