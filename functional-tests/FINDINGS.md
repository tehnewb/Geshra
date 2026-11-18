# Functional findings — first showcase build

2026-11-18, shared server http://localhost:4050/showcase, Chrome 157.0.8103.80 headless. Independent browser contexts; no server or production/demo mutations.

## Confirmed product defects

- **P1 Tree labels and branch mutation:** open showcase, expand the first `+`, then the remaining visible `+`. All four labels are `null` instead of Project/Sources/Main.java/README.md. Click Rename tree branch: branch becomes plain Renamed sources text, removing its caret and descendant DOM. Expected label-only update retaining children and controls. Source: TreeNode.create and setValue; text Span initialization overwrites queued text and setValue targets the outer branch. Evidence: tree-before.html/.png and tree-after.html/.png. Initial named-label test failed before rename; follow-up positional-caret probe confirms both independent failures.
- **P2 Radio server state stale during callback:** fresh page; check Beta. Native Alpha becomes unchecked, Beta checked, but visible Java callback says `Alpha=true; Beta=true`. Expected Alpha=false; Beta=true. Source: ShowcaseForms radio callbacks and runtime radio-peer synchronization; exact ordering cause requires investigation. Native control behavior is correct; immediate Java read is inconsistent.
- **P2 Image description accessibility:** inspect the constructor-only Image. It has `description="Blue Geshra sample illustration"` and no alt. Expected descriptive alt text. Source: Image.create sets description rather than alt. Asset decode failure is separate.
- **P2 document title:** after showcase-ready and a further 500 ms, document.title is Geshra although ShowcaseRoute.load calls ui.setTitle("Geshra UI showcase"). Expected title from that API. Source: UI.setTitle sends title packet; runtime supports RECEIVE_TITLE. Root-cause attribution pending.

## Confirmed showcase/resource failures

- **P1 local resources return HTML:** GET /showcase/sample.svg, sample.wav, sample.webm, and showcase.css all return HTTP 200, content-type text/html; charset=UTF-8, length 355. Expected SVG/WAV/WebM/CSS bytes with appropriate MIME. Image naturalWidth=0; image.decode rejects. Browser refuses stylesheet due to MIME; one failed stylesheet request and console error recorded. Media playback cannot be validated until resources work. Classify as resource delivery/integration failure pending source diagnosis, not image/codec failure.
- **P2 InputField sample overpromises Java feedback:** trusted fill edits native input but data-feedback remains unchanged. InputField is a raw Component rather than ValueComponent and has no value-sync registration. Expected either a supported callback demonstration or accurate disclosed limitation. Do not infer missing ValueComponent behavior as an InputField regression without an API contract.

## Test defects and coverage limits

- Progress selector matched both Determinate and Indeterminate labels by substring. Corrected to exact label matching; original graphics failure was a test defect, not a product defect. Retest pending.
- The grouped radio test stops on its callback mismatch, leaving range keyboard and multiselect checks unexecuted in the first sweep; run independently next.
- Every concrete source type is declared by rendered data-types: exact set comparison passed (113). This establishes sample presence, not functional pass for 113 components.
- Existing first sweep passes are stored individually in showcase-results.json. No blanket all-pass claim. Tree keyboard/selection, virtual edit/reconnect/disposal, media playback and broader edge cases remain outstanding.

## Ownership and readiness

Evidence is persisted in functional-tests/. Relevant Tree reproductions are complete; the first audited rebuild may proceed when coordinated by root. This tester has changed only its owned functional-tests directory. No production/demo fixes, server restarts, benchmarks, commits, or dependency installs.

## Follow-up verification and rebuild handoff

First automated sweep is COMPLETE and browser processes launched by this tester are closed. Safe for root-coordinated audited rebuild/restart now. Shared server remains running and untouched.

- 22 first-sweep cases passed; 5 failed, including one corrected test-selector failure. First-sweep results remain preserved rather than overwritten.
- Independent followup-results.json confirms trusted Range End key reaches 100 with Java 100.0; MultiSelect preserves comma and Japanese values; Select empty option yields empty Java feedback.
- Canvas circle center pixel is RGBA [22,163,74,255]; all eight SVG descendants have the SVG namespace; progress completes to 100 and resets to 40.
- Audio and video both have readyState=0 and demux errors, consistent with HTML resource bodies. Playback remains blocked by resource delivery.
- Tab buttons have no role or aria-selected attributes. Pointer tab switching passed; full expected tab keyboard/accessibility semantics need separate evaluation, not an assumed pass.
- Existing browser-tests/components.cjs PASSED against /components on shared port 4050, including its value read-back and virtual-list/lifecycle checks.
- Existing browser-tests/protocol.cjs PASSED: rejected=6, clickBytes=10, scriptExecuted=true. These are correctness runs, not timed benchmarks.
- Radio cause is source-supported: events.js input handler sends SEND_VALUE_CHANGE before synchronizeRadioPeers/flushValueSynchronization, so the selected radio callback executes before unchecked peer state reaches Java. A later read can be correct while immediate callback feedback is stale.
- Independent scripts showcase.cjs/followup.cjs are tester-owned, uncommitted correctness probes. Request AUDITOR review before treating these new scripts as maintained regression assets. They are not installed into CI or package scripts.
