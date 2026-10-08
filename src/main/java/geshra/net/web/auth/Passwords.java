package geshra.net.web.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Salted PBKDF2-HMAC-SHA256 password storage using the JDK rather than a new runtime dependency.
 * Hashes include their iteration count for migration. Password work occurs only at registration
 * and login; it is deliberately expensive and must never run in a per-packet UI hot path.
 */
public final class Passwords {
    /**
     * OWASP's PBKDF2-HMAC-SHA256 work factor.
     */
    private static final int ITERATIONS = 600_000;
    /**
     * Random source used only for password salts.
     */
    private static final SecureRandom RANDOM = new SecureRandom();
    /**
     * Fixed valid hash used for missing-account timing work.
     */
    private static final String DUMMY = "pbkdf2-sha256$600000$AAAAAAAAAAAAAAAAAAAAAA==$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    /**
     * Prevents instances of this password utility.
     */
    private Passwords() { }

    /**
     * Hashes a password with a fresh 128-bit salt.
     * @param password nonempty password, at most 1024 characters
     * @return self-describing password hash
     */
    public static String hash(String password) {
        /*
         * Independent salts prevent equal passwords from sharing a stored representation.
         */
        if (password == null || password.isEmpty() || password.length() > 1024) throw new IllegalArgumentException("Password must contain 1 to 1024 characters");
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        Base64.Encoder encoder = Base64.getEncoder();
        return "pbkdf2-sha256$" + ITERATIONS + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(derive(password, salt, ITERATIONS));
    }

    /**
     * Compares a password against a stored hash using a constant-time digest comparison.
     * @param password supplied password
     * @param encoded stored hash, or null for missing-account timing work
     * @return whether the supplied password matches
     */
    public static boolean matches(String password, String encoded) {
        /*
         * Bound hash parameters before allocating or deriving; corrupt storage cannot request
         * arbitrary iteration counts or unbounded salts.
         */
        if (password == null || password.length() > 1024) return false;
        boolean missing = encoded == null;
        String[] parts = (missing ? DUMMY : encoded).split("\\$", -1);
        if (parts.length != 4 || !parts[0].equals("pbkdf2-sha256")) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 10_000 || iterations > 2_000_000 || parts[2].length() > 64 || parts[3].length() > 64) return false;
            byte[] salt = Base64.getDecoder()
                    .decode(parts[2]);
            byte[] expected = Base64.getDecoder()
                    .decode(parts[3]);
            if (salt.length != 16 || expected.length != 32) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations)) && !missing;
        } catch (IllegalArgumentException invalidHash) {
            return false;
        }
    }

    /**
     * Derives a 256-bit digest and clears temporary password characters.
     * @param password supplied secret
     * @param salt stored or newly generated salt
     * @param iterations bounded work factor
     * @return derived digest
     */
    private static byte[] derive(String password, byte[] salt, int iterations) {
        /*
         * PBEKeySpec copies its input, so both owned character arrays are cleared in finally.
         */
        char[] characters = password.toCharArray();
        PBEKeySpec specification = new PBEKeySpec(characters, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification)
                    .getEncoded();
        } catch (GeneralSecurityException unavailableAlgorithm) {
            throw new IllegalStateException("The JDK must provide PBKDF2WithHmacSHA256", unavailableAlgorithm);
        } finally {
            specification.clearPassword();
            Arrays.fill(characters, '\0');
        }
    }
}
