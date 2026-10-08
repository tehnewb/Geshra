package geshra.net.web.ui.components.list;

import geshra.event.EventTypes;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.BrowserJson;
import geshra.net.web.ui.DOMUpdateParam;
import geshra.net.web.ui.DOMUpdateType;
import geshra.net.web.ui.components.Div;
import geshra.net.web.ui.event.ViewportEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Fixed-height virtual list rendering only the visible range plus overscan. Dataset access is
 * indexed and lazy; passing a List retains it without copying. Overlapping rows retain their
 * component IDs, callbacks, and state as the window moves. Off-window rows are disposed promptly.
 * Browser scroll and ResizeObserver notifications are coalesced once per animation frame.
 *
 * <p>The renderer must return a fresh component for each index. Rows have a fixed configured
 * height; variable-height measurement is intentionally not implied. Changes to a retained List
 * require refresh. Very large extents use browser-side scaled scrolling rather than overflowing
 * native CSS height limits. All server mutations belong to the session dispatch thread.</p>
 */
public class VirtualList<T> extends Div {
    /**
     * Bound on live rendered rows and untrusted viewport requests.
     */
    public static final int MAX_WINDOW_ITEMS = 4096;
    private final Div spacer = new Div(); // Native physical scroll extent.
    private final Div layer = new Div(); // Positioned rendered rows.
    private final ArrayDeque<Component> rows = new ArrayDeque<>(); // Only live window row wrappers.
    private final Function<? super T, ? extends Component> renderer; // Fresh row content factory.
    private IntFunction<? extends T> source; // Lazy indexed provider.
    private List<? extends T> items; // Optional retained list, never copied.
    private int count; // Logical dataset size.
    private int first; // Inclusive rendered window start.
    private int end; // Exclusive rendered window end.
    private int revision; // Configuration generation used to reject stale browser requests.
    private double rowHeight = 32; // Fixed row height in CSS pixels.
    private double viewportHeight = 320; // Initial viewport height in CSS pixels.
    private int overscan = 4; // Extra rows on each side of the viewport.

    /**
     * Creates a virtual list retaining an indexed List without copying its values.
     * @param items indexed dataset
     * @param renderer fresh component factory
     */
    public VirtualList(List<? extends T> items, Function<? super T, ? extends Component> renderer) {
        this(items.size(), items::get, renderer);
        this.items = items;
    }

    /**
     * Creates a virtual list backed by a lazy indexed provider.
     * @param count non-negative dataset size
     * @param source indexed data provider
     * @param renderer fresh component factory
     */
    public VirtualList(int count, IntFunction<? extends T> source, Function<? super T, ? extends Component> renderer) {
        if (count < 0) throw new IllegalArgumentException("Item count cannot be negative");
        this.count = count;
        this.source = Objects.requireNonNull(source, "source");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        attribute("class", "geshra-virtual-list");
        attribute("role", "list");
        attribute("tabindex", "0");
        attribute("style", "height:320px");
        spacer.attribute("aria-hidden", "true");
        layer.attribute("class", "geshra-virtual-items");
        add(spacer, layer);
        registerEventHandler(EventTypes.VIEWPORT, 0, this::viewportChanged);
    }

    /**
     * Configures the fixed row height; changes reset the virtual window.
     * @param pixels positive finite CSS pixel height, at most one million
     * @return this list
     */
    public VirtualList<T> setRowHeight(double pixels) {
        if (!(pixels > 0) || !Double.isFinite(pixels) || pixels > 1_000_000) throw new IllegalArgumentException("Invalid row height");
        rowHeight = pixels;
        if (isAttached()) refresh();
        return this;
    }

    /**
     * Configures a viewport height in CSS pixels.
     * @param pixels positive finite viewport height
     * @return this list
     */
    public VirtualList<T> setHeightPixels(double pixels) {
        if (!(pixels > 0) || !Double.isFinite(pixels) || pixels > 65536) throw new IllegalArgumentException("Invalid viewport height");
        viewportHeight = pixels;
        attribute("style", "height:" + pixels + "px");
        if (isAttached()) refresh();
        return this;
    }

    /**
     * Sets the bounded render buffer around the visible range.
     * @param overscan extra rows per side, zero through 256
     * @return this list
     */
    public VirtualList<T> setOverscan(int overscan) {
        if (overscan < 0 || overscan > 256) throw new IllegalArgumentException("Invalid overscan");
        this.overscan = overscan;
        if (isAttached()) refresh();
        return this;
    }

    /**
     * Replaces the retained List and refreshes the visible window.
     * @param items new indexed dataset
     * @return this list
     */
    public VirtualList<T> setItems(List<? extends T> items) {
        this.items = Objects.requireNonNull(items, "items");
        source = items::get;
        count = items.size();
        if (isAttached()) refresh();
        return this;
    }

    /**
     * Refreshes an externally changed dataset and starts a new configuration generation.
     */
    public void refresh() {
        UI owner = getUI();
        owner.beginUpdate();
        try {
            if (items != null) count = items.size();
            revision++;
            layer.clear();
            rows.clear();
            first = 0;
            end = 0;
            String configuration = BrowserJson.encode(Map.of("count", count, "rowHeight", rowHeight, "overscan", overscan, "revision", revision, "spacer", spacer.getComponentID(), "layer", layer.getComponentID()));
            queueForDispatch(DOMUpdateType.VIRTUAL_CONFIG, DOMUpdateParam.VALUE, configuration);
            renderWindow(0, Math.min(count, (int) Math.min(MAX_WINDOW_ITEMS, Math.ceil(viewportHeight / rowHeight) + overscan)));
            push();
        } finally {
            owner.endUpdate();
        }
    }

    /**
     * Scrolls to an item using browser-side physical/logical coordinate mapping.
     * @param index valid item index
     * @return this list
     */
    public VirtualList<T> scrollToIndex(int index) {
        if (index < 0 || index >= count) throw new IndexOutOfBoundsException(index);
        callMethod("fsiScrollToIndex", index);
        return this;
    }

    /**
     * Returns the current live rendered row count, independent of dataset size.
     * @return live rows
     */
    public int getRenderedCount() { return rows.size(); }

    /**
     * Returns the current dataset size.
     * @return logical item count
     */
    public int getItemCount() { return count; }

    /**
     * Applies a validated current-generation browser viewport.
     * @param event requested viewport
     */
    private void viewportChanged(ViewportEvent event) {
        if (event.getRevision() != revision) return;
        int start = event.getFirst();
        int finish = event.getEnd();
        if (start < 0 || finish < start || finish > count || finish - start > MAX_WINDOW_ITEMS) return;
        renderWindow(start, finish);
    }

    /**
     * Diffs overlapping indexed windows, retaining unchanged row components.
     * @param start inclusive start
     * @param finish exclusive end
     */
    private void renderWindow(int start, int finish) {
        UI owner = getUI();
        owner.beginUpdate();
        try {
            if (finish <= first || start >= end) {
                layer.clear();
                rows.clear();
                first = start;
                end = start;
            }
            while (first < start && !rows.isEmpty()) { layer.remove(rows.removeFirst()); first++; }
            while (end > finish && !rows.isEmpty()) { layer.remove(rows.removeLast()); end--; }
            while (first > start) {
                Component row = renderRow(first - 1);
                if (rows.isEmpty()) layer.add(row);
                else layer.insertBefore(rows.peekFirst(), row);
                rows.addFirst(row);
                first--;
            }
            while (end < finish) {
                Component row = renderRow(end++);
                rows.addLast(row);
                layer.add(row);
            }
            queueForDispatch(DOMUpdateType.VIRTUAL_WINDOW, DOMUpdateParam.VALUE, BrowserJson.encode(Map.of("revision", revision, "first", first, "end", end)));
            push();
        } finally {
            owner.endUpdate();
        }
    }

    /**
     * Creates one bounded fixed-height row without allocating a CSS model.
     * @param index dataset index
     * @return fresh row wrapper
     */
    private Component renderRow(int index) {
        Div row = new Div();
        row.attribute("class", "geshra-virtual-row");
        row.attribute("style", "height:" + rowHeight + "px");
        row.attribute("role", "listitem");
        row.attribute("aria-posinset", Integer.toString(index + 1));
        row.attribute("aria-setsize", Integer.toString(count));
        Component content = Objects.requireNonNull(renderer.apply(source.apply(index)), "renderer result");
        if (content.getParent() != null || content.isAttached()) throw new IllegalArgumentException("The renderer must return a fresh unattached component");
        row.add(content);
        return row;
    }

    @Override
    protected void create() { refresh(); }

    @Override
    protected void destroy() { rows.clear(); }
}
