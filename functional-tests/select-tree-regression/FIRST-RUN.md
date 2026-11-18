# First focused live run — not acceptance

2026-11-18; PID 19636; independently verified SHA256 FA9A9076A0AA62AE0CB03016C9DBAAFAB74C1F747E01EAA78EA1A62BFC82AD0D. Chrome 157.0.8103.80 and Firefox 157.0; isolated session per assertion case, sequential engines.

Evidence: evidence/FA9A9076A0AA62AE0CB03016C9DBAAFAB74C1F747E01EAA78EA1A62BFC82AD0D/{chromium,firefox}/results.json and focused-observations.json. Failure screenshots and full DOM accompany assertion results. Observation probe separately reads all failed fixture Java values and captures actual native value/index/options and focus/tab stops. Initial assertion results remain preserved.

## Confirmed failures in both engines

| Fixture | Native state | Java feedback (zero callbacks) | Expected |
| --- | --- | --- | --- |
| explicit-existing | javascript, index 1 | empty | Java and native both javascript |
| explicit-empty | empty-valued option, index 2 | java | Java empty, native index 2 |
| all-disabled | no selection, index -1 | disabled | Java empty, native no selection |
| listbox-default | size 4, empty-valued option index 2 | empty | no selected option, index -1 |
| multiple-default | multiple=true, empty-valued option index 2 | v | no selected option, index -1, no forced selection |
| explicit-property | empty, index -1 | empty | property-authored javascript, index 1 |

Tree initial Tab entry fails: Project/Sources/Main.java/README.md all tabindex=-1. Trusted Tab from Tree selection mode goes directly to Read tree selection. Consequently both Tab-entry and navigation-started-from-Tab cases fail. Directly focused tree navigation, selection, pointer and lifecycle cases work; this is not a blanket keyboard failure.

## Passed meaningful states

No-SEO UI.setTitle; truly untouched grouped default Java+DOM java/index0/count0; explicit missing; selected Option; disabled-first/group; initially empty; later population; silent assignment and later insertion; trusted selection callback/disabled keyboard skip; Tree Space/Enter and mode transitions; pointer Ctrl/Shift and rename; long-tree End/Home scroll-to-label; Tabbed ARIA/arrow/Home/End/native/pointer semantics and disable cycles; detach/restore node identity, disposal selection/navigation, whole tree disposal, sibling restore DOM/keyboard ordering; empty tree focus.

Chrome initially reports 21 pass/9 fail and Firefox 22 pass/8 fail across 30 cases. Chrome's ninth failure was a **test timing defect**: native focus Sources arrived before asynchronous roving-tabindex update. The active helper now waits for actual focus AND unique visible roving stop together. Independent follow-up asserts settled pointer-collapse state exactly {active: Sources, stops: [Sources]} in BOTH engines and passes. Thus eight substantive failing cases remain per engine; do not classify that transient ninth failure as a product defect.

## Handoff

All tester browsers closed. Safe for coordinated rebuild; no production/demo edits or server restart by tester. Existing components/protocol runs are held until focused checks pass, per CODER TASKS sequencing. New observations.cjs and helper/logging adjustments require AUDITOR review. No claim that Firefox popup visuals, touch, or unprovided fixture actions were covered. Production fix attribution remains with CODER TASKS/AUDITOR; no source root cause is inferred beyond saved exact states.
