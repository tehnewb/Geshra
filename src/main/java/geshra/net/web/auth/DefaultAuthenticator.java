package geshra.net.web.auth;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * In-memory users indexed by case-insensitive usernames, with salted PBKDF2 password hashes.
 * Applications can replace this Spring bean with their own Authenticator. Storage lasts for this
 * instance's lifetime; no account is created automatically.
 */
@Component
public class DefaultAuthenticator implements Authenticator {

    /**
     * Authentication result when the normalized username has no stored user.
     */
    public static final byte DOES_NOT_EXIST = 0;
    /**
     * Authentication result when the stored password matches.
     */
    public static final byte SUCCESS = 1;
    /**
     * Authentication result when the supplied password does not match.
     */
    public static final byte INVALID_PASSWORD = 2;

    /**
     * Maximum number of distinct usernames accepted by store.
     */
    private static final int MAX_USERS = 1_000_000;
    private final ConcurrentMap<String, User> users = new ConcurrentHashMap<>(); // Concurrent users indexed by lowercase usernames.

    /**
     * Creates an empty in-memory user store.
     */
    public DefaultAuthenticator() {

    }

    /**
     * Looks up a case-insensitive username and compares its stored password attribute with the supplied password.
     *
     * @param username case-insensitive username
     * @param password password to compare against the stored attribute
     * @return the resulting authenticate value
     */
    public AuthenticationResult authenticate(String username, String password) {
        User user = username == null ? null : users.get(username.toLowerCase(Locale.ROOT));
        if (Passwords.matches(password, user == null ? null : user.get("passwordHash", String.class, null)))
            return new AuthenticationResult(user, SUCCESS);
        return new AuthenticationResult(null, user == null ? DOES_NOT_EXIST : INVALID_PASSWORD);
    }

    /**
     * Stores or replaces a user by its normalized username. Empty usernames are ignored and new users
     * are rejected at the capacity limit.
     *
     * @param user user with username and password attributes
     */
    public synchronized void store(User user) {
        String username = user.get("username", String.class, "")
                .toLowerCase(Locale.ROOT);
        if (username.isEmpty())
            return;
        String password = user.get("password", String.class, null);
        if (password != null) {
            user.attribute("passwordHash", Passwords.hash(password));
            user.attribute("password", null);
        }
        if (user.get("passwordHash", String.class, null) == null) throw new IllegalArgumentException("A password or passwordHash is required");

        if (!users.containsKey(username) && users.size() >= MAX_USERS) {
            throw new IllegalStateException("Maximum number of users reached");
        }
        users.put(username, user);
    }

    /**
     * Registers or replaces an in-memory user without retaining the plain-text password.
     * @param username case-insensitive username
     * @param password password to hash
     * @param roles optional application roles
     * @return stored user
     */
    public User register(String username, String password, String... roles) {
        if (username == null || username.isBlank() || username.length() > 128) throw new IllegalArgumentException("Username must contain 1 to 128 characters");
        User user = new User()
                .attribute("username", username)
                .attribute("passwordHash", Passwords.hash(password))
                .roles(roles);
        store(user);
        return user;
    }

    /**
     * Returns the in-memory user map. Use standard map operations to inspect or remove users.
     *
     * @return the concurrent map of normalized usernames to users
     */
    public ConcurrentMap<String, User> getDatabase() {
        return users;
    }

    /**
     * Reports whether a case-insensitive username is present in this in-memory store.
     *
     * @param username case-insensitive username
     * @return the resulting exists value
     */
    public boolean exists(String username) {
        return users.containsKey(username.toLowerCase(Locale.ROOT));
    }
}
