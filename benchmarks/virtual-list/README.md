# Fixed-height virtual-list evaluation

This isolated fixture evaluates Geshra's existing lazy fixed-height list and its bounded-request update. It does not change the four-app plain-table comparison. The production feature supports fixed row heights only. It creates arbitrary server-owned Components on demand, retains overlapping row identities, and disposes evicted rows. Logical keys identify data; native component IDs identify only that component lifetime. Durable selection and edits belong to the application model. Refresh rebuilds callbacks and content rather than reusing stale objects by key.

The final policy sends changed ranges immediately up to 32 outstanding requests per list, with `setMaximumPendingRequests(1)` through `(32)` available. Lower limits trade less work for more waiting; 32 is a chosen latency-first bound, not a proven universal optimum or an aggregate server-work bound. Request tokens and ordered structural DOM updates preserve identity. No experimental pause timer or priority machinery remains. The final paired matrix is 48 visits plus eight warmups at 100,000 rows; see performance-results/virtual-list/FINAL.md.

The original 64-visit matrix and two 72-visit follow-ups are preserved intermediate variants, not measurements of the final default. Their saved JARs and raw observations explain why one-request, immediate two-request and reserved-slot policies were not selected for the latency-first default. No visits are pooled across builds.

## Candidates and scope

`range.cjs` bundles the actual production fixed-height range calculation and pinned `@tanstack/virtual-core@3.19.11` with identical measurement code/minification. This is a range-subsystem comparison, not a full TanStack integration, renderer ranking or scrolling-FPS claim. Both receive identical geometry and perform exact range checks. The TanStack adapter reuses its geometry object, initializes one core per state, and invalidates count/height/overscan changes. No element measurement is enabled for fixed rows. Both are checked against the same 90 changed configurations after timing. The timed loop holds count, row height and overscan constant and executes 200,000 changing ranges after a 1,000-range warmup. Six fresh contexts per browser/count/candidate alternate candidate order.

The core candidate is MIT licensed; its complete license is in THIRD_PARTY_NOTICES.md. Pinned upstream source: https://github.com/TanStack/virtual/tree/11b48e4ab8b08d483c8fe29a31ef11261ffbda04/packages/virtual-core. npm provenance was inspected by the sourcing chat, without independent signature verification. The exact tarball integrity is retained in package-lock.json. No third-party virtualizer is added to Geshra's published runtime.

## Real-server workload

The independent Spring Boot app uses the actual published local Geshra library, normal protocol and runtime. Logical counts are 10,000 and 100,000, generated lazily as integers. Both versions use identical application class/resource bytes (verified by inventory.ps1), 32px rows, 320px viewport, four-row overscan and the same native text/input/button content. Only sparse edited values are stored by the application. No dataset is preloaded in the browser.

Each cold session waits for the actual initial row and two animation frames, scrolls three rows per frame for 60 frames, performs 24 deterministic rapid jumps, waits for the final visible row, selects the last logical item, edits it, visits first/last, refreshes and reads provider/mounted counts. Timings retain failures. A separate correctness suite checks focus and selection, durable edits, reconnect, disposal, mixed deployments, pending/initial ACK interleavings, repeated renderer failures and stale request identities. Focus is handed to the list root before sending an evicting viewport; outgoing rows become inert during the RTT. The configured ceiling plus the latest desired range bounds client request records per list. New-server ACKs carry a per-list request token retained across resubmissions. One same-range render retry is allowed; subsequent failures emit `geshra-virtual-error` until the desired range/configuration changes.

The browser matrix alternates before/after order within every browser/count/profile/repeat block, four measured repeats and one separate warmup per cohort: 64 measured visits plus 16 warmups. All use a 1024×768 browser viewport. Local profile has no artificial delay. Delayed profile applies 75ms each direction to both HTTP and WebSocket traffic through the same ordered TCP relay. Chrome additionally uses CDP CPU rate 4; Firefox has delay only, with no CPU-throttling claim. Servers use Java 21, -Xms128m/-Xmx512m. Other versions stay idle during each visit. No concurrent build or performance job runs during retained measurements.

Metrics distinguish initial resource HTTP transferSize (browser-reported headers/body), actual WebSocket message payload bytes (excluding framing/TCP/TLS), request-to-ACK latency (network + server serialization/work), native mounted rows/provider reads, RAF sampling gaps and uncovered viewport pixels. RAF gaps are not compositor frame measurements. Browser script/layout counters are not backend CPU. Chrome-only post-GC heap/DOM counters include the document, runtime and observation code; request/ACK/long-task trace arrays are cleared after copying raw observations and before final GC. Firefox heap and unsupported long-task measurements are unavailable. Neither sample heap deltas nor low mounted counts prove absence of leaks. Variable-height rows, touch inertia, sustained WAN throughput/loss, backend CPU/allocation, multiuser load, physical devices and general library rankings are outside this pass.

## Build the current variant on this Windows workspace

```powershell
$env:JAVA_HOME='C:\Users\albert\.jdks\corretto-21.0.10'
npm --prefix benchmarks/virtual-list ci --ignore-scripts
node benchmarks/virtual-list/range.cjs
.\gradlew.bat test publishAllPublicationsToBuildRepository
.\gradlew.bat -p benchmarks/virtual-list bootJar
Copy-Item benchmarks/virtual-list/build/libs/virtual-list.jar build/virtual-list-bounded.jar
```

The actual pre-change artifact is preserved as build/virtual-list-before.jar, alongside measurement-time VirtualList-before.java, virtual-list-before.js and runtime-before.js under performance-results/virtual-list. Rebuilding the baseline requires that saved library revision; do not label current source as the old binary. The commands below reproduce the historical one-request matrix with its saved artifact. The current bounded-32 comparison uses the final commands at the end of this document.

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=10000 -jar build/virtual-list-before.jar --geshra.web.port=4301
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=100000 -jar build/virtual-list-before.jar --geshra.web.port=4302
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=10000 -jar build/virtual-list-after.jar --geshra.web.port=4401
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=100000 -jar build/virtual-list-after.jar --geshra.web.port=4402
node benchmarks/virtual-list/proxy.cjs
node benchmarks/virtual-list/correctness.cjs
.\benchmarks\virtual-list\inventory.ps1
node benchmarks/virtual-list/run.cjs
node benchmarks/virtual-list/report.cjs
```

The harness uses installed Chrome and Playwright Firefox. Configure VIRTUAL_REPEATS, VIRTUAL_COUNTS, VIRTUAL_VERSIONS and VIRTUAL_OUTPUT for explicitly labeled reduced diagnostics; the retained report requires the full matrix. Results and artifact hashes live under performance-results/virtual-list. Pilot/initial artifacts are diagnostics and are excluded from the final report. source-after.json records measurement-time source hashes; the preserved JAR hashes identify the actual measured binaries.

The historical immediate two-slot follow-up uses its preserved build/virtual-list-two-flight.jar; a current build belongs in virtual-list-bounded.jar. To reproduce that historical experiment, add its third backend on 4502, then run:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=100000 -jar build/virtual-list-two-flight.jar --geshra.web.port=4502
$env:VIRTUAL_COUNTS='100000'
$env:VIRTUAL_REPEATS='6'
$env:VIRTUAL_VERSIONS='before,after,two-flight'
$env:VIRTUAL_OUTPUT='latency.json'
node benchmarks/virtual-list/run.cjs
.\benchmarks\virtual-list\inventory.ps1 -Versions before,after,two-flight -Output inventory-two-flight.json
node benchmarks/virtual-list/latency-report.cjs
```

Set VIRTUAL_TEST_URL=http://localhost:4502/ for the dedicated correctness suite against the two-request build. source-two-flight.json and runtime-two-flight.json retain its measured source/runtime identity. The three-variant harness uses every cyclic order and its reverse; the two-variant harness retains its original alternating order.

## Final paired comparison

Use the saved original and current bounded artifacts, with Java backends and relay in separate terminals:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=100000 -jar build/virtual-list-before.jar --geshra.web.port=4302
& "$env:JAVA_HOME/bin/java.exe" -Xms128m -Xmx512m -Dvirtual.count=100000 -jar build/virtual-list-bounded.jar --geshra.web.port=4702
node benchmarks/virtual-list/proxy.cjs
```

After the backends are ready:

```powershell
$env:VIRTUAL_TEST_URL='http://localhost:4702/'
node benchmarks/virtual-list/correctness.cjs
$env:VIRTUAL_COUNTS='100000'
$env:VIRTUAL_REPEATS='6'
$env:VIRTUAL_VERSIONS='before,bounded'
$env:VIRTUAL_OUTPUT='bounded.json'
node benchmarks/virtual-list/run.cjs
.\benchmarks\virtual-list\inventory.ps1 -Versions before,bounded -Output inventory-bounded.json
node benchmarks/virtual-list/bounded-report.cjs
```

The final matrix has 48 measured visits and eight warmups. source-bounded.json, runtime-bounded.json and inventory-bounded.json identify the final source, bundle and JARs. Earlier 64/72/72-visit matrices remain separate; their artifacts are not overwritten or pooled. Stop all owned servers after testing. The selected default is a per-list request bound, not a universal optimum or an aggregate server-work bound.
