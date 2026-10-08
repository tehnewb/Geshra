package geshra.net.web.ui.event;

import geshra.event.Event;
import geshra.event.EventTypes;

/**
 * A coalesced browser request for a fixed-height virtual window. Bounds are item indices rather
 * than pixel positions, so server work is proportional to visible rows and never the dataset size.
 * @author Albert Beaupre
 */
public final class ViewportEvent extends Event {
    private final int revision; // Dataset/configuration generation.
    private final int first; // Inclusive visible-buffer range start.
    private final int end; // Exclusive visible-buffer range end.

    /**
     * Creates an indexed viewport request.
     * @param revision current configuration generation
     * @param first inclusive start
     * @param end exclusive end
     */
    public ViewportEvent(int revision, int first, int end) { super(EventTypes.VIEWPORT); this.revision = revision; this.first = first; this.end = end; }

    /**
     * Returns the configuration generation.
     * @return generation
     */
    public int getRevision() { return revision; }

    /**
     * Returns the inclusive first index.
     * @return first index
     */
    public int getFirst() { return first; }

    /**
     * Returns the exclusive end index.
     * @return end index
     */
    public int getEnd() { return end; }
}
