package geshra.net.web.ui;

import geshra.net.web.SessionContext;
import geshra.net.web.TextPacketEncoder;
import geshra.net.web.SeoPage;
import java.util.Map;
import java.net.URI;
import java.net.URISyntaxException;
import io.netty.buffer.ByteBuf;
import io.netty.util.collection.IntObjectHashMap;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Session-owned root component and primitive-key component registry. The browser root reserves ID
 * zero; children receive positive IDs and released IDs are reused from a primitive stack. Pending
 * components form an intrusive list, so flushing visits only changed components rather than the
 * entire UI tree. No dirty-list nodes or boxed registry keys are allocated.
 *
 * <p>All mutations belong to the session's dispatch thread. {@link #beginUpdate()} and
 * {@link #endUpdate()} group operations into one ordered WebSocket batch. WebSocket input is
 * automatically grouped; callers outside that path can bracket larger application updates.</p>
 */
public class UI extends Component {
    private final IntObjectHashMap<Component> components = new IntObjectHashMap<>(16, 0.75f); // Primitive ID registry.
    private final DOMDispatcher outgoing = new DOMDispatcher(); // Aggregates dirty components into one packet.
    private int[] recycledComponentIDs; // Lazily allocated primitive free-ID stack.
    private int recycledCount; // Occupied free-ID stack slots.
    private int nextComponentID = 1; // Next unused ID; zero belongs to the browser body.
    private Component firstDirty; // First component awaiting serialization.
    private Component lastDirty; // Tail allowing constant-time insertion.
    private int updateDepth; // Nesting depth of application update groups.
    private boolean flushing; // Prevents a reentrant channel callback from starting another flush.
    private boolean controlWritesPending; // Whether grouped title or script packets still need a channel flush.
    private Set<Component> retained; // Lazily allocated detached roots owned until disposal or reattachment.

    /**
     * Creates an empty body root with component ID zero.
     */
    public UI() { super("body"); }

    /**
     * Sends a UTF-8 document title using a pooled control packet.
     * @param title document title
     */
    public void setTitle(String title) {
        SessionContext context = SessionContext.get();
        ByteBuf packet = TextPacketEncoder.encode(context == null ? null : context.getChannel(), 3, title);
        if (context == null) {
            packet.release();
            throw new IllegalStateException("A session is required to set the title");
        }
        context.send(packet);
    }

    /**
     * Updates managed document metadata on client navigation.
     * @param page metadata, or null to clear the previous page's metadata
     * @param path normalized application path
     */
    public void setSeo(SeoPage page, String path) {
        setSeo(page, path, false);
    }

    /**
     * Updates document metadata while forcing protected or missing pages out of search indexes.
     * @param page optional page metadata
     * @param path normalized application path
     * @param noIndex force noindex regardless of optional page metadata
     */
    public void setSeo(SeoPage page, String path, boolean noIndex) {
        String origin = SessionContext.get()
                .getPublicUrl();
        String canonical = "";
        if (!origin.isEmpty()) {
            try {
                canonical = origin + new URI(null, null, path.startsWith("/") ? path : "/" + path, null).getRawPath();
            } catch (URISyntaxException invalidPath) {
                throw new IllegalArgumentException("Invalid canonical path", invalidPath);
            }
        }
        queueForDispatch(DOMUpdateType.SET_SEO, DOMUpdateParam.VALUE, BrowserJson.encode(Map.of("head", page == null ? "" : page.head(canonical), "language", page == null ? "en" : page.getLanguage(), "title", page == null ? "Geshra" : page.getTitle(), "noIndex", noIndex)));
        push();
    }

    /**
     * Retrieves a registered component without boxing its ID.
     * @param componentID component ID
     * @return registered component or null
     */
    public Component get(int componentID) { return components.get(componentID); }

    /**
     * Returns a recycled ID or the next unused positive ID.
     * @return available child component ID
     */
    public int nextComponentID() {
        if (recycledCount > 0) return recycledComponentIDs[--recycledCount];
        if (nextComponentID == Integer.MAX_VALUE) throw new IllegalStateException("Component IDs exhausted");
        return nextComponentID++;
    }

    /**
     * Associates a component with its primitive ID.
     * @param componentID component ID
     * @param component component to register
     */
    public void register(int componentID, Component component) { components.put(componentID, component); }

    /**
     * Retains a detached subtree until disposal or reattachment.
     * @param component detached root
     */
    void retain(Component component) {
        if (retained == null) retained = new HashSet<>();
        retained.add(component);
    }

    /**
     * Releases the UI's detached-root ownership.
     * @param component root to release
     */
    void releaseRetained(Component component) {
        if (retained != null) retained.remove(component);
    }

    @Override
    public void clear() {
        beginUpdate();
        try {
            if (retained != null) {
                while (!retained.isEmpty()) retained.iterator()
                        .next()
                        .dispose();
            }
            super.clear();
        } finally {
            endUpdate();
        }
    }

    /**
     * Removes a registration and recycles its positive ID exactly once.
     * @param componentID component ID
     * @return removed component or null
     */
    public Component recycle(int componentID) {
        Component removed = components.remove(componentID);
        if (removed != null && componentID > 0) {
            if (recycledComponentIDs == null) recycledComponentIDs = new int[8];
            else if (recycledCount == recycledComponentIDs.length) recycledComponentIDs = Arrays.copyOf(recycledComponentIDs, recycledCount * 2);
            recycledComponentIDs[recycledCount++] = componentID;
        }
        return removed;
    }

    /**
     * Defers DOM serialization until the matching outermost endUpdate call.
     */
    public void beginUpdate() { updateDepth++; }

    /**
     * Completes an update group and sends pending DOM work once at the outermost boundary.
     * @throws IllegalStateException if no update group is open
     */
    public void endUpdate() {
        if (updateDepth == 0) throw new IllegalStateException("No UI update group is open");
        if (--updateDepth == 0) flushPendingUpdates();
    }

    /**
     * Reports whether UI mutations are currently grouped.
     * @return true inside an update group
     */
    public boolean isUpdating() { return updateDepth != 0; }

    /**
     * Records a grouped control packet whose bytes were transferred to Netty without flushing.
     */
    public void controlWriteQueued() { controlWritesPending = true; }

    /**
     * Flushes pending mutations even inside an update group, establishing ordering before script
     * execution or navigation. The current group remains open afterward.
     */
    public void flushUpdates() {
        int depth = updateDepth;
        updateDepth = 0;
        try {
            flushPendingUpdates();
        } finally {
            updateDepth = depth;
        }
    }

    /**
     * Links a component once without allocating a collection entry.
     * @param component changed attached component
     */
    void markDirty(Component component) {
        if (component.dirtyQueued) return;
        component.dirtyQueued = true;
        if (lastDirty == null) firstDirty = component;
        else lastDirty.nextDirty = component;
        lastDirty = component;
    }

    /**
     * Collects only changed components and serializes their mutations in one batch.
     * Updates remain pending when the channel is unavailable or applying backpressure.
     */
    void flushPendingUpdates() {
        if (isUpdating() || flushing) return;
        SessionContext context = SessionContext.get();
        if (context == null || context.getChannel() == null) return;
        flushing = true;
        try {
            while (firstDirty != null) {
                Component component = firstDirty;
                firstDirty = component.nextDirty;
                if (firstDirty == null) lastDirty = null;
                component.nextDirty = null;
                component.dirtyQueued = false;
                component.collectUpdates(outgoing);
            }
            outgoing.flush(context.getChannel());
            if (controlWritesPending) {
                context.getChannel()
                        .flush();
                controlWritesPending = false;
            }
        } finally {
            flushing = false;
        }
    }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }

    @Override
    public boolean isAttached() { return true; }
}
