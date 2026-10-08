package geshra.net.web;

import geshra.net.web.ui.components.TextComponent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies server-specific session reclamation and the real writability callback used to resume
 * retained UI work. Owned transports, buffers, and thread-local context are always released.
 */
class TransportLifecycleTest {
    /**
     * A stopped listener releases detached sessions without affecting listeners sharing its routes.
     */
    @Test
    void invalidatesOnlyTheOwningListenerSessions() {
        RouteRegistry routes = new RouteRegistry(List.of());
        EmbeddedChannel firstServer = new EmbeddedChannel();
        EmbeddedChannel secondServer = new EmbeddedChannel();
        EmbeddedChannel firstClient = new EmbeddedChannel(firstServer, DefaultChannelId.newInstance(), true, false);
        EmbeddedChannel secondClient = new EmbeddedChannel(secondServer, DefaultChannelId.newInstance(), true, false);
        SessionContext first = new SessionContext(routes, "first-listener", firstClient);
        SessionContext second = new SessionContext(routes, "second-listener", secondClient);
        try {
            SessionContext.register(firstClient, first);
            SessionContext.register(secondClient, second);
            SessionContext.unregister(firstClient);
            SessionContext.invalidateSessions(firstServer);
            assertThat(SessionContext.getBySessionID("first-listener"))
                    .isNull();
            assertThat(SessionContext.getBySessionID("second-listener"))
                    .isSameAs(second);
        } finally {
            SessionContext.invalidate(first);
            SessionContext.invalidate(second);
            firstClient.finishAndReleaseAll();
            secondClient.finishAndReleaseAll();
            firstServer.finishAndReleaseAll();
            secondServer.finishAndReleaseAll();
        }
    }

    /**
     * Writability resumes queued work, preserves context, and respects caller-disabled input reads.
     */
    @Test
    void resumesPendingDomAndPreservesManualReadSettings() {
        RouteRegistry routes = new RouteRegistry(List.of());
        EmbeddedChannel channel = new EmbeddedChannel(new WebSocketHandler(routes, new PacketHandlerRegistry(List.of())));
        SessionContext context = new SessionContext(routes, "writable-session", channel);
        SessionContext.register(channel, context);
        SessionContext.set(context);
        try {
            TextComponent component = new TextComponent("initial", "p");
            context.getUI()
                    .add(component);
            BinaryWebSocketFrame initial = channel.readOutbound();
            initial.release();
            channel.config()
                    .setWriteBufferWaterMark(new WriteBufferWaterMark(32, 64));
            for (int pass = 0; pass < 2; pass++) {
                if (pass == 1) channel.config()
                        .setAutoRead(false);
                channel.write(Unpooled.buffer(128).writeZero(128));
                assertThat(channel.config().isAutoRead())
                        .isFalse();
                component.setText("pending");
                channel.flush();
                ByteBuf previous = channel.readOutbound();
                previous.release();
                BinaryWebSocketFrame frame = channel.readOutbound();
                try {
                    assertThat(frame.content().toString(StandardCharsets.UTF_8))
                            .contains("pending");
                } finally {
                    frame.release();
                }
                assertThat(channel.config().isAutoRead())
                        .isEqualTo(pass == 0);
                assertThat(SessionContext.get())
                        .isSameAs(context);
            }
        } finally {
            SessionContext.invalidate(context);
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }
}
