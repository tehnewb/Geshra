# Deterministic split-initialization regression design

Source-reviewed 2026-11-18; no browser launched for this design. Fixture request sent to CODER TASKS. Execution requires the next audited build hash.

The failure occurs when one decoded DOM batch flushes a normalized native value back to Java, then a later batch applies INITIAL_VALUE (type 34). If the later runtime assumes Java still knows the desired value and suppresses the final silent acknowledgement, native and Java values diverge. A selected-option/default test alone does not deterministically prove that the intermediate normalization reached Java.

Use two actual Java-owned controls and two separate user-driven server actions. No direct DOM assignment, source-only runtime import, mocked socket, or intercepted Java getter is required.

1. Select starts with an explicit existing value javascript. NumberField starts with 42. Dedicated read actions expose their actual Java getter values and value-change callback counts. Wait for Java and DOM agreement at baseline.
2. First action queues real SET_PROPERTY(value=missing) on Select, and SET_PROPERTY(value=invalid) on NumberField, without assigning Java values. Native Select normalizes to empty/no-selection; native number sanitizes to empty. Production flush sends silent synchronization through the real socket and Java ValueSyncPacketHandler.
3. Click Java read actions and assert intermediate Java empty for Select, null for NumberField, and callback count 0. Capture their native values, selectedIndex and exact outgoing SEND_VALUE_SYNC bytes/decoded IDs/values. This proves the intermediate mutation was observed by the real receiver rather than merely arranged in the browser.
4. A separate action queues real INITIAL_VALUE type 34 with javascript and 42. It MUST queue the command directly rather than call setValue, because mutating Java to the desired value would hide the regression. Since action 2 was observed at Java before this action, runtime delivery is deterministically split across separate decoded batches.
5. Read actual Java getters again: javascript and 42. Assert native agreement, exact final silent sync frames for both IDs, no SEND_VALUE_CHANGE for those IDs, and callback count 0. Record incoming server DOM packet boundaries around each phase and the actual build identity.

This is a controlled replay of delayed initialization through real production binary delivery and the real Java receiver. It does not claim that the original route-construction packet scheduler is deterministic. A separate socket interception/fragmentation test could validate original batch segmentation but is unnecessary to distinguish the faulty final acknowledgement path here.

The NumberField is deliberately sanitized with an invalid numeric property, making the non-select check a real native-normalization case. Its intermediate Java null must be observed before INITIAL_VALUE42; reading only its final desired value is insufficient.

Expected wire behavior: normalized intermediate and final values travel via outgoing action 10 (SEND_VALUE_SYNC), compact envelope 12 where negotiated; user value-change action 4 must remain absent for both IDs. Read/action buttons have their own click frames and do not count as input callbacks. Production packet reader and handler remain in use.

Fixture selectors/actions requested: data-regression=split-select and split-number; control aria labels matching; Read split-select / Read split-number; data-regression-feedback names; Deliver split normalization and Deliver delayed initial values buttons. Exact names remain provisional until source exists. New maintained CJS will be audited before acceptance and gated by EXPECTED_BUILD_SHA.

Native initial semantics remain separate: untouched size>1 and multiple selects should have no forced selection; explicit property should survive final option construction; no-selection and selected empty-valued option require distinct index/selectedOptions assertions. Prior failed snapshots remain preserved.
