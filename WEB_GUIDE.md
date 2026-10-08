# SEO, authentication, cookies, and sessions

These features work automatically when the library is a Spring dependency. Define your application routes and optional Authenticator bean; the framework handles HTTP responses, cookies, session lookup, and browser transport.

## SEO

Provide a SeoPage on a public route:

```java
private final SeoPage seo = new SeoPage("About our application")
        .description("A useful description of this page.")
        .content("The public content people should see before JavaScript runs.");

@Override
public SeoPage getSeo() { return seo; }
```

The initial HTTP response contains the title, description, canonical link, Open Graph tags, Twitter card tags, and the supplied public content. Client navigation updates managed metadata and removes the previous page's tags. Set your actual public origin to generate default canonical URLs and the sitemap:

```properties
geshra.web.public-url=https://example.com
```

`/sitemap.xml` includes public routes. `/robots.txt` advertises that sitemap. With no public URL configured, automatic canonical links are omitted and the sitemap is empty; the framework does not invent a domain from forwarded headers.

To customize either file completely, provide `web/robots.txt` or `web/sitemap.xml` in your application's resources.

Optional features:

```java
new SeoPage("Product details")
        .description("Product description.")
        .canonical("https://example.com/products/item")
        .image("https://example.com/images/item.png")
        .language("en")
        .structuredData(Map.of(
            "@context", "https://schema.org",
            "@type", "WebPage",
            "name", "Product details"
        ))
        .contentHtml("<main><h1>Product details</h1><a href=\"/products\">Products</a></main>");
```

`content(text)` safely escapes text. `contentHtml(html)` is explicitly trusted application HTML. It supplies a small initial representation without creating a full session UI tree for every crawler. Interactive Java components still render over WebSocket; their complete trees are not automatically converted into initial HTML. Keep initial content consistent with what visitors see.

For a dynamic route, override `getSeo(String path)` and `getSitemapPaths()`. The metadata path is normalized without surrounding slashes. Sitemap paths should enumerate real public pages rather than placeholder parameter patterns. Sitemaps are limited to 50,000 URLs.

`new SeoPage("Sign in").noIndex()` excludes a page from indexing and the generated sitemap. Protected routes are automatically excluded and marked noindex, including for authenticated visitors. Unknown paths return HTTP 404, anonymous protected requests return 401, and users with the wrong role receive 403. Static assets continue through the existing resource handler.

Packaged HTML shells are loaded once; filesystem shells stay live for development. Page responses use no-store so personalized or changing metadata is not shared accidentally. See [Google's JavaScript SEO guidance](https://developers.google.com/search/docs/crawling-indexing/javascript/javascript-seo-basics) for initial HTML, metadata, crawlable links, and status-code behavior.

## Authentication

The default in-memory authenticator starts with no accounts. Register users through the Spring bean:

```java
users.register("alice", password, "member", "editor");
```

Passwords are stored as salted PBKDF2-HMAC-SHA256 hashes with 600,000 iterations. `store(user)` remains available: it hashes a legacy password attribute and removes the plain-text value. Use register/store for credential changes; direct map insertion does not hash passwords.

From a Java UI callback:

```java
SessionContext session = SessionContext.get();
session.login(username.getValue(), passwordField.getValue(), result -> {
    if (result.isSuccess()) session.navigate("/account");
    else errorMessage.setText("Username or password was not accepted");
});
```

Login is asynchronous. Password verification uses two dedicated workers with a bounded queue, and each session permits ten attempts per minute. The browser performs a same-origin HTTP action; the framework verifies credentials, rotates its HttpOnly cookie, removes the old token, and runs the Java callback on the session dispatch thread after browser receipt of the cookie. Successful identity changes dispose the previous UI immediately; build new UI or navigate in the success callback. Failed login preserves the current UI.

`session.login(username, password)` without a callback reloads the current route after success. `session.logout()` clears session attributes and protected UI, rotates the token again, and returns to the home route.

For a database or identity provider, supply your own Spring bean:

```java
@Bean
public Authenticator authenticator() {
    return (username, password) -> {
        User user = verifyAgainstYourAccountStore(username, password);
        return new AuthenticationResult(user, user == null ? 0 : 1);
    };
}
```

`AuthenticationResult.isSuccess()` means a user was returned. The framework never accepts a browser-provided user, role, or authentication decision. The default account store and sessions are process-local; use an application Authenticator for persistent accounts. External identity-provider redirects, account recovery, and multi-factor enrollment are application integrations.

The password work factor follows [OWASP's password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

## Protected routes and roles

Require any logged-in user:

```java
@Override
public boolean requiresAuthentication() { return true; }
```

Or require any of the listed roles:

```java
@Override
public Collection<String> getAllowedRoles() { return List.of("admin", "editor"); }
```

Routes default to public. HTTP and WebSocket route loading use the same authorization check. Users support an immutable role set and the previous single role attribute:

```java
User user = session.getUser();
boolean signedIn = session.isAuthenticated();
boolean editor = session.hasRole("editor");
String username = user.getUsername();
```

Only access the user after confirming authentication. Route checks protect route entry; application services must also authorize their own operations.

## Session data

Use the existing typed storage without declaring a new session manager:

```java
SessionContext session = SessionContext.get();
session.attribute("cart", cart);
ShoppingCart saved = session.get("cart", ShoppingCart.class);
int visits = session.get("visits", Integer.class, 0);
session.attribute("temporary", null); // removes the attribute
```

Sessions survive page reloads and temporary reconnects until their inactivity timeout. `getCreatedAt()` and `getExpiresAt()` return epoch milliseconds. A browser heartbeat keeps the connected session active and renews its cookie before expiry.

The current session architecture owns one active WebSocket/UI per cookie. Opening another connection with the same cookie replaces the previous connection. Session UI mutations belong to that connection's dispatch thread; do not mutate UI directly from arbitrary background threads.

```properties
geshra.web.session-timeout=30m
geshra.web.session-cookie-name=sessionID
geshra.web.secure-cookies=true
geshra.web.public-url=https://example.com
```

Session cookies use HttpOnly, Path=/, SameSite=Lax, and Secure for configured HTTPS origins or direct TLS. Configure the public URL to match the actual deployment origin, including a nonstandard port when applicable. Reverse-proxy forwarded headers are not automatically trusted.

Same-origin checks cover browser session actions and WebSocket upgrades. State-changing HTTP actions also require a per-session CSRF token. Session tokens are rotated on authentication changes, expired sessions cannot be resurrected by late password work, and shutdown releases sessions and queued authentication requests. These choices follow [OWASP's session management guidance](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Cookies

Use real HTTP cookies from Java:

```java
session.setCookie(new Cookie("theme", "dark")
        .maxAge(Duration.ofDays(30)));
session.setCookie(new Cookie("privatePreference", "value")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict"));
```

The browser retrieves a validated Set-Cookie response. HttpOnly works through HTTP and cannot be enabled by document.cookie. Cookie writes are asynchronous and coalesced. After the response completes, `session.cookie("theme")` reflects the requested write; the next browser request refreshes the snapshot with the cookies the browser actually sends.

`session.removeCookie("theme")` deletes a root-scoped cookie. For another path or domain, delete with the same scope:

```java
session.setCookie(new Cookie("preference", "")
        .path("/account")
        .delete());
```

Cookie values must satisfy the native cookie format; use compact scalar values or encode complex data yourself. Headers are capped at 4,096 bytes and pending writes at 64 cookies. Invalid names, header injection, invalid SameSite values, insecure SameSite=None, and invalid cookie prefix combinations are rejected. The session cookie name is reserved.

The older `Cookie.encode()` API remains for script-readable cookies, and rejects HttpOnly to avoid implying protection it cannot provide.

## Browser helpers and example

The runtime exposes promise-based helpers for applications that also use JavaScript:

```javascript
const state = await window.geshra.session();
const result = await window.geshra.login(username, password);
if (result.success) { /* the server has verified the login */ }
await window.geshra.logout();
```

The helpers manage CSRF, serialized actions, cookie acknowledgment, and errors. They never expose the session cookie.

The [example application](examples/spring-app) includes `/login` and protected `/account` routes. It creates a demo user only when given an explicit `demo.password` setting. After building it, run:

```sh
java -jar examples/spring-app/build/libs/geshra-spring-example.jar --demo.password=your-test-password
```

Sign in as `demo` with that supplied password. The account page demonstrates typed session data, a server-readable HttpOnly preference cookie, and logout.

Java tests cover HTTP metadata, escaping, authorization, CSRF, cookie validation, password storage, token rotation, and invalidation during password verification. Chromium and Firefox checks cover actual HttpOnly enforcement, Java login callbacks, cookie writes, page reload/session resume, logout, and SEO content with JavaScript disabled.

For browser tests, launch the example with `--demo.password=browser-test-password`, then follow the development setup in [UI_GUIDE.md](UI_GUIDE.md). Set `DEMO_PASSWORD` when using another test password.
