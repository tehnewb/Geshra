# Fixed-height virtual list: reserved terminal range

This is the preserved reserved-slot experiment, not the current production policy. Its timer and priority code were removed. See [FINAL.md](FINAL.md) for the selected cap-32 default and final paired matrix.

The constant-space native fixed-height range function remains the selected implementation; the separate fair core comparison is in the original report. The initial one-request policy saved work but added a substantial wait after the final jump. This follow-up compares the unchanged original, preserved one-request variant and bounded two-request candidate on the same actual 100,000-row production fixture. Earlier 64 visits and 48 range-subsystem samples remain separate and are not pooled into these results.

The production API exposes `setMaximumPendingRequests(1)` or `(2)`. Two is a bounded pipeline: the browser can send a newer range while one older response is traveling, with only the latest desired range retained after both slots fill. One favors lower traffic. Tokens identify requests across repeated ranges, reconnect and reattachment; all structural DOM batches still apply in server order. Focus and inert protection account for every outstanding eviction. Off-window arbitrary Components are disposed rather than reassigned to another record; application state owns durable edits/selection. Fixed heights only.

The selected candidate reserves its second slot during continuous native scrolling. After 32ms without another scroll event, it can send the latest desired range without waiting for an older response. Explicit scrollToIndex calls can use that reserved slot immediately. One-request mode disables this extra slot. The immediate two-slot diagnostic is preserved in VIRTUAL-LIST-LATENCY.md; it filled both slots in bursts and did not adequately reduce final-jump waiting.

## Matched measurements

72 passing measured cold visits plus 12 separate warmups: six repeats × three variants × two browsers × two profiles. All three fixture class/resource sets are byte-identical. Six orders rotate all three positions, then reverse direction, within every browser/profile cohort. No overlapping performance or build jobs ran. List viewport 320px, rows 32px, overscan four; browser viewport 1024×768. Local has no artificial delay; delayed applies 75ms each direction equally to HTTP and WebSockets. Chrome additionally uses CPU rate 4 for delayed; Firefox has delay only and CPU rate 1. Values are ordinary six-run medians, except worst observed pending/mounted values. This small deterministic sample does not establish field percentiles.

| Browser / profile | Variant | Initial ready ms | Final-jump settle ms | Viewport requests | Max pending | WS payload down bytes | Provider reads | Max mounted |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| chrome / local | original | 51.1 | 20.8 | 86 | 1 | 237901 | 675 | 18 |
| chrome / local | one request | 51.3 | 20.9 | 84 | 1 | 257265 | 661 | 18 |
| chrome / local | two requests | 50.5 | 20.8 | 84 | 1 | 257297 | 661 | 18 |
| chrome / delayed | original | 567.3 | 190.0 | 86 | 17 | 232934 | 661 | 18 |
| chrome / delayed | one request | 560.9 | 300.5 | 10 | 1 | 69388 | 181 | 18 |
| chrome / delayed | two requests | 562.3 | 245.4 | 10 | 2 | 69968 | 183 | 18 |
| firefox / local | original | 72.0 | 32.0 | 86 | 1 | 237901 | 675 | 18 |
| firefox / local | one request | 70.0 | 33.0 | 84 | 1 | 257265 | 661 | 18 |
| firefox / local | two requests | 72.5 | 31.5 | 84 | 1 | 256299 | 659 | 18 |
| firefox / delayed | original | 548.5 | 202.0 | 86 | 16 | 232934 | 661 | 18 |
| firefox / delayed | one request | 552.5 | 280.5 | 11 | 1 | 76510 | 201 | 18 |
| firefox / delayed | two requests | 553.5 | 235.0 | 12 | 2 | 78217 | 205 | 18 |

| Browser / profile | Variant | Sampled frame-gap p95 median ms | Largest sampled gap ms | Uncovered viewport samples % | Request-to-ACK ms | Post-GC JS heap bytes | Long tasks |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| chrome / local | original | 10.1 | 10.4 | 1.2 | 0.7 | 2560664 | 0 |
| chrome / local | one request | 10.1 | 10.2 | 1.2 | 0.8 | 2578500 | 0 |
| chrome / local | two requests | 10.1 | 10.3 | 1.2 | 0.8 | 2587532 | 0 |
| chrome / delayed | original | 10.5 | 14.3 | 97.6 | 161.6 | 2565180 | 0 |
| chrome / delayed | one request | 10.2 | 15.6 | 97.6 | 166.9 | 2546034 | 0 |
| chrome / delayed | two requests | 10.2 | 16.7 | 97.6 | 166.5 | 2554676 | 0 |
| firefox / local | original | 16.0 | 17.0 | 1.2 | 1.0 | unavailable | unavailable |
| firefox / local | one request | 16.0 | 18.0 | 1.2 | 1.0 | unavailable | unavailable |
| firefox / local | two requests | 16.5 | 18.0 | 2.4 | 1.0 | unavailable | unavailable |
| firefox / delayed | original | 17.0 | 17.0 | 97.6 | 156.0 | unavailable | unavailable |
| firefox / delayed | one request | 17.0 | 18.0 | 97.6 | 156.5 | unavailable | unavailable |
| firefox / delayed | two requests | 17.0 | 17.0 | 97.6 | 156.5 | unavailable | unavailable |

Final-jump settle is measured from the last scripted scroll assignment until the actual final row is inside the viewport plus two animation frames. It is not click latency. RAF samples describe this scripted workload, not compositor FPS; uncovered samples during rapid scrolling/jumps remain a visible limitation of four-row overscan over a 150ms RTT. A bounded pipeline cannot remove the network floor. No smoother-scrolling, general fastest-library, or backend CPU/allocation claim follows from reduced request counts.

## Costs, identity and validation

Actual full runtime: original 21683 raw / 8030 gzip bytes; one-request 23658 raw / 8620 gzip bytes; two-request 24578 raw / 8896 gzip bytes. Two-request adds 866 gzip bytes over the original and 276 over the intermediate variant. HTTP/WS compression settings did not change. Raw WS totals count message payloads, excluding framing/TCP/TLS; initial resource observations cover assets, excluding HTML, so no total-wire-byte claim is made.

59 Java tests and the dedicated Chrome/Firefox suite cover optional legacy/compact tokens, lazy replacement, keys, partial provider/lifecycle failure, overlap selection, pre-send focus handoff, durable edits, disposal and both old/new client/server combinations. The deterministic suite also exercises repeated identical ranges with distinct tokens, duplicate ACKs, a partial failure alongside another pending request, reconnect and refresh with both slots occupied, and stale initial/generation acknowledgments. Acknowledged range failures retry once; unrelated successful requests cannot reset that retry budget. Final consumer/browser regression results accompany delivery.

Measured artifacts:
- build/virtual-list-before.jar: fa1986f0abb115c827c33bda4c79f23c474241772f84b7e72a7506988e6baa12.
- build/virtual-list-after.jar: 6661e7453b9e76c36d4cd578eaafd2eaec2a03ffc92d0b03879204f722f9b780.
- build/virtual-list-priority.jar: d66c685308f394048618343cdc37eaf94fdac9e8c88eda1ea9f21f9355556163.

source-after.json/runtime-sizes.json describe the preserved one-request measurement snapshot. source-priority.json/runtime-priority.json describe the two-request candidate. inventory-priority.json verifies all three fixture sets. The actual preserved JAR hashes identify measured binaries even if documentation changes later. Raw latency.json records every session, condition, trace and failure status; priority-summary.json contains these cohort summaries.

The range-only selection evidence, heap limitations, original matrix, upstream pin/license and reproduction instructions remain in [the earlier report](VIRTUAL-LIST.md) and [benchmark README](../../benchmarks/virtual-list/README.md). Chrome-only heap snapshots include document/runtime/instrumentation, have trace arrays cleared before final GC, and are not leak proofs. Firefox heap/unsupported long tasks are unavailable. Variable heights, touch inertia, WAN loss/bandwidth, concurrent users, backend assembly/GC and unrelated JS APIs remain unmeasured. No competitor fixture, compression setting, CI action, commit, push or deployment was changed for this follow-up.
