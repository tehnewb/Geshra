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
    private final int token; // Optional browser request identity; zero denotes an older client.

    /**
     * Creates an indexed viewport request.
     * @param revision current configuration generation
     * @param first inclusive start
     * @param end exclusive end
     */
    public ViewportEvent(int revision, int first, int end) { this(revision, first, end, 0); }

    /**
     * Creates a request with an identity preserved across reconnects and range resubmissions.
     * @param revision configuration generation
     * @param first inclusive start
     * @param end exclusive end
     * @param token non-negative request identity, or zero for older clients
     */
    public ViewportEvent(int revision, int first, int end, int token) { super(EventTypes.VIEWPORT); this.revision = revision; this.first = first; this.end = end; this.token = token; }

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

    /**
     * Returns the request identity echoed by a request-aware server.
     * @return request token, or zero for an older client
     */
    public int getToken() { return token; }
}
