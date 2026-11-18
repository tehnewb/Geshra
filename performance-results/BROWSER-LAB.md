# Geshra browser lab

Recorded 2026-11-18T00:02:11.800Z; browser 157.0.8103.80, win32/x64, AMD Ryzen 9 7945HX with Radeon Graphics. Headless local lab, current candidate only.

Source HEAD: `4cb01d23b71fac5a3c8a3c2c36d94256d0550361`; source SHA-256: `be6287106b79fe8189651657381bd667abb590d0d6a4ebab726f792071592c7c`. This fingerprint includes uncommitted maintained sources and harness files at run start.

72/72 matrix fixtures completed; 0 failed; 72 planned. A completed fixture is not a claim of zero console/network/accessibility findings. 3 repetitions per condition; nearest-rank p95 with fewer than 20 samples is the maximum, not a reliable population tail estimate.

## Explicit matrix

2 routes (home, component gallery), 2 viewports (1440×900 desktop and 390×844 touch/mobile-sized at DPR2), 3 profiles, cold/warm cache, 3 repetitions = 72 planned loads. Mobile settings emulate viewport/touch/CPU and retain the desktop browser user agent; this is not a physical phone. Cold means fresh context plus cleared browser cache; warm means an unmeasured same-context route visit before measurement. The server process and packaged static cache remain warm in both.

| Profile | HTTP latency ms | Download bytes/s | Upload bytes/s | CPU multiplier |
| --- | ---: | ---: | ---: | ---: |
| local | 0 | -1 | -1 | 1 |
| fast | 40 | 1250000 | 125000 | 2 |
| slow | 150 | 200000 | 93750 | 4 |

-1 throughput disables the CDP bandwidth limit. Emulated HTTP latency is not labeled WAN RTT or applied WS delay.

## Page and interaction results

Values are median / p95 (finite sample count), in milliseconds except CLS. Load LCP is the latest observed entry after route-ready +750ms and before interactions. CLS uses the largest session window (gaps <1s; duration <5s), excluding recent-input shifts. Event Timing interactions come from trusted browser clicks/typing; durations below the requested 16ms observation threshold can be absent. Lab INP is the observed interaction-duration estimate during this short session, not field INP or callback RTT.

| Route | Viewport | Profile | Cache | TTFB ms | LCP ms | CLS | Lab INP ms | Callback visual proxy ms |
| --- | --- | --- | --- | ---: | ---: | ---: | ---: | ---: |
| / | desktop | local | cold | 1.7 / 2.3 (3) | 44.0 / 92.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.2 / 23.4 (9) |
| / | desktop | local | warm | 1.7 / 1.8 (3) | 28.0 / 32.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.2 / 23.7 (9) |
| / | desktop | fast | cold | 40.8 / 40.8 (3) | 164.0 / 164.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 39.1 / 48.8 (9) |
| / | desktop | fast | warm | 40.7 / 40.8 (3) | 144.0 / 148.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.8 / 48.0 (9) |
| / | desktop | slow | cold | 150.7 / 150.7 (3) | 408.0 / 416.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.4 / 46.1 (9) |
| / | desktop | slow | warm | 150.7 / 150.8 (3) | 380.0 / 384.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.5 / 46.8 (9) |
| / | mobile | local | cold | 1.4 / 2.0 (3) | 44.0 / 44.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.3 / 23.8 (9) |
| / | mobile | local | warm | 1.5 / 1.9 (3) | 28.0 / 32.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.2 / 23.5 (9) |
| / | mobile | fast | cold | 40.8 / 40.9 (3) | 164.0 / 184.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.7 / 39.5 (9) |
| / | mobile | fast | warm | 40.7 / 40.7 (3) | 152.0 / 160.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.1 / 48.0 (9) |
| / | mobile | slow | cold | 150.8 / 150.8 (3) | 416.0 / 420.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 37.8 / 47.6 (9) |
| / | mobile | slow | warm | 150.7 / 150.8 (3) | 380.0 / 380.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 38.1 / 46.3 (9) |
| /components | desktop | local | cold | 1.3 / 1.5 (3) | 56.0 / 140.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.3 / 23.5 (9) |
| /components | desktop | local | warm | 1.5 / 1.5 (3) | 28.0 / 32.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 22.8 / 23.3 (9) |
| /components | desktop | fast | cold | 40.7 / 40.8 (3) | 424.0 / 424.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 41.2 / 49.1 (9) |
| /components | desktop | fast | warm | 40.7 / 40.8 (3) | 356.0 / 360.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 39.9 / 48.0 (9) |
| /components | desktop | slow | cold | 150.8 / 151.1 (3) | 896.0 / 912.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 46.6 / 58.3 (9) |
| /components | desktop | slow | warm | 150.7 / 150.9 (3) | 752.0 / 768.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 46.4 / 58.6 (9) |
| /components | mobile | local | cold | 1.1 / 1.3 (3) | 52.0 / 56.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 22.9 / 23.6 (9) |
| /components | mobile | local | warm | 1.3 / 1.5 (3) | 32.0 / 36.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 23.1 / 23.5 (9) |
| /components | mobile | fast | cold | 40.7 / 40.8 (3) | 416.0 / 424.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 47.4 / 58.9 (9) |
| /components | mobile | fast | warm | 40.7 / 41.2 (3) | 380.0 / 384.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 39.9 / 55.5 (9) |
| /components | mobile | slow | cold | 150.8 / 150.8 (3) | 904.0 / 908.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 46.6 / 67.2 (9) |
| /components | mobile | slow | warm | 150.7 / 150.8 (3) | 768.0 / 772.0 (3) | 0.0000 / 0.0000 | 16.0 / 16.0 (3) | 44.9 / 62.2 (9) |

Callback visual proxy spans the trusted click handler to a server-driven DOM mutation plus two animation frames. It approximates presentation, not measured display scanout; it is kept separate from INP. Three callbacks per document yield up to 9 callback samples per condition. Real keyboard input adds five characters on gallery visits.

## Rendering and network evidence

| Profile (all routes/viewports/cache states pooled) | FCP ms | DCL ms | Load ms | JS script ms | Style ms | Layout ms | Observed blocking ms |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| local | 32.0 / 92.0 (24) | 9.8 / 20.8 (24) | 9.9 / 20.8 (24) | 3.0 / 7.6 (24) | 0.4 / 9.2 (24) | 0.7 / 11.6 (24) | 0.0 / 0.0 (24) |
| fast | 184.0 / 424.0 (24) | 141.7 / 163.4 (24) | 141.9 / 163.6 (24) | 6.6 / 16.6 (24) | 0.8 / 20.2 (24) | 1.5 / 28.1 (24) | 0.0 / 7.0 (24) |
| slow | 420.0 / 908.0 (24) | 412.3 / 429.9 (24) | 412.6 / 430.4 (24) | 14.7 / 34.9 (24) | 1.6 / 38.4 (24) | 3.3 / 60.0 (24) | 0.0 / 70.0 (24) |

Pooling is descriptive only; compare individual conditions above for controlled comparisons. CDP duration counters are deltas over measured navigation through the settle interval. Long-task excess over 50 ms is an observed lab blocking indicator, not Lighthouse TBT (no TTI algorithm). Raw data includes FP, DNS/connect/TLS, request TTFB, application-ready time, all resource encoded/decoded/transfer sizes, HTTP waterfall/status/cache/encoding, WS handshake times and frame bytes. Loopback DNS/TLS zeros do not predict Internet DNS/TLS costs.

HTTP requests across matrix: 324; cache-served flags: 72; HTTP content-encoding gzip responses: 216; error/status>=400 requests: 36; console/page errors: 72; horizontal-overflow samples: 0. Console scope: this retained run also includes warm-prime console messages; do not compare its console count directly with measured-request failures. The current harness clears priming errors before measurement.

Request failures/statuses:

```json
[
  {
    "url": "http://localhost:4040/favicon.ico",
    "status": 404
  }
]
```

Supported PerformanceObserver entry types:

`element, event, first-input, interaction-contentful-paint, largest-contentful-paint, layout-shift, long-animation-frame, longtask, mark, measure, navigation, paint, resource, soft-navigation, visibility-state`

## Separate diagnostic results

### websocketThrottle: passed

| Experiment | DOM mutation ms median/p95 | Visual proxy ms median/p95 |
| --- | ---: | ---: |
| native-local | 6.5 / 6.9 (10) | 22.9 / 23.4 (10) |
| native-http400ms | 6.7 / 7.2 (10) | 23.2 / 23.7 (10) |
| relay-75ms-each-way | 163.3 / 169.2 (10) | 165.2 / 174.3 (10) |

The 400 ms HTTP experiment isolates latency with CPU 1 and unlimited bandwidth. Its native WS callback results show whether that installed CDP command affects WS messages. The separate real-server relay injects 75 ms per direction, preserving message order for this sequential workload; it is not a TCP loss, bandwidth, fragmentation, or congestion model.

### recovery: passed

```json
{
  "reconnect": {
    "mutationMs": 6.300000011920929,
    "visualProxyMs": 22.900000035762787
  },
  "offlineQueuedCallbackReplayed": true,
  "online": {
    "mutationMs": 7.099999964237213,
    "visualProxyMs": 23.599999964237213
  },
  "resume": {
    "mutationMs": 6.699999988079071,
    "visualProxyMs": 23.19999998807907
  },
  "visibilityState": "visible",
  "backgroundVisibility": "not simulated; renderer frozen/active was tested, OS sleep and real background scheduling remain untested"
}
```

### memory: passed

Forced-GC heap used: 2440140 → 2655088 bytes across 12 same-document home/gallery cycles. DOM nodes: 411 → 411; JS listeners: 74 → 74. Growth may include warmup/JIT/harness caches; this is not proof of either a leak or its absence.

```json
{
  "detachedConnected": false,
  "restoredIdentity": true,
  "restoredValue": "retained lab value",
  "staleSends": 0,
  "disposedWeakReferenceCollected": true
}
```

### rendering: passed

Ten trusted wheel actions: 17 live rows, final scrollTop 5000, frame interval ms 8.3 / 8.4 (144). The trace/screenshot run includes instrumentation and is not compared with matrix timings.

```json
{
  "audit": {
    "title": "Geshra",
    "language": "en",
    "missingControlNames": [
      {
        "tag": "INPUT",
        "type": "checkbox"
      },
      {
        "tag": "INPUT",
        "type": "number"
      },
      {
        "tag": "INPUT",
        "type": "search"
      },
      {
        "tag": "INPUT",
        "type": "url"
      },
      {
        "tag": "INPUT",
        "type": "tel"
      },
      {
        "tag": "INPUT",
        "type": "time"
      },
      {
        "tag": "INPUT",
        "type": "datetime-local"
      },
      {
        "tag": "INPUT",
        "type": "month"
      },
      {
        "tag": "INPUT",
        "type": "week"
      },
      {
        "tag": "INPUT",
        "type": "email"
      },
      {
        "tag": "INPUT",
        "type": "password"
      },
      {
        "tag": "TEXTAREA",
        "type": "textarea"
      }
    ],
    "overflow": false,
    "keyboardFocus": {
      "tag": "INPUT",
      "name": "Checkbox"
    }
  },
  "traceTotalsMs": {
    "UpdateLayoutTree": 15.556999999999999,
    "Layout": 14.263,
    "Paint": 4.809,
    "EvaluateScript": 1.686,
    "FunctionCall": 35.063000000000024
  }
}
```

The control-name check and native accessibility tree expose potential issues; they do not test contrast, screen readers, focus order completeness, or certify WCAG conformance. Trace durations can nest and must not be summed as exclusive CPU time.

## Separate Firefox subset

Firefox 157.0: 8/8 fixtures completed. unthrottled, fresh context, desktop user agent, viewport size only. No CDP network/CPU/trace measurements apply to Firefox. Two repeats per route/viewport; p95 is the maximum.

| Route | Viewport | LCP ms median/p95 | CLS | Lab INP | Long tasks | Callback visual proxy ms |
| --- | --- | ---: | --- | --- | --- | ---: |
| / | 1440 | 24.0 / 129.0 (2) | unsupported | unavailable / unavailable (0) | unsupported | 10.0 / 18.0 (2) |
| / | 390 | 29.0 / 31.0 (2) | unsupported | unavailable / unavailable (0) | unsupported | 10.0 / 11.0 (2) |
| /components | 1440 | 71.0 / 82.0 (2) | unsupported | unavailable / unavailable (0) | unsupported | 10.0 / 11.0 (2) |
| /components | 390 | 69.0 / 95.0 (2) | unsupported | unavailable / unavailable (0) | unsupported | 10.0 / 10.0 (2) |

Unavailable INP means no qualifying Event Timing entries were observed; it is not zero. Unsupported CLS/Long Tasks are null in the raw data. Firefox subset page errors: 0. Native HTTP/WS profiling remains Chrome-only.

## Scope and remaining tests

- Current-candidate lab characterization only; previous payload artifacts remain preserved, but no comparable throttled baseline was collected.
- No physical mobile device, Safari/WebKit, real WAN/TLS/DNS, OS sleep, real background visibility scheduling, multi-user capacity, packet loss, or network bandwidth shaping of WS messages.
- Actual CDP offline/online with socket closure and queued callback replay, reconnect, frozen/active renderer resume, explicit retain/restore/dispose, and 12 GC-observed route cycles are tested separately.
- Existing Chromium/Firefox protocol/component/auth suites cover native controls, login/logout/cookies, keyboard Escape, virtual-list bounds, missing capability state and late-state downgrade. Their run status is recorded in the accompanying documentation, not inferred from these matrix cases.
- Simulated server-message delay is not server-side Netty backpressure. JVM backpressure regression coverage remains separate; sustained browser receive-bandwidth exhaustion is not tested.
- LCP/CLS capture is bounded to the scripted session; later application behavior, arbitrary customer scripts, scroll-driven content and field user distributions can differ.
- Local diagnostics include mobile screenshot, Chrome trace and sanitized HAR under build/reports/browser-lab. The JSON report is retained alongside matrix results; raw matrix samples support independent inspection.

Metric semantics: [INP](https://web.dev/articles/inp), [CLS](https://web.dev/articles/cls), [CDP Network](https://chromedevtools.github.io/devtools-protocol/tot/Network/), [Playwright WebSocket relay](https://playwright.dev/docs/api/class-websocketroute).
