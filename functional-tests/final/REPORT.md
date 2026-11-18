# Final showcase functional verification

2026-11-18, http://localhost:4050/showcase. PID **8004**, JAR SHA256 **6E16CCC113F6DA7891FF98A2E1CAE8C1AA095EFE2C40D7D4E89B5A2C85E85608**. Test runners independently read launch metadata and PID file, hash the actual JAR and reject disagreement. This is disk identity verification, not an independent attestation of the running process command line. Root supplied the live server identity.

Chrome **157.0.8103.80** and Firefox **157.0**, Windows, separate headless contexts with trusted keyboard/pointer inputs. Each engine passed **28 core + 11 extended cases** (78 executed case outcomes total). Console/page/request diagnostics recorded by the core suites are empty; extended page/request errors are empty. All tester browser sessions closed. Server untouched; no production/demo files changed, dependency installs, benchmarks or shutdown performed.

## Narrow-patch outcomes

| Change | Verified outcome in both engines |
| --- | --- |
| Password feedback | Entered `secret-test`; exact feedback `Password updated (11 characters)`. Plaintext absent from feedback and visible body text. The input retains its native password behavior. Initial instructions no longer promise a Java value read-back. |
| Showcase Select | Value is `java` under **explicit configuration**. Passing case is named `explicit configured Select Java value`. This does not resolve the general grouped-native-default bug. |
| WAV MIME | HTTP 200, audio/wav, exactly 32044 bytes; trusted Space playback, currentTime > 0.05, Java play/pause feedback and native paused=true after Java pause. |
| WebM MIME | HTTP 200, video/webm, exactly 5418 bytes; same trusted playback/advancing-clock/pause checks; no media error. |
| Multiselect assertion strengthened | Exact Java feedback `Java value: [a,b, 日本語]` and exact selected DOM value array `["a,b", "日本語"]`, rather than a generic feedback-prefix check. |

The full core/extended smoke was rerun on the final JAR, including Tree label/rename/selection modes, Image alt/decode, immediate radio callback peers, title, generic InputField callback, untouched form defaults, checkbox label activation, upload, overlays, tables/tabs, graphics/status, virtual list jump/refresh, retained-node lifecycle, listener removal, and 390px overflow. Both mobile document widths are exactly 390 with no overflowing cards. Static-semantic checks support observed type coverage rather than every-method certification.

## Evidence

- `chromium/showcase-results.json` and `firefox/showcase-results.json`: each case and actual build identity.
- `chromium/extended-results.json` and `firefox/extended-results.json`: explicit Select configuration, exact media MIME/body length, media currentTime, tree modes, static semantics, mobile/keyboard outcomes.
- Each engine directory also contains rendered-samples.json, full-page desktop/mobile captures and mobile-390.json.
- `TYPE-COVERAGE.md`: all 113 source concrete types, observed states and explicit gaps, carried forward with final-patch dispositions. Core inventory checks compare the exact sorted type sets.
- Prior full regression runs against unchanged runtime behavior: existing components.cjs/protocol.cjs/optimizer-protocol.cjs passed in both engines on prior corrected build, as recorded in ../corrected/REPORT.md. Java 65 / consumer 2 passing tests are owner-reported, not rerun by this tester.
- First-build evidence remains in parent directory; intermediate build evidence and original failing grouped-Select results remain in ../corrected/. No historical results were overwritten by final runs.

## Open and unverified

- **OPEN P2: untouched grouped-Select default ordering.** Earlier build produced DOM value empty and selectedIndex=2 (Empty value) despite first option Java. Exact Chrome/Firefox evidence: ../corrected/select-default.json and failing extended-results.json. Initial Java value was not observable from the demo and was never inferred. Final explicit setValue is a demo workaround. No product resolution claimed.
- **OPEN accessibility:** Tree label/caret controls remain outside native keyboard tab order; no complete Arrow/Home/End tree navigation. Tabbed native buttons support Enter/Space but lack ARIA tab/tabpanel/aria-selected and composite arrow navigation. Passing native-button tests do not certify composite accessibility.
- **OPEN documented limitation:** TableSelectionMode is policy-only; no interactive row selection. No nonexistent Menu, table sorting/filtering/pagination or download API is claimed.
- **UNVERIFIED:** Firefox native picker popup appearance, touch input and OS picker visuals; virtual-list editable-row focus/reconnect/dispose/empty/dynamic-source cases absent from this showcase; upload size/error paths; media seek/mute actions; dynamic SVG updates. Existing Java tests do not substitute for those browser cases. A focused fixture would be needed to exercise them.
- **UNRESOLVED hypothesis:** generic UI.setTitle versus route SEO ordering outside the explicit showcase metadata fix. Showcase title passes; no broader library fix claimed.

## Auditor handoff

Please relay test-owned `showcase.cjs`, `extended.cjs`, `build-identity.cjs`, `followup.cjs`, `select-default.cjs` and this report for final re-audit. Audit-requested fixes are implemented: password count/no-echo assertions, explicit screenshot-failure recording instead of swallowing, actual artifact hash/PID verification, exact MultiSelect feedback/value assertions, nested finally closure despite evidence-writing failure, and nonzero assertion-failure exit codes. `followup.cjs` and `select-default.cjs` are historical **observation scripts**, not assertion suites or current-build acceptance tests; do not rerun them to overwrite original evidence. They are retained for reproducibility with their original fixtures.

Functional testing is finished with the above explicit limits. Await final AUDITOR disposition; shutdown remains a root-owned coordination action.
