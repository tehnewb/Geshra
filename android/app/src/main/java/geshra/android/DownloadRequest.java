package geshra.android;

/**
 * Holds one user-initiated authenticated download until its system save dialog completes.
 * Credentials remain in memory only and are sent exclusively to the exact configured origin.
 * @param address validated source URL
 * @param userAgent WebView's browser identity for the same server
 * @param cookie same-origin browser-managed session cookies, or null
 * @param mimeType HTTP content type used for the native save dialog
 * @param filename sanitized proposed document name
 */
record DownloadRequest(String address, String userAgent, String cookie, String mimeType, String filename) { }
