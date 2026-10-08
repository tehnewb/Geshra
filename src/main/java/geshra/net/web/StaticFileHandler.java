package geshra.net.web;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.stream.ChunkedStream;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Shares bounded packaged-asset caching across all connections of one server. Cached JAR resources
 * are served without IO or repeated compression; filesystem resources stay live for development.
 * Assets larger than one MiB stream in small chunks through Netty's ChunkedWriteHandler, respecting
 * channel backpressure rather than allocating a byte array for the complete file. HEAD requests
 * use metadata without opening the content stream. Mutable cache state is concurrency-safe.
 */
@ChannelHandler.Sharable
public class StaticFileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    /**
     * Default retained payload budget shared by every connection of one server.
     */
    public static final int DEFAULT_CACHE_BYTES = 8 * 1024 * 1024;
    /**
     * Above this size, assets stream rather than allocating a complete response byte array.
     */
    private static final int MAX_BUFFERED_BYTES = 1024 * 1024;
    /**
     * Reusable empty response payload.
     */
    private static final byte[] EMPTY_CONTENT = new byte[0];
    private final ResourceLoader resources; // Consumer classloader and resource resolution.
    private final String staticLocation; // Application asset prefix with a trailing slash.
    private final StaticAssetCache cache; // Server-scoped bounded immutable representations.

    /**
     * Uses application classpath assets and the default cache budget.
     */
    public StaticFileHandler() { this(new DefaultResourceLoader(), "classpath:/web/"); }

    /**
     * Uses an application resource prefix and the default cache budget.
     * @param resources resource resolver
     * @param staticLocation application asset prefix
     */
    public StaticFileHandler(ResourceLoader resources, String staticLocation) { this(resources, staticLocation, DEFAULT_CACHE_BYTES); }

    /**
     * Creates a handler with a bounded packaged-asset cache.
     * @param resources resource resolver
     * @param staticLocation application asset prefix
     * @param cacheBytes maximum retained original and compressed bytes; zero disables caching
     */
    public StaticFileHandler(ResourceLoader resources, String staticLocation, int cacheBytes) {
        this.resources = resources;
        this.staticLocation = staticLocation.endsWith("/") ? staticLocation : staticLocation + "/";
        cache = new StaticAssetCache(cacheBytes);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (!request.method().equals(HttpMethod.GET) && !request.method().equals(HttpMethod.HEAD)) {
            send(ctx, request, HttpResponseStatus.METHOD_NOT_ALLOWED, "text/plain", EMPTY_CONTENT, false);
            return;
        }
        String path;
        try {
            path = new QueryStringDecoder(request.uri())
                    .path();
            if (!isSafePath(path)) {
                send(ctx, request, HttpResponseStatus.BAD_REQUEST, "text/plain", EMPTY_CONTENT, false);
                return;
            }
            path = path.substring(1);
        } catch (IllegalArgumentException failure) {
            send(ctx, request, HttpResponseStatus.BAD_REQUEST, "text/plain", EMPTY_CONTENT, false);
            return;
        }
        if (path.isEmpty() || path.endsWith("/")) path += "index.html";
        CachedAsset asset = cache.get(path);
        if (asset != null) { sendAsset(ctx, request, asset); return; }
        Resource resource = find(path);
        if (!resource.exists() && path.indexOf('.', path.lastIndexOf('/') + 1) < 0) {
            path = "index.html";
            asset = cache.get(path);
            if (asset != null) { sendAsset(ctx, request, asset); return; }
            resource = find(path);
        }
        if (!resource.exists() || !resource.isReadable()) {
            send(ctx, request, HttpResponseStatus.NOT_FOUND, "text/plain", EMPTY_CONTENT, false);
            return;
        }
        WebFileType type = WebFileType.fromFilename(path);
        try {
            long length = resource.isOpen() ? -1 : resource.contentLength();
            if (request.method().equals(HttpMethod.HEAD)) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.EMPTY_BUFFER);
                response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, type.getContentType());
                if (length >= 0) response.headers()
                        .set(HttpHeaderNames.CONTENT_LENGTH, length);
                finish(ctx, request, response);
                return;
            }
            if (length < 0 || length > MAX_BUFFERED_BYTES) {
                stream(ctx, request, resource, type.getContentType(), length);
                return;
            }
            byte[] content;
            try (InputStream input = resource.getInputStream()) { content = input.readAllBytes(); }
            if (resource instanceof ClassPathResource && !resource.isFile()) asset = cache.put(path, content, type);
            else asset = new CachedAsset(content, type.getContentType(), null);
            sendAsset(ctx, request, asset);
        } catch (IOException failure) {
            send(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, "text/plain", "Unable to read resource".getBytes(StandardCharsets.UTF_8), false);
        }
    }

    /**
     * Checks path segment boundaries without split arrays or regex parsing.
     * @param path decoded request path
     * @return true when no traversal or platform path escape is present
     */
    private boolean isSafePath(String path) {
        if (path.isEmpty() || path.charAt(0) != '/' || path.indexOf('\\') >= 0 || path.indexOf('\0') >= 0) return false;
        for (int i = 1, length = path.length(); i < length - 1; i++) {
            if (path.charAt(i) == '.' && path.charAt(i + 1) == '.' && path.charAt(i - 1) == '/' && (i + 2 == length || path.charAt(i + 2) == '/')) return false;
        }
        return true;
    }

    /**
     * Resolves application assets before library fallback assets.
     * @param path normalized asset path
     * @return resource, possibly absent
     */
    private Resource find(String path) {
        Resource application = resources.getResource(staticLocation + path);
        if (application.exists() || staticLocation.equals("classpath:/web/")) return application;
        return resources.getResource("classpath:/web/" + path);
    }

    /**
     * Selects a retained gzip representation only when the client accepts it.
     * @param ctx response context
     * @param request incoming request
     * @param asset immutable response representations
     */
    private void sendAsset(ChannelHandlerContext ctx, FullHttpRequest request, CachedAsset asset) {
        boolean gzip = asset.gzipContent() != null && acceptsGzip(request.headers().get(HttpHeaderNames.ACCEPT_ENCODING));
        send(ctx, request, HttpResponseStatus.OK, asset.contentType(), gzip ? asset.gzipContent() : asset.content(), gzip);
    }

    /**
     * Recognizes explicit gzip preferences, including zero quality and wildcard exclusions.
     * The common header without quality parameters allocates no token strings.
     * @param header Accept-Encoding header or null
     * @return true when gzip is acceptable
     */
    private boolean acceptsGzip(String header) {
        if (header == null) return false;
        boolean wildcard = false;
        for (int start = 0, length = header.length(); start < length;) {
            int end = header.indexOf(',', start);
            if (end < 0) end = length;
            int semicolon = header.indexOf(';', start);
            if (semicolon < 0 || semicolon > end) semicolon = end;
            int nameStart = start;
            int nameEnd = semicolon;
            while (nameStart < nameEnd && header.charAt(nameStart) <= ' ') nameStart++;
            while (nameEnd > nameStart && header.charAt(nameEnd - 1) <= ' ') nameEnd--;
            boolean gzip = nameEnd - nameStart == 4 && header.regionMatches(true, nameStart, "gzip", 0, 4);
            boolean star = nameEnd - nameStart == 1 && header.charAt(nameStart) == '*';
            if (gzip || star) {
                double quality = 1;
                if (semicolon < end) {
                    int parameter = semicolon + 1;
                    while (parameter < end && header.charAt(parameter) <= ' ') parameter++;
                    if (parameter + 1 < end && (header.charAt(parameter) == 'q' || header.charAt(parameter) == 'Q') && header.charAt(parameter + 1) == '=') {
                        try {
                            quality = Double.parseDouble(header.substring(parameter + 2, end));
                        } catch (NumberFormatException failure) {
                            quality = 0;
                        }
                    }
                }
                boolean accepted = quality > 0 && quality <= 1;
                if (gzip) return accepted;
                wildcard = accepted;
            }
            start = end + 1;
        }
        return wildcard;
    }

    /**
     * Sends a bounded small response, wrapping cached bytes without copying them.
     * @param ctx response context
     * @param request request determining HEAD and connection handling
     * @param status HTTP status
     * @param contentType MIME type
     * @param content immutable response bytes
     * @param gzip whether the bytes are already gzip encoded
     */
    private void send(ChannelHandlerContext ctx, FullHttpRequest request, HttpResponseStatus status, String contentType, byte[] content, boolean gzip) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, request.method().equals(HttpMethod.HEAD) ? Unpooled.EMPTY_BUFFER : Unpooled.wrappedBuffer(content));
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, contentType);
        response.headers()
                .setInt(HttpHeaderNames.CONTENT_LENGTH, content.length);
        response.headers()
                .set(HttpHeaderNames.VARY, HttpHeaderNames.ACCEPT_ENCODING);
        if (gzip) response.headers()
                .set(HttpHeaderNames.CONTENT_ENCODING, HttpHeaderValues.GZIP);
        finish(ctx, request, response);
    }

    /**
     * Applies persistent-connection semantics and transfers a complete response to Netty.
     * @param ctx response context
     * @param request incoming request
     * @param response response message
     */
    private void finish(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setKeepAlive(response, keepAlive);
        ChannelFuture sent = ctx.writeAndFlush(response);
        if (!keepAlive) sent.addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Streams large content in bounded chunks; Netty closes the stream when complete or failed.
     * @param ctx response context
     * @param request incoming request
     * @param resource large resource
     * @param mime HTTP MIME type
     * @param length known length or negative for unknown length
     * @throws IOException if opening the resource fails
     */
    private void stream(ChannelHandlerContext ctx, FullHttpRequest request, Resource resource, String mime, long length) throws IOException {
        InputStream input = resource.getInputStream();
        HttpResponse response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        response.headers()
                .set(HttpHeaderNames.CONTENT_TYPE, mime);
        if (length >= 0) response.headers()
                .set(HttpHeaderNames.CONTENT_LENGTH, length);
        else HttpUtil.setTransferEncodingChunked(response, true);
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        HttpUtil.setKeepAlive(response, keepAlive);
        ctx.write(response);
        ChannelFuture sent = ctx.writeAndFlush(new HttpChunkedInput(new ChunkedStream(input, 8192)));
        if (!keepAlive) sent.addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Drops packaged assets when this handler's server stops.
     */
    void clearCache() { cache.clear(); }

    /**
     * Preserves the legacy reload entry point. Files are not cached, so edits require no invalidation;
     * immutable packaged resources remain fixed for the lifetime of their server.
     * @param uri legacy asset URI
     * @throws IOException retained for source compatibility
     */
    public static void reload(String uri) throws IOException {
        /*
         * Live file resources are read on each request; no watcher, thread, or global cache is needed.
         */
    }
}
