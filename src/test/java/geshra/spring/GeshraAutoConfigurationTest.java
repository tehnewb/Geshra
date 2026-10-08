package geshra.spring;

import geshra.net.web.PacketHandlerRegistry;
import geshra.net.web.Route;
import geshra.net.web.RouteRegistry;
import geshra.net.web.WebServer;
import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.DefaultAuthenticator;
import geshra.net.web.ui.UI;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies geshra auto configuration behavior using isolated state and observable outcomes.
 */
class GeshraAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeshraAutoConfiguration.class))
            .withPropertyValues("geshra.web.port=0"); // Creates an isolated Spring context with an ephemeral web port for each test.

    /**
     * Verifies that starts without routes and stops with the spring context.
     */
    @Test
    void startsWithoutRoutesAndStopsWithTheSpringContext() {
        AtomicReference<WebServer> server = new AtomicReference<>();
        runner.run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .hasSingleBean(WebServer.class);
            server.set(context.getBean(WebServer.class));
            assertThat(server.get().isRunning())
                    .isTrue();
            assertThat(context.getBean(PacketHandlerRegistry.class).getAllHandlers())
                    .hasSize(12);
            assertThat(context.getBean(GeshraWebProperties.class).isOpenBrowser())
                    .isFalse();
        });
        assertThat(server.get().isRunning())
                .isFalse();
    }

    /**
     * Verifies that serves bundled assets and route shell without arepository checkout.
     */
    @Test
    void servesBundledAssetsAndRouteShellWithoutARepositoryCheckout() {
        runner.run(context -> {
            int port = context.getBean(WebServer.class)
                    .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                var html = get(client, port, "/?example=true");
                assertThat(html.statusCode())
                        .isEqualTo(200);
                assertThat(html.body())
                        .contains("/js/runtime.js", "body id=\"0\"");
                assertThat(get(client, port, "/my-route/123").statusCode())
                        .isEqualTo(404);
                var runtime = get(client, port, "/js/runtime.js");
                assertThat(runtime.statusCode())
                        .isEqualTo(200);
                assertThat(runtime.body())
                        .contains("WebSocket");
                assertThat(get(client, port, "/css/grid.css").statusCode())
                        .isEqualTo(200);
                assertThat(get(client, port, "/css/chat.css").statusCode())
                        .isEqualTo(200);
                assertThat(get(client, port, "/js/grid-lite.js").statusCode())
                        .isEqualTo(200);
                var charts = get(client, port, "/js/highcharts.js");
                assertThat(charts.statusCode())
                        .isEqualTo(200);
                assertThat(charts.body())
                        .doesNotContain("PipeMasters", "Pipe Masters");
                assertThat(get(client, port, "/js/stockcharts.js").statusCode())
                        .isEqualTo(200);
                assertThat(get(client, port, "/missing.png").statusCode())
                        .isEqualTo(404);
                assertThat(get(client, port, "/%2e%2e/LICENSE").statusCode())
                        .isEqualTo(400);
                var head = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(head.statusCode())
                        .isEqualTo(200);
                assertThat(head.body())
                        .isEmpty();
            }
        });
    }

    /**
     * Verifies that discovers consumer routes and honors custom authentication.
     */
    @Test
    void discoversConsumerRoutesAndHonorsCustomAuthentication() {
        DefaultAuthenticator custom = new DefaultAuthenticator();
        Route home = new Route() {
            /**
             * Restores object state from the supplied serialized bytes.
             *
             * @param ui ui supplied to this operation
             */
            public void load(UI ui) { }
            /**
             * Returns the path retained by this instance.
             * @return the resulting path value
             */
            public String getPath() {
                return "";
            }
            /**
             * Returns the allowed roles retained by this instance.
             * @return the resulting allowed roles value
             */
            public Collection<String> getAllowedRoles() {
                return List.of();
            }
        };
        runner.withBean(Route.class, () -> home)
                .withBean(Authenticator.class, () -> custom)
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(Authenticator.class);
                    assertThat(context.getBean(RouteRegistry.class).getHome())
                            .isSameAs(home);
                    assertThat(WebServer.getAuthenticator())
                            .isSameAs(custom);
                });
    }

    /**
     * Verifies that can disable the web framework.
     */
    @Test
    void canDisableTheWebFramework() {
        runner.withPropertyValues("geshra.web.enabled=false")
                .run(context -> {
            assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(WebServer.class)
                    .doesNotHaveBean(RouteRegistry.class)
                    .doesNotHaveBean(PacketHandlerRegistry.class);
        });
    }

    /**
     * Verifies that serves application overrides while keeping the bundled runtime.
     *
     * @param directory temporary application asset directory
     */
    @Test
    void servesApplicationOverridesWhileKeepingTheBundledRuntime(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("index.html"), "<body>Application shell</body>");
        runner.withPropertyValues("geshra.web.static-location=" + directory.toUri())
                .run(context -> {
            int port = context.getBean(WebServer.class)
                    .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                assertThat(get(client, port, "/").body())
                        .contains("Application shell");
                assertThat(get(client, port, "/js/runtime.js").body())
                        .contains("WebSocket");
            }
        });
    }

    /**
     * Verifies that reports an occupied port as aspring startup failure.
     */
    @Test
    void reportsAnOccupiedPortAsASpringStartupFailure() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            runner.withPropertyValues("geshra.web.port=" + occupied.getLocalPort())
                    .run(context -> {
                assertThat(context)
                        .hasFailed();
                assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(BindException.class);
            });
        }
    }

    /**
     * Returns the  retained by this instance.
     *
     * @param client HTTP client used for this request
     * @param port TCP port from zero to 65535; zero selects an ephemeral port
     * @param path requested path relative to the resource or route root
     * @return the resulting  value
     */
    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        /*
         * Resolve the existing session or test response without retaining an additional global context.
         */
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Exercises a file larger than the buffering threshold through the real HTTP pipeline.
     * @param directory temporary asset directory
     * @throws Exception if temporary IO fails
     */
    @Test
    void streamsLargeFilesAndReportsHeadWithoutSendingTheBody(@TempDir Path directory) throws Exception {
        byte[] content = new byte[2 * 1024 * 1024];
        Arrays.fill(content, (byte) 'x');
        Files.write(directory.resolve("large.bin"), content);
        runner.withPropertyValues("geshra.web.static-location=" + directory.toUri())
                .run(context -> {
            int port = context.getBean(WebServer.class)
                        .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                URI uri = URI.create("http://localhost:" + port + "/large.bin");
                HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(response.statusCode())
                        .isEqualTo(200);
                assertThat(response.body())
                        .isEqualTo(content);
                HttpResponse<byte[]> head = client.send(HttpRequest.newBuilder(uri).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofByteArray());
                assertThat(head.body())
                        .isEmpty();
                assertThat(head.headers().firstValueAsLong("Content-Length").orElseThrow())
                        .isEqualTo(content.length);
            }
        });
    }
}
