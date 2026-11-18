# Fixed-height virtual-list results: preserved one-request variant

This historical intermediate variant is retained alongside its raw evidence. The current cap-32 default and final paired measurements are in [FINAL.md](FINAL.md). The range-only candidate comparison below remains the selection evidence for the native fixed-height calculation.

The production implementation retains the simple constant-space fixed-height range calculation. It adds one outstanding viewport request plus the latest desired range, request identities across reconnects, bounded failure recovery, explicit logical keys/lazy provider replacement and pre-send focus/inert protection. No third-party virtualizer or unrelated JS API enters the production bundle. This finite workload does not establish a universal fastest implementation.

## Range subsystem selection

Pinned TanStack virtual-core 3.19.11 versus the actual production range function; identical minifier/harness, reused adapter geometry, constant configuration during timing, exact ranges and 90 changed-configuration checks afterward. Six fresh contexts per browser/count/candidate, alternating order; 1,000 warmup evaluations, then 200,000 timed evaluations. Geometry is supplied directly to both range subsystems; this is not the complete TanStack observer/renderer lifecycle. Small native initialization times are at browser timer resolution. Transient allocation rates were not profiled.

| Browser | Logical rows | Native loop median ms | Core loop median ms | Native / core post-GC JS delta bytes | Native / core numeric backing delta bytes |
| --- | ---: | ---: | ---: | ---: | ---: |
| chrome | 10000 | 5.6 | 356.7 | 7046 / 80308 | 0 / 160000 |
| chrome | 100000 | 4.5 | 436.8 | 7008 / 441108 | 0 / 1600000 |
| firefox | 10000 | 2.5 | 236.0 | unavailable / unavailable | unavailable / unavailable |
| firefox | 100000 | 2.0 | 379.5 | unavailable / unavailable | unavailable / unavailable |

Candidate bundle bytes include identical measurement/configuration-check code and retained upstream notices; they are not incremental production payloads.
- fixed: 1148 raw bytes, 569 gzip bytes; SHA-256 0c65c379f019c0c5360c49b764655b5dd866d416905e029146bb63cc8269fa8d.
- tanstack: 33040 raw bytes, 9953 gzip bytes; SHA-256 5146129af66a2c6a3a5eaec7806ae645cd0a6c21e36c81e960dab889bb4aef05.

Actual full runtime: 21683 → 23658 raw bytes; 8030 → 8620 gzip bytes. The additional recovery/identity behavior costs 590 gzip bytes. No compression setting changed.

## Real production fixture

Environment: AMD Ryzen 9 7945HX with Radeon Graphics        , 32 logical CPUs, win32/x64, OS 10.0.26200, Node v24.14.0, Java 21. Browser versions are retained per sample. Five fixture class/resource entries match byte-for-byte across the two preserved JARs. 64 passing measured cold visits plus 16 separate warmups; four repeats per cohort, alternating version order within every cohort. Browser viewport 1024×768; list viewport 320px, row height 32px, overscan four on each side. Delayed means 75ms each direction for both HTTP and WS; Chrome CPU rate 4, Firefox CPU rate 1. Local CPU rate 1. No parallel build/benchmark ran.

Values below are ordinary four-run medians except maximum pending/mounted values, which are worst observed across the cohort. Four observations do not establish field p95 or confidence intervals.

| Browser / profile / rows | Ready ms before → after | Viewport requests before → after | Max pending before → after | WS payload down bytes before → after | Provider reads before → after | Max mounted before → after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| chrome / local / 10000 | 50.7 → 51.3 | 86 → 84 | 1 → 1 | 235316 → 254344 | 675 → 661 | 18 → 18 |
| chrome / local / 100000 | 51.0 → 51.5 | 86 → 84 | 1 → 1 | 237901 → 257265 | 675 → 661 | 18 → 18 |
| chrome / delayed / 10000 | 569.6 → 570.2 | 86 → 10 | 17 → 1 | 230448 → 68767 | 661 → 181 | 18 → 18 |
| chrome / delayed / 100000 | 570.7 → 570.9 | 86 → 10 | 17 → 1 | 232934 → 69388 | 661 → 181 | 18 → 18 |
| firefox / local / 10000 | 72.0 → 70.5 | 86 → 84 | 2 → 1 | 235316 → 254344 | 675 → 661 | 18 → 18 |
| firefox / local / 100000 | 70.0 → 73.0 | 86 → 84 | 3 → 1 | 237901 → 257205 | 675 → 661 | 18 → 18 |
| firefox / delayed / 10000 | 549.5 → 553.0 | 86 → 10 | 16 → 1 | 230448 → 69844 | 661 → 184 | 18 → 18 |
| firefox / delayed / 100000 | 543.0 → 552.5 | 86 → 11 | 16 → 1 | 232934 → 76489 | 661 → 201 | 18 → 18 |

| Browser / profile / rows | Last jump settle ms before → after | Frame-gap p95 median ms before → after | Max sampled gap ms before → after | Samples with uncovered viewport % before → after | Request-to-ACK ms before → after | Long tasks before → after |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| chrome / local / 10000 | 19.9 → 20.4 | 10.1 → 10.1 | 10.2 → 10.5 | 1.2 → 1.2 | 0.5 → 0.6 | 0 → 0 |
| chrome / local / 100000 | 20.2 → 20.1 | 10.1 → 10.1 | 10.2 → 11.4 | 1.2 → 1.2 | 0.6 → 0.6 | 0 → 0 |
| chrome / delayed / 10000 | 188.8 → 310.6 | 10.6 → 10.3 | 14.9 → 16.3 | 97.6 → 97.6 | 161.1 → 167.5 | 0 → 0 |
| chrome / delayed / 100000 | 187.8 → 310.6 | 10.6 → 10.2 | 14.2 → 15.1 | 97.6 → 97.6 | 161.2 → 167.1 | 0 → 0 |
| firefox / local / 10000 | 31.0 → 32.0 | 16.5 → 17.0 | 17.0 → 17.0 | 1.2 → 1.2 | 1.0 → 1.0 | unavailable → unavailable |
| firefox / local / 100000 | 31.0 → 33.0 | 16.5 → 16.5 | 17.0 → 17.0 | 1.2 → 1.2 | 1.0 → 1.0 | unavailable → unavailable |
| firefox / delayed / 10000 | 204.0 → 258.0 | 16.0 → 16.0 | 18.0 → 19.0 | 97.6 → 97.6 | 156.0 → 156.0 | unavailable → unavailable |
| firefox / delayed / 100000 | 202.5 → 298.5 | 16.0 → 17.0 | 17.0 → 17.0 | 97.6 → 97.6 | 156.0 → 156.5 | unavailable → unavailable |

Frame-gap p95 is calculated within each 84-sample scripted-scroll visit, then medianed across four visits; it is not interaction latency or compositor FPS. Uncovered pixels sample viewport coverage while the real network response is pending. A four-row overscan cannot cover arbitrary jumps or sustained high-speed scrolling across a 150ms RTT. Bounded queued work does not remove that network floor or establish smoother scrolling.

| Browser / profile / rows | Post-GC JS heap bytes before → after | Initial-to-final heap delta bytes before → after | Post-GC DOM nodes before → after |
| --- | ---: | ---: | ---: |
| chrome / local / 10000 | 2560660 → 2578392 | 1111388 → 1131254 | 194 → 194 |
| chrome / local / 100000 | 2560684 → 2578448 | 1111412 → 1131310 | 194 → 194 |
| chrome / delayed / 10000 | 2567890 → 2545898 | 1119258 → 1098830 | 194 → 194 |
| chrome / delayed / 100000 | 2565118 → 2545864 | 1116486 → 1098784 | 194 → 194 |
| firefox / local / 10000 | unavailable → unavailable | unavailable → unavailable | unavailable → unavailable |
| firefox / local / 100000 | unavailable → unavailable | unavailable → unavailable | unavailable → unavailable |
| firefox / delayed / 10000 | unavailable → unavailable | unavailable → unavailable | unavailable → unavailable |
| firefox / delayed / 100000 | unavailable → unavailable | unavailable → unavailable | unavailable → unavailable |

Heap/DOM probes are Chrome-only retained-state snapshots, include document/runtime/observer overhead, and are not backend memory or leak proofs. Raw trace arrays were copied to results then cleared before final GC. Lazy provider reads separate data-access volume from mounted DOM; ACK latency still combines network, server generation/serialization and delivery. No isolated backend CPU or allocation claim is made. Raw initial resource observations cover HTTP assets and WS payloads; HTML document/framing/TCP/TLS are excluded, so these are not claimed as total wire bytes.

## Correctness, provenance and limits

59 Java tests include provider failures, attachment cleanup, duplicate live keys, lazy replacement, stale revisions, optional token grammar and old-client input. Chrome and Firefox separately pass focus/selection, durable edits, reconnect, disposal, mixed old/new deployment combinations and deterministic initial/request/stale-token/failure/retained-list interleavings. Overlap retains DOM identity; eviction hands focus to the list and disposes rows. Arbitrary components are not reassigned to another record through a DOM pool: that would need a separate renderer/update contract to preserve callbacks and input semantics.

Baseline JAR SHA-256: fa1986f0abb115c827c33bda4c79f23c474241772f84b7e72a7506988e6baa12. Candidate JAR SHA-256: 6661e7453b9e76c36d4cd578eaafd2eaec2a03ffc92d0b03879204f722f9b780. Actual files: build/virtual-list-before.jar and build/virtual-list-after.jar. source-after.json is the measurement-time source snapshot; inventory.json verifies fixture byte identity. runtime-sizes.json records the actual bundle hashes. Pilot and earlier adapter diagnostic files are excluded. No commits, pushes, deployment or new CI actions are part of this pass.

The result applies to fixed-height rows on these two browser builds and this deterministic single-session workload. Variable-height measurement, pooling arbitrary Components, mobile/touch scrolling, backend throughput/allocation/assembly, concurrent users, WAN bandwidth/loss and other JS APIs need separate work and benchmarks. The existing four-app unvirtualized comparison remains unchanged.

Reproduction commands and metric definitions: [benchmark README](../../benchmarks/virtual-list/README.md). Upstream source: [pinned TanStack core](https://github.com/TanStack/virtual/tree/11b48e4ab8b08d483c8fe29a31ef11261ffbda04/packages/virtual-core), [MIT license](../../benchmarks/virtual-list/THIRD_PARTY_NOTICES.md).
