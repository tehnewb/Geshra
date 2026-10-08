# Build a Spring Boot application with Geshra

Add Geshra to a Java 21 Spring Boot 3 application. The library registers the HTTP server, WebSocket packet handlers, route registry, and default authenticator through Spring Boot auto-configuration. Your application supplies its routes and services.

## Start with the example

The [Spring application example](examples/spring-app) contains the same Java application with Gradle and Maven builds. Before the first Central release, publish the checkout locally:

```sh
./gradlew clean build publishToMavenLocal
./gradlew -p examples/spring-app -PuseMavenLocal=true bootRun
```

On Windows use `.\gradlew.bat`. For Maven, run `mvn spring-boot:run` inside `examples/spring-app` after local publication. The example's default dependency version is `1.0.0-SNAPSHOT`; after releasing, set `releaseVersion` to the published version and use Maven Central.

Open `http://localhost:4040/`. A button on the example page updates its heading through a Java click listener. Stop the application to close its listener and Netty threads.

## Register routes

Implement `geshra.net.web.Route` and make the implementation a Spring bean in your application's component scan. The empty path (`""`) represents the home page. Use paths such as `"account"` for other pages. A path such as `/account/123` falls back to the `account` route if there is no exact registered route. Read `SessionContext.get().get("routePath", String.class, "")` inside the route to retrieve the normalized requested path.

```java
@org.springframework.stereotype.Component
public class HomeRoute implements geshra.net.web.Route {
    public String getPath() { return ""; }
    public java.util.Collection<String> getAllowedRoles() { return java.util.List.of(); }

    public void load(geshra.net.web.ui.UI ui) {
        ui.setTitle("My application");
        var heading = new geshra.net.web.ui.components.H1("Welcome");
        var button = new geshra.net.web.ui.components.Button("Say hello");
        button.addClickListener(event -> heading.setText("Hello"));
        ui.add(heading);
        ui.add(button);
    }
}
```

The UI tree belongs to a browser session. Create UI components in `load`, where the current session is available. Keep singleton services separate from session-specific components. Ordinary Spring constructor injection is available in your route class.

`getAllowedRoles()` returning an empty collection makes a route public. Restricted routes compare the current session user's `role` with the allowed role strings. Applications supply their own identity and authentication workflows. The built-in authenticator is an in-memory example; supply an `Authenticator` bean for application authentication.

`DefaultAuthenticator.getDatabase()` returns a standard `ConcurrentMap<String, User>`. Use map operations such as `get`, `put`, and `remove` when accessing the default user store directly.

## Handle events

The event API follows Valthorne's typed numeric routing model. `EventHandler<E>` replaces `EventListener<E>`, and explicit registration priorities replace `@EventPriority`. Higher priorities execute first; handlers with equal priority execute in registration order.

Component convenience methods still accept lambdas:

```java
button.addClickListener(event -> heading.setText("Clicked"));
button.addClickListener(10, event -> System.out.println("Runs before normal handlers"));
```

For a separate publisher, register a built-in descriptor:

```java
EventPublisher publisher = new EventPublisher();
EventHandler<ClickEvent> handler = event -> System.out.println(event.getComponent());
publisher.register(EventTypes.CLICK, 10, handler);
// When this owner no longer needs the handler:
publisher.unregister(EventTypes.CLICK, handler);
```

`event.consume()` stops the remaining handlers for that publication. Publication resets consumption, allowing sequential reuse of an event. Handler exceptions propagate directly. Registration changes affect later publications; an active publication finishes against its original handler snapshot.

Custom events call `super(yourEventType)` and use a unique `EventType<YourEvent>` ID. Construct their publisher with a type count larger than the highest route ID. Routing uses only the descriptor ID, so subscribing to a superclass does not subscribe to its subclasses. A single mutable event instance must not be published concurrently. The old automatic creation timestamp is removed.

## Add application styles and assets

Put assets under your application's `src/main/resources/web/`. The server checks the configured application resource location before the library's bundled fallback resources. To customize the HTML shell, create `web/index.html`:

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>My application</title>
    <link rel="stylesheet" href="/css/app.css">
    <script src="/js/runtime.js" defer></script>
</head>
<body id="0"></body>
</html>
```

The `body` ID and runtime script are required by the component protocol. The runtime connects to `/ws` on the page's host. You can override `web/js/runtime.js` as well, but the bundled version already matches the library's protocol.

Classpath assets are read through Spring resources, including from executable application JARs. A filesystem resource location is also supported:

```properties
geshra.web.static-location=file:./public/
```

Resources are read for each request, so filesystem edits are visible without a file watcher. Existing assets are served directly, including query strings such as `/css/app.css?v=2`. Unknown extensionless paths receive the HTML shell so Java routes can handle navigation. Missing asset files receive HTTP 404.

## Configure application startup

| Property | Default | Purpose |
| --- | --- | --- |
| `geshra.web.enabled` | `true` | Enable the library's web auto-configuration |
| `geshra.web.port` | `4040` | Netty HTTP and WebSocket port; `0` chooses an available port |
| `geshra.web.open-browser` | `false` | Open the default browser after startup |
| `geshra.web.static-location` | `classpath:/web/` | Application assets checked before bundled fallbacks |
| `geshra.web.static-cache-bytes` | `8388608` | Maximum retained packaged-asset payload bytes, including gzip; `0` disables caching |

The server implements Spring's `SmartLifecycle`: startup binds the port before the Spring context finishes starting, and closing the context releases the listener and event loops. A bind failure prevents startup. `WebServer.getPort()` returns the assigned port, which is useful when tests configure port zero.

No `@ComponentScan("geshra")`, scheduling annotation, database configuration, or custom Spring application type is needed. The library's starter contains no servlet or reactive server. If you add one to your application, configure it and Netty on separate ports.

Immutable JAR resources are cached across connections within the byte budget. Filesystem resources are not cached, so edits stay visible. Large resources stream instead of being read into one large byte array. The server releases its cache and owned active or detached sessions when it stops.

WebSocket input automatically batches UI mutations. For bulk updates already running on the session's dispatch thread, use nested-safe update groups:

```java
ui.beginUpdate();
try {
    heading.setText("Updated");
    button.setText("Continue");
} finally {
    ui.endUpdate();
}
```

The group preserves every mutation and sends them together in bounded packets. `executeJS` and navigation flush required DOM changes before sending their control message. DOM serialization uses pooled buffers; callers of `DOMUpdate.encode()` own and must release the returned buffer. Custom encoders extend `writeTo(ByteBuf)` to participate in batch serialization. See [PERFORMANCE.md](PERFORMANCE.md) for measurements and tuning.

The auto-configuration backs off when you supply your own `WebServer`, `RouteRegistry`, `PacketHandlerRegistry`, `Authenticator`, or concrete built-in packet-handler bean. Additional `PacketHandler` beans are collected into the registry; handler IDs must be unique.

## Package and distribute your application

Use the Spring Boot plugin in your application build:

```sh
./gradlew bootJar
java -jar build/libs/geshra-spring-example.jar
```

For the Maven example:

```sh
mvn package
java -jar target/geshra-spring-example-1.0.0-SNAPSHOT.jar
```

Your packaged application includes the library and its runtime resources. It can run from another directory without this repository. Publishing the library itself is covered in [PUBLISHING.md](PUBLISHING.md).
