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
import java.util.HashSet;
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
 *
 * <p>At most 32 browser range requests are outstanding per connection; newer scroll positions
 * replace pending desired state. The configurable one-request policy favors lower traffic.
 * Rows are never reassigned to another logical item. Overlap preserves
 * native selection and focus; eviction hands focus to the list root after preceding native input
 * events. Keep durable edits and selection in the application model, since off-window rows are
 * disposed. Refresh rebuilds content and callbacks even when a logical key remains unchanged.</p>
 *
 * <pre>{@code
 * VirtualList<Record> list = new VirtualList<>(count, repository::get, this::renderRecord)
 *         .setKeyProvider(record -> record.id())
 *         .setRowHeight(32)
 *         .setOverscan(4);
 * }</pre>
 * @param <T> indexed dataset value type
 */
public class VirtualList<T> extends Div {
    /**
     * Bound on live rendered rows and untrusted viewport requests.
     */
    public static final int MAX_WINDOW_ITEMS = 4096;
    /**
     * Maximum browser request pipeline depth; independent of dataset size and mounted row count.
     */
    public static final int MAX_PENDING_REQUESTS = 32;
    private final Div spacer = new Div(); // Native physical scroll extent.
    private final Div layer = new Div(); // Positioned rendered rows.
    private final ArrayDeque<VirtualRow> rows = new ArrayDeque<>(); // Only live window row wrappers.
    private HashSet<String> keys; // Custom live-window keys only; null avoids a set for unique index keys.
    private final Function<? super T, ? extends Component> renderer; // Fresh row content factory.
    private IntFunction<? extends T> source; // Lazy indexed provider.
    private List<? extends T> items; // Optional retained list, never copied.
    private Function<? super T, String> keyProvider; // Optional application identity; null uses the dataset index.
    private int count; // Logical dataset size.
    private int first; // Inclusive rendered window start.
    private int end; // Exclusive rendered window end.
    private int revision; // Configuration generation used to reject stale browser requests.
    private double rowHeight = 32; // Fixed row height in CSS pixels.
    private double viewportHeight = 320; // Initial viewport height in CSS pixels.
    private int overscan = 4; // Extra rows on each side of the viewport.
    private int maximumPendingRequests = MAX_PENDING_REQUESTS; // Latency-first pipeline depth; lower values trade waiting for less work.

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
     * Bounds outstanding browser range requests. The default 32 sends changed ranges immediately
     * until this ceiling is reached, then retains only the latest desired range. Smaller values
     * reduce intermediate work but may add waiting after a jump. This per-list client bound does
     * not establish a server-work bound under arbitrary network or concurrent-user conditions.
     * @param maximum one through 32 outstanding requests
     * @return this list
     */
    public VirtualList<T> setMaximumPendingRequests(int maximum) {
        if (maximum < 1 || maximum > MAX_PENDING_REQUESTS) throw new IllegalArgumentException("Maximum pending requests must be between one and 32");
        maximumPendingRequests = maximum;
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
     * Sets stable application keys; only live-window keys are checked for nulls and duplicates.
     * Keys do not preserve stale components across refresh or reorder. The renderer still creates
     * fresh components, with listeners bound to the current record. Default keys are item indices.
     * @param provider non-null provider of non-null keys, at most 1024 characters each
     * @return this list
     */
    public VirtualList<T> setKeyProvider(Function<? super T, String> provider) {
        keyProvider = Objects.requireNonNull(provider, "provider");
        if (keys == null) keys = new HashSet<>();
        if (isAttached()) refresh();
        return this;
    }

    /**
     * Replaces a lazy indexed dataset without materializing or copying its values.
     * @param count non-negative logical size
     * @param source lazy indexed provider
     * @return this list
     */
    public VirtualList<T> setDataProvider(int count, IntFunction<? extends T> source) {
        if (count < 0) throw new IllegalArgumentException("Item count cannot be negative");
        this.source = Objects.requireNonNull(source, "source");
        this.count = count;
        items = null;
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
            if (keys != null) keys.clear();
            int windowSize = (int) Math.min(MAX_WINDOW_ITEMS, Math.ceil(viewportHeight / rowHeight) + overscan);
            first = Math.min(first, Math.max(0, count - windowSize));
            end = first;
            int initialEnd = Math.min(count, first + windowSize);
            String configuration = BrowserJson.encode(Map.of("count", count, "rowHeight", rowHeight, "overscan", overscan, "revision", revision, "spacer", spacer.getComponentID(), "layer", layer.getComponentID(), "first", first, "end", initialEnd, "requestAware", true, "maxPending", maximumPendingRequests));
            queueForDispatch(DOMUpdateType.VIRTUAL_CONFIG, DOMUpdateParam.VALUE, configuration);
            renderWindow(first, initialEnd, false, 0);
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
        if (start < 0 || finish < start || finish > count || finish - start > MAX_WINDOW_ITEMS) {
            acknowledgeWindow(start, finish, true, true, event.getToken());
            return;
        }
        renderWindow(start, finish, true, event.getToken());
    }

    /**
     * Diffs overlapping indexed windows, retaining unchanged row components.
     * @param start inclusive start
     * @param finish exclusive end
     * @param requested whether this acknowledges a browser request rather than initial configuration
     * @param token browser request identity, or zero for initial/older-client windows
     */
    private void renderWindow(int start, int finish, boolean requested, int token) {
        UI owner = getUI();
        owner.beginUpdate();
        boolean completed = false;
        try {
            if (finish <= first || start >= end) {
                layer.clear();
                rows.clear();
                if (keys != null) keys.clear();
                first = start;
                end = start;
            }
            while (first < start && !rows.isEmpty()) { removeRow(rows.removeFirst()); first++; }
            while (end > finish && !rows.isEmpty()) { removeRow(rows.removeLast()); end--; }
            while (first > start) {
                VirtualRow row = renderRow(first - 1);
                attachRow(row, rows.peekFirst());
                rows.addFirst(row);
                first--;
            }
            while (end < finish) {
                VirtualRow row = renderRow(end);
                attachRow(row, null);
                rows.addLast(row);
                end++;
            }
            completed = true;
        } finally {
            try {
                acknowledgeWindow(start, finish, requested, !completed, token);
            } finally {
                owner.endUpdate();
            }
        }
    }

    /**
     * Creates one bounded fixed-height row without allocating a CSS model.
     * @param index dataset index
     * @return fresh row wrapper
     */
    private VirtualRow renderRow(int index) {
        T item = source.apply(index);
        String key = keyProvider == null ? Integer.toString(index) : Objects.requireNonNull(keyProvider.apply(item), "row key");
        if (key.length() > 1024 || (keys != null && keys.contains(key))) throw new IllegalArgumentException("Invalid or duplicate live row key");
        Component content = Objects.requireNonNull(renderer.apply(item), "renderer result");
        if (content.getParent() != null || content.isAttached()) throw new IllegalArgumentException("The renderer must return a fresh unattached component");
        VirtualRow row = new VirtualRow(key);
        row.attribute("class", "geshra-virtual-row");
        row.attribute("style", "height:" + rowHeight + "px");
        row.attribute("role", "listitem");
        row.attribute("aria-posinset", Integer.toString(index + 1));
        row.attribute("aria-setsize", Integer.toString(count));
        row.attribute("data-geshra-key", key);
        try {
            row.add(content);
        } catch (RuntimeException failure) {
            disposeFailedRow(row, failure);
            throw failure;
        }
        if (keys != null) keys.add(key);
        return row;
    }

    /**
     * Disposes an evicted row and releases its bounded logical-key entry.
     * @param row live wrapper being evicted
     */
    private void removeRow(VirtualRow row) {
        if (keys != null) keys.remove(row.key());
        layer.remove(row);
    }

    /**
     * Attaches a fully constructed row before committing its deque/index bookkeeping.
     * @param row fresh wrapper
     * @param before insertion reference, or null to append
     */
    private void attachRow(VirtualRow row, VirtualRow before) {
        try {
            if (before == null) layer.add(row);
            else layer.insertBefore(before, row);
        } catch (RuntimeException failure) {
            if (keys != null) keys.remove(row.key());
            disposeFailedRow(row, failure);
            throw failure;
        }
    }

    /**
     * Releases a failed construction while preserving both the original and cleanup failures.
     * @param row wrapper with possibly partially registered content
     * @param failure original application failure
     */
    private void disposeFailedRow(VirtualRow row, RuntimeException failure) {
        try {
            row.dispose();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    /**
     * Reports the actual committed range, including after a rejected request or renderer failure.
     * The browser may then send a newer desired range without replaying stale structural changes.
     * @param requestFirst requested inclusive start
     * @param requestEnd requested exclusive end
     * @param requested whether this is a browser request acknowledgment
     * @param failed whether validation or rendering failed
     * @param token browser request identity, or zero for initial/older-client windows
     */
    private void acknowledgeWindow(int requestFirst, int requestEnd, boolean requested, boolean failed, int token) {
        Map<String, Object> window = Map.of("revision", revision, "first", first, "end", end, "requestFirst", requestFirst, "requestEnd", requestEnd, "failed", failed, "requested", requested, "token", token);
        queueForDispatch(DOMUpdateType.VIRTUAL_WINDOW, DOMUpdateParam.VALUE, BrowserJson.encode(window));
        push();
    }

    @Override
    protected void create() { refresh(); }

    @Override
    protected void destroy() { rows.clear(); if (keys != null) keys.clear(); }
}
