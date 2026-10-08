package geshra.net.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPOutputStream;

/**
 * Bounded server-owned cache for immutable packaged assets. Hits perform one concurrent map lookup;
 * no resource lookup, disk access, byte-array copy, or compression is required. Cold insertions are
 * serialized to enforce both a byte budget and an entry limit. Gzip bytes count against the same
 * budget as originals. File resources are intentionally excluded by the caller to preserve editing.
 */
final class StaticAssetCache {
    /**
     * Bound on key and metadata overhead, including small or empty assets.
     */
    private static final int MAX_ENTRIES = 256;
    private final ConcurrentHashMap<String, CachedAsset> assets = new ConcurrentHashMap<>(); // Published immutable entries.
    private final int maxBytes; // Maximum retained original and compressed payload bytes.
    private int cachedBytes; // Retained payload bytes; guarded by this cache's insertion lock.

    /**
     * Creates an empty cache with a fixed payload budget.
     * @param maxBytes non-negative byte limit; zero disables caching
     */
    StaticAssetCache(int maxBytes) {
        if (maxBytes < 0) throw new IllegalArgumentException("Static cache budget cannot be negative");
        this.maxBytes = maxBytes;
    }

    /**
     * Returns an immutable cached entry without performing IO.
     * @param path normalized asset path
     * @return cached entry or null
     */
    CachedAsset get(String path) { return assets.get(path); }

    /**
     * Publishes a packaged asset if its representations fit in the remaining budget.
     * When caching is unavailable, returns an uncached original without spending CPU on compression.
     * @param path normalized asset path
     * @param content original immutable bytes
     * @param type recognized file format
     * @return cached or uncached response representation
     * @throws IOException if gzip encoding fails
     */
    synchronized CachedAsset put(String path, byte[] content, WebFileType type) throws IOException {
        CachedAsset existing = assets.get(path);
        if (existing != null) return existing;
        String mime = type.getContentType();
        if (maxBytes == 0 || assets.size() >= MAX_ENTRIES || content.length > maxBytes - cachedBytes) return new CachedAsset(content, mime, null);
        byte[] gzip = null;
        if (content.length >= 512 && (type == WebFileType.HTML || type == WebFileType.CSS || type == WebFileType.JAVASCRIPT || type == WebFileType.JSON || type == WebFileType.SVG)) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream(Math.min(content.length, 8192));
            try (GZIPOutputStream output = new GZIPOutputStream(compressed)) {
                output.write(content);
            }
            if (compressed.size() < content.length && compressed.size() <= maxBytes - cachedBytes - content.length) gzip = compressed.toByteArray();
        }
        CachedAsset asset = new CachedAsset(content, mime, gzip);
        cachedBytes += content.length + (gzip == null ? 0 : gzip.length);
        assets.put(path, asset);
        return asset;
    }

    /**
     * Drops retained assets when their server stops.
     */
    synchronized void clear() {
        assets.clear();
        cachedBytes = 0;
    }
}
