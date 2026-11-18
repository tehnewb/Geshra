package geshra.net.web;

import geshra.net.web.packets.ViewportPacketHandler;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.components.list.VirtualList;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies optional viewport identity under both real action grammars, including old clients.
 */
class ViewportIdentityTest {
    /**
     * Four-field requests and optional tokens round-trip; trailing fields are rejected.
     */
    @Test
    void echoesOptionalIdentityAndRejectsTrailingFields() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "viewport-identity", channel);
        SessionContext.set(context);
        try {
            VirtualList<Integer> list = new VirtualList<>(1000, index -> index, index -> new Span("Row " + index));
            context.getUI()
                    .add(list);
            ViewportPacketHandler handler = new ViewportPacketHandler();
            for (boolean compact : new boolean[]{false, true}) {
                context.compactInput(compact);
                for (int token : new int[]{0, 127, 128, Integer.MAX_VALUE}) {
                    BinaryWebSocketFrame previous;
                    while ((previous = channel.readOutbound()) != null) previous.release();
                    ByteBuf packet = Unpooled.buffer();
                    try {
                        for (int value : new int[]{list.getComponentID(), 1, 100, 110}) {
                            if (compact) Wire.writeUnsigned(packet, value);
                            else packet.writeInt(value);
                        }
                        if (token != 0) {
                            if (compact) Wire.writeUnsigned(packet, token);
                            else packet.writeInt(token);
                        }
                        handler.handlePacket(context, packet);
                        BinaryWebSocketFrame acknowledgment = channel.readOutbound();
                        try {
                            assertThat(acknowledgment.content().toString(StandardCharsets.UTF_8))
                                    .contains("\"token\":" + token, "\"requested\":true");
                        } finally {
                            acknowledgment.release();
                        }
                        packet.clear();
                        for (int value : new int[]{list.getComponentID(), 1, 100, 110, 7, 8}) {
                            if (compact) Wire.writeUnsigned(packet, value);
                            else packet.writeInt(value);
                        }
                        assertThatThrownBy(() -> handler.handlePacket(context, packet))
                                .isInstanceOf(IllegalArgumentException.class);
                    } finally {
                        packet.release();
                    }
                }
            }
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }
}
