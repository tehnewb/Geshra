# Contributing to Geshra

Geshra is a Java 21 library for Spring Boot applications. Contributions should keep its APIs simple and preserve native browser behavior, bounded memory use, and low allocation overhead.

## Build the library

Install JDK 21, clone the repository, and use the included Gradle wrapper. On Windows, use `gradlew.bat` for the commands below.

```sh
./gradlew clean build publishAllPublicationsToBuildRepository publishToMavenLocal
./gradlew -p examples/spring-app -PuseBuildRepository=true clean test bootJar
mvn -B -f examples/spring-app/pom.xml clean verify
```

The library tests include the source rules in [AGENTS.md](AGENTS.md). Follow those rules for Java changes, and update the relevant guide when public behavior changes.

## Check browser behavior

Start the packaged example in one terminal:

```sh
java -jar examples/spring-app/build/libs/geshra-spring-example.jar --demo.password=browser-test-password
```

In another terminal, install the development dependencies and run both browser suites:

```sh
cd browser-tests
npm ci
npx playwright install chromium firefox
npm test
BROWSER=firefox npm test
```

The example account is enabled only when `demo.password` is supplied. Use a test password when running the example. On PowerShell, select Firefox with `$env:BROWSER='firefox'` before `npm test`.

## Submit a change

Open an issue for a bug or substantial API proposal, or submit a focused pull request. Explain the behavior being changed and how you verified it. For a bug fix, include a reproduction or regression check that demonstrates the failure. For a performance claim, include the workload, measurements, and commands needed to reproduce it; see [PERFORMANCE.md](PERFORMANCE.md).

Report security vulnerabilities through the private channel in [SECURITY.md](SECURITY.md).

Geshra's original code is covered by the [MIT license](LICENSE). Bundled vendor assets retain their separate terms, listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
