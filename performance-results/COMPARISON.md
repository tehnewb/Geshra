# Matched application comparison

Recorded 2026-11-18T00:42:57.871Z; Chrome 157.0.8103.80, win32/x64, AMD Ryzen 9 7945HX with Radeon Graphics, Node v24.14.0. Desktop viewport 1440×900. Current Geshra candidate; separate from earlier gallery/browser-lab runs.

64/64 fixtures completed; 0 failed; 64 planned. 4 repetitions per app/profile/cache condition. Values below are median / nearest-rank p95 (sample count). With four repeats, p95 is the largest observed value, not a reliable population percentile. No statistical universal-speed ranking is claimed.

Fixture source SHA-256: `7fba9118935d5af6a42861ccc71a5fccbf83405939100fd801d4508089ea88f2`. Effective packaged versions, full dependency lists, production metadata and SHA-256 artifact digests are in comparison-inventory.json. Geshra uses its existing Spring Boot 3.4.3 runtime; Vaadin 25.3.5 uses Spring Boot 4.1.2. Both run Java 21. Client versions: React/React DOM 19.3.1, esbuild 0.28.2.

## What is matched

Native unthemed controls and native two-column table; identical shared stylesheet, visible text, data and operation semantics. The initial app shells contain no visible SSR/SEO application content. Seven buttons, one text input, three status paragraphs, 1,000 actual rows when created; no virtualization. Each button performs a real authoritative server operation before displaying the result. Geshra/Flow own native UI components on the server. React/plain DOM consume deltas from the same real session-owned Node service. React uses production createRoot, stable row keys, standard memoized native rows/table and immutable state updates; no dev server, HMR, custom reconciler or React Compiler.

The workload is server-dependent. React can update local-only state without a server round trip; this test does not rank local-state rendering or claim that network work is necessary for every React UI. Geshra/Flow eagerly synchronize typed input; API clients submit the value on Echo. Typing bytes are preserved separately, and whole-workload totals include them. Vaadin uses @NoTheme, full-duplex WS and a forced app-specific optimized production bundle; its general precompiled component bundle is not used. These libraries still offer different features, security/session machinery and backend architectures.

Initial screenshots are checked separately along with common CSS and layout. Diagnostic screenshots and GC interfere with timings and are excluded from the timing matrix.

## Controlled conditions

Each profile has one excluded complete workload warmup per implementation. Each repetition rotates implementation order; with four implementations/four repetitions, every library occupies each order position once per profile/cache condition. All runs are sequential. Cold browser cache uses a fresh context and cleared cache, not a cold server; warm cache primes the same context without measured operations. JIT/server resource caches remain warm. The delayed profile uses the same raw TCP relay for every app, applying 75 ms per direction per chunk to both HTTP and WS, plus 4× browser CPU slowdown. Local has zero added delay and 1× CPU. This is latency emulation, not bandwidth/loss shaping, real Internet RTT or measured physical mobile hardware.

## Initial render and payload

Ready means the complete matched native content plus two animation frames; first successful real server actions validate usability separately. LCP is the latest observed preinteraction entry after a 750 ms settling interval. Initial stream bytes include delivered HTTP headers and WS bootstrap traffic; exclude TCP/IP/TLS and close traffic. JavaScript/CSS/HTML encoded body sizes below use Resource Timing and are separate from stream-byte totals.

| Profile | Cache | App | Ready ms | LCP ms | Initial server→browser stream bytes | Initial HTTP requests |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| local | cold | geshra | 39.7 / 47.3 (4) | 40.0 / 44.0 (4) | 10485 / 10485 (4) | 3 / 3 (4) |
| local | cold | vaadin | 119.9 / 127.1 (4) | 116.0 / 128.0 (4) | 161357 / 161359 (4) | 12 / 12 (4) |
| local | cold | react | 63.0 / 76.7 (4) | 58.0 / 60.0 (4) | 71146 / 71146 (4) | 3 / 3 (4) |
| local | cold | dom | 42.6 / 45.0 (4) | 28.0 / 28.0 (4) | 2174 / 2174 (4) | 3 / 3 (4) |
| local | warm | geshra | 31.4 / 35.4 (4) | 28.0 / 32.0 (4) | 10488 / 10488 (4) | 3 / 3 (4) |
| local | warm | vaadin | 52.0 / 55.5 (4) | 44.0 / 48.0 (4) | 4504 / 4508 (4) | 12 / 12 (4) |
| local | warm | react | 30.8 / 33.1 (4) | 24.0 / 28.0 (4) | 447 / 447 (4) | 3 / 3 (4) |
| local | warm | dom | 29.0 / 31.7 (4) | 26.0 / 28.0 (4) | 447 / 447 (4) | 3 / 3 (4) |
| latency-cpu | cold | geshra | 722.0 / 724.6 (4) | 714.0 / 716.0 (4) | 10485 / 10485 (4) | 3 / 3 (4) |
| latency-cpu | cold | vaadin | 1824.9 / 1909.4 (4) | 1816.0 / 1900.0 (4) | 161356 / 161356 (4) | 12 / 12 (4) |
| latency-cpu | cold | react | 532.3 / 546.2 (4) | 524.0 / 540.0 (4) | 71146 / 71146 (4) | 3 / 3 (4) |
| latency-cpu | cold | dom | 416.9 / 419.9 (4) | 384.0 / 388.0 (4) | 2174 / 2174 (4) | 3 / 3 (4) |
| latency-cpu | warm | geshra | 690.4 / 692.8 (4) | 682.0 / 684.0 (4) | 10492 / 10492 (4) | 3 / 3 (4) |
| latency-cpu | warm | vaadin | 615.8 / 618.8 (4) | 608.0 / 612.0 (4) | 5082 / 5085 (4) | 12 / 12 (4) |
| latency-cpu | warm | react | 245.5 / 251.0 (4) | 230.0 / 244.0 (4) | 447 / 447 (4) | 3 / 3 (4) |
| latency-cpu | warm | dom | 230.7 / 232.2 (4) | 204.0 / 220.0 (4) | 447 / 447 (4) | 3 / 3 (4) |

Cold local body bytes (median encoded / decoded; bodies, excluding protocol headers):

| App | HTML | JS | CSS |
| --- | ---: | ---: | ---: |
| geshra | 331 / 684 | 7822 / 21204 | 286 / 470 |
| vaadin | 959 / 1781 | 153876 / 563393 | 280 / 470 |
| react | 226 / 314 | 69848 / 224697 | 283 / 470 |
| dom | 226 / 314 | 878 / 1914 | 283 / 470 |

Warm-cache results must be considered too: cache policies differ and are preserved, not forcibly equalized. A framework with small cold assets can still transfer more bytes on warm navigation if it does not reuse the HTTP cache. Raw HTTP status/encoding/cache/decoded bytes and every resource are retained.

A cold-start footprint advantage must not be generalized to every transfer window. Compare warm navigation separately, and compare both directions of whole-workload traffic. In this fixture the API clients compress row data rather than transmitting native UI creation commands; bulk downstream bytes and client event bytes therefore show different tradeoffs. The tables retain these costs separately.

## Authoritative operations

Each measured document performs 3 increments, Echo(Ada), create 1,000 rows, update every tenth label (identifiers 1,11,…), select 500, remove 500 and clear. Every final revision and all row contents are verified. These tables pool cold/warm documents within the named profile; they are descriptive, not isolated local-render microbenchmarks. Mutation time is trusted click handler → server-produced revision; visual proxy adds two animation frames and does not prove display scanout. Both remain distinct from INP.

| Profile | App | Increment DOM ms | Create1000 DOM ms | Update tenth DOM ms | Select DOM ms | Remove DOM ms | Clear DOM ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |
| local | geshra | 6.6 / 7.1 (24) | 27.7 / 31.1 (8) | 7.8 / 8.1 (8) | 7.5 / 8.6 (8) | 7.6 / 7.8 (8) | 12.5 / 14.6 (8) |
| local | vaadin | 8.4 / 8.9 (24) | 84.6 / 98.2 (8) | 11.1 / 13.3 (8) | 9.2 / 9.3 (8) | 9.2 / 9.5 (8) | 29.9 / 33.0 (8) |
| local | react | 6.9 / 7.6 (24) | 14.5 / 15.4 (8) | 9.1 / 9.3 (8) | 8.2 / 8.6 (8) | 8.5 / 8.7 (8) | 10.1 / 10.7 (8) |
| local | dom | 7.1 / 7.3 (24) | 9.6 / 10.0 (8) | 8.6 / 8.8 (8) | 7.8 / 8.1 (8) | 8.0 / 8.1 (8) | 8.9 / 9.5 (8) |
| latency-cpu | geshra | 163.2 / 168.3 (24) | 259.1 / 263.8 (8) | 164.9 / 172.9 (8) | 163.8 / 168.4 (8) | 160.1 / 171.1 (8) | 190.8 / 193.7 (8) |
| latency-cpu | vaadin | 166.3 / 176.0 (24) | 424.9 / 559.0 (8) | 175.6 / 184.0 (8) | 170.1 / 175.9 (8) | 174.8 / 179.7 (8) | 260.3 / 267.3 (8) |
| latency-cpu | react | 165.2 / 174.5 (24) | 193.9 / 199.8 (8) | 177.3 / 180.2 (8) | 166.3 / 168.0 (8) | 172.1 / 176.3 (8) | 174.1 / 184.4 (8) |
| latency-cpu | dom | 163.9 / 171.5 (24) | 173.3 / 177.4 (8) | 170.8 / 176.3 (8) | 164.7 / 171.3 (8) | 162.7 / 163.8 (8) | 172.1 / 174.5 (8) |

| Profile | App | Echo DOM ms | Create visual proxy ms | Workload browser→server stream bytes | Workload server→browser stream bytes |
| --- | --- | ---: | ---: | ---: | ---: |
| local | geshra | 7.0 / 7.1 (8) | 31.8 / 39.3 (8) | 234 / 234 (8) | 78509 / 78509 (8) |
| local | vaadin | 9.1 / 9.9 (8) | 102.2 / 122.1 (8) | 407 / 408 (8) | 84223 / 84223 (8) |
| local | react | 7.3 / 8.0 (8) | 27.0 / 30.4 (8) | 6315 / 6315 (8) | 7841 / 7841 (8) |
| local | dom | 7.3 / 8.2 (8) | 27.3 / 28.0 (8) | 6315 / 6315 (8) | 7841 / 7841 (8) |
| latency-cpu | geshra | 156.8 / 160.6 (8) | 276.0 / 280.6 (8) | 234 / 234 (8) | 78509 / 78509 (8) |
| latency-cpu | vaadin | 172.9 / 177.5 (8) | 530.1 / 663.0 (8) | 389 / 390 (8) | 84217 / 84217 (8) |
| latency-cpu | react | 161.5 / 173.9 (8) | 252.7 / 260.7 (8) | 6315 / 6315 (8) | 7841 / 7841 (8) |
| latency-cpu | dom | 161.9 / 172.9 (8) | 232.6 / 244.0 (8) | 6315 / 6315 (8) | 7841 / 7841 (8) |

Whole-workload byte windows include native typing and a 500 ms post-operation tail for protocol acknowledgments. Per-operation byte windows in raw JSON end at the visual proxy; delayed protocol acknowledgments can fall into a later window, so they are not exact lifetime per-action billing. Heartbeat/connection-close traffic outside the window is excluded. HTTP and WS payload formats differ; the common relay counts real application-stream bytes rather than comparing JSON body bytes against framed WS bytes.

Compression is preserved rather than disabled: the API clients receive gzip-compressed JSON. The separate stock-server handshake diagnostic recorded these negotiated WebSocket extensions: geshra=none, vaadin=permessage-deflate;client_max_window_bits=15, react=not used, dom=not used. Thus bulk-transfer results reflect both UI representation and actual compression settings, not a library-independent codec ranking.

## Browser work and responsiveness

| Profile | App | Initial Script ms | Create Script ms | Create Layout ms | CLS | Observed lab INP ms |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| local | geshra | 2.1 / 3.6 (8) | 8.3 / 10.1 (8) | 8.7 / 10.5 (8) | 0.0000 / 0.0000 (8) | 16.0 / 16.0 (8) |
| local | vaadin | 17.2 / 32.5 (8) | 49.6 / 60.4 (8) | 7.2 / 8.4 (8) | 0.0139 / 0.0139 (8) | 16.0 / 16.0 (8) |
| local | react | 10.5 / 25.8 (8) | 7.5 / 9.8 (8) | 7.1 / 8.7 (8) | 0.0000 / 0.0000 (8) | 16.0 / 16.0 (8) |
| local | dom | 0.5 / 0.7 (8) | 0.3 / 0.4 (8) | 7.2 / 7.4 (8) | 0.0000 / 0.0000 (8) | 16.0 / 16.0 (8) |
| latency-cpu | geshra | 12.0 / 20.0 (8) | 38.0 / 39.4 (8) | 39.5 / 41.2 (8) | 0.0000 / 0.0000 (8) | 16.0 / 16.0 (8) |
| latency-cpu | vaadin | 88.3 / 180.4 (8) | 224.2 / 337.7 (8) | 36.2 / 42.1 (8) | 0.0139 / 0.0139 (8) | 16.0 / 16.0 (8) |
| latency-cpu | react | 53.3 / 114.6 (8) | 30.2 / 34.9 (8) | 35.2 / 36.2 (8) | 0.0000 / 0.0000 (8) | 16.0 / 24.0 (8) |
| latency-cpu | dom | 2.1 / 3.4 (8) | 1.8 / 2.2 (8) | 34.0 / 40.1 (8) | 0.0000 / 0.0000 (8) | 16.0 / 16.0 (8) |

CDP duration deltas are browser counters, not server CPU or end-to-end CPU cost. CLS uses largest session windows and excludes recent-input shifts; INP is a short-session observed Event Timing estimate from genuine clicks/typing, not field p75 or asynchronous server latency. Unsupported/missing entries remain unavailable. Raw observations retain long tasks, timings, support flags, resource waterfalls, WS frames and CPU counters.

## Retained browser state and visual checks

| App | Initial DOM nodes → after 6 cycles | JS listeners | Used JS heap bytes | Native bounds/font/title matched |
| --- | ---: | ---: | ---: | --- |
| geshra | 48 → 50 | 30 → 43 | 1574236 → 2497576 | {"x":254,"y":20,"width":932,"height":160}; 14px Arial, sans-serif; Matched benchmark |
| vaadin | 115 → 115 | 211 → 224 | 3530380 → 5008936 | {"x":254,"y":20,"width":932,"height":160}; 14px Arial, sans-serif; Matched benchmark |
| react | 43 → 43 | 166 → 179 | 2023340 → 3549880 | {"x":254,"y":20,"width":932,"height":160}; 14px Arial, sans-serif; Matched benchmark |
| dom | 42 → 42 | 20 → 33 | 1477972 → 2282784 | {"x":254,"y":20,"width":932,"height":160}; 14px Arial, sans-serif; Matched benchmark |

These are forced-GC browser heap/DOM observations after six matched create/clear cycles. They do not measure server session memory, process RSS, peak allocation, GC overhead, or prove absence of leaks. Different framework caches and delegated event systems affect listener/counter totals. Initial and populated-table screenshots are saved under build/comparison-artifacts.

## Failures, interpretation and limits

Measured fixture failures: 0; page errors in completed fixtures: 0; failed/HTTP>=400 requests: 0.

```json
[]
```

- Rankings apply only to these matched server-dependent operations and configurations. The plain DOM implementation is a floor, not a full application framework.
- Vaadin has richer general framework/session infrastructure; Geshra, Flow and React are not globally feature-equivalent. No Grid, themed button, paid component, virtualization, SEO-rendered app or commercial feature is compared against a bare native equivalent.
- Four repeated page samples per condition are small and correlated; per-operation pooled samples are also nested within visits. No confidence-based universal speedup conclusion is supported.
- Different Java/Node server stacks and application implementation choices remain part of the tested systems; this does not isolate library internals. No backend CPU, JVM allocation, per-user server heap, multi-user scalability or server cold-start claim.
- Chrome-only desktop lab; no Firefox/Safari comparison, physical mobile, TLS/DNS WAN, congestion/bandwidth/loss model, energy use, accessibility certification or field-user percentile.
- Initial stylesheet equality and screenshots verify visible fixture parity. Cache behavior and protocol overhead remain real architectural differences rather than being artificially removed.

Build/run instructions: benchmarks/comparison/README.md. Primary references: [Vaadin Gradle production configuration](https://vaadin.com/docs/latest/flow/configuration/gradle), [Vaadin Element API](https://vaadin.com/docs/latest/flow/component-internals/element-api), [React createRoot](https://react.dev/reference/react-dom/client/createRoot), [React memo](https://react.dev/reference/react/memo), [INP](https://web.dev/articles/inp), [CLS](https://web.dev/articles/cls).
