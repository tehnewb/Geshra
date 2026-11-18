package virtualbench;

import geshra.net.web.ui.Component;
import geshra.net.web.ui.components.Button;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.components.Span;
import geshra.net.web.ui.components.TextField;
import geshra.net.web.ui.components.list.VirtualList;
import java.util.HashMap;
import java.util.Map;

/**
 * Session-owned deterministic rows generated only when requested. Sparse edits and selection
 * live in the application model, independently of the mounted row/component lifetime.
 * Counts and reads are exposed as observable correctness diagnostics, not simulated work.
 */
public final class VirtualState {
    private final Div root = new Div(); // Container for controls and bounded rows.
    private final Span status = new Span("none"); // Last authoritative interaction result.
    private final Map<Integer, String> edits = new HashMap<>(); // Only explicitly edited row values.
    private final VirtualList<Integer> list; // Real production virtual list.
    private int reads; // Provider invocations, independent of total dataset count.

    /**
     * Creates a 320px viewport of 32px rows and fixed four-row overscan.
     * @param count logical row count
     */
    public VirtualState(int count) {
        list = new VirtualList<>(count, index -> { reads++; return index; }, this::row);
        list.setHeightPixels(320);
        list.setRowHeight(32);
        list.setOverscan(4);
        list.ariaLabel("Virtual benchmark");
        status.ariaLabel("Result");
        Button last = new Button("Last");
        last.addClickListener(event -> list.scrollToIndex(count - 1));
        Button first = new Button("First");
        first.addClickListener(event -> list.scrollToIndex(0));
        Button refresh = new Button("Refresh");
        refresh.addClickListener(event -> list.refresh());
        Button stats = new Button("Stats");
        stats.addClickListener(event -> status.setText("reads=" + reads + ";mounted=" + list.getRenderedCount()));
        Button dispose = new Button("Dispose");
        dispose.addClickListener(event -> list.dispose());
        root.add(last, first, refresh, stats, dispose, status, list);
    }

    /**
     * Returns the session-owned root, ready for normal UI attachment.
     * @return native container
     */
    public Component root() { return root; }

    /**
     * Creates one native row; events close over its logical index, never a recycled DOM slot.
     * @param index immutable logical identity in this fixture
     * @return fresh row content
     */
    private Component row(int index) {
        Div content = new Div();
        content.attribute("data-index", Integer.toString(index));
        TextField input = new TextField("", edits.getOrDefault(index, "Row " + index));
        input.ariaLabel("Edit " + index);
        input.addValueChangeListener(event -> edits.put(index, input.getValue()));
        Button select = new Button("Select " + index);
        select.addClickListener(event -> status.setText("selected=" + index));
        content.add(new Span(Integer.toString(index)), input, select);
        return content;
    }
}
