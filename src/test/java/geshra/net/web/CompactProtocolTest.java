package geshra.net.web;

import geshra.net.web.packets.MousePacketHandler;
import geshra.net.web.packets.RoutePacketHandler;
import geshra.net.web.packets.ValueChangePacketHandler;
import geshra.net.web.ui.DOMDispatcher;
import geshra.net.web.ui.DOMUpdate;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.TextField;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies exact compact bytes, bounded integer decoding, legacy compatibility, and real action
 * dispatch. Browser tests separately verify native DOM behavior under the negotiated protocol.
 */
class CompactProtocolTest {
    /**
     * Replacement handlers and occupied envelope opcodes retain the original wire contract.
     */
    @Test
    void requiresExplicitCompactSupportFromReplacements() {
        PacketHandler replacement = new MousePacketHandler() {
            @Override
            public void handlePacket(SessionContext context, ByteBuf packet) { packet.readInt(); }
        };
        assertThat(new PacketHandlerRegistry(List.of(replacement)).supportsCompactInput())
                .isFalse();
        assertThat(new PacketHandlerRegistry(List.of(new MousePacketHandler())).supportsCompactInput())
                .isTrue();
    }
    /**
     * Exercises every base-128 width transition and rejects malformed or overflowing inputs.
     */
    @Test
    void roundTripsBoundariesAndRejectsMalformedIntegers() {
        ByteBuf buffer = Unpooled.buffer();
        try {
            for (int value : new int[]{0, 127, 128, 16383, 16384, 2097151, 2097152, 268435455, 268435456, Integer.MAX_VALUE}) {
                buffer.clear();
                Wire.writeUnsigned(buffer, value);
                assertThat(Wire.readUnsigned(buffer))
                        .isEqualTo(value);
                assertThat(buffer.isReadable())
                        .isFalse();
            }
            buffer.clear();
            buffer.writeBytes(new byte[]{(byte) 128, 0});
            assertThatThrownBy(() -> Wire.readUnsigned(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
            buffer.clear();
            buffer.writeBytes(new byte[]{(byte) 255, (byte) 255, (byte) 255, (byte) 255, 8});
            assertThatThrownBy(() -> Wire.readUnsigned(buffer))
                    .isInstanceOf(IllegalArgumentException.class);
            buffer.clear();
            buffer.writeByte(128);
            assertThatThrownBy(() -> Wire.readUnsigned(buffer))
                    .isInstanceOf(IndexOutOfBoundsException.class);
        } finally {
            buffer.release();
        }
    }

    /**
     * Child identifiers use binary integers while the operation and parameter order remain stable.
     */
    @Test
    void encodesSmallerLosslessMutations() {
        DOMUpdate update = new DOMUpdate(DOMUpdateType.APPEND_CHILD, 42)
                .param(DOMUpdateParam.IDENTIFIER, 43)
                .param(DOMUpdateParam.HTML, "div");
        ByteBuf legacy = update.encode();
        ByteBuf compact = Unpooled.buffer();
        try {
            update.writeCompactTo(compact);
            assertThat(compact.readableBytes())
                    .isEqualTo(10)
                    .isLessThan(legacy.readableBytes());
            assertThat((int) compact.readUnsignedByte())
                    .isEqualTo(8);
            assertThat(Wire.readUnsigned(compact))
                    .isEqualTo(42);
            assertThat((int) compact.readUnsignedByte())
                    .isEqualTo(2);
            assertThat((int) compact.readUnsignedByte())
                    .isEqualTo(16 | 4 << 5);
            assertThat(Wire.readUnsigned(compact))
                    .isEqualTo(43);
            assertThat((int) compact.readUnsignedByte())
                    .isEqualTo(1);
            assertThat(Wire.readUnsigned(compact))
                    .isEqualTo(3);
            assertThat(compact.readCharSequence(3, StandardCharsets.UTF_8).toString())
                    .isEqualTo("div");
        } finally {
            legacy.release();
            compact.release();
        }
    }

    /**
     * Failed Unicode encoding restores the packet writer, preserving the retry boundary.
     */
    @Test
    void rejectsOversizedUnicodeWithoutPartialWrites() {
        ByteBuf packet = Unpooled.buffer();
        try {
            packet.writeByte(99);
            DOMUpdate update = new DOMUpdate(DOMUpdateType.SET_TEXT, 1)
                    .param(DOMUpdateParam.TEXT, "é".repeat(32768));
            assertThatThrownBy(() -> update.writeCompactTo(packet))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(packet.writerIndex())
                    .isEqualTo(1);
        } finally {
            packet.release();
        }
    }

    /**
     * Compact batches require channel opt-in; custom encoder overrides keep their legacy contract.
     */
    @Test
    void preservesLegacyAndCustomEncoderCompatibility() {
        EmbeddedChannel channel = new EmbeddedChannel();
        try {
            DOMDispatcher dispatcher = new DOMDispatcher();
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 1).param(DOMUpdateParam.TEXT, "value"));
            dispatcher.flush(channel);
            BinaryWebSocketFrame legacy = channel.readOutbound();
            assertThat((int) legacy.content().getUnsignedByte(0))
                    .isEqualTo(1);
            legacy.release();
            channel.attr(Wire.COMPACT)
                    .set(true);
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 1));
            dispatcher.flush(channel);
            BinaryWebSocketFrame compact = channel.readOutbound();
            assertThat((int) compact.content().getUnsignedByte(0))
                    .isEqualTo(Wire.SERVER_COMPACT);
            compact.release();
            dispatcher.queue(new DOMUpdate(DOMUpdateType.SET_TEXT, 1) {
                @Override
                public void writeTo(ByteBuf buffer) { super.writeTo(buffer); }
            });
            dispatcher.flush(channel);
            BinaryWebSocketFrame custom = channel.readOutbound();
            assertThat((int) custom.content().getUnsignedByte(0))
                    .isEqualTo(1);
            custom.release();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Real dispatch unpacks coordinate/modifier flags, synchronizes Unicode input, and resets mode.
     */
    @Test
    void dispatchesCompactActionsAndRestoresLegacyMode() {
        RouteRegistry routes = new RouteRegistry(List.of());
        PacketHandlerRegistry handlers = new PacketHandlerRegistry(List.of(new MousePacketHandler(), new ValueChangePacketHandler(), new RoutePacketHandler()));
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketHandler(routes, handlers));
        SessionContext session = new SessionContext(routes, "compact-test", channel);
        SessionContext.register(channel, session);
        SessionContext.set(session);
        try {
            Button button = new Button("click");
            AtomicInteger clicks = new AtomicInteger();
            button.addClickListener(event -> {
                assertThat(event.getClientX())
                        .isEqualTo(-12);
                assertThat(event.isCtrlPressed())
                        .isTrue();
                clicks.incrementAndGet();
            });
            TextField input = new TextField();
            session.getUI()
                    .add(button, input);
            ByteBuf click = Unpooled.buffer();
            click.writeByte(Wire.CLIENT_COMPACT);
            click.writeByte(1);
            Wire.writeUnsigned(click, button.getComponentID());
            click.writeByte(8);
            Wire.writeUnsigned(click, 23);
            for (int index = 0; index < 5; index++) Wire.writeUnsigned(click, 0);
            channel.writeInbound(new BinaryWebSocketFrame(click));
            assertThat(clicks.get())
                    .isEqualTo(1);
            assertThat(session.isCompactInput())
                    .isFalse();
            ByteBuf value = Unpooled.buffer();
            value.writeByte(Wire.CLIENT_COMPACT);
            value.writeByte(4);
            Wire.writeUnsigned(value, input.getComponentID());
            Wire.writeUnsigned(value, 6);
            value.writeCharSequence("東京", StandardCharsets.UTF_8);
            channel.writeInbound(new BinaryWebSocketFrame(value));
            assertThat(input.getValue())
                    .isEqualTo("東京");
            assertThat(SessionContext.get())
                    .isNull();
        } finally {
            SessionContext.invalidate(session);
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }
}
