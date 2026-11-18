# Performance and memory usage

The library reduces per-update allocation and repeated work in DOM serialization, component lookup, keyboard dispatch, and static serving. The compact protocol is negotiated per connection; legacy clients and custom packet handlers retain their original format. Both formats preserve typed event routing, structural update ordering, and synchronous application callbacks.

## Changes to serialization and UI updates

DOM mutations store their common one or two parameters inline. Additional keys use compact arrays, replacing linked maps and entry objects. Internal component setters avoid temporary parameter maps. UTF-8 values are written directly into an outgoing buffer without intermediate byte arrays or one direct buffer per mutation.

Dispatchers allocate queue storage only when used. Monotonic priorities need no sorting; mixed priorities use stable ordering. Queue insertion preserves explicit priorities, fixing integer overflow in maximum-priority property updates. Ordinary packets target 64 KiB and obey the unsigned 16-bit mutation count. A single mutation is indivisible and can exceed the target size if its individual parameters fit the protocol limits.

UI component lookup uses an existing Netty primitive-key map, and released IDs use a primitive stack. Dirty components link directly into their owning UI's pending list, so sending changes visits affected components instead of repeatedly traversing the entire tree. WebSocket actions group mutations automatically. Explicit `beginUpdate` and `endUpdate` support nested bulk changes outside that path.

Slow connections pause inbound reads and retain unsent mutations until output becomes writable. Script execution and navigation establish a DOM ordering barrier. Unicode title and parameter lengths count encoded bytes; oversized values fail explicitly and partially encoded buffers are released.

The Valthorne event publisher already dispatches through immutable arrays without allocation, reflection, or locks on the publication path. Styles have only one constructor-supplied callback, so they invoke its `EventHandler` directly instead of allocating a routing table. Style lookup and inline output avoid copied declaration lists. Packet opcodes use an immutable array index, and protocol enum lookups use precomputed explicit-code tables. Keyboard names use a single initialized index that preserves the original handling of duplicate browser key names.

## Static assets and lifecycle

One shared static handler serves all connections of a server. Immutable packaged resources reuse retained payloads and gzip representations; filesystem resources stay uncached so development edits remain visible. Both representations count toward `geshra.web.static-cache-bytes`, which defaults to 8 MiB and is a limit rather than an upfront allocation. Entry count is capped at 256 to bound metadata, including zero-byte files.

Set `geshra.web.static-cache-bytes=0` to trade repeated reads and compression for less retained payload memory. Assets above one MiB stream in 8 KiB chunks through Netty instead of allocating a complete file-sized response array. HEAD responses use available metadata without reading the body. The cache is discarded at server shutdown, and sessions belonging to that listener are invalidated immediately rather than retaining UI trees until the inactivity timeout.

The runtime uses the Netty HTTP module and its required foundations rather than the complete Netty aggregation. The library does not change the host application's JVM heap, garbage collector, worker workload, or authentication policy.

## Verification

The tests cover batching, ordering, Unicode limits, buffer ownership, packet splitting, backpressure, gzip negotiation, cache bounds, live files, and streaming through a real HTTP connection. Use representative application workloads when assessing deployment memory and latency requirements.

## Repeatable benchmarks

The base JVM suite has 24 parameter combinations; the separate compression assessment adds two levels on a constructed native-row fixture. Use `-PbenchmarkInclude=geshra.benchmark.CompressionBenchmark.*` to run only that assessment. It measures codec CPU and steady-state Java allocation, not browser inflate or native compressor memory, and does not enable production compression.

Use JDK 21. Benchmark dependencies belong to a separate JMH source set and never enter the published library. The full run uses two fresh JVM forks, three 500 ms warmup iterations and five 500 ms measurement iterations per parameter combination, with GC allocation profiling:

```sh
./gradlew benchmark
./gradlew benchmark -PbenchmarkSmoke
```

On Windows use `.\gradlew.bat`. Full and smoke results default to `build/reports/benchmarks/jvm.json`; preserve a run with `'-PbenchmarkOutput=build/reports/benchmarks/candidate.json'`. Smoke timings only verify that fixtures execute; they are not optimization evidence. The base suite covers 24 parameter combinations: ASCII/Unicode/structural/style encoding, zero through 32 event listeners, fixed/compact integer width transitions, and actual grouped component updates with 1, 32, or 256 components. Every measured transport frame is consumed and released.

Build the example against the current library and start it before measuring browsers:

```sh
./gradlew publishAllPublicationsToBuildRepository
./gradlew -p examples/spring-app -PuseBuildRepository=true bootJar
java -jar examples/spring-app/build/libs/geshra-spring-example.jar --demo.password=browser-test-password
```

From `browser-tests`, run `npm ci`, install the development browsers with `npx playwright install chromium firefox`, then run:

```sh
npm test
npm run benchmark
```

`BASE_URL` selects the example server; `BROWSER_EXECUTABLE` optionally selects an installed Chromium browser. `BROWSER=firefox npm test` runs the correctness suites in Firefox. `BENCHMARK_OUTPUT` writes browser JSON relative to the process working directory, and `BENCHMARK_LABEL` records the measured revision. PowerShell sets these through `$env:NAME='value'` instead of the POSIX prefix syntax.

`npm run benchmark:compare` compares the checked-in browser runs; optional CLI paths select other runs. It refuses to present a comparison when browser, operating system, architecture, or CPU differ. Run correctness checks separately from timed measurements, and avoid simultaneous builds or other benchmark processes.

The browser benchmark measures actual gallery payload bytes and frames, 50 callback-latency samples after 10 warmups, 100 DOM decode/apply samples after 20 warmups, and browser action encoding for clicks, Unicode input, viewport requests, and 60,000-character text. Escaping the encoded array to a browser-global sink prevents scalar replacement of an unobserved result. Compact action backing storage must equal its payload size. Runtime source and reproducibly gzipped bundle sizes are recorded too. Payload totals exclude WebSocket framing, HTTP headers, TCP, and TLS. This is a local loopback measurement, not a WAN throughput or multi-user capacity claim.

For a before/after comparison, run the previous revision's example on another port and set `BENCHMARK_LEGACY=true` when benchmarking a server without compact support. Measure revisions sequentially on the same machine with the same browser and settings. `BASELINE_URL=... node compatibility.cjs` checks the current browser bundle against that previous server. Ordinary consumer tests also exercise legacy clients against the current server.

CI checks the generated runtime before Java packaging, runs JVM smoke benchmarks and real-browser checks, and uploads JSON artifacts even on failure. Timing remains advisory because shared-runner load is variable. Correctness, bundle reproducibility, exact packet sizes, and generous gallery byte budgets are deterministic gates. Full benchmark results should accompany material performance changes; no recurring automation or remote publication is required to run them.

## Compact wire format

The initial session-state message advertises protocol `2` only when every registered built-in action handler opts into compact decoding and envelope opcode `12` is unoccupied. `PacketHandler.supportsCompactInput()` defaults to false. Built-in subclasses conservatively revert to legacy unless they explicitly opt in. This preserves replacement handlers that read fixed-width fields.

Client compact actions begin with byte `12`, then the original action opcode. IDs, lengths, revisions, viewport indices, and authentication acknowledgment identifiers use canonical unsigned base-128 integers limited to `Integer.MAX_VALUE`. Coordinate fields retain the old signed-short semantics and use ZigZag encoding. Mouse button/modifier flags share one byte; keyboard repeat/modifier flags share one byte. Heartbeats remain a single byte. Text fields retain the 65,535 UTF-8-byte limit, and complete client actions cannot exceed the existing 65,536-byte frame limit. Uploads remain whole-frame uploads, not a streaming-file protocol.

Receiving a compact action, or explicitly advertising the compact reader during supported initial-route bootstrap, opts that connection into server batch opcode `7`. Batch counts remain unsigned 16-bit. Each mutation contains its original operation byte, variable-length component ID, parameter count, and parameters. A parameter header packs its five-bit key with a three-bit representation: `0` = length-prefixed UTF-8, `1` = string `false`, `2` = string `true`, `3` = empty string, `4` = canonical unsigned child/reference ID. Other representations are rejected. Boolean tokens remain strings at the DOM boundary, preserving existing setter behavior.

Legacy server batch opcode `1` and control-message formats remain supported. Batches containing a custom `DOMUpdate` subclass retain the legacy encoder contract. Both encoders roll back partial mutations on failure. Compact readers reject overflow, overlong integers, truncation, unsupported representations, and invalid lengths. Reconnect queues have both a 128-packet and one-MiB payload bound; a changed negotiated protocol discards incompatible queued bytes and rebuilds the route.

Capability negotiation waits at most one second for the initial session state. If that state is unavailable, the browser requests its route with legacy encoding so an otherwise live connection cannot remain blank indefinitely. The wait is canceled when state arrives or the connection closes; late state can still enable compaction for subsequent actions. Browser regression checks deliberately withhold the first state packet to verify recovery.

## Browser source organization

Maintained source lives under `src/main/resources/web/js/runtime/`: transport/session ownership, legacy and compact codecs, packet readers/writers and sizing, DOM application, node lifecycle, native event/value handling, and virtual lists each have focused modules. Native behavior and asynchronous server callbacks are preserved. Parameter scratch storage is reused within a batch rather than allocating a parameter object for every mutation. Browser-owned node state remains weak except for explicitly retained detached roots.

### Fixed-height virtual-list selection and request bound

The [virtual-list report](performance-results/virtual-list/FINAL.md) records a separate fixed-height evaluation and final paired Chrome/Firefox matrix. The native constant-space range calculation won the measured range-only comparison against pinned TanStack virtual-core 3.19.11; no third-party virtualizer enters the production runtime. This does not establish an overall library or scrolling-FPS ranking.

The final default sends changed ranges immediately with at most 32 outstanding requests per list; applications can choose 1–32. Smaller experimental limits reduced delayed traffic but added final-viewport waiting. The selected bound preserves Chrome's measured delayed final-jump timing around 190ms; Firefox observed 185ms to 191.5ms. Workload traffic and runtime payload increase for logical keys, request identity and interaction protection; this is not a bandwidth improvement. At 100,000 logical rows, both versions mounted at most 18 rows in this fixture. Rapid delayed scrolling still exposes viewport gaps with four-row overscan. Raw results, artifact/source hashes, metric limits, earlier variants and reproduction commands accompany the report.

Run `npm run build:runtime` in `browser-tests` after editing sources. The pinned development-only esbuild dependency creates the checked-in, minified classic `runtime.js`. Consumers still load one script and need no Node installation or frontend build. `npm run check:runtime` detects stale artifacts; `npm run check:source` checks syntax, cross-language documentation rules, and formatting. Do not hand-edit the generated bundle or reformat vendor scripts.

## Recorded comparison and limits

Machine-readable runs are retained under `performance-results/`. The initial comparison uses the previous `4cb01d2` implementation and the candidate on the same Windows x64 Ryzen 9 7945HX machine with Corretto 21.0.10 and Chrome 157. Timing is noisy; the byte counts are the reliable improvements:

| Workload | Previous | Compact candidate |
| --- | ---: | ---: |
| Gallery server payload | 10,775 bytes | 8,487 bytes |
| Gallery client payload | 74 bytes | 51 bytes |
| 60 measured click actions, including warmups | 1,200 bytes | 600 bytes |
| 60 callback responses | 7,320 bytes | 7,080 bytes |
| Runtime JavaScript | 51,955 bytes | 21,204 bytes |
| Gzipped runtime | 12,164 bytes | 7,835 bytes |

The final sequential browser comparison recorded a 0.5 ms median callback round trip before and 0.4 ms after. Applying 1,000 mutations took a 0.8 ms median for the previous runtime and 0.7 ms for compact decoding in the candidate; within the candidate, legacy decoding also measured 0.7 ms. These sub-millisecond differences should not be treated as statistically established speedups. For 60,000-character ASCII input, candidate compact encoding measured approximately 152 µs versus 160 µs for its legacy encoder while retaining exactly the payload size. Earlier reruns varied enough to reinforce that timing is advisory.

Timed callbacks use `element.click()`, whose coordinates are zero; this explains the measured 20-to-10-byte click reduction. Physical pointer coordinates can require additional varint bytes. Large IDs and large coordinates can also make a compact field wider than its fixed-width counterpart. The retained results describe these fixtures rather than promising a fixed compression ratio for every application event.

Compaction trades additional integer/representation work for fewer bytes. It is not universally faster in an isolated CPU benchmark. Event dispatch and reusable-buffer codecs show approximately zero measured allocation per operation; actual component changes still allocate mutation objects. The full results include that cost rather than claiming an allocation-free UI. Large ASCII input can favor the native legacy text encoder on CPU despite compact packets retaining fewer bytes. Evaluate this tradeoff with the application's RTT, bandwidth, workload, and browser rather than extrapolating a local microbenchmark.

Representative final JMH means are below; the raw JSON retains confidence intervals and all forks/iterations. The intervals overlap for several comparisons, so these are cost estimates rather than established relative speedups.

| JVM operation | Legacy | Compact |
| --- | ---: | ---: |
| Reusable-buffer ASCII mutation encoding | 18.69 ns/op | 22.97 ns/op |
| Reusable-buffer Unicode mutation encoding | 36.24 ns/op | 40.05 ns/op |
| Grouped update of 32 real components | 1.86 µs/op | 1.76 µs/op |
| Grouped update of 256 real components | 13.11 µs/op | 14.51 µs/op |

Both 32-component groups allocated approximately 1,871 bytes per operation, and both 256-component groups approximately 14,415 bytes. This identifies mutation allocation as a remaining measurable optimization target; changing the wire representation alone does not remove it.

The existing session model still has one active socket per cookie and process-local UI state. Slow application callbacks, simultaneous-user capacity, real WAN latency, arbitrary application scripts, and bulk upload streaming need separate workload-specific evaluation. DOM backpressure is covered; the pre-existing control-message drop behavior under an unwritable channel is not changed by this protocol optimization. Do not infer reliable control-message delivery under congestion from the payload benchmarks.

## Extended browser lab

The [startup/cache optimization pass](performance-results/optimizer-pass/OPTIMIZATION.md) preserves the original comparator evidence and records fresh matched baseline/candidate runs. Initial-route bootstrap reduced delayed readiness by about 22%, and private ETag validation reduced warm initial downstream bytes by about 75%. Cold initial traffic increased by 448 bytes, local timing gains were not established, and bulk mutation traffic remained unchanged. Exact conditions, hashes, regressions and limits are in that report.

The separate [matched application comparison](performance-results/COMPARISON.md) measures isolated Geshra, Vaadin Flow, production React and plain-DOM fixtures with the same native UI and authoritative server operations. Its [build and run instructions](benchmarks/comparison/README.md) describe the pinned versions, counterbalanced conditions, equal HTTP/WebSocket delay relay and limits; these results do not establish a universal library ranking.

The development-only browser lab characterizes the current candidate with a finite sequential matrix: home and component-gallery routes, desktop and mobile-sized touch viewports, three explicit HTTP/CPU profiles, cold and warm browser cache, and three repetitions per condition (72 loads). Run these commands from `browser-tests` against the packaged example above:

```sh
npm run lab:matrix
npm run lab:diagnostics
npm run lab:firefox
npm run lab:report
```

`BROWSER_EXECUTABLE` selects an installed Chromium executable; otherwise Playwright Chromium is used. The matrix writes `performance-results/browser-lab.json` by default, including source fingerprints, machine/browser settings, raw observations and per-condition median/p95. `LAB_OUTPUT` selects another file; `LAB_PROFILES=local` and `LAB_REPEATS=1` select an explicitly smaller smoke matrix. Neither timings nor metric support are inferred from browser screenshots.

`lab:firefox` runs a separate unthrottled eight-case navigation/real-interaction subset with desktop and mobile-sized viewports, recording unsupported metrics as unavailable in `performance-results/browser-lab-firefox.json` (`LAB_FIREFOX` overrides the path). Chrome CDP network/CPU profiles and tracing do not apply to Firefox. Chromium and Firefox protocol, component and authentication suites both passed during the recorded lab review; those correctness runs are separate from timing samples.

The separate diagnostics write under `build/reports/browser-lab` (`LAB_ARTIFACTS` overrides the directory). Preserve `diagnostics.json` as `performance-results/browser-lab-diagnostics.json` before generating the checked-in report; `LAB_DIAGNOSTICS` can instead select the generated file directly. `LAB_REPORT` selects a report destination. [The browser lab report](performance-results/BROWSER-LAB.md) records results, support, known findings and exact coverage. Earlier payload comparison artifacts are preserved and are not presented as a throttled baseline.

Navigation timing, FP/FCP/LCP, session-window CLS, genuine Event Timing interactions, long-task observations, script/style/layout counters, resource waterfall/compression/cache bytes, and WebSocket frames are retained separately. The callback-to-double-animation-frame measurement is a presentation proxy for the asynchronous server response, not INP. Missing or unsupported metrics stay unavailable. Three-sample p95 is the maximum observed sample, not a reliable population-tail estimate. The matrix avoids request interception, tracing, screenshots and forced GC; those run separately.

The lab also isolates CDP HTTP latency from actual WebSocket callback traffic and tests a real-server WebSocket relay with an explicit per-direction message delay. This relay models message delay, not TCP bandwidth, congestion or packet loss. Do not assume DevTools HTTP throttling applies the same network conditions to WebSocket frames. Separate diagnostics exercise reconnect, offline queue replay, frozen-renderer resume, large-list wheel scrolling, retained/disposed nodes, forced-GC memory trends, a native accessibility-tree/name audit, screenshots, a sanitized HAR and a Chrome trace. They do not certify accessibility or prove absence of memory leaks. Real background visibility, OS sleep, physical devices, field distributions, multi-user capacity and sustained browser-driven server backpressure remain separate work.
