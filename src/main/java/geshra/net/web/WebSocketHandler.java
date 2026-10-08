package geshra.net.web;

import geshra.net.web.packets.KeyUpPacketHandler;
import geshra.net.web.packets.MousePacketHandler;
import geshra.net.web.packets.RoutePacketHandler;
import geshra.net.web.packets.ValueChangePacketHandler;
import geshra.net.web.ui.UI;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;


import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

import geshra.net.web.ui.BrowserJson;

import java.util.Map;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles WebSocket session creation, session resume, and binary packet dispatch.
 * <p>
 * Session cookies are treated as opaque secure random tokens instead of predictable numeric IDs. A
 * token can resume an existing {@link SessionContext} only while that session is still present and
 * not expired on the server. Malformed, unknown, or expired cookie values are ignored and replaced
 * with a new session.
 * <p>
 * This handler also ensures the thread-local {@link SessionContext} is cleared after every packet so
 * Netty event-loop threads cannot accidentally reuse the previous packet's session.
 *
 * @author Albert
 * @version 1.0
 * @since September 2024
 */
public class WebSocketHandler extends SimpleChannelInboundHandler<BinaryWebSocketFrame> {

    /**
     * Logger used for WebSocket session and packet errors.
     */
    private static final Logger Log = LoggerFactory.getLogger(WebSocketHandler.class);

    private final PacketHandlerRegistry packetHandlerRegistry; // Registry used to route packet IDs to server-side handlers.

    private final RouteRegistry routeRegistry; // Registry used by newly created sessions to resolve application routes.
    private boolean pausedForBackpressure; // Whether this handler temporarily disabled inbound reads for queued output.

    /**
     * Creates a WebSocket handler bound to the application's route and packet registries.
     *
     * @param routeRegistry registry used by sessions to render routes
     * @param packetHandlerRegistry registry used to dispatch binary packets
     */
    public WebSocketHandler(RouteRegistry routeRegistry, PacketHandlerRegistry packetHandlerRegistry) {
        this.routeRegistry = routeRegistry;
        this.packetHandlerRegistry = packetHandlerRegistry;
    }

    /**
     * Detaches the channel when the browser disconnects.
     * <p>
     * The session itself remains resumable until {@link SessionContext#SESSION_TIMEOUT} expires.
     *
     * @param ctx channel handler context
     * @throws Exception when Netty cleanup fails
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
        Log.debug("Channel closed: {}", ctx.channel().remoteAddress());
        SessionContext.unregister(ctx.channel());
    }

    /**
     * Logs newly opened channels.
     * <p>
     * The session is not created here because the WebSocket handshake headers are not available until
     * {@link #userEventTriggered(ChannelHandlerContext, Object)} receives the handshake-complete event.
     *
     * @param ctx channel handler context
     * @throws Exception when Netty activation fails
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
        Log.debug("Channel opened: {}", ctx.channel().remoteAddress());
    }

    /**
     * Pauses input while outbound writes are backpressured and resumes pending DOM work when writable.
     */
    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        boolean writable = ctx.channel()
                .isWritable();
        if (!writable && ctx.channel().config().isAutoRead()) {
            pausedForBackpressure = true;
            ctx.channel()
                    .config()
                    .setAutoRead(false);
        } else if (writable) {
            if (pausedForBackpressure) {
                pausedForBackpressure = false;
                ctx.channel()
                        .config()
                        .setAutoRead(true);
            }
            SessionContext session = SessionContext.get(ctx.channel());
            if (session != null) {
                SessionContext previous = SessionContext.get();
                try {
                    SessionContext.set(session);
                    session.getUI()
                            .push();
                } finally {
                    if (previous == null) SessionContext.clear();
                    else SessionContext.set(previous);
                }
            }
        }
        super.channelWritabilityChanged(ctx);
    }
    /**
     * Reads and dispatches one binary packet from the browser.
     * <p>
     * The first byte is the packet ID. Remaining bytes are left for the selected packet handler. Bad
     * packet IDs are ignored rather than crashing the connection.
     *
     * @param ctx channel handler context
     * @param frame incoming WebSocket frame
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, BinaryWebSocketFrame frame) {
        ByteBuf buffer = frame.content();
        if (!buffer.isReadable()) {
            return;
        }

        int packetID = buffer.readUnsignedByte();
        PacketHandler handler = packetHandlerRegistry.getHandler(packetID);

        if (handler == null) {
            Log.debug("Unknown Packet ID: {}", packetID);
            return;
        }

        SessionContext context = SessionContext.get(ctx.channel());
        if (context == null || context.isExpired()) {
            Log.debug("Packet received for missing or expired session from {}", ctx.channel().remoteAddress());
            ctx.close();
            return;
        }

        UI ui = context.getUI();
        try {
            context.touch();
            SessionContext.set(context);
            ui.beginUpdate();
            handler.handlePacket(context, buffer);
        } catch (RuntimeException exception) {
            Log.error("Packet {} failed: {}", packetID, exception.getMessage(), exception);
        } finally {
            try {
                SessionContext.set(context);
                ui.endUpdate();
            } finally {
                SessionContext.clear();
            }
        }
    }

    /**
     * Creates or resumes a session once the WebSocket handshake is complete.
     * <p>
     * A valid cookie token resumes the existing server-side session. Missing, malformed, expired, or
     * unknown tokens create a new session and send a replacement cookie packet back to the browser.
     *
     * @param ctx channel handler context
     * @param evt Netty user event
     * @throws Exception when Netty event propagation fails
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete handshake) {


            HttpHeaders headers = handshake.requestHeaders();
            SessionContext session = SessionContext.getBySessionID(ctx.channel().attr(SessionContext.SESSION_ID).get());

            if (session == null) {
                session = new SessionContext(routeRegistry, SessionTokens.create(), ctx.channel());

                Log.debug("Created browser session");
            } else {
                Log.debug("Resumed browser session");
            }

            SessionContext.register(ctx.channel(), session);
            session.send(TextPacketEncoder.encode(ctx.channel(), 6, BrowserJson.encode(Map.of("action", "state", "csrfToken", session.csrfToken(), "cookieMaxAge", session.getCookieMaxAge()))));
            session.flushCookies();
            SessionContext.clear();
            return;
        }

        super.userEventTriggered(ctx, evt);
    }

    /**
     * Handles uncaught channel exceptions by closing the channel.
     * <p>
     * Browser reconnects, sleeping tabs, refreshes, proxy timeouts, and closed laptop/network changes
     * commonly surface in Netty as {@code Connection reset}, {@code Broken pipe}, or
     * {@code Connection aborted}. Those are normal disconnects for a WebSocket application and should
     * not be printed as application errors. Real packet/server failures are still logged.
     *
     * @param ctx channel handler context
     * @param cause exception that reached the pipeline
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isNormalDisconnect(cause)) {
            Log.debug("WebSocket disconnected normally from {}: {}", ctx.channel().remoteAddress(), safeMessage(cause));
            ctx.close();
            return;
        }

        Log.error("Error on channel[{}]: {}", ctx.channel().remoteAddress(), cause.getMessage(), cause);
        ctx.close();
    }

    /**
     * Returns whether the exception represents a normal browser/network disconnect.
     *
     * @param throwable exception to inspect
     * @return {@code true} when the exception should not be treated as an application error
     */
    private boolean isNormalDisconnect(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String typeName = current.getClass()
                    .getName()
                    .toLowerCase();
            String message = current.getMessage();
            String lowerMessage = message == null ? "" : message.toLowerCase();

            if (typeName.contains("socketexception")
                    || typeName.contains("nativeioexception")
                    || typeName.contains("ioexception")) {
                if (lowerMessage.contains("connection reset")
                        || lowerMessage.contains("broken pipe")
                        || lowerMessage.contains("connection aborted")
                        || lowerMessage.contains("forcibly closed")
                        || lowerMessage.contains("connection closed")
                        || lowerMessage.contains("closed channel")
                        || lowerMessage.contains("connection timed out")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    /**
     * Safely extracts a concise log message from an exception.
     *
     * @param throwable exception to describe
     * @return concise exception message
     */
    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "disconnect";
        }

        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

}
