# Geshra

[![Build and test](https://github.com/tehnewb/Geshra/actions/workflows/ci.yml/badge.svg)](https://github.com/tehnewb/Geshra/actions/workflows/ci.yml)

A Java 21 library for Spring Boot applications. Add the dependency, define your routes as Spring beans, and run your own Spring application. Spring Boot automatically starts the Netty HTTP and WebSocket server and stops it when the application closes. The browser runtime is included in the library JAR.

The library includes Java UI components, typed events, routing, sessions, authentication hooks, and Netty HTTP/WebSocket serving. Applications own their pages, styles, services, and data. Component ID reuse and the default authenticator use standard Java collections.

Shared browser resources remain in `src/main/resources/web`: the HTML shell, runtime, chat and grid styles, chart and grid scripts, and generic demo resources. The library packages these assets and serves them from the classpath. Optional Highcharts assets retain their separate vendor licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Add the dependency

The release coordinates below become available after the first Maven Central release. This checkout builds `1.0.0-SNAPSHOT` by default; see the local development instructions below to try it before publication.

### Gradle

In a Java 21 Spring Boot 3 application:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.albertbeaupre:geshra:1.0.0")
}
```

### Maven

In a Java 21 Spring Boot 3 application:

```xml
<dependency>
    <groupId>io.github.albertbeaupre</groupId>
    <artifactId>geshra</artifactId>
    <version>1.0.0</version>
</dependency>
```

The dependency includes the Spring Boot starter. No library package scanning, library entry point, or separate browser build is required. Use your application's Spring Boot plugin to run it or package an executable application JAR.

## Run a Spring application

```java
package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

Create a route in the same application package:

```java
package com.example.demo;

import geshra.net.web.Route;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.H1;
import org.springframework.stereotype.Component;
import java.util.Collection;
import java.util.List;

@Component
public class HomeRoute implements Route {
    public String getPath() { return ""; }
    public Collection<String> getAllowedRoles() { return List.of(); }
    public void load(UI ui) { ui.add(new H1("Hello from Spring Boot")); }
}
```

Run `./gradlew bootRun` or `mvn spring-boot:run` in your application, then open `http://localhost:4040/`. Java components send UI updates through `/ws`. With no registered routes, the server starts and unknown routes display a 404 message.

For a complete application with both build formats, see [examples/spring-app](examples/spring-app). [TUTORIAL.md](TUTORIAL.md) covers configuration and application assets.

## Configure the server

Add optional settings to your application's `application.properties`:

```properties
geshra.web.port=4040
geshra.web.open-browser=false
geshra.web.static-location=classpath:/web/
geshra.web.static-cache-bytes=8388608
```

Set `geshra.web.enabled=false` to disable the library's web auto-configuration. Application resources in `src/main/resources/web/` override bundled assets. `static-location` also accepts a Spring resource location such as `file:./public/`. Assets work inside executable Spring Boot JARs.

The framework uses its own Netty listener. `geshra.web.port` controls this listener; Spring Boot's `server.port` controls a servlet or reactive server if your application also includes one. Choose separate ports when using both.

Packaged assets share a bounded cache that retains at most the configured payload budget and 256 entries. Original and precompressed gzip bytes count toward that budget; filesystem assets stay live. Set `static-cache-bytes=0` to disable caching. Large files stream in small chunks. WebSocket actions automatically group DOM changes into bounded packets, and slow connections pause input while their output is backpressured.

See [PERFORMANCE.md](PERFORMANCE.md) for memory usage, ownership rules, and server tuning.

The UI toolkit includes native form controls, layouts, dialogs, popovers, disclosures, status indicators, media, SVG/canvas, and a bounded virtual list for large datasets. See [UI_GUIDE.md](UI_GUIDE.md) for examples, browser semantics, lifecycle rules, and the full component catalog. The example Spring application includes a gallery at `/components`.

Routes can provide server-delivered SEO content and metadata, with automatic sitemap and robots endpoints. Authentication uses hashed passwords, role checks, real HttpOnly cookies, CSRF-protected login/logout, and resumable typed sessions. [WEB_GUIDE.md](WEB_GUIDE.md) covers the simple APIs and deployment settings; the example includes `/login` and protected `/account` routes.

## Build and try locally

Use JDK 21 to run the included Gradle 8.13 wrapper. On Windows, replace `./gradlew` with `.\gradlew.bat`.

```sh
./gradlew clean build publishToMavenLocal
./gradlew -p examples/spring-app -PuseMavenLocal=true bootRun
```

For Maven, run `mvn spring-boot:run` from `examples/spring-app` after local publication. Local publication requires no Central credentials or signing key.

The build produces the library JAR, sources JAR, Javadoc JAR, and Maven metadata. `publishAllPublicationsToBuildRepository` writes a Maven repository under `build/repository` for inspection and integration testing.

## Publish to Maven Central

See [PUBLISHING.md](PUBLISHING.md) for namespace verification, signing, credentials, and release commands. The release workflow stages a signed deployment in the Central Portal, where the maintainer publishes it after validation. CI builds the library and tests a separate Spring Boot consumer.

## Contribute and report issues

See [CONTRIBUTING.md](CONTRIBUTING.md) for builds, browser checks, and pull requests. Use [GitHub issues](https://github.com/tehnewb/Geshra/issues) for bugs and feature requests, and [SECURITY.md](SECURITY.md) for private vulnerability reports.

Geshra's original code is available under the [MIT license](LICENSE).
