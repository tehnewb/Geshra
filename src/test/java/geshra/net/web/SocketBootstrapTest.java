package geshra.net.web;

import geshra.net.web.packets.RoutePacketHandler;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.TextComponent;
import io.netty.buffer.ByteBuf;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises real HTTP upgrades and initial DOM delivery, including explicit compact opt-in,
 * replacement handler contracts, same-origin checks and malformed bootstrap ownership.
 * Every ephemeral server, HTTP client and WebSocket is released by the test that creates it.
 */
class SocketBootstrapTest {
    /**
     * The initial route is rendered once without a client route packet, and compact output requires
     * an explicit client capability in addition to server handler eligibility.
     * @throws Exception when the real local transport fails
     */
    @Test
    void bootstrapsOnlyWithExplicitClientCapability() throws Exception {
        for (boolean compact : new boolean[]{false, true}) {
            AtomicInteger loads = new AtomicInteger();
            WebServer server = server(new RoutePacketHandler(), loads);
            try (HttpClient client = HttpClient.newHttpClient()) {
                BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
                String query = "/ws?geshra-bootstrap=%2F" + (compact ? "&geshra-protocol=2" : "");
                WebSocket socket = connect(client, server, query, received);
                try {
                    byte[] state = received.poll(5, TimeUnit.SECONDS);
                    assertThat(state)
                            .isNotNull();
                    assertThat(state[0])
                            .isEqualTo((byte) 6);
                    assertThat(new String(state, 3, state.length - 3, StandardCharsets.UTF_8))
                            .contains("\"bootstrap\":true");
                    byte[] dom;
                    do {
                        dom = received.poll(5, TimeUnit.SECONDS);
                        assertThat(dom)
                                .isNotNull();
                    } while (dom[0] != 1 && dom[0] != 7);
                    assertThat(dom[0])
                            .isEqualTo((byte) (compact ? 7 : 1));
                    assertThat(loads.get())
                            .isEqualTo(1);
                } finally {
                    socket.abort();
                }
            } finally {
                server.stop();
            }
        }
    }

    /**
     * A replacement route decoder is never bypassed by the eager path; normal packets still reach it.
     * @throws Exception when the real local transport fails
     */
    @Test
    void preservesReplacementRouteHandler() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger packets = new AtomicInteger();
        RoutePacketHandler custom = new RoutePacketHandler() {
            @Override
            public void handlePacket(SessionContext context, ByteBuf packet) { packets.incrementAndGet(); }
        };
        WebServer server = server(custom, loads);
        try (HttpClient client = HttpClient.newHttpClient()) {
            BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
            WebSocket socket = connect(client, server, "/ws?geshra-bootstrap=%2F&geshra-protocol=2", received);
            try {
                byte[] state = received.poll(5, TimeUnit.SECONDS);
                assertThat(new String(state, 3, state.length - 3, StandardCharsets.UTF_8))
                        .contains("\"bootstrap\":false");
                assertThat(loads.get())
                        .isZero();
                socket.sendBinary(ByteBuffer.wrap(new byte[]{0, 0, 1, '/'}), true)
                        .join();
                for (int attempt = 0; attempt < 100 && packets.get() == 0; attempt++) Thread.sleep(10);
                assertThat(packets.get())
                        .isEqualTo(1);
            } finally {
                socket.abort();
            }
        } finally {
            server.stop();
        }
    }

    /**
     * Malformed, duplicate, oversized and cross-origin bootstrap requests never render a route.
     */
    @Test
    void rejectsInvalidBootstrapBeforeRendering() {
        AtomicInteger loads = new AtomicInteger();
        WebServer server = server(new RoutePacketHandler(), loads);
        try (HttpClient client = HttpClient.newHttpClient()) {
            for (String query : new String[]{"/ws?geshra-bootstrap=%00", "/ws?geshra-bootstrap=%2F&geshra-bootstrap=%2F", "/ws?geshra-bootstrap=%2F" + "x".repeat(2200), "/ws?geshra-bootstrap=%ZZ"}) {
                assertThatThrownBy(() -> client.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + server.getPort() + query), new WebSocket.Listener() { }).join())
                        .isInstanceOf(RuntimeException.class);
            }
            assertThatThrownBy(() -> client.newWebSocketBuilder().header("Origin", "http://evil.test").buildAsync(URI.create("ws://localhost:" + server.getPort() + "/ws?geshra-bootstrap=%2F&geshra-protocol=2"), new WebSocket.Listener() { }).join())
                    .isInstanceOf(RuntimeException.class);
            assertThat(loads.get())
                    .isZero();
        } finally {
            server.stop();
        }
    }

    /**
     * An acknowledged rendering failure emits the explicit recovery close instead of silently
     * leaving the browser attached to an incomplete UI.
     * @throws Exception when the local WebSocket transport fails
     */
    @Test
    void signalsFailedBootstrapAndAllowsNormalRetry() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        WebServer server = server(new RoutePacketHandler(), loads, true);
        try (HttpClient client = HttpClient.newHttpClient()) {
            BlockingQueue<Integer> closed = new LinkedBlockingQueue<>();
            WebSocket failing = client.newWebSocketBuilder()
                    .buildAsync(URI.create("ws://localhost:" + server.getPort() + "/ws?geshra-bootstrap=%2F&geshra-protocol=2"), new WebSocket.Listener() {
                @Override
                public void onOpen(WebSocket socket) { socket.request(Long.MAX_VALUE); }
                @Override
                public CompletionStage<?> onClose(WebSocket socket, int code, String reason) {
                    if (reason.equals("bootstrap failed")) closed.add(code);
                    return null;
                }
            })
                    .join();
            try {
                assertThat(closed.poll(5, TimeUnit.SECONDS))
                        .isEqualTo(1011);
            } finally { failing.abort(); }
            BlockingQueue<byte[]> received = new LinkedBlockingQueue<>();
            WebSocket retry = connect(client, server, "/ws", received);
            try {
                assertThat(received.poll(5, TimeUnit.SECONDS))
                        .isNotNull();
                retry.sendBinary(ByteBuffer.wrap(new byte[]{0, 0, 1, '/'}), true)
                        .join();
                byte[] dom;
                do {dom = received.poll(5, TimeUnit.SECONDS); assertThat(dom)
                        .isNotNull();} while (dom[0] != 1);
                assertThat(loads.get())
                        .isEqualTo(2);
            } finally { retry.abort(); }
        } finally { server.stop(); }
    }

    /**
     * Starts one native route whose invocation count detects accidental duplicate bootstraps.
     * @param handler route packet implementation under test
     * @param loads route invocation counter
     * @return owned listening server
     */
    private WebServer server(RoutePacketHandler handler, AtomicInteger loads) {
        return server(handler, loads, false);
    }

    /**
     * Starts a route with an optional one-time failure to test bootstrap recovery signaling.
     * @param handler route packet implementation
     * @param loads invocation counter
     * @param failFirst whether the first render should fail
     * @return owned listening server
     */
    private WebServer server(RoutePacketHandler handler, AtomicInteger loads, boolean failFirst) {
        Route route = new Route() {
            @Override
            public String getPath() { return ""; }
            @Override
            public Collection<String> getAllowedRoles() { return List.of(); }
            @Override
            public void load(UI ui) {
                if (loads.incrementAndGet() == 1 && failFirst) throw new IllegalStateException("Intentional bootstrap test failure");
                ui.add(new TextComponent("Bootstrapped", "h1"));
            }
        };
        WebServer server = new WebServer(new PacketHandlerRegistry(List.of(handler)), new RouteRegistry(List.of(route)), 0, false, new DefaultResourceLoader(), "classpath:/web/");
        server.start();
        return server;
    }

    /**
     * Connects a bounded binary listener that requests one subsequent message after each callback.
     * @param client owned HTTP client
     * @param server owned listening server
     * @param query upgrade URI including optional bootstrap
     * @param received thread-safe message sink
     * @return owned browser-like WebSocket
     */
    private WebSocket connect(HttpClient client, WebServer server, String query, BlockingQueue<byte[]> received) {
        return client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://localhost:" + server.getPort() + query), new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket socket) { socket.request(1); }
            @Override
            public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer bytes, boolean last) {
                byte[] copy = new byte[bytes.remaining()];
                bytes.get(copy);
                received.add(copy);
                socket.request(1);
                return null;
            }
        })
                .join();
    }
}
