package com.example.demo;

import geshra.net.web.RouteRegistry;
import geshra.net.web.WebServer;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.DOMUpdateParam;
import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies consumer application behavior using isolated state and observable outcomes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "geshra.web.port=0")
class ConsumerApplicationTest {
    @Autowired
    private WebServer server; // Auto-configured server borrowed from the test Spring context.
    @Autowired
    private RouteRegistry routes; // Consumer routes borrowed from the test Spring context.

    /**
     * Verifies that dependency auto configures the consumer and serves its assets.
     */
    @Test
    void dependencyAutoConfiguresTheConsumerAndServesItsAssets() throws Exception {
        assertThat(server.isRunning())
                .isTrue();
        assertThat(routes.getHome())
                .isInstanceOf(HomeRoute.class);
        try (HttpClient client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.getPort() + "/")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode())
                    .isEqualTo(200);
            assertThat(response.body())
                    .contains("/js/runtime.js");
            var stylesheet = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.getPort() + "/css/grid.css")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(stylesheet.statusCode())
                    .isEqualTo(200);
        }
    }

    /**
     * Verifies that renders the consumer route over web socket.
     */
    @Test
    void rendersTheConsumerRouteOverWebSocket() throws Exception {
        CompletableFuture<String> rendered = new CompletableFuture<>();
        CompletableFuture<Integer> clickTarget = new CompletableFuture<>();
        CompletableFuture<String> clicked = new CompletableFuture<>();
        try (HttpClient client = HttpClient.newHttpClient()) {
            WebSocket socket = client.newWebSocketBuilder()
                    .buildAsync(
                    URI.create("ws://localhost:" + server.getPort() + "/ws"), new WebSocket.Listener() {
                        private final ByteArrayOutputStream message = new ByteArrayOutputStream(); // Complete binary message accumulated across fragments.
                        @Override
                        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                            byte[] fragment = new byte[data.remaining()];
                            data.get(fragment);
                            message.writeBytes(fragment);
                            if (last) {
                                byte[] bytes = message.toByteArray();
                                String text = new String(bytes, StandardCharsets.UTF_8);
                                if (text.contains("Hello from Spring Boot")) {
                                    rendered.complete(text);
                                }
                                if (text.contains("The Java listener handled your click")) clicked.complete(text);
                                int target = findClickTarget(ByteBuffer.wrap(bytes));
                                if (target >= 0) clickTarget.complete(target);
                                message.reset();
                            }
                            webSocket.request(1);
                            return null;
                        }
                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            rendered.completeExceptionally(error);
                        }
                    })
                    .get(5, TimeUnit.SECONDS);
            try {
                socket.sendBinary(ByteBuffer.wrap(new byte[] {0, 0, 1, '/'}), true)
                        .get(5, TimeUnit.SECONDS);
                assertThat(rendered.get(5, TimeUnit.SECONDS))
                        .contains("Hello from Spring Boot");
                ByteBuffer click = ByteBuffer.allocate(19);
                click.put((byte) 1)
                        .putInt(clickTarget.get(5, TimeUnit.SECONDS))
                        .put((byte) 0);
                for (int i = 0; i < 6; i++) click.putShort((short) 0);
                click.put((byte) 0)
                        .flip();
                socket.sendBinary(click, true)
                        .get(5, TimeUnit.SECONDS);
                assertThat(clicked.get(5, TimeUnit.SECONDS))
                        .contains("The Java listener handled your click");
            } finally {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
                        .get(5, TimeUnit.SECONDS);
            }
        }
    }

    /**
     * Reads the actual component ID advertising a click handler from the DOM wire protocol.
     * @param message complete binary message
     * @return click target ID, or negative when this message has no such binding
     */
    private static int findClickTarget(ByteBuffer message) {
        /*
         * Use the server's advertised binding rather than assuming component allocation order.
         * Only click names need decoding; other parameter payloads can be skipped in place.
         */
        if (message.remaining() < 3 || Byte.toUnsignedInt(message.get()) != 1) return -1;
        int count = Short.toUnsignedInt(message.getShort());
        for (int i = 0; i < count; i++) {
            int type = Byte.toUnsignedInt(message.get());
            int id = message.getInt();
            int parameters = Byte.toUnsignedInt(message.get());
            for (int j = 0; j < parameters; j++) {
                int key = Byte.toUnsignedInt(message.get());
                int length = Short.toUnsignedInt(message.getShort());
                if (type == DOMUpdateType.ADD_EVENT_LISTENER.getCode() && key == DOMUpdateParam.EVENT_NAME.getCode()) {
                    byte[] value = new byte[length];
                    message.get(value);
                    if (new String(value, StandardCharsets.UTF_8).equals("click")) return id;
                } else message.position(message.position() + length);
            }
        }
        return -1;
    }
}
