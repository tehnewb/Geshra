package geshra.net.web;

import geshra.net.web.ui.UI;
import geshra.net.web.ui.BrowserJson;
import geshra.net.web.auth.User;
import geshra.net.web.auth.AuthenticationResult;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.util.AttributeKey;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Maintains all server-side state for a single browser session.
 * <p>
 * A session is intentionally separated from a WebSocket channel. The WebSocket channel is only the
 * current live transport, while the session token is the browser identity that can survive a page
 * refresh or a temporary reconnect. When a channel disconnects, the session is detached rather than
 * immediately destroyed, which allows the next WebSocket handshake carrying the same cookie token to
 * resume the same {@link UI} instance.
 * <p>
 * Sessions are held in two registries:
 * <ul>
 *     <li>{@code SESSIONS_BY_CHANNEL}: currently connected WebSocket channels.</li>
 *     <li>{@code SESSIONS_BY_TOKEN}: resumable sessions keyed by secure random session tokens.</li>
 * </ul>
 * <p>
 * Disconnected sessions are automatically removed after {@link #SESSION_TIMEOUT}. This prevents
 * stale browser cookies or abandoned tabs from leaking server memory forever.
 *
 * <pre>{@code
 * SessionContext session = SessionContext.get(ctx.channel());
 * SessionContext.set(session);
 * try {
 *     session.handleRoute("home");
 * } finally {
 *     SessionContext.clear();
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class SessionContext {
    /**
     * Reports authentication implementation failures without credentials or bearer tokens.
     */
    private static final Logger LOG = LoggerFactory.getLogger(SessionContext.class);

    /**
     * The amount of time a disconnected or inactive session is allowed to remain resumable.
     */
    public static final Duration SESSION_TIMEOUT = Duration.ofHours(8);
    /**
     * Fixed timeout conversion, shared by hot expiry checks and periodic cleanup.
     */
    private static final long SESSION_TIMEOUT_MILLIS = SESSION_TIMEOUT.toMillis();

    /**
     * The Netty channel attribute used to store the server-side session token.
     */
    public static final AttributeKey<String> SESSION_ID = AttributeKey.valueOf("sessionID");

    /**
     * Stores the current session for code paths that need session-scoped access without passing the session manually.
     */
    private static final ThreadLocal<SessionContext> CURRENT = new ThreadLocal<>();

    /**
     * Tracks currently connected sessions by their active Netty channel.
     */
    private static final Map<Channel, SessionContext> SESSIONS_BY_CHANNEL = new ConcurrentHashMap<>();
    /**
     * Allocation-free live diagnostics view of currently connected sessions.
     */
    private static final Map<Channel, SessionContext> CONNECTED_VIEW = Collections.unmodifiableMap(SESSIONS_BY_CHANNEL);

    /**
     * Tracks resumable sessions by their secure random browser cookie token.
     */
    private static final Map<String, SessionContext> SESSIONS_BY_TOKEN = new ConcurrentHashMap<>();

    /**
     * Periodically removes expired detached sessions so abandoned cookies do not leak server memory.
     */
    private static final ScheduledExecutorService CLEANER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SessionContext-Cleaner");
        thread.setDaemon(true);
        return thread;
    });

    static {
        CLEANER.scheduleAtFixedRate(SessionContext::cleanupExpiredSessions, 1, 1, TimeUnit.MINUTES);
    }

    private final Map<String, Object> attributes; // Stores application-level values attached to this session.

    private volatile String sessionID; // Secure random browser token, rotated on authentication changes.
    private volatile String csrfToken = SessionTokens.create(); // Browser anti-forgery secret, independent of the cookie.
    private final long timeoutMillis; // Configured inactivity lifetime.
    private final String cookieName; // Reserved session cookie name.
    private final String publicUrl; // Configured public origin for default canonical URLs.
    private final long createdAt = System.currentTimeMillis(); // Session creation timestamp.
    private volatile Map<String, String> cookies = Map.of(); // Latest browser cookie snapshot.
    private ArrayList<String> pendingCookies; // Lazily allocated validated Set-Cookie headers.
    private boolean cookieFetchPending; // Coalesces queued cookies into one HTTP request.
    private Consumer<AuthenticationResult> loginCallback; // Optional one-shot Java login callback.
    private AuthenticationResult authenticationResult; // Server-computed result; never trusted from browser input.
    private boolean authenticationCompleted; // Whether an HTTP authentication action awaits acknowledgment.
    private boolean logoutCompleted; // Whether the acknowledgment must clear all protected UI.
    private int authenticationNonce; // Correlates the verified HTTP result with its browser cookie acknowledgment.
    private long loginWindow; // Start of the per-session attempt window.
    private int loginAttempts; // Attempt count within the current minute.

    private final UI ui; // UI tree and component state associated with this browser session.

    private final RouteRegistry routeRegistry; // Route registry used to render and navigate this session's UI.

    private volatile long lastAccessedAt; // Last moment this session was used by a handshake, packet, or server-side send.

    private volatile Channel channel; // Current active WebSocket channel, or null while the session is detached/disconnected.
    private volatile Channel serverChannel; // Accepting server channel retained while detached for precise lifecycle cleanup.
    private volatile boolean invalidated; // Prevents expired or shut-down sessions from being revived by late authentication.

    /**
     * Creates a new resumable browser session.
     * <p>
     * The provided {@code sessionID} must already be generated by a cryptographically secure token
     * generator. This constructor does not register the session globally; callers must use
     * {@link #register(Channel, SessionContext)} so the session maps and channel attributes stay
     * consistent.
     *
     * @param routeRegistry registry used to resolve routes for this session
     * @param sessionID secure random session token stored in the browser cookie
     * @param channel current WebSocket channel for the browser connection
     */
    public SessionContext(RouteRegistry routeRegistry, String sessionID, Channel channel) {
        this(routeRegistry, sessionID, channel, new SessionPolicy());
    }

    /**
     * Creates a session with a validated server policy.
     * @param routeRegistry route index
     * @param sessionID opaque token
     * @param channel current WebSocket channel or null
     * @param policy server session policy
     */
    public SessionContext(RouteRegistry routeRegistry, String sessionID, Channel channel, SessionPolicy policy) {
        this.routeRegistry = Objects.requireNonNull(routeRegistry, "routeRegistry");
        this.sessionID = Objects.requireNonNull(sessionID, "sessionID");
        this.channel = channel;
        this.serverChannel = channel == null ? null : channel.parent();
        this.ui = new UI();
        this.attributes = new ConcurrentHashMap<>();
        this.timeoutMillis = policy.timeout()
                .toMillis();
        this.cookieName = policy.cookieName();
        this.publicUrl = policy.publicUrl();
        touch();
    }

    /**
     * Returns the current authenticated user, or null.
     * @return authenticated user
     */
    public User getUser() { return get("user", User.class, null); }

    /**
     * Reports whether this session has an authenticated user.
     * @return authenticated state
     */
    public boolean isAuthenticated() { return getUser() != null; }

    /**
     * Checks a named role on the current user.
     * @param role application role
     * @return whether access is granted
     */
    public boolean hasRole(String role) { User user = getUser(); return user != null && user.hasRole(role); }

    /**
     * Reads a cookie from the most recent browser request or completed cookie write.
     * @param name cookie name
     * @return value or null
     */
    public String cookie(String name) { return cookies.get(name); }

    /**
     * Queues a real HTTP Set-Cookie response, including HttpOnly when requested.
     * @param cookie application cookie; the session cookie name is reserved
     */
    public synchronized void setCookie(Cookie cookie) {
        if (cookieName.equals(cookie.getName())) throw new IllegalArgumentException("The session cookie is managed by the framework");
        String header = cookie.toHeader();
        if (pendingCookies == null) pendingCookies = new ArrayList<>(2);
        if (pendingCookies.size() >= 64) throw new IllegalStateException("Too many pending cookies");
        pendingCookies.add(header);
        if (!cookieFetchPending) { cookieFetchPending = true; browserAction(Map.of("action", "cookies")); }
    }

    /**
     * Deletes an application cookie scoped to the root path.
     * @param name cookie name
     */
    public void removeCookie(String name) { setCookie(new Cookie(name, "").delete()); }

    /**
     * Starts an asynchronous browser-backed login and reports the server-verified result.
     * @param username account username
     * @param password supplied password
     * @param callback callback on the session dispatch thread; navigate or build UI on success
     */
    public synchronized void login(String username, String password, Consumer<AuthenticationResult> callback) {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        if (username.length() > 128 || password.length() > 1024) throw new IllegalArgumentException("Credentials exceed their supported length");
        if (channel == null || !channel.isActive()) throw new IllegalStateException("Login requires a connected browser");
        if (loginCallback != null) throw new IllegalStateException("A login request is already pending");
        loginCallback = Objects.requireNonNull(callback, "callback");
        browserAction(Map.of("action", "login", "username", Objects.requireNonNull(username), "password", Objects.requireNonNull(password)));
    }

    /**
     * Logs in and reloads the current route after successful authentication.
     * @param username account username
     * @param password supplied password
     */
    public void login(String username, String password) { login(username, password, result -> { if (result.isSuccess()) reload(); }); }

    /**
     * Requests logout, cookie rotation, removal of session data, and navigation to the home route.
     */
    public void logout() { browserAction(Map.of("action", "logout")); }

    /**
     * Reloads the current application route.
     */
    public void reload() { routeRegistry.handleRoute(get("routePath", String.class, "/"), ui); }

    /**
     * Returns the inactivity expiration instant in epoch milliseconds.
     * @return current inactivity deadline
     */
    public long getExpiresAt() { return lastAccessedAt + timeoutMillis; }

    /**
     * Returns the creation instant in epoch milliseconds.
     * @return creation timestamp
     */
    public long getCreatedAt() { return createdAt; }

    /**
     * Returns the current anti-forgery token for same-origin HTTP actions.
     * @return CSRF token
     */
    String csrfToken() { return csrfToken; }

    /**
     * Returns the explicitly configured public origin.
     * @return public origin, possibly empty
     */
    public String getPublicUrl() { return publicUrl; }

    /**
     * Returns the browser cookie lifetime for periodic renewal.
     * @return cookie lifetime in seconds
     */
    public long getCookieMaxAge() { return timeoutMillis / 1000; }

    /**
     * Resends pending cookie work after a temporarily disconnected browser reconnects.
     */
    synchronized void flushCookies() {
        if (pendingCookies != null && !pendingCookies.isEmpty()) {
            cookieFetchPending = true;
            browserAction(Map.of("action", "cookies"));
        }
    }

    /**
     * Restricts HTTP session lookup to the accepting listener.
     * @param owner accepting server channel
     * @return matching listener
     */
    boolean belongsTo(Channel owner) { return serverChannel == owner; }

    /**
     * Logs a custom authenticator's exception without silently discarding it.
     * @param failure authenticator exception
     */
    void authenticationFailure(RuntimeException failure) { LOG.error("Application authenticator failed", failure); }

    /**
     * Replaces the latest immutable cookie snapshot.
     * @param values decoded browser cookies
     */
    void cookies(Map<String, String> values) { cookies = Map.copyOf(values); }

    /**
     * Transfers pending HTTP cookie headers to their response.
     * @return queued headers
     */
    synchronized List<String> takeCookies() {
        cookieFetchPending = false;
        if (pendingCookies == null || pendingCookies.isEmpty()) return List.of();
        List<String> result = List.copyOf(pendingCookies);
        Map<String, String> updated = new HashMap<>(cookies);
        for (String header : result) {
            int equals = header.indexOf('=');
            int end = header.indexOf(';');
            String name = header.substring(0, equals);
            if (header.contains("Max-Age=0")) updated.remove(name);
            else updated.put(name, header.substring(equals + 1, end < 0 ? header.length() : end));
        }
        cookies = Map.copyOf(updated);
        pendingCookies.clear();
        return result;
    }

    /**
     * Limits repeated password derivations for one browser session.
     * @return whether another login attempt is allowed
     */
    synchronized boolean allowLogin() {
        long now = System.currentTimeMillis();
        if (now - loginWindow >= 60_000) { loginWindow = now; loginAttempts = 0; }
        return loginAttempts++ < 10;
    }

    /**
     * Stores an HTTP-computed result for the server-side callback.
     * @param result server authentication result
     * @param logout whether session data must be cleared
     * @param nonce browser cookie acknowledgment identifier
     */
    synchronized void authenticated(AuthenticationResult result, boolean logout, int nonce) {
        if (logout) attributes.clear();
        if (logout || result.isSuccess()) {
            attribute("user", result.user());
            rotate(this);
            ui.clear();
        }
        authenticationResult = result;
        authenticationCompleted = true;
        logoutCompleted = logout;
        authenticationNonce = nonce;
    }

    /**
     * Completes a Java callback using only the result recorded by the HTTP server.
     * @param nonce browser acknowledgment matching the completed HTTP action
     */
    public void completeAuthentication(int nonce) {
        Consumer<AuthenticationResult> callback;
        AuthenticationResult result;
        boolean logout;
        synchronized (this) {
            if (!authenticationCompleted || nonce != authenticationNonce) return;
            callback = loginCallback;
            result = authenticationResult;
            logout = logoutCompleted;
            loginCallback = null;
            authenticationResult = null;
            authenticationCompleted = false;
        }
        if (logout) { navigate("/"); }
        else {
            if (callback != null) callback.accept(result);
            else if (result.isSuccess()) reload();
        }
    }

    /**
     * Sends a typed runtime action after all preceding UI mutations.
     * @param action JSON-compatible runtime action
     */
    private void browserAction(Map<String, Object> action) {
        ui.flushUpdates();
        send(TextPacketEncoder.encode(channel, 6, BrowserJson.encode(action)));
    }

    /**
     * Registers a session before a browser has opened its WebSocket.
     * @param session detached session
     * @param owner accepting server channel
     */
    static synchronized void registerDetached(SessionContext session, Channel owner) {
        /*
         * Owner tracking lets server shutdown release HTTP-created sessions as well as live sockets.
         */
        session.serverChannel = owner;
        SESSIONS_BY_TOKEN.put(session.sessionID, session);
    }

    /**
     * Rotates both secrets and removes the old bearer token immediately.
     * @param session session changing authentication state
     */
    private static synchronized void rotate(SessionContext session) {
        /*
         * One index transition prevents the old token from resuming an authenticated session.
         */
        SESSIONS_BY_TOKEN.remove(session.sessionID, session);
        session.sessionID = SessionTokens.create();
        session.csrfToken = SessionTokens.create();
        Map<String, String> updated = new HashMap<>(session.cookies);
        updated.put(session.cookieName, session.sessionID);
        session.cookies = Map.copyOf(updated);
        SESSIONS_BY_TOKEN.put(session.sessionID, session);
        if (session.channel != null) session.channel.attr(SESSION_ID)
                .set(session.sessionID);
    }

    /**
     * Stores or replaces a session-scoped attribute.
     * <p>
     * Attribute values are useful for application state that should survive a page refresh but should
     * not be global to every user. Passing a {@code null} value removes the key because the backing
     * map is concurrent and does not support null values.
     *
     * @param key attribute key to store
     * @param object attribute value to store, or {@code null} to remove the key
     * @return this session for chaining
     */
    public SessionContext attribute(String key, Object object) {
        Objects.requireNonNull(key, "key");
        if (object == null) {
            this.attributes.remove(key);
        } else {
            this.attributes.put(key, object);
        }
        touch();
        return this;
    }

    /**
     * Stores all non-null values from the provided map into this session.
     * <p>
     * Values already present under the same keys are replaced. Null values are treated as removals so
     * callers can clear entries while still using this bulk update method.
     *
     * @param attributes attributes to merge into this session
     * @return this session for chaining
     */
    public SessionContext attributes(Map<String, Object> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            attribute(entry.getKey(), entry.getValue());
        }
        touch();
        return this;
    }

    /**
     * Reads a typed attribute from the session.
     * <p>
     * If the key is not present, {@code defaultValue} is returned. If the key is present but cannot be
     * cast to {@code clazz}, the normal {@link ClassCastException} from {@link Class#cast(Object)} is
     * allowed to surface because that indicates incorrect application usage.
     *
     * @param key attribute key to read
     * @param clazz expected value type
     * @param defaultValue fallback value when the key does not exist
     * @param <A> expected value type
     * @return stored value or {@code defaultValue}
     */
    public <A> A get(String key, Class<A> clazz, A defaultValue) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(clazz, "clazz");
        Object value = this.attributes.get(key);
        touch();
        return value == null ? defaultValue : clazz.cast(value);
    }

    /**
     * Reads a typed attribute from the session.
     * <p>
     * This overload returns {@code null} when the key does not exist. Use
     * {@link #get(String, Class, Object)} when a fallback value is required.
     *
     * @param key attribute key to read
     * @param clazz expected value type
     * @param <A> expected value type
     * @return stored value, or {@code null} when absent
     */
    public <A> A get(String key, Class<A> clazz) {
        return get(key, clazz, null);
    }

    /**
     * Returns a read-only view of the session attributes.
     * <p>
     * The returned map reflects live changes but cannot be modified directly. Use
     * {@link #attribute(String, Object)} or {@link #attributes(Map)} to mutate session state.
     *
     * @return unmodifiable live view of this session's attributes
     */
    public Map<String, Object> attributes() {
        touch();
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Executes a callback using the current thread-local session.
     * <p>
     * The caller's binding is restored after the callback. HTTP and WebSocket dispatch boundaries
     * own final cleanup, so nested navigation does not remove its caller's active session.
     *
     * @param consumer code to execute against the current session
     * @throws IllegalStateException when no session has been attached to the current thread
     */
    public static void access(Consumer<SessionContext> consumer) {
        /*
         * Bind the active session for the callback and restore prior thread-local state in the cleanup path.
         */
        Objects.requireNonNull(consumer, "consumer");
        SessionContext sessionContext = get();
        if (sessionContext == null) {
            throw new IllegalStateException("No session context is available for this thread.");
        }

        try {
            sessionContext.touch();
            consumer.accept(sessionContext);
        } finally {
            SessionContext.set(sessionContext);
        }
    }

    /**
     * Registers or rebinds a session to a WebSocket channel.
     * <p>
     * If the same session was already attached to another live channel, that old channel is closed.
     * This avoids two browser tabs or a stale reconnect racing over one mutable server-side UI tree.
     *
     * @param channel active WebSocket channel
     * @param session session to bind to the channel
     */
    public static synchronized void register(Channel channel, SessionContext session) {
        /*
         * Use concurrent indices so the session can be found consistently by its channel and session identifier.
         */
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(session, "session");

        Channel oldChannel = session.channel;
        if (oldChannel != null && oldChannel != channel) {
            SESSIONS_BY_CHANNEL.remove(oldChannel, session);
            oldChannel.attr(SESSION_ID)
                    .set(null);
            if (oldChannel.isOpen()) {
                oldChannel.close();
            }
        }

        session.channel = channel;
        session.serverChannel = channel.parent();
        session.touch();
        SESSIONS_BY_CHANNEL.put(channel, session);
        SESSIONS_BY_TOKEN.put(session.sessionID, session);
        channel.attr(SESSION_ID)
                .set(session.sessionID);
    }

    /**
     * Detaches a channel from its session without immediately destroying the session.
     * <p>
     * This is intentionally different from invalidation. A disconnected browser may reconnect with
     * the same cookie token and resume the existing UI state until the session timeout expires.
     *
     * @param channel channel that became inactive
     */
    public static synchronized void unregister(Channel channel) {
        /*
         * Remove the channel binding and session index together before lifecycle cleanup.
         */
        if (channel == null) {
            return;
        }

        SessionContext session = SESSIONS_BY_CHANNEL.remove(channel);
        channel.attr(SESSION_ID)
                .set(null);

        if (session != null && session.channel == channel) {
            session.channel = null;
            session.touch();
        }
    }

    /**
     * Permanently removes a session from both active and resumable registries.
     * <p>
     * Use this for explicit logout or forced server-side invalidation. Unlike
     * {@link #unregister(Channel)}, this method prevents future cookie-based resume.
     *
     * @param session session to invalidate
     */
    public static synchronized void invalidate(SessionContext session) {
        /*
         * Locate the indexed session and let unregister perform the associated cleanup.
         */
        if (session == null) {
            return;
        }
        session.invalidated = true;
        session.attributes.clear();

        Channel activeChannel = session.channel;
        if (activeChannel != null) {
            SESSIONS_BY_CHANNEL.remove(activeChannel, session);
            activeChannel.attr(SESSION_ID)
                    .set(null);
            if (activeChannel.isOpen()) {
                activeChannel.close();
            }
        }

        session.channel = null;
        SESSIONS_BY_TOKEN.remove(session.sessionID, session);
        session.serverChannel = null;
    }

    /**
     * Releases active and detached sessions belonging to one stopped listener, including when
     * several listeners share the same route registry.
     * @param owner accepting server channel
     */
    static void invalidateSessions(Channel owner) {
        /*
         * Shutdown is a cold boundary: remove retained UI trees immediately rather than waiting
         * for the inactivity cleaner, while leaving other servers' sessions untouched.
         */
        if (owner == null) return;
        for (SessionContext session : SESSIONS_BY_TOKEN.values()) {
            if (session.serverChannel == owner) invalidate(session);
        }
    }

    /**
     * Retrieves the active session currently bound to a channel.
     *
     * @param channel WebSocket channel to look up
     * @return active session, or {@code null} when the channel is not registered
     */
    public static SessionContext get(Channel channel) {
        /*
         * Resolve the existing session or test response without retaining an additional global context.
         */
        return SESSIONS_BY_CHANNEL.get(channel);
    }

    /**
     * Retrieves a resumable session by its secure random token.
     * <p>
     * Expired sessions are removed and treated as missing.
     *
     * @param sessionID secure random token from the browser cookie
     * @return resumable session, or {@code null} when missing or expired
     */
    public static SessionContext getBySessionID(String sessionID) {
        /*
         * Read the shared index directly; a missing identifier remains an absent session.
         */
        if (sessionID == null) {
            return null;
        }

        SessionContext session = SESSIONS_BY_TOKEN.get(sessionID);
        if (session == null) {
            return null;
        }

        if (session.isExpired()) {
            invalidate(session);
            return null;
        }

        session.touch();
        return session;
    }

    /**
     * Returns currently connected sessions keyed by channel.
     * <p>
     * This does not include detached-but-resumable sessions. The returned map is read-only to prevent
     * callers from bypassing the registration and invalidation rules.
     *
     * @return read-only live view of active channel sessions
     */
    public static Map<Channel, SessionContext> all() {
        /*
         * Expose the concurrent session values as a read-only live view rather than copying every session.
         */
        return CONNECTED_VIEW;
    }

    /**
     * Removes every expired session from the resumable session registry.
     * <p>
     * This method is safe to call manually and is also called by a daemon cleaner thread.
     */
    public static void cleanupExpiredSessions() {
        /*
         * Compare all sessions against one timestamp and remove expired entries through the normal cleanup path.
         */
        long now = System.currentTimeMillis();
        for (SessionContext session : SESSIONS_BY_TOKEN.values()) {
            if (now - session.lastAccessedAt > session.timeoutMillis) {
                invalidate(session);
            }
        }
    }

    /**
     * Sets the session associated with the current thread.
     *
     * @param session session to expose through {@link #get()}
     */
    public static void set(SessionContext session) {
        /*
         * Bind only the calling thread; sessions on other event loops retain their own context.
         */
        CURRENT.set(session);
    }

    /**
     * Reads the session associated with the current thread.
     *
     * @return current thread-local session, or {@code null} when none is set
     */
    public static SessionContext get() {
        /*
         * Resolve the existing session or test response without retaining an additional global context.
         */
        return CURRENT.get();
    }

    /**
     * Clears the thread-local session associated with the current thread.
     */
    public static void clear() {
        /*
         * Remove the thread-local reference so an event-loop thread cannot retain a completed session.
         */
        CURRENT.remove();
    }

    /**
     * Sends a binary payload to this session's current WebSocket channel.
     * <p>
     * If the channel is closed, missing, or currently not writable, the buffer is released to avoid a
     * Netty reference-count leak. Callers should not use the buffer after passing it to this method.
     *
     * @param buf payload buffer to send
     */
    public void send(ByteBuf buf) {
        Objects.requireNonNull(buf, "buf");
        touch();

        Channel activeChannel = this.channel;
        if (activeChannel != null && activeChannel.isActive() && activeChannel.isWritable()) {
            BinaryWebSocketFrame frame = new BinaryWebSocketFrame(buf);
            if (ui.isUpdating()) {
                activeChannel.write(frame);
                ui.controlWriteQueued();
            } else activeChannel.writeAndFlush(frame);
        } else {
            buf.release();
        }
    }

    /**
     * Replaces the current WebSocket channel reference.
     * <p>
     * Prefer {@link #register(Channel, SessionContext)} for normal use because it updates the global
     * channel registry and channel attribute at the same time. This setter remains available for older
     * code paths that need to update the raw reference directly.
     *
     * @param channel channel to attach, or {@code null} to detach
     */
    public void setChannel(Channel channel) {
        this.channel = channel;
        touch();
    }

    /**
     * Marks the session as recently used.
     */
    public void touch() {
        this.lastAccessedAt = System.currentTimeMillis();
    }

    /**
     * Checks whether this session has exceeded the inactivity timeout.
     *
     * @return {@code true} when the session should be removed from the resumable registry
     */
    public boolean isExpired() {
        return invalidated || System.currentTimeMillis() - lastAccessedAt > timeoutMillis;
    }

    /**
     * Gets the secure random session token.
     *
     * @return secure session token stored in the browser cookie
     */
    public String getSessionID() {
        return sessionID;
    }

    /**
     * Gets the current active WebSocket channel.
     *
     * @return current channel, or {@code null} while disconnected
     */
    public Channel getChannel() {
        return channel;
    }

    /**
     * Gets the server-side UI tree for this session.
     *
     * @return UI owned by this session
     */
    public UI getUI() {
        touch();
        return ui;
    }

    /**
     * Renders a route into this session's UI.
     *
     * @param path route path requested by the browser
     */
    public void handleRoute(String path) {
        touch();
        this.routeRegistry.handleRoute(path, this.ui);
    }

    /**
     * Sends a navigation instruction to this session's browser.
     *
     * @param path route path or URL to navigate to
     */
    public void navigate(String path) {
        touch();
        this.routeRegistry.navigate(path);
    }
}
