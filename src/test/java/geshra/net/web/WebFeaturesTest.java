package geshra.net.web;

import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.User;
import geshra.net.web.auth.AuthenticationResult;
import geshra.net.web.auth.DefaultAuthenticator;
import geshra.spring.GeshraAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests actual HTTP SEO, cookie flags, CSRF, authorization, token rotation, and late password work.
 */
class WebFeaturesTest {
    /**
     * Finds the safe browser anti-forgery token in session JSON.
     */
    private static final Pattern CSRF = Pattern.compile("\"csrfToken\":\"([^\"]+)\"");
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GeshraAutoConfiguration.class))
            .withPropertyValues("geshra.web.port=0", "geshra.web.public-url=http://example.test")
            .withBean("home", Route.class, () -> new FeatureTestRoute("", List.of(), new SeoPage("Public <page>").description("A \"quoted\" & useful description").content("Visible without JavaScript").structuredData(Map.of("@type", "WebPage", "name", "</script><script>unsafe</script>"))))
            .withBean("private", Route.class, () -> new FeatureTestRoute("private", List.of("member"), new SeoPage("Private").content("Do not leak")))
            .withBean("unindexed", Route.class, () -> new FeatureTestRoute("unindexed", List.of(), new SeoPage("Unindexed").noIndex())); // Real ephemeral server and public/protected route fixtures.

    /**
     * Crawlers receive metadata and content without JavaScript, while protected and missing pages use real statuses.
     */
    @Test
    void servesInitialMetadataContentAndPublicSitemap() {
        runner.run(context -> {
            int port = context.getBean(WebServer.class)
                    .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> page = get(client, port, "/", null);
                assertThat(page.statusCode())
                        .isEqualTo(200);
                assertThat(page.body())
                        .contains("<title data-geshra-seo>Public &lt;page&gt;</title>", "Visible without JavaScript", "og:title", "twitter:card", "application/ld+json", "\\u003c/script>", "href=\"http://example.test/\"");
                assertThat(page.body())
                        .doesNotContain("<script>unsafe</script>");
                assertThat(page.headers().allValues("set-cookie"))
                        .isEmpty();
                assertThat(get(client, port, "/private", null).statusCode())
                        .isEqualTo(401);
                assertThat(get(client, port, "/private", null).body())
                        .doesNotContain("Do not leak");
                assertThat(get(client, port, "/missing", null).statusCode())
                        .isEqualTo(404);
                assertThat(get(client, port, "/missing", null).headers().firstValue("X-Robots-Tag").orElse(""))
                        .contains("noindex");
                String sitemap = get(client, port, "/sitemap.xml", null)
                        .body();
                assertThat(sitemap)
                        .contains("http://example.test/")
                        .doesNotContain("/private", "/unindexed");
                assertThat(get(client, port, "/robots.txt", null).body())
                        .contains("Sitemap: http://example.test/sitemap.xml", "Disallow: /_geshra/");
                HttpResponse<String> head = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
                assertThat(head.body())
                        .isEmpty();
                assertThat(head.headers().firstValueAsLong("content-length").orElse(0))
                        .isPositive();
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /**
     * Login and logout rotate HttpOnly cookies, enforce CSRF, and share role checks with HTTP routes.
     */
    @Test
    void authenticatesRotatesAndClearsSessionState() {
        runner.run(context -> {
            DefaultAuthenticator users = context.getBean(DefaultAuthenticator.class);
            users.register("alice", "test-secret", "member", "editor");
            users.register("guest", "guest-secret", "guest");
            int port = context.getBean(WebServer.class)
                    .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> initial = get(client, port, "/_geshra/session", null);
                String cookie = cookie(initial);
                String oldToken = cookie.substring(cookie.indexOf('=') + 1);
                assertThat(initial.headers().firstValue("set-cookie").orElse(""))
                        .contains("HTTPOnly", "SameSite=Lax", "Path=/");
                assertThat(post(client, port, "/_geshra/login", cookie, "", "username=alice&password=test-secret").statusCode())
                        .isEqualTo(403);
                HttpResponse<String> login = post(client, port, "/_geshra/login", cookie, csrf(initial), "username=alice&password=test-secret");
                String loggedIn = cookie(login);
                assertThat(loggedIn)
                        .isNotEqualTo(cookie);
                assertThat(SessionContext.getBySessionID(oldToken))
                        .isNull();
                assertThat(login.body())
                        .contains("\"authenticated\":true", "\"username\":\"alice\"")
                        .doesNotContain("passwordHash", "test-secret", oldToken);
                assertThat(get(client, port, "/private", loggedIn).statusCode())
                        .isEqualTo(200);
                SessionContext session = SessionContext.getBySessionID(loggedIn.substring(loggedIn.indexOf('=') + 1));
                session.attribute("private-data", "value");
                HttpResponse<String> logout = post(client, port, "/_geshra/logout", loggedIn, csrf(login), "");
                assertThat(logout.body())
                        .contains("\"authenticated\":false");
                assertThat(session.get("private-data", String.class))
                        .isNull();
                assertThat(get(client, port, "/private", cookie(logout)).statusCode())
                        .isEqualTo(401);
                assertThat(SessionContext.getBySessionID(loggedIn.substring(loggedIn.indexOf('=') + 1)))
                        .isNull();
                HttpResponse<String> guest = post(client, port, "/_geshra/login", cookie(logout), csrf(logout), "username=guest&password=guest-secret");
                assertThat(get(client, port, "/private", cookie(guest)).statusCode())
                        .isEqualTo(403);
                HttpResponse<String> blocked = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/_geshra/session")).header("Origin", "http://evil.test").GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(blocked.statusCode())
                        .isEqualTo(403);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
        });
    }

    /**
     * Password work is offloaded and cannot resurrect a session invalidated while verification runs.
     */
    @Test
    void rejectsLateAuthenticationOfInvalidatedSessions() {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<String> worker = new AtomicReference<>();
        runner.withBean(Authenticator.class, () -> (username, password) -> {
            worker.set(Thread.currentThread().getName());
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Authentication test did not release");
            } catch (InterruptedException interrupted) {
                Thread.currentThread()
                        .interrupt();
                throw new IllegalStateException(interrupted);
            }
            return new AuthenticationResult(new User().attribute("username", "alice").roles("member"), 1);
        })
                .run(context -> {
            int port = context.getBean(WebServer.class)
                    .getPort();
            try (HttpClient client = HttpClient.newHttpClient()) {
                HttpResponse<String> initial = get(client, port, "/_geshra/session", null);
                String cookie = cookie(initial);
                SessionContext session = SessionContext.getBySessionID(cookie.substring(cookie.indexOf('=') + 1));
                var future = client.sendAsync(request(port, "/_geshra/login", cookie, csrf(initial), "username=alice&password=test"), HttpResponse.BodyHandlers.ofString());
                assertThat(entered.await(5, TimeUnit.SECONDS))
                        .isTrue();
                SessionContext.invalidate(session);
                release.countDown();
                assertThat(future.get(5, TimeUnit.SECONDS).body())
                        .contains("\"authenticated\":false");
                assertThat(worker.get())
                        .startsWith("Geshra-Authentication");
                assertThat(session.getUser())
                        .isNull();
                assertThat(get(client, port, "/private", cookie).statusCode())
                        .isEqualTo(401);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            } finally {
                release.countDown();
            }
        });
    }

    /**
     * Cookie headers reject injection and enforce native security flag combinations.
     */
    @Test
    void validatesCookiesAndStoresSaltedPasswords() {
        assertThatThrownBy(() -> new Cookie("bad\r\nname", "value").toHeader())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cookie("name", "bad\r\nvalue").toHeader())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cookie("name", "value").sameSite("None").toHeader())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cookie("__Host-name", "value").secure(true).domain("example.test").toHeader())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cookie("name", "value").httpOnly(true).encode())
                .isInstanceOf(IllegalStateException.class);
        DefaultAuthenticator users = new DefaultAuthenticator();
        var first = users.register("first", "same-password", "member");
        var second = users.register("second", "same-password", "member");
        assertThat(first.get("password", String.class))
                .isNull();
        assertThat(first.get("passwordHash", String.class))
                .isNotEqualTo(second.get("passwordHash", String.class));
    }

    /**
     * Fetches an HTTP representation with an optional browser cookie.
     * @param client HTTP client
     * @param port ephemeral application port
     * @param path request path
     * @param cookie cookie header or null
     * @return HTTP response
     * @throws Exception when the request fails
     */
    private HttpResponse<String> get(HttpClient client, int port, String path, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) request.header("Cookie", cookie);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Posts one session action.
     * @param client HTTP client
     * @param port application port
     * @param path action path
     * @param cookie browser cookie
     * @param csrf anti-forgery token
     * @param body encoded form body
     * @return action response
     * @throws Exception when the request fails
     */
    private HttpResponse<String> post(HttpClient client, int port, String path, String cookie, String csrf, String body) throws Exception {
        return client.send(request(port, path, cookie, csrf, body), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Builds a same-session form request.
     * @param port application port
     * @param path action path
     * @param cookie browser cookie
     * @param csrf anti-forgery token
     * @param body encoded body
     * @return request
     */
    private HttpRequest request(int port, String path, String cookie, String csrf, String body) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", cookie)
                .header("X-CSRF-Token", csrf)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
     * Extracts the issued cookie name and value without its response attributes.
     * @param response action response
     * @return request Cookie header
     */
    private String cookie(HttpResponse<String> response) { return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0]; }

    /**
     * Extracts a server-issued anti-forgery token.
     * @param response session response
     * @return anti-forgery token
     */
    private String csrf(HttpResponse<String> response) { var matcher = CSRF.matcher(response.body()); if (!matcher.find()) throw new AssertionError("Missing CSRF token"); return matcher.group(1); }
}
