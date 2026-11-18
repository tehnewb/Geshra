# Geshra UI showcase

Run the example with Java 21 after publishing the local library:

```powershell
$env:JAVA_HOME='C:/Users/albert/.jdks/corretto-21.0.10'
./gradlew.bat publishAllPublicationsToBuildRepository --console=plain
./gradlew.bat -p examples/spring-app -PuseBuildRepository=true test bootJar --console=plain
& "$env:JAVA_HOME/bin/java.exe" -jar examples/spring-app/build/libs/geshra-spring-example.jar --geshra.web.port=4050
```

Open <http://localhost:4050/showcase>. No account or external media service is required. All UI samples are real Geshra components with fresh state per browser session. Reload or follow “Reload and reset showcase” to reset the page. The original `/components` gallery remains intact.

The latest grouped-select/keyboard correction pass also supplies <http://localhost:4050/showcase-regressions>. See UI-BUG-RETEST.md for the current tested artifact, behavior and independent verification status; older showcase reports below retain their original build identities.

## Source inventory and coverage

`showcase-inventory.csv` maps all **113 public concrete component classes** in `src/main/java/geshra/net/web/ui/components` and its subpackages to the showcase. The browser currently exposes 48 sample cards and 113 distinct `data-types` names. These counts describe implementation coverage; they do **not** assert that independent visual and functional testing passed.

Each card has `data-demo` (the first source type) and comma-separated `data-types`. Match types by splitting the value on commas; CSS `~=` does not match a comma-delimited value. `data-category` marks the containing section, and `data-showcase-ready=true` marks the completed route assembly. Dynamic overlays, tab panels and lazy virtual rows must be opened/scrolled before checking their visible states.

| Category | Real APIs and meaningful states |
| --- | --- |
| Forms | All native input subclasses, concrete InputField/NativeTextField, labels, checkbox mixed state, grouped radios, select/option/option groups, multi-select, datalist suggestions, hidden field, disabled/read-only/invalid inputs, file upload, named form submission, fieldset/legend/output |
| Text and semantics | H1–H6, inline and block text primitives, definition/ordered/unordered lists, hyperlinks and every structural container |
| Layout and status | Horizontal/vertical/grid/stack, spacer, toolbar/button bar, card/panel/divider, badge/spinner/alert, determinate and indeterminate progress and meter |
| Collections | Table/header/body/row/column/cell/caption with clear/reset, tabbed selected/unselected panels, tree branch expansion/selection modes/rename |
| Overlays | Details/Summary, Accordion, Dialog open/close/Escape, Popover open/light dismiss, explicitly controlled Tooltip |
| Virtualization | Million-row lazy VirtualList with fixed-height rows, scroll, jump-to-last and refresh |
| Graphics/media | Local Image/Figure, Audio/Video native playback and Java pause feedback, Canvas draw action, all nine SVG primitives |
| Lifecycle | Retained node move/detach/restore/dispose, unsubscribe; reload resets the disposed example |
| Resource elements | Link supplies the local stylesheet; empty Script is intentionally inert, with no arbitrary user code execution |

## Explicit nonvisual and unsupported cases

- `Component` and `ValueComponent` are abstract bases, not independent widgets. Their lifecycle and value contracts are exercised through concrete samples. `UI` is the session root. Concrete InputField has no typed value/getter contract; its sample uses a generic native input-event callback instead of claiming ValueComponent behavior.
- `FileUploadHandler`, `RowPopulator`, `TreeSelectionListener`, selection enums and the package-private `Tab`/`VirtualRow` associations are behavioral/support types, not additional visual widgets. Public concrete subcomponents such as TableHead and TabButton appear inside valid parent components.
- HiddenField, DataList, OptionGroup, Script and Link do not necessarily have a standalone visible rectangle. Their corresponding controls or documented effects provide observable coverage.
- `TableSelectionMode` currently retains policy only; Table does not implement selectable rows. The sample states this explicitly and does not fake row-selection behavior.
- The grouped Select sample is now truly untouched: no setValue workaround. Product initialization and explicit/disabled/mode/later-insertion behavior are covered by the correction-pass fixtures and retest report.
- Tree provides one roving tab stop, visible-node arrow/Home/End navigation, expansion, independent selection and retained lifecycle handling. Tabs provide ARIA relationships and enabled-tab keyboard navigation. See UI-BUG-RETEST.md for verified states and limits.
- There is no dedicated Menu component in the current source. Native browser picker appearance and support differ by browser/platform; the showcase does not invent replacement pickers.
- VirtualList supports fixed heights. Variable-height virtualization is not demonstrated or claimed.
- Tooltip is a semantic node; show/hide and positioning belong to the application. No automatic pointer or keyboard positioning behavior is claimed.
- Audio/video playback uses native controls because asynchronous server-triggered play may not retain browser user activation. The bundled two-second WAV tone and short silent WebM animation are deterministic local fixtures generated for this showcase. SVG is a local authored illustration; no upstream media license is involved.
- This is a component/state showcase, not an exhaustive enumeration of every CSS setter, event overload or browser feature. Style APIs are exercised through the real components and responsive layout samples.

## Independent evidence and bug tracking

VISUAL TESTER owns `visual-test-results/`; FUNCTIONAL TESTER owns `functional-tests/`. Their inventory and evidence are separate from implementation claims. Final functional report: `functional-tests/final/REPORT.md`, with 39 checks passed per browser in Chrome and Firefox on final PID 8004 / JAR SHA-256 `6E16CCC113F6DA7891FF98A2E1CAE8C1AA095EFE2C40D7D4E89B5A2C85E85608`. Final targeted visual report: `visual-test-results/final/report.txt`; prior corrected-build type/state matrices and screenshots are retained under `visual-test-results/corrected/`. All 113 concrete types and 94 representative visual state records are mapped, but not every API/state/platform is verified. See SHOWCASE-BUGS.md for resolved findings, open product issues and coverage limits. AUDITOR granted final code/evidence signoff after reviewing scripts, provenance, reproducible runtime and both tester reports.

The initial Image and Tree hypotheses were independently reproduced, fixed, regression-tested and retested in both browsers. Radio callback ordering, form defaults/labeling, resource namespace, responsive containment, password feedback and media MIME findings were also corrected. The subsequent correction pass fixes grouped native-default ordering, split-packet value synchronization, tree keyboard/lifecycle controls, tab semantics and generic UI.setTitle/pending-SEO ordering. Both engines pass the new focused functional checks; current visual/audit disposition is recorded in UI-BUG-RETEST.md. Table row selection remains a documented feature gap.

## Shared local server ownership

Current showcase process ID is recorded in `build/showcase-server.pid`, listening on port 4050. Logs: `build/showcase-server.log` and `build/showcase-server-error.log`. Before stopping or restarting, verify that the recorded PID command line is this example JAR and that it owns port 4050. Coordinate restarts with both testers; do not start a competing server. Eventual computer shutdown also stops the server, so screenshots, reports and repro instructions must be saved first.
