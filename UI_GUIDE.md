# UI components

Add the library dependency, define a Spring `Route`, and construct components inside its `load(UI)` method or an event callback. The runnable [component gallery](examples/spring-app/src/main/java/com/example/demo/routes/ComponentsRoute.java) is available at `http://localhost:4040/components` in the example application. Its [walkthrough](examples/spring-app/README.md) maps each focused example to its source file.

The toolkit uses actual browser elements. It delegates focus, keyboard interaction, form validation, disclosure behavior, modal dialogs, media playback, and SVG/canvas rendering to the browser. Java callbacks run on the server over WebSocket, so property readback and event handling are asynchronous. It does not emulate synchronous JavaScript execution on the server.

## Forms

```java
TextField name = new TextField("Your name", "");
Checkbox notifications = new Checkbox(true);
RangeField volume = new RangeField(50);
volume.setMin(0);
volume.setMax(100);
volume.setStep(5);
Select language = new Select();
language.addOption("java", "Java");
language.addOption("kotlin", "Kotlin");
TextArea notes = new TextArea().setRows(4);
ui.add(new Label("Name", name), name,
       new Label("Notifications", notifications), notifications,
       new Label("Volume", volume), volume,
       new Label("Language", language), language, notes);
notifications.addValueChangeListener(event -> {
    // isChecked() already contains the browser's new checked state.
    volume.setEnabled(notifications.isChecked());
});
```

`setValue` and `setChecked` assign properties without firing input/change handlers, like JavaScript assignments. Use `setValueAndNotify` when you explicitly want a Java-side notification. A browser input event updates the cached value before application handlers run. Browser sanitization, range clamping, select defaults, and radio peer changes synchronize quietly; a getter immediately after sending a command may still contain the requested value until the browser responds.

`NumberField` uses `Double`, with `null` for an empty or sanitized nonfinite value; `getValueAsNumber()` returns NaN when empty. Dates, times, colors, and text use native strings rather than locale-dependent Java conversions. `RadioButton(name, submissionValue)` uses Boolean checked state independently of its native form value. Matching name and form determine native radio groups.

`MultiSelect` uses `List<String>` in document order and supports empty strings, commas, Unicode, and plus signs without ambiguous value encoding:

```java
MultiSelect choices = new MultiSelect().setRows(4);
choices.addOption("a,b", "First choice");
choices.addOption("+", "Second choice");
choices.setValue(List.of("a,b", "+"));
ui.add(choices);
```

## Virtual lists

```java
VirtualList<String> list = new VirtualList<>(
    1_000_000,
    index -> "Item " + index,
    item -> new Span(item)
);
list.setHeightPixels(320);
list.setRowHeight(32);
list.setOverscan(4);
ui.add(list);
list.scrollToIndex(900_000);
```

The indexed provider runs only for newly rendered rows. No million-item array or component tree is allocated. The alternative `new VirtualList<>(items, renderer)` retains your indexed List without copying it; use an ArrayList or another constant-time indexed list. Call `refresh()` after changing that retained list, or `setItems(newItems)` to replace it.

Rows have fixed heights. A renderer must return a fresh unattached component for each row. Overlapping windows preserve native nodes, component IDs, and callbacks; rows leaving the window are disposed. Scroll/resize notifications are coalesced to one animation-frame request and unchanged windows send no extra requests. The normal live count is approximately `ceil(viewportHeight / rowHeight) + 2 * overscan + 1`. Requests are capped at 4,096 rows and stale configuration generations are rejected. Large datasets scale native scroll coordinates into a maximum eight-million-pixel extent.

Keep provider and renderer functions fast: they run on the session dispatch thread. Refresh rebuilds the current window and retains the browser's scroll position, which is clamped if the dataset shrinks. Variable-height rows, remote data fetching, and grid virtualization are separate concerns.

## Native events, properties, and methods

```java
Details details = new Details("More information", new Paragraph("Content"));
details.listen("toggle", event -> System.out.println(event.getValue()));
Dialog dialog = new Dialog(new Paragraph("Saved"));
Button open = new Button("Open");
open.addClickListener(event -> dialog.showModal());
dialog.listen("close", event -> System.out.println(dialog.getReturnValue()));
ui.add(details, open, dialog);

field.setProperty("required", true); // actual Boolean, not the string "true"
field.attribute("autocomplete", "name");
field.ariaLabel("Full name");
field.callMethod("setSelectionRange", 0, 3);
```

`listen(name, handler)` supports native event names, including focus, blur, change, invalid, toggle, close, cancel, contextmenu, and media events. `DomEvent` contains the component, name, and a value snapshot; typed click and keyboard listeners expose their existing detailed payloads. Checkbox snapshots contain checked; multiple selection snapshots use the toolkit's lossless wire representation, so use `MultiSelect.getValue()` for selected application values.

To cancel a browser default action, register `listen(name, true, handler)`. Cancellation happens synchronously in the browser before forwarding. Consuming a Java event only controls Java listener propagation. It cannot retroactively stop a browser event. Save the handler instance and pass it to `removeListener(name, handler)` to unsubscribe.

`setProperty` and `callMethod` accept strings, booleans, numbers, null, Object arrays, iterables, and maps with string keys. Component arguments resolve to their native elements, for example `button.setProperty("popoverTargetElement", popover)`. Native method results stay in the browser; promise failures and method errors are reported in its console. Browser permissions and user-activation requirements still apply.

## Ownership and batching

`add` and `insertBefore` move an existing component without recreating its native node. `detach()` retains a subtree, its IDs, values, and listeners; add it to a parent to restore it. `remove`, `clear`, and `dispose()` permanently dispose components. Use fresh components after disposal. Clearing the UI also disposes retained detached roots.

WebSocket callbacks automatically batch changes. For application code outside that path, group related operations:

```java
ui.beginUpdate();
try {
    heading.setText("Updated");
    volume.setValue(75.0);
} finally {
    ui.endUpdate();
}
```

Structural creation runs before property configuration; initial values run after option properties. Moving or detaching existing nodes can establish a flush boundary to preserve native node identity. Removing a subtree sends one root removal while releasing its server registrations in one traversal.

## Catalog

All names below are Java component classes. Most are in `geshra.net.web.ui.components`; collections, tabs, and tables have their existing subpackages.

| Group | Components |
| --- | --- |
| Inputs | TextField, TextArea, NumberField, RangeField, Checkbox, Switch, RadioButton, Select, MultiSelect, Option, OptionGroup, DataList, SearchField, UrlField, TelephoneField, EmailField, PasswordField, DateField, DateTimeField, TimeField, MonthField, WeekField, ColorField, HiddenField, FileUploader |
| Layout | Div, VerticalLayout, HorizontalLayout, GridLayout, StackLayout, Card, Panel, Toolbar, ButtonBar, Spacer, Divider |
| Text | Span, Paragraph, H1–H6, Strong, Emphasis, Small, Mark, Code, Preformatted, Blockquote, Label, Caption, Summary |
| Semantic structure | Section, Article, Header, Footer, Main, Navigation, Aside, Figure, Fieldset, Legend, OrderedList, DefinitionList, DescriptionTerm, DescriptionDetail, UnorderedList, ListItem |
| Overlays and disclosure | Dialog, Popover, Details, Accordion |
| Status | Badge, Alert, Output, Tooltip, Spinner, Progress, Meter |
| Navigation and data | Button, Hyperlink, Link, Form, Tabbed, Tree, Table, VirtualList |
| Media and graphics | Image, Audio, Video, Canvas, Svg, SvgGroup, SvgPath, SvgCircle, SvgRectangle, SvgLine, SvgPolyline, SvgPolygon, SvgText |

Text classes use textContent; `setHTML` explicitly requests innerHTML. Semantic wrappers retain their native tag behavior. Tooltip provides semantic tooltip content; associate it through aria-describedby and manage its presentation in your application. DataList supplies native suggestions; `attachTo(input)` links a NativeTextField. Give controls visible Labels or ariaLabel values.

Horizontal layouts, grids, cards, panels, indicators, dialogs, and virtual lists use the small bundled `/css/components.css`. GridLayout defaults to responsive columns; `setColumns(n)` selects an explicit count. Native controls keep platform styling. Applications can override this stylesheet through their own `src/main/resources/web/css/components.css`.

Canvas commands call the native 2D context with typed arguments:

```java
Canvas canvas = new Canvas(240, 120);
canvas.setDrawingProperty("fillStyle", "#1565c0");
canvas.draw("fillRect", 10, 10, 80, 40);
ui.add(canvas);
```

SVG elements use the correct SVG namespace. Set case-sensitive attributes such as viewBox, d, cx, fill, and stroke with `attribute`. Use `setProperty` for DOM properties and `attribute` for SVG attribute values.

## Verification

Run Java tests with JDK 21 and `./gradlew test`. Build the separate example with `./gradlew publishToMavenLocal` and `./gradlew -p examples/spring-app -PuseMavenLocal=true bootJar`, then run its executable JAR.

Browser tests are development-only dependencies. Start the example with `--demo.password=browser-test-password` for the authentication checks:

```sh
cd browser-tests
npm ci
npx playwright install chromium firefox
npm test
BROWSER=firefox npm test
```

The default application address is `http://localhost:4040`; set `BASE_URL` to change it. `BROWSER_EXECUTABLE` optionally selects a local browser binary. Tests exercise native controls, silent assignments, radio peer state, dialogs, popovers, listener removal, retained nodes, a million-item list, SVG, and actual canvas pixels. CI runs the packaged consumer against Chromium and Firefox.

Native behavior references: [HTML input value](https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/value), [native dialog](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Elements/dialog), and [HTML living standard](https://html.spec.whatwg.org/multipage/).
