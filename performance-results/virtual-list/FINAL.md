# Fixed-height virtualization: final bounded policy

Geshra keeps its purpose-built constant-space fixed-height range calculation. It won the matched range-only comparison against pinned TanStack virtual-core 3.19.11 without adding a production dependency or per-dataset browser metadata. The final production policy sends changed ranges immediately up to a configurable hard ceiling of 32 outstanding requests per list, then retains only the latest desired range. Smaller limits (1–31) trade less intermediate work for more waiting. No experimental pause timer or jump-priority machinery remains.

This is a latency-first bound, not a bandwidth optimization or proof that 32 is optimal. The per-list client request ceiling does not bound all server work under arbitrary network conditions or concurrent users. It also does not remove the network round trip, promise smooth arbitrary jumps, support variable-height measurement or establish a universal fastest implementation.

## Final paired measurements

48 passing measured cold visits plus eight separate warmups: six repeats × two versions × two browsers × two profiles, with alternating version order within every cohort. All five application class/resource entries are byte-identical between the saved original and final JARs. Logical rows 100,000; 32px row height, 320px list viewport, four-row overscan; browser viewport 1024×768. AMD Ryzen 9 7945HX with Radeon Graphics, 32 logical CPUs, win32/x64, OS 10.0.26200, Node v24.14.0, Java 21 with -Xms128m/-Xmx512m. Browser versions are recorded per sample. No overlapping build or performance job ran.

Local has no added delay. Delayed applies 75ms each direction to both HTTP and WebSockets; Chrome additionally uses CPU rate 4, Firefox uses delay only and CPU rate 1. Values are ordinary six-run medians except worst observed pending/mounted values. No field percentiles or confidence interval is inferred from six visits.

| Browser / profile | Variant | Initial ready ms | Final-jump settle ms | Viewport requests | Max pending | WS payload down bytes | Provider reads | Max mounted |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| chrome / local | original | 51.5 | 20.1 | 86 | 1 | 237901 | 675 | 18 |
| chrome / local | bounded 32 | 52.3 | 19.3 | 84 | 1 | 257299 | 661 | 18 |
| chrome / delayed | original | 564.0 | 190.0 | 86 | 20 | 232934 | 661 | 18 |
| chrome / delayed | bounded 32 | 564.4 | 189.3 | 84 | 21 | 257299 | 661 | 18 |
| firefox / local | original | 66.0 | 17.0 | 86 | 2 | 237901 | 675 | 18 |
| firefox / local | bounded 32 | 75.0 | 18.0 | 84 | 5 | 257299 | 661 | 18 |
| firefox / delayed | original | 535.0 | 185.0 | 86 | 21 | 232934 | 661 | 18 |
| firefox / delayed | bounded 32 | 539.0 | 191.5 | 84 | 20 | 255540 | 657 | 18 |

Measured costs remain: delayed downstream WS payload increased about 10.5% in Chrome and 9.7% in Firefox. Firefox local readiness rose from 66 to 75ms. No overall speed improvement over the original implementation is established; the selected policy preserves practical final-viewport latency while adding a request bound and correctness protections.

| Browser / profile | Variant | Frame-gap p95 median ms | Largest sampled gap ms | Uncovered viewport samples % | Request-to-ACK ms | Browser script / layout ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| chrome / local | original | 10.1 | 10.3 | 1.2 | 0.5 | 32.2 / 13.5 |
| chrome / local | bounded 32 | 10.1 | 11.2 | 1.2 | 0.7 | 34.6 / 13.6 |
| chrome / delayed | original | 8.6 | 15.4 | 97.6 | 156.2 | 147.4 / 62.7 |
| chrome / delayed | bounded 32 | 8.7 | 15.2 | 97.6 | 158.2 | 169.8 / 67.0 |
| firefox / local | original | 9.0 | 10.0 | 1.2 | 1.0 | unavailable / unavailable |
| firefox / local | bounded 32 | 9.0 | 9.0 | 1.2 | 1.0 | unavailable / unavailable |
| firefox / delayed | original | 9.0 | 10.0 | 97.6 | 152.0 | unavailable / unavailable |
| firefox / delayed | bounded 32 | 9.0 | 11.0 | 97.6 | 152.0 | unavailable / unavailable |

Final-jump settle runs from the last scripted scroll assignment to the actual final row being inside the viewport plus two animation frames. Frame-gap p95 is a within-visit nearest-rank statistic of 84 scripted RAF samples, then medianed across six visits; it is not compositor FPS or interaction latency. Four-row overscan cannot cover rapid jumps or sustained high-speed scrolling over a 150ms RTT. Uncovered viewport samples are a visible limitation, not hidden by lower request counts. Browser script/layout counters include observation overhead and are not server CPU.

| Browser / profile | Variant | Post-GC JS heap bytes | Initial-to-final heap change bytes | Post-GC DOM nodes | Long tasks |
| --- | --- | ---: | ---: | ---: | ---: |
| chrome / local | original | 2560760 | 1111488 | 194 | 0 |
| chrome / local | bounded 32 | 2583406 | 1135566 | 194 | 0 |
| chrome / delayed | original | 2566226 | 1117594 | 194 | 0 |
| chrome / delayed | bounded 32 | 2589068 | 1141298 | 194 | 0 |
| firefox / local | original | unavailable | unavailable | unavailable | unavailable |
| firefox / local | bounded 32 | unavailable | unavailable | unavailable | unavailable |
| firefox / delayed | original | unavailable | unavailable | unavailable | unavailable |
| firefox / delayed | bounded 32 | unavailable | unavailable | unavailable | unavailable |

Heap/DOM snapshots are Chrome-only, include document/runtime/instrumentation, and are not backend-memory or leak proofs. Recorded request/ACK/long-task arrays were copied out and cleared before final GC. Firefox heap and unsupported long-task data are unavailable. Lazy provider reads measure data-access volume; ACK latency combines network and server generation/serialization/delivery. No isolated backend CPU/allocation/assembly claim is made.

## Payload and policy costs

Actual full runtime: 21683 → 24193 raw bytes, 8030 → 8789 gzip bytes (+759 gzip bytes). Added logical-key and request-identity metadata can increase workload traffic. WS totals count message payloads, excluding framing/TCP/TLS. Raw initial HTTP observations cover assets, excluding the HTML document, so no total-wire-byte claim is made. Compression settings are unchanged.

The preserved one-request matrix reduced delayed work and bytes but substantially delayed the final viewport. Immediate two-slot and reserved-slot experiments also left latency costs. These separate artifacts explain the final preference for immediate bounded dispatch; their visits are not pooled into this paired comparison. Limits below 32 remain explicit application configuration choices. The final cap is justified for this workload, not asserted as a universal optimum.

## Correctness and provenance

The lazy indexed provider avoids materializing the whole dataset on either side. Overlapping rows retain component identity and native input selection. Logical keys are separate from component IDs; refresh rebuilds current content/callbacks. Eviction hands focus to the list before sending the request and makes outgoing rows inert. Pending-range intersections protect rows any outstanding request may dispose. All structural DOM batches remain ordered and applied; tokens prevent stale/repeated ACKs from freeing another request. Durable edits/selection belong to the application model. Arbitrary Components are not reassigned through a DOM pool.

59 Java tests cover lazy replacement, stale revisions, bounded keys, provider/attachment failures and optional legacy/compact request identities. Chrome/Firefox dedicated tests cover cap-32 saturation, latest-range release, refresh/reconnect from saturation, disposal releasing request records, partial failure alongside another pending request, repeated ranges with different tokens, old/new deployment combinations, focus/selection and durable edits. Final consumer and complete browser-regression results accompany delivery.

Actual measured baseline: build/virtual-list-before.jar, SHA-256 fa1986f0abb115c827c33bda4c79f23c474241772f84b7e72a7506988e6baa12. Final: build/virtual-list-bounded.jar, SHA-256 9a89198633f13902a253edeaeeb70f1e38ae020f9da98c8dc467f6ad7f115b29. inventory-bounded.json verifies fixture byte identity; source-bounded.json and runtime-bounded.json record measurement-time source/runtime hashes. Earlier source snapshots belong to their explicitly named intermediate variants. Saved binary hashes identify the actual measured artifacts even if documentation changes afterward.

Reproduce with [benchmark README](../../benchmarks/virtual-list/README.md). Range-only selection and upstream license: [range evidence](VIRTUAL-LIST.md), [pinned TanStack source](https://github.com/TanStack/virtual/tree/11b48e4ab8b08d483c8fe29a31ef11261ffbda04/packages/virtual-core), [MIT notice](../../benchmarks/virtual-list/THIRD_PARTY_NOTICES.md). Earlier policy evidence: [immediate two-slot](VIRTUAL-LIST-LATENCY.md), [reserved slot](VIRTUAL-LIST-PRIORITY.md). Existing unvirtualized four-app results are unchanged. Variable-height rows, touch inertia, WAN bandwidth/loss, multiuser server load and unrelated JS APIs require separate measurements. No commits, pushes, deployment or new CI actions are part of this pass.
