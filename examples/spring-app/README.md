# Geshra Spring Boot demo

This application demonstrates how to use Geshra from a normal Spring Boot application. Start with the home route for a minimal example, then explore the focused component examples or the sign-in flow.

## Run the application

Use Java 21. From the Geshra repository root, publish the library locally and start the example:

```sh
./gradlew publishToMavenLocal
./gradlew -p examples/spring-app -PuseMavenLocal=true bootRun
```

On Windows, use `gradlew.bat` instead of `./gradlew`. For Maven, publish locally first, then run `mvn spring-boot:run` inside `examples/spring-app`.

Open [the home page](http://localhost:4040/) or [the component gallery](http://localhost:4040/components).

## Read the examples

| Source | What it demonstrates |
| --- | --- |
| [DemoApplication](src/main/java/com/example/demo/DemoApplication.java) | Ordinary Spring Boot startup and automatic library configuration |
| [HomeRoute](src/main/java/com/example/demo/routes/HomeRoute.java) | A heading, one Java click callback, and public SEO content |
| [ComponentsRoute](src/main/java/com/example/demo/routes/ComponentsRoute.java) | Assembling independent examples into a gallery |
| [LoginRoute](src/main/java/com/example/demo/routes/LoginRoute.java) | Native form validation and asynchronous authentication |
| [AccountRoute](src/main/java/com/example/demo/routes/AccountRoute.java) | Role protection, session attributes, HttpOnly cookies, and logout |
| [DemoAccounts](src/main/java/com/example/demo/auth/DemoAccounts.java) | Registering an optional demo user with the member role |

The application package contains startup, `routes` contains Spring route beans, `auth` contains account configuration, and `gallery` contains reusable example sections.

## Explore the gallery

Each factory creates fresh components for the current session. Read or reuse a section independently of the other examples.

| Example | Try it |
| --- | --- |
| [FormControlsDemo](src/main/java/com/example/demo/gallery/FormControlsDemo.java) | Change a field, read the synchronized Java values, then assign values without firing change listeners |
| [OverlaysDemo](src/main/java/com/example/demo/gallery/OverlaysDemo.java) | Toggle a disclosure, open a modal, dismiss it with Escape, and show a popover |
| [StatusLayoutsDemo](src/main/java/com/example/demo/gallery/StatusLayoutsDemo.java) | Compose badges, indicators, cards, and layout containers |
| [NodeLifecycleDemo](src/main/java/com/example/demo/gallery/NodeLifecycleDemo.java) | Move, detach, restore, and dispose a component; unsubscribe a native listener |
| [VirtualListDemo](src/main/java/com/example/demo/gallery/VirtualListDemo.java) | Browse a million lazy rows or jump to the final item while keeping only a small window rendered |
| [GraphicsDemo](src/main/java/com/example/demo/gallery/GraphicsDemo.java) | Draw through the native canvas API and construct SVG elements |

The form example deliberately starts with an out-of-range slider value and an invalid date. The browser normalizes them just as it would for JavaScript assignments. Disposing the retained input permanently ends its lifecycle; use move and detach when you want to restore it.

## Try authentication

Build and start the executable example with an explicit demo password:

```sh
./gradlew -p examples/spring-app -PuseMavenLocal=true bootJar
java -jar examples/spring-app/build/libs/geshra-spring-example.jar --demo.password=your-test-password
```

Open [sign in](http://localhost:4040/login), use the username `demo` and the password supplied at startup, and continue to `/account`. Save and read the preference cookie, reload to observe the session visit count, and sign out. The account is created only when `demo.password` is supplied.

Routes are Spring singletons. Keep per-browser component state inside `load` or the gallery factories, and put resumable user state in `SessionContext`. See the [UI guide](../../UI_GUIDE.md) and [web guide](../../WEB_GUIDE.md) for the library APIs.
