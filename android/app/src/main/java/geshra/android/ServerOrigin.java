package geshra.android;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Parses the application's public endpoint once and compares complete HTTP origins, including
 * effective ports. This platform-independent policy is also used for restored state, redirects,
 * file actions and new windows. User-info, malformed authorities and non-network schemes are
 * never accepted. Release callers must disable the explicit debug-only HTTP allowance.
 */
final class ServerOrigin {
    private final URI start; // Validated public endpoint and initial route.
    private final String scheme; // Canonical configured transport protocol.
    private final String host; // Exact canonical host, without suffix-based trust.
    private final int port; // Configured or default protocol port.

    /**
     * Validates a configured endpoint before any WebView or native intent can load it.
     * @param address absolute public server URL without credentials or a fragment
     * @param development whether a debug build explicitly permits HTTP
     */
    ServerOrigin(String address, boolean development) {
        start = parse(address);
        if (start == null || start.getFragment() != null || (!"https".equalsIgnoreCase(start.getScheme()) && !(development && "http".equalsIgnoreCase(start.getScheme()))))
            throw new IllegalArgumentException("A valid HTTPS server URL is required.");
        scheme = start.getScheme()
                .toLowerCase(Locale.ROOT);
        host = start.getHost()
                .toLowerCase(Locale.ROOT);
        port = effectivePort(start);
    }

    /**
     * Returns the initial route exactly as validated; it contains no embedded credentials.
     * @return ASCII network URL
     */
    String startUrl() { return start.toASCIIString(); }

    /**
     * Requires exact scheme, host and effective port equality for application navigation.
     * @param address candidate restored, requested or redirected URL
     * @return whether the complete origin belongs to this application
     */
    boolean contains(String address) {
        URI uri = parse(address);
        return uri != null && scheme.equalsIgnoreCase(uri.getScheme()) && host.equalsIgnoreCase(uri.getHost()) && port == effectivePort(uri);
    }

    /**
     * Separates user-clicked external HTTP links from app navigation and dangerous schemes.
     * @param address candidate link target
     * @return the permitted navigation class before any native action is attempted
     */
    NavigationDecision classify(String address) {
        if (contains(address)) return NavigationDecision.IN_APP;
        URI uri = parse(address);
        return uri != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) ? NavigationDecision.EXTERNAL : NavigationDecision.BLOCKED;
    }

    /**
     * Preserves ordinary HTTPS iframe embedding without promoting its origin to native trust.
     * Same-origin frames and inert blank/srcdoc documents remain available. An HTTP development
     * document may embed HTTP frames; release/mixed-content policy never gains that exception.
     * @param address child-frame navigation target
     * @return whether normal browser frame loading is allowed, without native privileges
     */
    boolean allowsFrame(String address) {
        if (contains(address) || "about:blank".equals(address) || "about:srcdoc".equals(address)) return true;
        URI uri = parse(address);
        return uri != null && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equals(scheme) && "http".equalsIgnoreCase(uri.getScheme()));
    }

    /**
     * Rejects ambiguous or privileged URL forms without throwing for user-controlled input.
     * @param address raw candidate URL
     * @return parsed absolute hierarchical URL, or null when unsafe/malformed
     */
    private static URI parse(String address) {
        /*
         * Parse complete authorities rather than applying substring trust checks, and reject
         * malformed input before any browser or native action uses it.
         */
        if (address == null || address.length() > 8192 || address.indexOf('\\') >= 0) return null;
        try {
            URI uri = new URI(address);
            return uri.isAbsolute() && !uri.isOpaque() && uri.getHost() != null && uri.getRawUserInfo() == null && uri.getPort() >= -1 && uri.getPort() != 0 && uri.getPort() <= 65535 ? uri : null;
        } catch (URISyntaxException failure) {
            // Malformed external input is a rejected boundary value, not an application failure.
            return null;
        }
    }

    /**
     * Compares omitted default ports with their explicit equivalent.
     * @param uri parsed network URI
     * @return explicit port or the protocol's standard port
     */
    private static int effectivePort(URI uri) {
        /*
         * Normalize omitted standard ports while preserving distinct explicit server ports.
         */
        return uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
    }
}
