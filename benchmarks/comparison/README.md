# Matched server-dependent browser fixtures

These isolated applications compare a deliberately small shared workload, not the complete feature sets of the libraries. Nothing is added to Geshra's production routes. Previous browser-lab artifacts are separate.

All four render native `main`, heading, seven buttons, text input, three status paragraphs and a two-column native table with the same stylesheet. Initial documents have no visible server-rendered application content. The fixture starts empty, then performs three authoritative server increments, a form echo, create 1,000 rows, update identifiers 1/11/…/991, select identifier 500, remove identifier 500, and clear. No framework virtualizes rows. Labels and IDs are deterministic. Every button operation executes real server work before displaying its result; no implementation injects client predictions or artificial server sleeps.

Geshra and Vaadin Flow own the UI on the server using their normal native-element APIs. Vaadin has `@NoTheme`, full-duplex WebSocket transport and an application-specific optimized production bundle rather than the much larger general precompiled frontend bundle. React uses an ordinary production `createRoot`, native elements, keyed rows and immutable state updates. Plain DOM uses explicit native mutations. React/plain DOM share a small real Node HTTP service and authoritative per-context session state. Their submitted form carries the native value; Geshra/Flow eagerly synchronize native input to the server. Typing traffic is reported separately. This is **not a local-state React rendering benchmark** and does not imply React needs network round trips for local UI behavior.

Versions are pinned: Vaadin 25.3.5, Spring Boot 4.1.2 for Vaadin, Java 21, React/React DOM 19.3.1, esbuild 0.28.2. Geshra retains its actual Spring Boot 3.4.3/Netty runtime. Effective packaged dependencies and artifact SHA-256 hashes are recorded by `inventory.cjs`. Vaadin 24.10.14 was attempted first but required a paid extended-support license; no license was acquired or bypassed. The successful current open-source build is used. Generated Vaadin frontend sources retain upstream structure and are excluded from maintained source formatting rules.

## Build on Windows

Run from the repository root with JDK 21 installed and Node available. The first Java dependency and Vaadin production frontend builds require network access.

```powershell
$env:JAVA_HOME='C:\Users\albert\.jdks\corretto-21.0.10'
.\gradlew.bat publishAllPublicationsToBuildRepository
.\gradlew.bat -p benchmarks/comparison/geshra bootJar
.\gradlew.bat -p benchmarks/comparison/tools wrapper --gradle-version 8.14.3
.\benchmarks\comparison\tools\gradlew.bat -p benchmarks/comparison/vaadin bootJar
npm --prefix benchmarks/comparison/client ci
npm --prefix benchmarks/comparison/client run build
node benchmarks/comparison/inventory.cjs
```

The independent wrapper is needed by Vaadin's newer Spring Boot generation; it does not modify the repository wrapper. Both Java artifacts are production Boot JARs. The client build has `NODE_ENV=production`, minification and no dev/HMR server. If installed dependencies are absent, install the existing browser harness with `npm --prefix browser-tests ci` and its Playwright Chromium browser, or set `BROWSER_EXECUTABLE` to installed Chrome.

Start these foreground commands in separate terminals, then stop all owned processes after testing:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -jar benchmarks/comparison/geshra/build/libs/matched-geshra.jar --geshra.web.port=4101
& "$env:JAVA_HOME/bin/java.exe" -jar benchmarks/comparison/vaadin/build/libs/matched-vaadin.jar
node benchmarks/comparison/client/server.cjs
node benchmarks/comparison/proxy.cjs
```

The proxy exposes browser ports 4201–4204 and a loopback-only control port 4199. Backends use 4101–4104. Never run simultaneous benchmark jobs or rebuild while measuring.

## Measure and inspect

From `benchmarks/comparison`:

```powershell
$env:BROWSER_EXECUTABLE='C:\Program Files\Google\Chrome\Application\chrome.exe'
node checks.cjs
node run.cjs
node diagnostics.cjs
node report.cjs
```

The full matrix uses four repeats in a rotating four-library Latin order, two cache states, and two profiles: 64 measured visits. Each profile first runs one excluded complete warmup per implementation. Cold browser cache means a fresh context and cleared cache, **not a cold server process**. Warm cache is a same-context unmeasured initial visit followed by a new document. Server caches/JIT remain warm. CPU rate is 1 locally and 4 under the delayed profile. All profiles use the same desktop viewport. Reports use ordinary sample medians (averaging the middle pair for even counts) and nearest-rank p95; four-sample p95 is the maximum observation.

The delayed profile adds 75 ms **per direction per application-stream chunk** at the common TCP relay. Unlike CDP HTTP-only throttling, this applies to HTTP requests/responses and WebSocket frames equally. It preserves stream order, adds no backend sleeps and imposes no throughput/loss model. Relay byte counters include HTTP headers and WebSocket framing, excluding TCP/IP/TLS; the lab is unencrypted loopback. This is a controlled delay experiment, not a real WAN.

`COMPARISON_REPEATS`, `COMPARISON_PROFILES=local`, `COMPARISON_APPS=geshra,react,dom`, and `COMPARISON_OUTPUT` permit explicitly smaller or reduced matrices. Missing implementations must be identified, not assigned assumed results. Raw JSON retains failures. Readiness means complete matched native content plus two animation frames; successful first server actions additionally verify usability. Callback mutation and two-frame visual proxies are distinct from observed Event Timing interaction estimates. The observer support list and raw entries are retained. The per-operation correctness checks validate the dataset and revision; script/layout counters are browser work, not server CPU measurements.

Separate diagnostics check shared CSS and layout, save initial/table screenshots and collect post-GC browser heap/DOM counters after six equivalent create/clear cycles. These are retained-state observations, not proof of absence of leaks or backend-memory comparisons. Artifacts live under `build/comparison-artifacts`; machine-readable runs and the generated report live under `performance-results/comparison*` and `performance-results/COMPARISON.md`.

Primary setup references: [Vaadin production/Gradle configuration](https://vaadin.com/docs/latest/flow/configuration/gradle), [Vaadin Element API](https://vaadin.com/docs/latest/flow/component-internals/element-api), [React createRoot](https://react.dev/reference/react-dom/client/createRoot), [React versions](https://react.dev/versions). No universal fastest-library ranking is supported by this finite fixture.
