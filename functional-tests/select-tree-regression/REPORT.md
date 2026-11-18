# Select, Tree and Tab functional regression report

**2026-11-18 — focused acceptance checks passed.** Actual recorded PID **20008**, independently verified JAR SHA256 **302C535EC25D97EA266627B1A6F36EC8B2CA0F243703CCD9B75E0BE823D5A92A**. Live URLs: http://localhost:4050/showcase and /showcase-regressions. Artifact hash is read from the actual JAR and checked against launch metadata and PID file before every focused run; root/CODER TASKS supplied readiness. This checks disk identity, not independent process attestation.

Chrome **157.0.8103.80** and Firefox **157.0**, Windows, headless isolated contexts, sequential engine ownership. Each engine passed **33/33 focused cases**, with **zero recorded page/console/request or observed-frame decoder errors**. Existing `browser-tests/components.cjs` and `protocol.cjs` also passed in each engine, with saved logs and exit codes 0. No auth suite or performance benchmarks were run. All tester-owned browser contexts and processes are closed; shared server remains unchanged.

## Evidence

All current evidence is partitioned at:

`evidence/302C535EC25D97EA266627B1A6F36EC8B2CA0F243703CCD9B75E0BE823D5A92A/`

Each chromium/ and firefox/ directory contains `results.json` (actual identity, browser version, 33 case outcomes, snapshots and diagnostics), `legacy-components.log`, `legacy-protocol.log`, and `legacy-exit-codes.json`. First-run failures remain under the prior FA9A9076... SHA and in FIRST-RUN.md. Older full 113-component baseline and final showcase evidence elsewhere in functional-tests remains unchanged and applies to its original builds; this focused acceptance does not relabel those entire catalog tests as rerun.

## Resolved by independent browser checks

| Concern | Observed final outcome in both engines |
| --- | --- |
| Truly untouched grouped Select | Java java, DOM java/index0, zero user callbacks; fixture contains no explicit setValue workaround |
| Explicit existing/missing/empty values | Existing javascript/index1; missing empty/index-1; selected empty option index2. Exact Java getters agree and callback count stays 0 |
| Option.setSelected | Java/DOM javascript/index1, silent |
| Disabled option/group/all-disabled | First eligible enabled option selected; all-disabled Java empty/native no selection index-1; keyboard skips disabled option |
| Listbox/multiple defaults | No forced selection; index-1 and exact Java empty value |
| Explicit property assignment | value=javascript survives final options; multiple value/selectedIndex properties select exactly javascript/index1; Java encoded multiple read-back is exactly vjavascript; zero callbacks |
| Empty/later insertion | Initially no selection; populated grouped tree chooses java; later option insertion preserves assigned javascript; programmatic assignment stays silent |
| Real trusted Select edit | Keyboard edit produces expected Java value and exactly one callback; later reads agree |
| Delayed initialization after prior normalization | Real Select and NumberField silent normalization reaches Java first, then delayed type34 restores Java/native values with zero user callbacks and actual final silent-sync frames |
| UI.setTitle without SeoPage | Regression title arrives correctly after route assembly; separate packet timing is explicitly awaited |
| Initial Tree Tab and ARIA | Exactly four selectable nodes initially aria-selected=false; true Tab enters Project, next Tab exits and Shift+Tab returns; proper tree/treeitem/group/expanded/leaf semantics |
| Tree navigation and modes | Visible preorder Up/Down/Home/End boundaries; Right/Left expansion/parent behavior; focus independent of selection; Space/Enter and SINGLE/MULTIPLE/NONE transitions; Ctrl/Shift pointer compatibility |
| Tree rename and collapse | Child DOM identity retained; renamed branch remains keyboard-operable; pointer collapse from focused descendant settles on Sources with one visible roving stop |
| Long tree focus visibility | End focuses and reveals Row99 inside 140px viewport; Home focuses/reveals Long root |
| Tree inherited lifecycle | Detached selected leaf clears selection, is skipped and restores exact node handle/ID once; leaf/branch disposal cleans selection and navigation, last-child ARIA; whole-tree disposal removes hierarchy without dead focus commands; external mutation action does not steal focus into tree |
| Sibling restore order | Exact retained A identity; DOM and keyboard traversal A,B,C after detach/restore |
| Tabbed semantics | Actual tablist/tab/tabpanel with bidirectional ID relationships; automatic Arrow/Home/End activation and wrap, native Enter/Space, pointer compatibility and one selected enabled roving stop |
| Tab disable/enable | Disabled selected panel remains selected while enabled Settings owns stop; disabled tabs skipped; all-disabled zero stops; enabling restores current selected stop, including both Settings and Overview cycles |

## Deterministic split wire/Java proof

The dedicated fixture queues actual production SET_PROPERTY and INITIAL_VALUE commands; it does not call Java setValue for the delayed restoration or fake browser events. The test waits for each Java getter phase before requesting the next phase, making the split deterministic through separate real server DOM batches and the actual Java ValueSyncPacketHandler.

Both engines observe:

1. Baseline Java Select=javascript, Number=42.0, native agreement.
2. Normalization action makes native Select and Number empty, sends actual action10 silent frames for both IDs, then Java getters report Select empty and Number null; callbacks0.
3. Delayed type34 restores native javascript/42 and sends actual final action10 frames; Java getters return javascript/42.0; callbacks0. No user value-change action4 for these IDs is observed between phases.

Full frame bytes, decoded component IDs/values, native values and server-DOM packet counts are saved in each engine's first case observations. This is a controlled real-protocol replay, not a claim that the original route-creation packet scheduler itself is deterministic.

## Remaining limits and dispositions

- Prior grouped-default and initial Tree keyboard/ARIA defects are now resolved by the above direct browser evidence. Prior explicit initialization/native-mode mismatches also pass. First failed evidence is retained, including the identified Chrome pointer-collapse test-timing issue; the helper now awaits native focus and unique roving-stop agreement together.
- Existing Table selection remains a disclosed policy-only feature gap. No implementation or passing row-selection behavior is invented. Tree typeahead/select-all and other unsupported shortcuts are not certified.
- Firefox native popup **appearance**, OS picker visuals and touch are unverified. Functional native keyboard/value tests do not substitute for visual popup testing.
- Button-driven detach/dispose first moves DOM focus externally; those cases verify no focus stealing and valid roving fallback, not server-initiated removal while focus stays inside a descendant. The fixture's pointer-collapse case does exercise real descendant focus handoff.
- Tree Java read-back exposes cardinality, while exact selected names are observed through aria-selected; the report does not claim a direct Java name-list read.
- No added fixture covers after-attach Option.setSelected(false), dynamic selected-option deletion/group replacement, retained focus during button-driven rename, repeated-key synthesis or tree disposal initiated without external user focus. Broader virtual-list/upload/media limits from previous catalog reports remain outside this narrow run.
- Main catalog 48 cards/113 names was not independently recounted in this narrow run; its prior evidence is preserved, and the existing component/protocol smoke was rerun on this actual candidate.

## Final handoff

Tester changes are confined to functional-tests. No production/demo fix, server restart, dependency install, commit or shutdown was performed. Latest maintained `regressions.cjs`, `observations.cjs` changes and this report are ready for final AUDITOR review. Source syntax and project formatting checks pass. `observations.cjs` is honestly labeled as an observation probe with a separate explicit settled-collapse assertion; it was used on the earlier failing SHA, not rerun as an extra final acceptance suite.

Functional verification for this requested fix set is complete with the explicit limits above. Root/CODER TASKS retains final audit, documentation integration and shutdown coordination.
