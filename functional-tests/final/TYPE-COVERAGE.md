# Corrected build per-type observations

Build SHA256: 6E16CCC113F6DA7891FF98A2E1CAE8C1AA095EFE2C40D7D4E89B5A2C85E85608. Chrome 157.0.8103.80 and Firefox 157.0. Both engines have core and extended JSON evidence. OBSERVED means the rendered sample/semantics were inspected; it does not assert every method or state passed.

| Concrete type | Sample | Observed coverage / limits |
| --- | --- | --- |
| Accordion | Details | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Alert | Badge | OBSERVED roles/status and numeric states; progress 40/100/reset + indeterminate, meter thresholds validated |
| Article | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Aside | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Audio | Audio | PASS trusted keyboard playback, currentTime advancement, Java pause and no media error; correct audio/wav and video/webm MIME verified; seek/mute not demonstrated |
| Badge | Badge | OBSERVED roles/status and numeric states; progress 40/100/reset + indeterminate, meter thresholds validated |
| Blockquote | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Button | Button | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| ButtonBar | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Canvas | Canvas | PASS native 2D pixels after server draw commands |
| Caption | Table | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Card | Badge | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Checkbox | Checkbox | PASS checked/unchecked Java callback; mixed Checkbox and label activation; radio immediate peer state |
| Code | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| ColorField | ColorField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| DataList | DataList | PASS native list association/options and free text; OS picker visuals untested |
| DateField | DateField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| DateTimeField | DateTimeField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| DefinitionList | DefinitionList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| DescriptionDetail | DefinitionList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| DescriptionTerm | DefinitionList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Details | Details | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Dialog | Details | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Div | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Divider | Badge | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| EmailField | EmailField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Emphasis | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Fieldset | Form | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Figure | Image | PASS local image decode/native alt and Figure child structure |
| FileUploader | FileUploader | PASS actual 15-byte upload callback; over-limit rejection not exercised |
| Footer | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Form | Form | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| GridLayout | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H1 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H2 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H3 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H4 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H5 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| H6 | H1 | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Header | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| HiddenField | HiddenField | PASS hidden native input and Java read action |
| HorizontalLayout | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Hyperlink | Hyperlink | OBSERVED real href /components and target _blank; existing route regressions cover navigation |
| Image | Image | PASS local image decode/native alt and Figure child structure |
| InputField | InputField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Label | Form | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Legend | Form | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Link | Script | PASS local stylesheet linked and sheet loaded |
| ListItem | OrderedList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Main | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Mark | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Meter | Badge | OBSERVED roles/status and numeric states; progress 40/100/reset + indeterminate, meter thresholds validated |
| MonthField | MonthField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| MultiSelect | MultiSelect | PASS comma and Unicode selections; empty/reset boundaries not all exercised |
| NativeTextField | NativeTextField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Navigation | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| NumberField | NumberField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Option | Select | PASS explicit showcase java configuration and trusted selection/callback; general native grouped-default bug OPEN |
| OptionGroup | Select | PASS explicit showcase java configuration and trusted selection/callback; general native grouped-default bug OPEN |
| OrderedList | OrderedList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Output | Form | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Panel | Badge | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Paragraph | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| PasswordField | PasswordField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Popover | Details | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Preformatted | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Progress | Badge | OBSERVED roles/status and numeric states; progress 40/100/reset + indeterminate, meter thresholds validated |
| RadioButton | RadioButton | PASS checked/unchecked Java callback; mixed Checkbox and label activation; radio immediate peer state |
| RangeField | RangeField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Script | Script | OBSERVED intentionally empty/inert script; execution covered by existing protocol regression |
| SearchField | SearchField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Section | Main | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Select | Select | PASS explicit showcase java configuration and trusted selection/callback; general native grouped-default bug OPEN |
| Small | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Spacer | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Span | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Spinner | Badge | OBSERVED roles/status and numeric states; progress 40/100/reset + indeterminate, meter thresholds validated |
| StackLayout | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Strong | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Summary | Details | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Svg | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgCircle | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgGroup | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgLine | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgPath | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgPolygon | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgPolyline | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgRectangle | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| SvgText | Svg | OBSERVED all native SVG tags/namespaces and declared shape attributes; dynamic shape updates not provided |
| Switch | Switch | PASS checked/unchecked Java callback; mixed Checkbox and label activation; radio immediate peer state |
| Tabbed | Tabbed | PASS pointer and Enter/Space selection; no ARIA tab roles/selection or Arrow/Home/End contract implemented |
| TabButton | Tabbed | PASS pointer and Enter/Space selection; no ARIA tab roles/selection or Arrow/Home/End contract implemented |
| Table | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TableBody | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TableCell | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TableColumn | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TableHead | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TableRow | Table | OBSERVED native table structure, rows, clear/reset; interactive selection unsupported |
| TelephoneField | TelephoneField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| TextArea | TextArea | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| TextComponent | Paragraph | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| TextField | TextField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| TimeField | TimeField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| Toolbar | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Tooltip | Tooltip | PASS open/close and relevant native Escape/keyboard or explicit show/hide states |
| Tree | Tree | PASS labels/expand/collapse/rename descendants, SINGLE/MULTIPLE/NONE, Ctrl/Shift; no arrow navigation implementation claimed |
| TreeNode | Tree | PASS labels/expand/collapse/rename descendants, SINGLE/MULTIPLE/NONE, Ctrl/Shift; no arrow navigation implementation claimed |
| UnorderedList | OrderedList | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| UrlField | UrlField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |
| VerticalLayout | HorizontalLayout | OBSERVED source-backed native tags/text/order and sample screenshot; toolbar/button-bar callbacks where applicable |
| Video | Audio | PASS trusted keyboard playback, currentTime advancement, Java pause and no media error; correct audio/wav and video/webm MIME verified; seek/mute not demonstrated |
| VirtualList | VirtualList | PASS bounded million-row rendering, wheel/jump-last/refresh; edit/focus/reconnect/dispose/empty-source states absent in showcase |
| WeekField | WeekField | PASS trusted edit and server callback; disabled/read-only/invalid and required form states where applicable |

