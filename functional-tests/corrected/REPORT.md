# Corrected showcase functional report

Tested 2026-11-18 at http://localhost:4050/showcase. PID 41068; JAR SHA256 independently verified as `14C7C97554C00BA1A73C1B2178A092E2935FFFE48057132CF55B5EE285BEDA41`. Chrome 157.0.8103.80 and Firefox 157.0, Windows, headless browser contexts with trusted Playwright keyboard/pointer interactions. Results concern this exact build; the proposed password-feedback fix has not yet been deployed/retested.

## Results and known-finding dispositions

Both browsers passed all 28 core cases, then 8 of 9 extended cases. Core diagnostics and extended page/request errors are empty. Evidence is under chromium/ and firefox/: showcase-results.json, extended-results.json, rendered-samples.json, mobile-390.json, screenshots. First-build evidence remains separately in the parent functional-tests directory.

Confirmed fixed in both browsers: Tree initial labels, label-only rename with descendants retained, expansion/collapse after rename, immediate radio peer state, Image decode and native alt, showcase title, local stylesheet/resources, generic InputField native event feedback, untouched form defaults, checkbox label activation, and 390px horizontal overflow. Both mobile measurements: document width 390, viewport 390, no overflowing sample cards.

Tree NONE preserves the existing selected set while preventing new selection; tests verify that existing behavior, not an invented clear-on-policy-change requirement. MULTIPLE Ctrl toggle and inclusive Shift range, SINGLE replacement, and post-rename expansion pass.

WAV and WebM truly play: trusted Space activation produces Java play feedback; currentTime advances above 0.05 seconds before Java pause, then native paused=true and Java pause feedback. Observed durations 2 seconds and 2.37229 seconds. Both engines have no media errors. This is stronger evidence than HTTP success or loadedmetadata alone.

Existing components.cjs, protocol.cjs and optimizer-protocol.cjs all PASS in Chrome and Firefox on port 4050. Components checks include Java read-back of normalization/defaults, silent programmatic assignments without extra value-change notification, retained-node identity/listener disposal, overlapping virtual rows and bounded last-item view. No performance benchmarks were run. Java/consumer suites were run by the implementation/audit owners, not independently rerun by this tester.

## Remaining findings

1. **P2 Select/OptionGroup default ordering — reproduced in both browsers.** Fresh `/showcase`, do not edit the `Select,Option,OptionGroup` control. It displays Empty value, input.value is empty. Expected Java, the first option inside Languages, under Select's documented untouched-select first-option behavior. Source: Select.java:7,16,36 and ShowcaseForms.java:58. The sample never calls setValue for this Select. Extended case `checkbox label mixed and native default values` retains this exact failing assertion, with screenshots in both engine directories. Investigation should check attachment/option insertion and native selection timing; root cause is not yet established. Plain Select defaults in existing /components regressions pass, so this is specifically the grouped-first-option composition. User selection and callback synchronization work.

2. **P3 media MIME types — delivery correctness, no observed playback failure.** `/showcase-assets/sample.wav` and sample.webm are delivered as application/octet-stream rather than audio/wav and video/webm. HTTP evidence: build/showcase-http-check.json; asset resolution uses StaticAssetCache's FileType content type. Both browsers successfully sniff/decode/play; classify separately from the resolved HTML-body resource failure.

3. **Password demo plaintext feedback — pending narrow rebuild.** Current tested build reflects the supplied password in its feedback; visual tester reported this and CODER TASKS prepared an audited count/status-only fix. Core PasswordField test currently expects the old text and MUST be changed for the final build. Do not carry the old all-pass count forward as validation of the security fix.

## Coverage and explicit limits

TYPE-COVERAGE.md reconciles all 113 concrete source types individually to their sample, observed meaningful states and limitations. Static semantics are marked OBSERVED, not every-API PASS. Abstract Component/ValueComponent and UI are exercised through concrete descendants/root. Interfaces, enums and package-private associations are support APIs, not extra widgets.

- Table selection is an explicit existing unsupported policy-only feature. No sorting, filtering, pagination or Menu API is invented or claimed.
- Tab buttons support native Enter/Space and pointer activation. They have no ARIA tab/tabpanel semantics or aria-selected, and no Arrow/Home/End navigation implementation is claimed. Tree's clickable labels are not a fully keyboard-navigable ARIA tree. These remain accessibility limitations, not passing tab/tree keyboard-composite tests.
- Nonvisual Script is deliberately empty/inert in showcase; existing protocol test verifies actual script execution. Link loads the stylesheet; HiddenField is invisible with Java read feedback; DataList associates native options and accepts free text, but OS picker visuals are not covered.
- Virtual list showcase covers a million logical items, bounded DOM, trusted wheel, last jump and refresh. It has no editable row, empty/source-switch controls, list-disposal action or reconnect harness. Consequently virtual row edit/focus/reconnect/dispose, empty/dynamic provider boundaries are NOT TESTED by the showcase sweep. Existing correctness sources and owner-reported Java regressions must not be conflated with those browser states. Add a focused fixture if those states are required before completion.
- File upload tests a real 15-byte Unicode text payload and visible callback length/name; contents are not echoed, over-limit and error paths were not exercised. Downloads have no dedicated demonstrated API.
- Media play/pause and real clock progression pass; seek/mute methods are not exposed as demo actions. SVG shapes have native namespaces/tags; dynamic shape changes are absent. Touch and real OS picker interactions were not exercised.
- Temporary extended-test mistakes were corrected: VerticalLayout uses inline flex styles, not a geshra-vertical class; Firefox legitimately serializes range 40 as `40.0`, so its numerical value is asserted. Neither is a product defect. The Select first-option failure was deliberately retained.

## Handoff

The active corrected-build sweep is complete. All tester-owned browser processes are closed. Safe for root-coordinated final narrow rebuild; shared server was not stopped or changed. Recheck password no-echo and the Select finding after fixes, rerun affected core cases and capture new build identity. No commits, dependencies, production/demo modifications, or shutdown performed.

Please relay `functional-tests/showcase.cjs`, `extended.cjs` and `followup.cjs` to AUDITOR for review. They are owned test probes, not CI changes; all named functions have multi-line documentation and scripts were formatted using the existing project Prettier. Case failures now return nonzero process exit status. The original first-build followup is historical and is not a final-build regression runner.
