package geshra.net.web;

import geshra.net.web.auth.Authenticator;
import geshra.net.web.auth.AuthenticationResult;
import geshra.net.web.auth.DefaultAuthenticator;
import geshra.net.web.ui.BrowserJson;
import geshra.net.web.ui.UI;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import java.net.URI;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Server-owned session resolution, origin validation, and bounded authentication execution.
 * Password derivation runs on two dedicated workers, never a Netty UI event-loop thread.
 */
final class SessionService implements AutoCloseable {
    private final RouteRegistry routes; // Server's application routes.
    private final Authenticator authenticator; // Server's configured credential verifier.
    private final SessionPolicy policy; // Validated session settings.
    private final ThreadPoolExecutor authentication; // Bounded password work, allocated on server startup.

    /**
     * Creates the server-owned session service.
     * @param routes route index
     * @param authenticator credential verifier
     * @param policy validated session settings
     */
    SessionService(RouteRegistry routes, Authenticator authenticator, SessionPolicy policy) {
        this.routes = routes;
        this.authenticator = authenticator;
        this.policy = policy;
        authentication = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(64), new DefaultThreadFactory("Geshra-Authentication", true));
    }

    /**
     * Resolves a cookie only within this accepting server.
     * @param ctx current HTTP channel
     * @param headers request headers
     * @return current session or null
     */
    SessionContext find(ChannelHandlerContext ctx, HttpHeaders headers) {
        Map<String, String> values = new HashMap<>();
        String header = headers.get(HttpHeaderNames.COOKIE);
        if (header != null) {
            for (var cookie : ServerCookieDecoder.STRICT.decode(header)) values.putIfAbsent(cookie.name(), cookie.value());
        }
        String token = values.get(policy.cookieName());
        SessionContext session = token == null || token.length() > 128 ? null : SessionContext.getBySessionID(token);
        if (session != null && session.belongsTo(ctx.channel().parent())) {
            session.cookies(values);
            return session;
        }
        return null;
    }

    /**
     * Creates an HTTP-initialized session without allocating any UI components.
     * @param ctx current HTTP channel
     * @return new indexed session
     */
    SessionContext create(ChannelHandlerContext ctx) {
        SessionContext session = new SessionContext(routes, SessionTokens.create(), null, policy);
        SessionContext.registerDetached(session, ctx.channel().parent());
        return session;
    }

    /**
     * Builds the reserved HttpOnly browser cookie.
     * @param ctx current HTTP channel
     * @param session session whose token is being issued
     * @return validated Set-Cookie header
     */
    String cookie(ChannelHandlerContext ctx, SessionContext session) {
        return new Cookie(policy.cookieName(), session.getSessionID())
                .httpOnly(true)
                .secure(policy.secureCookies() || ctx.pipeline().get(SslHandler.class) != null)
                .maxAge(policy.timeout())
                .toHeader();
    }

    /**
     * Rejects cross-origin browser requests; missing Origin supports non-browser clients.
     * @param ctx current channel
     * @param headers request headers
     * @return whether the origin belongs to this application
     */
    boolean sameOrigin(ChannelHandlerContext ctx, HttpHeaders headers) {
        String origin = headers.get(HttpHeaderNames.ORIGIN);
        if (origin == null) return true;
        try {
            URI actual = URI.create(origin);
            String expected = policy.publicUrl().isEmpty() ? (ctx.pipeline().get(SslHandler.class) != null || policy.secureCookies() ? "https://" : "http://") + headers.get(HttpHeaderNames.HOST) : policy.publicUrl();
            URI target = URI.create(expected);
            return actual.getHost() != null && actual.getRawUserInfo() == null && actual.getRawQuery() == null && actual.getRawFragment() == null && actual.getPath().isEmpty() && actual.getScheme().equalsIgnoreCase(target.getScheme()) && actual.getHost().equalsIgnoreCase(target.getHost()) && effectivePort(actual) == effectivePort(target);
        } catch (IllegalArgumentException invalidOrigin) {
            return false;
        }
    }

    /**
     * Checks the browser's anti-forgery header against the session secret.
     * @param session resolved session
     * @param headers request headers
     * @return valid anti-forgery proof
     */
    boolean csrf(SessionContext session, HttpHeaders headers) {
        String supplied = headers.get("X-CSRF-Token");
        return supplied != null && supplied.length() <= 128 && MessageDigest.isEqual(session.csrfToken().getBytes(StandardCharsets.US_ASCII), supplied.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * Serializes safe browser session information without the session token or password data.
     * @param session current session
     * @return browser-readable session JSON
     */
    String state(SessionContext session) {
        return BrowserJson.encode(Map.of("authenticated", session.isAuthenticated(), "username", session.getUser() == null ? "" : session.getUser().getUsername(), "csrfToken", session.csrfToken(), "expiresAt", session.getExpiresAt(), "cookieMaxAge", session.getCookieMaxAge()));
    }

    /**
     * Runs bounded credential verification, delivering the result on the browser UI dispatch thread.
     * @param session current session
     * @param username supplied username
     * @param password supplied password
     * @param nonce browser cookie acknowledgment identifier
     * @param completed response callback
     */
    void login(SessionContext session, String username, String password, int nonce, Consumer<AuthenticationResult> completed) {
        authentication.execute(new AuthenticationTask(this, authenticator, session, username, password, nonce, completed));
    }

    /**
     * Delivers a verified result, rejecting a session that expired while verification ran.
     * @param session browser session
     * @param result verified result
     * @param nonce browser cookie acknowledgment identifier
     * @param completed HTTP completion callback
     */
    void finish(SessionContext session, AuthenticationResult result, int nonce, Consumer<AuthenticationResult> completed) {
        dispatch(session, () -> {
            AuthenticationResult effective = session.isExpired() ? new AuthenticationResult(null, DefaultAuthenticator.INVALID_PASSWORD) : result;
            if (!session.isExpired()) session.authenticated(effective, false, nonce);
            completed.accept(effective);
        });
    }

    /**
     * Dispatches an authentication transition on the current WebSocket event loop when connected.
     * @param session current session
     * @param action thread-confined transition
     */
    void dispatch(SessionContext session, Runnable action) {
        Runnable bound = () -> {
            SessionContext previous = SessionContext.get();
            SessionContext.set(session);
            UI ui = session.getUI();
            ui.beginUpdate();
            try {
                action.run();
            } finally {
                try {
                    SessionContext.set(session);
                    ui.endUpdate();
                } finally {
                    if (previous == null) SessionContext.clear();
                    else SessionContext.set(previous);
                }
            }
        };
        Channel target = session.getChannel();
        if (target == null || target.eventLoop().isShuttingDown()) { bound.run(); return; }
        try {
            target.eventLoop()
                    .execute(bound);
        } catch (RejectedExecutionException shuttingDown) {
            bound.run();
        }
    }

    /**
     * Returns the origin used for sitemap and default canonical URLs.
     * @return configured public origin, possibly empty
     */
    String publicUrl() { return policy.publicUrl(); }

    /**
     * Normalizes an origin's implicit HTTP or HTTPS port.
     * @param uri absolute origin
     * @return effective TCP port
     */
    private int effectivePort(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80; }

    @Override
    public void close() {
        for (Runnable pending : authentication.shutdownNow()) {
            if (pending instanceof AuthenticationTask task) task.cancel();
        }
    }
}
