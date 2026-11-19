package geshra.android;

/**
 * Distinguishes the trusted application origin from user-initiated external web links and
 * unsupported navigation. No decision permits arbitrary native intents or script URL schemes.
 */
enum NavigationDecision {
    /**
     * Navigation remains within the exact configured origin.
     */
    IN_APP,
    /**
     * A user gesture may open this web URL in the system browser.
     */
    EXTERNAL,
    /**
     * The URI is malformed or uses an unsupported privileged scheme.
     */
    BLOCKED
}
