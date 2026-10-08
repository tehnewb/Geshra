package geshra.net.web.ui;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;

/**
 * Queues thread-confined DOM mutations and writes them directly into pooled WebSocket packets.
 * Storage is allocated only when used. Already ordered mutations require no sorting; mixed
 * priorities use a stable sort so equal priorities preserve insertion order. Queue insertion never
 * changes a mutation's priority, avoiding integer overflow in ordinary maximum-priority updates.
 *
 * <p>Inactive or unwritable channels retain pending work. Packets split at the unsigned 16-bit
 * mutation-count limit. Each packet uses one buffer, with ownership transferred to Netty. A failed
 * encoder releases its buffer and keeps unsent mutations available for correction or clearing.</p>
 */
public class DOMDispatcher {
    /**
     * Largest mutation count representable in one browser packet.
     */
    private static final int MAX_PACKET_UPDATES = 65535;
    /**
     * Target payload size bounding ordinary packet buffers and slow-client write bursts.
     */
    private static final int TARGET_PACKET_BYTES = 65536;
    /**
     * Stable ascending priority order; sorting occurs only when insertion order requires it.
     */
    private static final Comparator<DOMUpdate> PRIORITY_ORDER = Comparator.comparingInt(DOMUpdate::getPriority);
    private final Component owner; // Owning component, or null for standalone dispatchers.
    private ArrayList<DOMUpdate> updates; // Lazily allocated ordered pending mutations.
    private boolean needsSorting; // Whether queued priorities are out of order.
    private int lastPriority; // Priority of the last inserted mutation.

    /**
     * Creates an empty standalone dispatcher without allocating queue storage.
     */
    public DOMDispatcher() { this(null); }

    /**
     * Creates a dispatcher that notifies its component when attached work is queued.
     * @param owner owning component, or null
     */
    DOMDispatcher(Component owner) { this.owner = owner; }

    /**
     * Queues one mutation without a varargs array.
     * @param update mutation to append
     * @return this dispatcher
     */
    public DOMDispatcher queue(DOMUpdate update) {
        Objects.requireNonNull(update, "update");
        if (updates == null) updates = new ArrayList<>(4);
        int priority = update.getPriority();
        if (!updates.isEmpty() && priority < lastPriority) needsSorting = true;
        lastPriority = priority;
        updates.add(update);
        if (owner != null) owner.updatesQueued();
        return this;
    }

    /**
     * Queues multiple mutations in the supplied order.
     * @param updates mutations to append
     * @return this dispatcher
     */
    public DOMDispatcher queue(DOMUpdate... updates) {
        for (DOMUpdate update : updates) queue(update);
        return this;
    }

    /**
     * Moves ordered mutations from another dispatcher, leaving it empty.
     * @param dispatcher source dispatcher; must not be this dispatcher
     * @return this dispatcher
     */
    public DOMDispatcher queue(DOMDispatcher dispatcher) {
        if (dispatcher == this) throw new IllegalArgumentException("Cannot merge a dispatcher into itself");
        if (!dispatcher.hasUpdates()) return this;
        if (dispatcher.needsSorting) dispatcher.updates.sort(PRIORITY_ORDER);
        for (int i = 0, size = dispatcher.updates.size(); i < size; i++) queue(dispatcher.updates.get(i));
        dispatcher.clear();
        return this;
    }

    /**
     * Reports whether any work remains queued.
     * @return true when mutations are pending
     */
    boolean hasUpdates() { return updates != null && !updates.isEmpty(); }

    /**
     * Cancels an unrendered child's creation before it is moved to another parent.
     * @param childID identifier of the child
     */
    void cancelCreation(int childID) {
        if (updates != null) updates.removeIf(update -> update.createsChild(childID));
    }

    /**
     * Releases queued mutation references and resets ordering state.
     */
    void clear() {
        if (updates != null) {
            if (updates.size() > 512) updates = null;
            else updates.clear();
        }
        needsSorting = false;
        lastPriority = 0;
    }

    /**
     * Writes pending mutations in bounded packets, flushing each packet to relieve backpressure.
     * A single large mutation remains indivisible and may exceed the target packet size.
     * @param channel client channel
     */
    public void flush(Channel channel) {
        if (!hasUpdates() || channel == null || !channel.isActive() || !channel.isWritable()) return;
        if (needsSorting) updates.sort(PRIORITY_ORDER);
        int sent = 0;
        int size = updates.size();
        try {
            while (sent < size && channel.isWritable()) {
                int count = Math.min(MAX_PACKET_UPDATES, size - sent);
                ByteBuf packet = channel.alloc()
                        .buffer(Math.min(TARGET_PACKET_BYTES, 3 + count * 32));
                boolean transferred = false;
                try {
                    packet.writeByte(1);
                    packet.writeShort(0);
                    int encoded = 0;
                    while (encoded < count && packet.writerIndex() < TARGET_PACKET_BYTES) {
                        DOMUpdate update = updates.get(sent + encoded);
                        update.writeTo(packet);
                        encoded++;
                    }
                    packet.setShort(1, encoded);
                    channel.writeAndFlush(new BinaryWebSocketFrame(packet));
                    transferred = true;
                    sent += encoded;
                } finally {
                    if (!transferred) packet.release();
                }
            }
            if (sent == size) clear();
        } finally {
            if (sent > 0) {
                if (hasUpdates()) updates.subList(0, sent)
                        .clear();
            }
        }
    }
}
