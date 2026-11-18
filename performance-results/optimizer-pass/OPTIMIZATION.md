# Startup and browser-cache optimization pass

This bounded pass changes production cache validation and initial-route bootstrap. It preserves the original matched competitor comparison and fixtures, and measures the saved baseline JAR against the candidate. No production compression or bulk DOM rewrite is included.

Machine: AMD Ryzen 9 7945HX with Radeon Graphics, win32/x64; Chrome 157.0.8103.80; Node v24.14.0; Java 21 for both application JARs and JMH.

Baseline runtime artifact SHA-256: `3469cd8a938ca5cd1f48861b09589eecbcc1aaecf8e20fb5674ec647c458a937`. Candidate: `f59255d031d96c55c64d6b7760538566c4339bfb8f6e8fa16c15af20ac72aa7c`. Full inventories and source hashes are retained beside this report.

## Shipped changes

- Immutable packaged non-HTML assets receive precomputed weak SHA-256 validators, browser-private `no-cache` policy, `Vary: Accept-Encoding`, and bodyless 304 responses. Hashing happens once on cache insertion, not on a cache hit. A changed deployed body changes its tag; pages, session responses, live files, errors and streamed content remain `no-store`. No long-lived freshness or stale-asset promise is introduced.
- New documents advertise bootstrap support. A new client can include a bounded initial path and explicit compact-reader capability during the same-origin/session-validated WebSocket handshake. The server renders immediately through the exact built-in route path, acknowledges before DOM output, and the client omits the otherwise redundant route request.
- Custom route handlers retain their original packet contract. Changed paths during handshake are navigated. Rendering failures signal an explicit 1011 recovery close; the client rebuilds through plain `/ws` and drops queued events with stale component IDs. Long paths and legacy/rolling-downgrade servers retain plain-socket negotiation and timeout recovery.

## Matched before/after conditions

Each version has 16 measured visits: local and 75 ms-per-direction application-stream delay / CPU 4 profiles, cold and warm browser cache, four repetitions per condition, plus one excluded complete workload warmup per profile. Same production fixture, visible native controls/CSS, 1,000 rows, operation sequence, browser, viewport and TCP relay implementation. The relay applies delay to both HTTP and WebSocket traffic and verifies its applied setting before/after each visit. Server/JIT caches remain warm; cold means browser cache, not server cold start.

Runs were sequential **baseline→candidate**, not counterbalanced across versions. CPU frequency, thermal state and background drift were not controlled. Four-sample p95 is the observed maximum, and tiny local timing differences are not established speedups. Large controlled-delay effects and deterministic byte/body reuse are the stronger evidence. Competitor builds/settings were not changed or rerun for this pass.

Values below are median / nearest-rank p95 (count). Readiness is complete matched content plus two animation frames, not display scanout or field Web Vitals.

| Profile | Cache | Ready before ms | Ready after ms | Median change | Initial downstream before bytes | After bytes |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| local | cold | 42.0 / 48.1 (4) | 44.7 / 49.5 (4) | 6.3% | 10485 / 10485 (4) | 10933 / 10933 (4) |
| local | warm | 33.9 / 36.4 (4) | 33.3 / 33.3 (4) | -1.9% | 10488 / 10488 (4) | 2586 / 2586 (4) |
| latency-cpu | cold | 723.8 / 736.2 (4) | 564.5 / 568.4 (4) | -22.0% | 10489 / 10489 (4) | 10937 / 10937 (4) |
| latency-cpu | warm | 694.7 / 700.5 (4) | 538.1 / 539.6 (4) | -22.5% | 10492 / 10492 (4) | 2590 / 2590 (4) |

Initial stream bytes include HTTP headers and WebSocket bootstrap, excluding TCP/IP/TLS and close traffic. Cache validators still require network validation; this does not achieve fingerprinted-asset zero-request warm startup.

## Rendering, body reuse and workload costs

| Profile | Cache | LCP before/after ms medians | CLS before/after medians | HTTP304 responses before/after | Initial Script before/after ms medians |
| --- | --- | ---: | ---: | ---: | ---: |
| local | cold | 40.0 / 40.0 | 0.0000 / 0.0000 | 0 / 0 | 3.4 / 3.6 |
| local | warm | 30.0 / 28.0 | 0.0000 / 0.0000 | 0 / 8 | 1.1 / 0.9 |
| latency-cpu | cold | 720.0 / 560.0 | 0.0000 / 0.0000 | 0 / 0 | 18.5 / 15.8 |
| latency-cpu | warm | 688.0 / 528.0 | 0.0000 / 0.0000 | 0 / 8 | 5.9 / 5.7 |

Chrome CDP may expose a successfully revalidated cached response as 200; raw body transfer sizes and retained cache flags are also available. Zero CLS is limited to this scripted observation. CDP script counters include the measured orchestration window and do not isolate server CPU.

| Profile | Operation | Before DOM ms | After DOM ms |
| --- | --- | ---: | ---: |
| local | Increment | 6.7 / 7.2 (24) | 6.8 / 7.2 (24) |
| local | Create 1000 | 26.9 / 27.9 (8) | 27.5 / 28.5 (8) |
| local | Update tenth | 7.5 / 8.1 (8) | 7.7 / 9.6 (8) |
| local | Clear | 12.4 / 12.9 (8) | 12.5 / 12.8 (8) |
| latency-cpu | Increment | 164.7 / 168.4 (24) | 163.6 / 168.5 (24) |
| latency-cpu | Create 1000 | 261.2 / 269.6 (8) | 264.3 / 280.8 (8) |
| latency-cpu | Update tenth | 167.8 / 172.4 (8) | 164.7 / 172.7 (8) |
| latency-cpu | Clear | 190.3 / 193.8 (8) | 188.8 / 195.2 (8) |

| Profile | Whole-workload upstream before/after bytes medians | Downstream before/after bytes medians |
| --- | ---: | ---: |
| local | 234 / 234 | 78509 / 78509 |
| latency-cpu | 234 / 234 | 78509 / 78509 |

Bulk downstream UI mutation bytes are a remaining bottleneck. These changes do not compress row commands or promise a bulk-render speedup. Ordinary operation timing shifts must be read with the sequential-run limitation above.

## Compression assessment: measured, not enabled

Separate JMH fixture: production DOM serialization for 1,000 native two-cell rows, 53,495 bytes in one compact frame. This is a separate constructed row fixture, not the exact matched app (which includes additional row attributes). Raw DEFLATE resets context per frame, reuses output/native compressor state, and excludes initial component construction. Two fresh forks, 3×500 ms warmup, 5×500 ms measurement, GC profiler, Java 21. No browser workload ran during the retained isolated compression measurement.

| Level | Mean µs/group | JMH error µs | Measured Java allocation B/group | Compressed fixture bytes |
| --- | ---: | ---: | ---: | ---: |
| 1 | 202.24 | 24.95 | 2.77 | 17347 |
| 6 | 1240.17 | 49.18 | 17.02 | 14873 |

Level 1 saves most of the fixture bytes at much lower CPU cost than level 6. This does not establish an end-to-end WebSocket implementation benefit: native compressor memory, context creation, browser inflate, fragmented-message handling, selective control-frame policy, backpressure and concurrent-session behavior were not measured. Small GC-profiler B/op readings are steady-state measurements, not a zero-native-memory claim. Global compression remains disabled; no new production dependency or per-connection compression context was added.

## Verification and remaining limits

- 54 library tests and 2 Spring consumer tests; Chrome and Firefox protocol/native-control/auth suites; explicit ETag/gzip/HEAD/304/deployment-change checks; real-server initial bootstrap, path-change recovery, acknowledged render failure and old-server fallback. The recorded run commands and final audit disposition accompany delivery.
- Original four-app, 64-visit competitor artifacts remain separate; this pass compares only the saved Geshra baseline and candidate using the unchanged matched fixture. No new ranking against competitors is established.
- No backend allocation/heap/assembly claim for startup/cache changes; they primarily remove network work. Native compression state and browser decoding remain unmeasured.
- Warm assets still revalidate; further improvement would need safe content-addressed resource URLs. Bulk creation, row-command volume, sustained receive backpressure, physical devices, WAN bandwidth/loss, TLS/DNS, multiple users and field percentiles remain outside this bounded pass.

## Reproduce

Provenance: source-after.json is the measurement-time source snapshot. Subsequent source edits changed documentation and Javadocs only; the minified runtime bundle is unchanged. The preserved candidate JAR and its recorded SHA-256 identify the actual measured binary.

Preserved baseline JAR: build/optimizer-baseline/matched-geshra.jar. Preserved measured candidate: build/optimizer-candidate/matched-geshra.jar. Run the existing matched harness with COMPARISON_APPS=geshra and select the saved/current backend using COMPARISON_GESHRA_BACKEND when starting proxy.cjs. Each raw run records COMPARISON_LABEL and the SHA-256 of COMPARISON_ARTIFACT; select distinct COMPARISON_OUTPUT paths. Build/server commands remain in benchmarks/comparison/README.md.

Compression: `./gradlew benchmark -PbenchmarkInclude=geshra.benchmark.CompressionBenchmark.* -PbenchmarkOutput=performance-results/optimizer-pass/compression-jmh.json` (PowerShell quotes each -P argument; Windows uses gradlew.bat). Correctness: browser-tests npm test; BASELINE_URL=http://localhost:4111 enables saved-old-server checks.

HTTP semantics: [RFC 9110 conditional requests](https://www.rfc-editor.org/rfc/rfc9110.html), [RFC 9111 browser/private caching](https://www.rfc-editor.org/rfc/rfc9111.html).
