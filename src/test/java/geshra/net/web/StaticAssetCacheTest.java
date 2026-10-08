package geshra.net.web;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses actual temporary JAR resources to verify cross-request reuse, gzip negotiation, and strict
 * cache bounds. Synthetic resources are unnecessary: the production Spring classpath resolver
 * reads the same archive format that consumers package into their applications.
 */
class StaticAssetCacheTest {
    /**
     * Cached original and gzip arrays are reused across requests while zero quality is respected.
     * @param directory temporary archive directory
     * @throws Exception if archive construction or response decoding fails
     */
    @Test
    void reusesPackagedRepresentationsAndHonorsGzipExclusions(@TempDir Path directory) throws Exception {
        byte[] original = "const example = 'cached packaged asset';\n".repeat(100)
                .getBytes(StandardCharsets.UTF_8);
        Path archive = directory.resolve("assets.jar");
        try (OutputStream file = Files.newOutputStream(archive); ZipOutputStream zip = new ZipOutputStream(file)) {
            zip.putNextEntry(new ZipEntry("web/js/example.js"));
            zip.write(original);
            zip.closeEntry();
        }
        try (URLClassLoader loader = new URLClassLoader(new URL[] { archive.toUri().toURL() }, null)) {
            StaticFileHandler handler = new StaticFileHandler(new DefaultResourceLoader(loader), "classpath:/web/", 16384);
            EmbeddedChannel channel = new EmbeddedChannel(handler);
            try {
                FullHttpResponse first = request(channel, "gzip");
                byte[] compressed;
                try {
                    assertThat(first.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                    .isEqualTo("gzip");
                    compressed = first.content()
                            .array();
                    try (GZIPInputStream input = new GZIPInputStream(new ByteArrayInputStream(ByteBufUtil.getBytes(first.content())))) {
                        assertThat(input.readAllBytes())
                    .isEqualTo(original);
                    }
                } finally {
                    first.release();
                }
                FullHttpResponse second = request(channel, "gzip, deflate");
                try {
                    assertThat(second.content().array())
                    .isSameAs(compressed);
                } finally {
                    second.release();
                }
                FullHttpResponse identity = request(channel, "*;q=1, gzip;q=0");
                try {
                    assertThat(identity.headers().get(HttpHeaderNames.CONTENT_ENCODING))
                    .isNull();
                    assertThat(ByteBufUtil.getBytes(identity.content()))
                    .isEqualTo(original);
                } finally {
                    identity.release();
                }
            } finally {
                handler.clearCache();
                channel.finishAndReleaseAll();
            }
        }
    }

    /**
     * Payload budgets and entry-count limits also bound zero-byte resources and metadata.
     * @throws Exception if representation creation fails
     */
    @Test
    void boundsPayloadsAndEntryMetadata() throws Exception {
        StaticAssetCache cache = new StaticAssetCache(4);
        cache.put("first", new byte[4], WebFileType.PNG);
        cache.put("second", new byte[1], WebFileType.PNG);
        assertThat(cache.get("first"))
                    .isNotNull();
        assertThat(cache.get("second"))
                    .isNull();
        cache.clear();
        for (int i = 0; i < 300; i++) cache.put("empty-" + i, new byte[0], WebFileType.PNG);
        assertThat(cache.get("empty-255"))
                    .isNotNull();
        assertThat(cache.get("empty-256"))
                    .isNull();
        StaticAssetCache disabled = new StaticAssetCache(0);
        disabled.put("disabled", new byte[1], WebFileType.PNG);
        disabled.put("empty", new byte[0], WebFileType.PNG);
        assertThat(disabled.get("disabled"))
                    .isNull();
        assertThat(disabled.get("empty"))
                    .isNull();
    }

    /**
     * Sends one ordinary asset request to the shared handler.
     * @param channel owned embedded transport
     * @param encoding accepted compression formats
     * @return owned HTTP response
     */
    private FullHttpResponse request(EmbeddedChannel channel, String encoding) {
        DefaultFullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/js/example.js");
        request.headers()
                .set(HttpHeaderNames.ACCEPT_ENCODING, encoding);
        channel.writeInbound(request);
        return channel.readOutbound();
    }
}
