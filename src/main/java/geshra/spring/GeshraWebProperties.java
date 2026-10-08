package geshra.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

/**
 * Configuration for the library's Netty HTTP and WebSocket server.
 */
@ConfigurationProperties("geshra.web")
public class GeshraWebProperties {
    private Duration sessionTimeout = Duration.ofHours(8); // Browser-session inactivity lifetime.
    private String sessionCookieName = "sessionID"; // Reserved HttpOnly cookie name.
    private boolean secureCookies; // Force HTTPS cookies behind an explicitly configured proxy.
    private String publicUrl = ""; // Public origin for canonical URLs, sitemap, and origin checks.
    private int staticCacheBytes = 8 * 1024 * 1024; // Retained packaged-asset budget, including gzip bytes.
    private boolean enabled = true; // Whether auto-configuration installs the web server.
    private int port = 4040; // Configured TCP port; zero requests an ephemeral port.
    private boolean openBrowser; // Whether startup opens the server URL in the desktop browser.
    private String staticLocation = "classpath:/web/"; // Resource prefix used for application static-file overrides.


    /**
     * Returns the session inactivity lifetime.
     * @return session lifetime
     */
    public Duration getSessionTimeout() { return sessionTimeout; }

    /**
     * Sets the session inactivity lifetime.
     * @param timeout positive duration
     */
    public void setSessionTimeout(Duration timeout) { sessionTimeout = timeout; }

    /**
     * Returns the reserved session cookie name.
     * @return cookie name
     */
    public String getSessionCookieName() { return sessionCookieName; }

    /**
     * Sets the reserved session cookie name.
     * @param name cookie name
     */
    public void setSessionCookieName(String name) { sessionCookieName = name; }

    /**
     * Returns the HTTPS-only cookie policy.
     * @return force Secure cookies
     */
    public boolean isSecureCookies() { return secureCookies; }

    /**
     * Sets the HTTPS-only cookie policy.
     * @param secure force Secure cookies
     */
    public void setSecureCookies(boolean secure) { secureCookies = secure; }

    /**
     * Returns the explicit public HTTP(S) origin.
     * @return public origin
     */
    public String getPublicUrl() { return publicUrl; }

    /**
     * Sets the public origin used for SEO and origin checks.
     * @param url absolute HTTP(S) origin or empty
     */
    public void setPublicUrl(String url) { publicUrl = url; }

    /**
     * Returns the enabled retained by this instance.
     * @return the resulting is enabled value
     */
    public boolean isEnabled() {
        return enabled;
    }
    /**
     * Updates the enabled used by subsequent operations.
     *
     * @param enabled enabled supplied to this operation
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    /**
     * Returns the port retained by this instance.
     * @return the resulting port value
     */
    public int getPort() {
        return port;
    }
    /**
     * Updates the port used by subsequent operations.
     *
     * @param port TCP port from zero to 65535; zero selects an ephemeral port
     */
    public void setPort(int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("geshra.web.port must be between 0 and 65535");
        }
        this.port = port;
    }
    /**
     * Returns the open browser retained by this instance.
     * @return the resulting is open browser value
     */
    public boolean isOpenBrowser() {
        return openBrowser;
    }
    /**
     * Updates the open browser used by subsequent operations.
     *
     * @param openBrowser whether to open the desktop browser after binding
     */
    public void setOpenBrowser(boolean openBrowser) {
        this.openBrowser = openBrowser;
    }
    /**
     * Returns the static location retained by this instance.
     * @return the resulting static location value
     */
    public String getStaticLocation() {
        return staticLocation;
    }
    /**
     * Updates the static location used by subsequent operations.
     *
     * @param staticLocation nonblank application static resource prefix
     */
    public void setStaticLocation(String staticLocation) {
        this.staticLocation = staticLocation;
    }

    /**
     * Returns the retained packaged-asset payload budget.
     * @return maximum cache bytes
     */
    public int getStaticCacheBytes() { return staticCacheBytes; }

    /**
     * Configures the retained packaged-asset payload budget.
     * @param bytes non-negative byte limit; zero disables caching
     */
    public void setStaticCacheBytes(int bytes) {
        if (bytes < 0) throw new IllegalArgumentException("Static cache budget cannot be negative");
        staticCacheBytes = bytes;
    }
}
