package geshra.android;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Exercises origin boundary decisions without Android mocks or network access. Cases cover
 * parsed authorities, explicit/default ports, malformed and privileged URL forms, restored
 * routes and the distinct debug-only cleartext allowance.
 */
public final class ServerOriginTest {
    /**
     * Equivalent complete origins can navigate while host suffixes and changed ports cannot.
     */
    @Test
    public void comparesExactOriginsAndEffectivePorts() {
        ServerOrigin origin = new ServerOrigin("https://app.example.test/start", false);
        assertTrue(origin.contains("https://app.example.test/account?from=app"));
        assertTrue(origin.contains("https://APP.EXAMPLE.TEST:443/other#anchor"));
        assertFalse(origin.contains("https://app.example.test:444/"));
        assertFalse(origin.contains("https://app.example.test.attacker.test/"));
        assertFalse(origin.contains("https://attacker.test/app.example.test/"));
        assertFalse(origin.contains("http://app.example.test/"));
    }

    /**
     * Credentials and malformed authorities never reach the browser or an external intent.
     */
    @Test
    public void rejectsAmbiguousAndPrivilegedDestinations() {
        ServerOrigin origin = new ServerOrigin("https://app.example.test/", false);
        String[] rejected = {null, "", "/account", "//app.example.test/", "https://user:password@app.example.test/", "https://app.example.test\\@attacker.test/", "https://app.example.test:0/", "https://app.example.test:65536/", "https://%61pp.example.test/", "javascript:alert(1)", "intent://app.example.test/", "file:///data/private", "content://app.example.test/private", "data:text/html,test", "https://app.example.test/\nattack"};
        for (String address : rejected) {
            assertFalse(origin.contains(address));
            assertEquals(NavigationDecision.BLOCKED, origin.classify(address));
        }
    }

    /**
     * Public external web links are distinguishable from in-app links before gesture checks.
     */
    @Test
    public void separatesExternalWebNavigation() {
        ServerOrigin origin = new ServerOrigin("https://app.example.test/", false);
        assertEquals(NavigationDecision.IN_APP, origin.classify("https://app.example.test/settings"));
        assertEquals(NavigationDecision.EXTERNAL, origin.classify("https://other.example.test/"));
        assertEquals(NavigationDecision.EXTERNAL, origin.classify("http://other.example.test/"));
    }

    /**
     * HTTP configuration requires an explicit debug policy and cannot be restored into release.
     */
    @Test
    public void confinesHttpToExplicitDevelopmentConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new ServerOrigin("http://10.0.2.2:4060/showcase", false));
        assertThrows(IllegalArgumentException.class, () -> new ServerOrigin("https://user@app.example.test/", true));
        assertThrows(IllegalArgumentException.class, () -> new ServerOrigin("https://app.example.test/#secret", false));
        ServerOrigin development = new ServerOrigin("http://10.0.2.2:4060/showcase", true);
        assertTrue(development.contains("http://10.0.2.2:4060/login"));
        assertFalse(development.contains("http://10.0.2.2:4061/login"));
        assertEquals("http://10.0.2.2:4060/showcase", development.startUrl());
    }

    /**
     * IPv6 loopback comparison still includes the explicit port without suffix matching.
     */
    @Test
    public void supportsExplicitIpv6Origins() {
        ServerOrigin development = new ServerOrigin("http://[::1]:4060/", true);
        assertTrue(development.contains("http://[::1]:4060/account"));
        assertFalse(development.contains("http://[::1]:4061/account"));
    }

    /**
     * Iframe loading does not give an external document the parent's native origin privileges.
     */
    @Test
    public void permitsWebFramesWithoutPromotingTheirOrigin() {
        ServerOrigin origin = new ServerOrigin("https://app.example.test/", false);
        assertTrue(origin.allowsFrame("https://video.example.test/player"));
        assertFalse(origin.contains("https://video.example.test/player"));
        assertTrue(origin.allowsFrame("about:blank"));
        assertTrue(origin.allowsFrame("about:srcdoc"));
        assertFalse(origin.allowsFrame("http://video.example.test/player"));
        assertFalse(origin.allowsFrame("file:///data/private"));
        assertFalse(origin.allowsFrame("content://private/document"));
        ServerOrigin development = new ServerOrigin("http://10.0.2.2:4060/", true);
        assertTrue(development.allowsFrame("http://10.0.2.2:4061/frame"));
    }
}
