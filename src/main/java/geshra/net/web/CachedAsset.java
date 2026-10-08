package geshra.net.web;

/**
 * Immutable static response bytes, MIME type, and optional precompressed gzip representation.
 * Arrays are owned by the asset cache and only wrapped for reading by responses. Their combined
 * length is charged to the cache budget; response buffers must never mutate these arrays.
 * @param content original asset bytes
 * @param contentType HTTP MIME type
 * @param gzipContent gzip representation or null
 */
record CachedAsset(byte[] content, String contentType, byte[] gzipContent) { }
