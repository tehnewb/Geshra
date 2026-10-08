package geshra.net.web;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable dense opcode index built at startup. Browser packet dispatch performs one bounds check
 * and array access without hashing or boxed integers. The table covers only the highest registered
 * unsigned-byte opcode; diagnostics build a read-only map lazily instead of retaining duplicate
 * indexing structures on every server. Handler lifecycles remain owned by Spring.
 */
@Component
public class PacketHandlerRegistry {
    private final PacketHandler[] handlers; // Dense unsigned-byte opcode table.
    private Map<Integer, PacketHandler> handlerView; // Lazily constructed diagnostics view, guarded by this registry.

    /**
     * Indexes startup handlers and rejects duplicate or unrepresentable opcodes.
     * @param allHandlers handlers available at startup
     */
    @Autowired
    public PacketHandlerRegistry(List<PacketHandler> allHandlers) {
        int highest = -1;
        for (PacketHandler handler : allHandlers) {
            int id = handler.getID();
            if (id < 0 || id > 255) throw new IllegalArgumentException("Packet opcode must fit in an unsigned byte: " + id);
            highest = Math.max(highest, id);
        }
        handlers = new PacketHandler[highest + 1];
        for (PacketHandler handler : allHandlers) {
            int id = handler.getID();
            if (handlers[id] != null) throw new IllegalStateException("Duplicate packet handler ID: " + id);
            handlers[id] = handler;
        }
    }

    /**
     * Returns the indexed handler or null for an unknown opcode.
     * @param packetID unsigned browser packet opcode
     * @return registered handler or null
     */
    public PacketHandler getHandler(int packetID) { return packetID >= 0 && packetID < handlers.length ? handlers[packetID] : null; }

    /**
     * Returns a stable read-only diagnostics view, constructing it only when requested.
     * @return indexed handlers
     */
    public synchronized Map<Integer, PacketHandler> getAllHandlers() {
        if (handlerView == null) {
            Map<Integer, PacketHandler> snapshot = new HashMap<>();
            for (int i = 0; i < handlers.length; i++) {
                if (handlers[i] != null) snapshot.put(i, handlers[i]);
            }
            handlerView = Collections.unmodifiableMap(snapshot);
        }
        return handlerView;
    }
}
