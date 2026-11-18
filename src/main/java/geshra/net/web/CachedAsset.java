package geshra.net.web;

/**
 * Immutable static response bytes, MIME type, and optional precompressed gzip representation.
 * Arrays are owned by the asset cache and only wrapped for reading by responses. Their combined
 * length is charged to the cache budget; response buffers must never mutate these arrays.
 * @param content original asset bytes
 * @param contentType HTTP MIME type
 * @param gzipContent gzip representation or null
 * @param entityTag weak content validator for immutable packaged bytes, or null for live resources
 */
record CachedAsset(byte[] content, String contentType, byte[] gzipContent, String entityTag) {
    /**
     * Creates an uncached/live representation that must not survive a browser reload.
     * @param content response bytes
     * @param contentType MIME type
     * @param gzipContent optional encoded representation
     */
    CachedAsset(byte[] content, String contentType, byte[] gzipContent) { this(content, contentType, gzipContent, null); }
}
