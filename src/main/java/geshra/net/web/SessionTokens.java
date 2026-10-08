package geshra.net.web;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque 256-bit session and anti-forgery tokens.
 */
final class SessionTokens {
    /**
     * Secure random generator shared only by token creation.
     */
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Prevents instances of this token generator.
     */
    private SessionTokens() { }

    /**
     * Generates one URL-safe opaque token.
     * @return token without Base64 padding
     */
    static String create() {
        /*
         * Tokens carry no account information; identities are stored only on the server.
         */
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
