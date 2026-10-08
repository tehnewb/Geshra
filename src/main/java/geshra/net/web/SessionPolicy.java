package geshra.net.web;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Validated server-wide session and origin settings. publicUrl is an application-configured
 * HTTP(S) origin, never a value taken from untrusted forwarded headers.
 * @param timeout session inactivity lifetime
 * @param cookieName browser session cookie name
 * @param secureCookies force HTTPS-only session cookies
 * @param publicUrl optional public HTTP(S) origin
 */
public record SessionPolicy(Duration timeout, String cookieName, boolean secureCookies, String publicUrl) {
    /**
     * Creates the default eight-hour policy.
     */
    public SessionPolicy() { this(SessionContext.SESSION_TIMEOUT, "sessionID", false, ""); }

    /**
     * Validates the policy before any server channels are started.
     * @param timeout positive inactivity lifetime
     * @param cookieName validated cookie name
     * @param secureCookies force HTTPS-only cookies
     * @param publicUrl optional HTTP(S) origin
     */
    public SessionPolicy(Duration timeout, String cookieName, boolean secureCookies, String publicUrl) {
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(cookieName, "cookieName");
        Objects.requireNonNull(publicUrl, "publicUrl");
        if (timeout.toMillis() < 1000) throw new IllegalArgumentException("Session timeout must be at least one second");
        new Cookie(cookieName, "validation")
                .toHeader();
        if (!publicUrl.isEmpty()) {
            URI uri = URI.create(publicUrl);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new IllegalArgumentException("Public URL must be an HTTP(S) origin");
            publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
            secureCookies |= "https".equals(uri.getScheme());
        }
        this.timeout = timeout;
        this.cookieName = cookieName;
        this.secureCookies = secureCookies;
        this.publicUrl = publicUrl;
    }
}
