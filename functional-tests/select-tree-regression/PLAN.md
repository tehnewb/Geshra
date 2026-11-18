# Select and Tree focused regression plan

Prepared 2026-11-18 from source review only. No browser opened and no test run against the old port-4050 build. Prior evidence under functional-tests/final and corrected remains historical and unchanged. Execution awaits audited new-build readiness and actual PID/JAR hash.

## Source-backed findings and fixture requirements

- Current Select.create skips ValueComponent.create unless assigned; runtime browser normalization and DOM insertion ordering determine untouched selection. The earlier optgroup-first composition selected a later direct empty option. New regression MUST use a real Select with no explicit setValue anywhere before checking its first selected option. An explicitly assigned showcase control cannot prove this fixed.
- Option.setSelected uses native Boolean property updates. Disabled options/groups can use inherited setEnabled(false) or setProperty("disabled", true); tests must record which actual supported API is used. Browser-dependent property behavior must be compared to native controls constructed with the same structure, not invented Java rules.
- Java getValue and browser value/selectedIndex/selectedOptions must all be observable. Each fixture should have a read-state action returning current Java value plus a callback count. This distinguishes silent normalization/programmatic assignments from trusted user value-change notifications.
- Current Tree retains root, selection deque and mode; TreeNode has parent/children, hidden/selected state, separate label/caret/container. Current TreeSelectionListener preserves pointer Ctrl toggle and Shift preorder range. New keyboard implementation must retain those pointer behaviors and rename identity while adding focus/ARIA.
- Prefer named fixture selectors/data-demo and visible state/read actions. Need an initially empty Select with add/insert/remove/reset actions, default grouped Select, explicit existing/missing/empty values, selected options, disabled-option/group compositions, and Tree with at least root, branch, grandchild, sibling. If new implementation changes state policies, document those policies before defining assertions.

## Select cases — Chrome and Firefox

| Case | Required observations |
| --- | --- |
| Untouched optgroup first, later direct empty option | No setValue; Java value equals first enabled option; native selectedIndex/order; no application change callback caused by normalization |
| Untouched flat, multiple groups, all grouped | First eligible native default; compare equivalent native reference if disabled rules differ |
| Explicit existing value before attach | Requested option selected after complete attachment; Java/DOM agree; zero user callback count |
| Explicit missing value before attach and after attach | Native no-selection value empty / selectedIndex=-1 where applicable; Java normalized; no false callback; later matching insertion behavior recorded against documented policy/native reference |
| Explicit empty value with empty option / without empty option | Distinguish selected empty-valued option from no selection using index and selectedOptions; Java empty value alone is insufficient evidence |
| Option.setSelected(true/false) before/after attach | Native selection agrees with Java; multiple preselected options follow native single-select ordering; programmatic updates stay silent |
| Disabled option and disabled group | Default skips ineligible entries as native does; trusted keyboard cannot select disabled entries; explicit assignment behavior compared with native rather than assumed rejected |
| Empty then later add/insert | Empty count/value/index first, then first eligible option selected; group-before-option insertion; Java state/read-back, no unsolicited value-change callback |
| Mutation | Clear/remove selected option; append options after selected state; replace group; selection and Java normalization after actual update flush |
| Trusted change | Pointer/keyboard or selectOption where native popup access is unavailable; exact Unicode/comma/empty values; one callback per committed real change; later read equals callback value |

Do not label Firefox popup appearance verified from selectOption or DOM assertions. Native reference construction is a test oracle, not a substitute for exercising Geshra APIs/server callbacks.

## Tree cases — Chrome and Firefox

1. Initial role=tree; role=treeitem nodes; accessible names equal labels without descendant-label duplication; role=group where implemented. Exactly one visible item has tabindex=0; other items -1. Parent aria-expanded tracks collapse state; leaves do not falsely claim expansion. aria-selected and aria-multiselectable follow documented mode.
2. Trusted Tab enters one tree item; Tab/Shift+Tab exits to adjacent controls without visiting every node. Focus and selected node are independently read; do not assume focus-implies-selection unless implemented/documented.
3. ArrowDown/ArrowUp traverse only visible items in preorder and clamp at ends. Home/End focus first/last visible items. Hidden descendants are never keyboard destinations.
4. ArrowRight expands a collapsed branch, then moves to its first child when expanded; leaf Right leaves focus/state stable. ArrowLeft collapses expanded branch, otherwise moves to parent; root Left stays stable.
5. Enter/Space execute documented select/activation semantics once; Space/arrow default scrolling suppressed while handled. NONE allows navigation without changing selection; SINGLE replaces; MULTIPLE supports documented toggle/range modifiers. Assert Java selection set, item aria-selected and highlight agree. Distinguish Ctrl movement from Ctrl toggle when specified.
6. Collapse a branch while a descendant owns focus, through both keyboard and pointer/server action: focus returns to a visible stable ancestor/roving stop; no hidden active element and exactly one visible tabindex=0.
7. Pointer label selection, caret expansion, Ctrl toggling and Shift range remain compatible; pointer focus updates roving stop appropriately. A click must not trigger duplicate selection through event bubbling.
8. Rename focused/selected branch: label and accessible name update, branch/grandchild DOM identity retained, aria-expanded/selected and focus retained, subsequent arrows/collapse/select still work.
9. Repeated boundary arrows, leaf-only/empty tree fixture if supported, dynamic child addition and mode changes. Detached/disposed trees must not send stale key listeners where a fixture exists; otherwise mark explicitly untested.

## Execution and audit discipline

- New test scripts/evidence stay under functional-tests/select-tree-regression. Read and verify new actual build metadata, PID file and JAR hash; allocate output by hash and engine, never overwrite previous artifacts.
- Capture page/console errors, failed requests, Java state/callback counts and DOM/ARIA/focus snapshots on failure. Trusted inputs drive behavior; evaluate is used for observation/oracle setup only, never to fake widget behavior.
- Keep independent outcomes and nonzero failed-case exit status. Preserve screenshot failures as evidence. Browser cleanup runs in finally even if report writing fails. No swallowed errors.
- AUDITOR reviews all new maintained CJS before acceptance. Existing final suites can provide small unaffected smoke only after new fixture-specific checks pass and audit approves script changes.
- Report product defects, fixture gaps, browser/platform limits and test defects separately. No blanket all-pass claim from inventory or static ARIA presence.

## Ready-state handoff

Source-review preparation is complete; no browser activity or production/demo edit. Await new URL/PID/hash and fixture selectors plus the implemented keyboard selection policy. The most important fixture requirement is direct Java read-back of the **untouched** grouped Select and zero-change callback count, alongside separate explicit-value cases.
