package geshra.net.web;

import geshra.net.web.auth.AuthenticationResult;
import geshra.net.web.auth.DefaultAuthenticator;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

/**
 * Issues HttpOnly session cookies before WebSocket upgrade and implements same-origin HTTP
 * session actions. Identity changes run on the session's UI thread; credential work is bounded
 * and offloaded. This handler owns one connection's pending handshake cookie.
 */
final class SessionHttpHandler extends ChannelDuplexHandler {
    private final SessionService sessions; // Shared server-owned session operations.
    private String handshakeCookie; // New-session cookie added to the upgrade HTTP response.

    /**
     * Creates a connection's session gateway.
     * @param sessions shared server session service
     */
    SessionHttpHandler(SessionService sessions) { this.sessions = sessions; }

    @Override
    public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
        if (message instanceof HttpResponse response && response.status().equals(HttpResponseStatus.SWITCHING_PROTOCOLS) && handshakeCookie != null) {
            response.headers()
                    .add(HttpHeaderNames.SET_COOKIE, handshakeCookie);
            handshakeCookie = null;
        }
        super.write(ctx, message, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object message) throws Exception {
        if (!(message instanceof FullHttpRequest request)) { super.channelRead(ctx, message); return; }
        String path;
        try {
            path = new QueryStringDecoder(request.uri())
                    .path();
        } catch (IllegalArgumentException invalidPath) {
            super.channelRead(ctx, message);
            return;
        }
        boolean upgrade = "/ws".equals(path) && "websocket".equalsIgnoreCase(request.headers().get(HttpHeaderNames.UPGRADE));
        if (!upgrade && !path.startsWith("/_geshra/")) { super.channelRead(ctx, message); return; }
        if (!sessions.sameOrigin(ctx, request.headers())) {
            reply(ctx, request, HttpResponseStatus.FORBIDDEN, "{\"error\":\"Origin rejected\"}");
            ReferenceCountUtil.release(request);
            return;
        }
        SessionContext session = sessions.find(ctx, request.headers());
        if (upgrade) {
            if (session == null) {
                session = sessions.create(ctx);
            }
            handshakeCookie = sessions.cookie(ctx, session);
            ctx.channel()
                    .attr(SessionContext.SESSION_ID)
                    .set(session.getSessionID());
            super.channelRead(ctx, message);
            return;
        }
        try {
            if ("/_geshra/session".equals(path) && request.method().equals(HttpMethod.GET)) {
                if (session == null) session = sessions.create(ctx);
                FullHttpResponse response = HttpResponses.create(request, HttpResponseStatus.OK, "application/json", sessions.state(session));
                response.headers()
                        .add(HttpHeaderNames.SET_COOKIE, sessions.cookie(ctx, session));
                HttpResponses.send(ctx, response);
                return;
            }
            if (!request.method().equals(HttpMethod.POST)) {
                reply(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "{\"error\":\"Use POST\"}");
                return;
            }
            if (session == null || !sessions.csrf(session, request.headers())) {
                reply(ctx, request, HttpResponseStatus.FORBIDDEN, "{\"error\":\"Session or CSRF token rejected\"}");
                return;
            }
            SessionContext current = session;
            Map<String, List<String>> form = new QueryStringDecoder(request.content().toString(StandardCharsets.UTF_8), false)
                    .parameters();
            String requestValue = first(form, "_request");
            int nonce = requestValue.isEmpty() ? 0 : Integer.parseInt(requestValue);
            switch (path) {
                case "/_geshra/login" -> {
                    String type = request.headers()
                            .get(HttpHeaderNames.CONTENT_TYPE, "");
                    if (!type.startsWith("application/x-www-form-urlencoded")) {
                        reply(ctx, request, HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE, "{\"error\":\"Use form encoding\"}");
                        return;
                    }
                    String username = first(form, "username");
                    String password = first(form, "password");
                    if (username.length() > 128 || password.length() > 1024 || !current.allowLogin()) {
                        current.authenticated(new AuthenticationResult(null, DefaultAuthenticator.INVALID_PASSWORD), false, nonce);
                        reply(ctx, request, HttpResponseStatus.TOO_MANY_REQUESTS, "{\"error\":\"Login limit reached\"}");
                        return;
                    }
                    request.retain();
                    try {
                        sessions.login(current, username, password, nonce, result -> {
                            try {
                                FullHttpResponse response = HttpResponses.create(request, HttpResponseStatus.OK, "application/json", sessions.state(current));
                                response.headers()
                                        .add(HttpHeaderNames.SET_COOKIE, sessions.cookie(ctx, current));
                                response.headers()
                                        .set("X-Authentication-Success", result.isSuccess());
                                HttpResponses.send(ctx, response);
                            } finally {
                                request.release();
                            }
                        });
                    } catch (RejectedExecutionException overloaded) {
                        request.release();
                        current.authenticated(new AuthenticationResult(null, DefaultAuthenticator.INVALID_PASSWORD), false, nonce);
                        reply(ctx, request, HttpResponseStatus.SERVICE_UNAVAILABLE, "{\"error\":\"Authentication busy\"}");
                    }
                }
                case "/_geshra/logout" -> {
                    request.retain();
                    sessions.dispatch(current, () -> {
                        try {
                            current.authenticated(new AuthenticationResult(null, 0), true, nonce);
                            FullHttpResponse response = HttpResponses.create(request, HttpResponseStatus.OK, "application/json", sessions.state(current));
                            response.headers()
                                    .add(HttpHeaderNames.SET_COOKIE, sessions.cookie(ctx, current));
                            HttpResponses.send(ctx, response);
                        } finally {
                            request.release();
                        }
                    });
                }
                case "/_geshra/cookies" -> {
                    FullHttpResponse response = HttpResponses.create(request, HttpResponseStatus.OK, "application/json", sessions.state(current));
                    for (String header : current.takeCookies()) response.headers()
                                    .add(HttpHeaderNames.SET_COOKIE, header);
                    response.headers()
                            .add(HttpHeaderNames.SET_COOKIE, sessions.cookie(ctx, current));
                    HttpResponses.send(ctx, response);
                }
                default -> reply(ctx, request, HttpResponseStatus.NOT_FOUND, "{\"error\":\"Unknown session action\"}");
            }
        } catch (IllegalArgumentException invalidInput) {
            reply(ctx, request, HttpResponseStatus.BAD_REQUEST, "{\"error\":\"Invalid session action input\"}");
        } finally {
            request.release();
        }
    }

    /**
     * Reads one form field without exposing credentials in logs or URLs.
     * @param form decoded POST body
     * @param name requested field
     * @return field value or an empty string
     */
    private String first(Map<String, List<String>> form, String name) {
        List<String> values = form.get(name);
        return values == null || values.isEmpty() ? "" : values.getFirst();
    }

    /**
     * Sends a small JSON error or action response.
     * @param ctx request channel
     * @param request incoming request
     * @param status HTTP status
     * @param body JSON content
     */
    private void reply(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String body) {
        HttpResponses.send(ctx, HttpResponses.create(request, status, "application/json", body));
    }
}
