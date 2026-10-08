package geshra.net.web;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import java.nio.charset.StandardCharsets;

/**
 * Writes small HTTP page and session responses with consistent HEAD and connection behavior.
 */
final class HttpResponses {
    /**
     * Prevents instances of this response writer.
     */
    private HttpResponses() { }

    /**
     * Builds a UTF-8 response with no shared-cache storage.
     * @param request incoming request
     * @param status response status
     * @param type MIME type
     * @param body UTF-8 content
     * @return caller-owned response
     */
    static FullHttpResponse create(FullHttpRequest request, HttpResponseStatus status, String type, String body) {
        /*
         * HEAD reports the GET representation length while sending no body.
         */
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, request.method().equals(HttpMethod.HEAD) ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(bytes));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, type + "; charset=UTF-8");
        response.headers()
                .setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.length);
        response.headers()
                .set(HttpHeaderNames.CACHE_CONTROL, "no-store");
        response.headers()
                .set("X-Content-Type-Options", "nosniff");
        HttpUtil.setKeepAlive(response, HttpUtil.isKeepAlive(request));
        return response;
    }

    /**
     * Transfers a response to Netty and honors the requested connection lifetime.
     * @param ctx response channel
     * @param response owned response
     */
    static void send(ChannelHandlerContext ctx, FullHttpResponse response) {
        /*
         * Ownership transfers exactly once; closing waits until the response has been written.
         */
        boolean keepAlive = HttpUtil.isKeepAlive(response);
        ChannelFuture sent = ctx.writeAndFlush(response);
        if (!keepAlive) sent.addListener(ChannelFutureListener.CLOSE);
    }
}
