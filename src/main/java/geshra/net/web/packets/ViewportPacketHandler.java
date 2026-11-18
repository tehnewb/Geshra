package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.PacketInput;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.list.VirtualList;
import geshra.net.web.ui.event.ViewportEvent;
import io.netty.buffer.ByteBuf;

/**
 * Routes bounded virtual-window requests to their owning list, with no per-dataset scan.
 */
public final class ViewportPacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int id = PacketInput.integer(context, packet);
        int revision = PacketInput.integer(context, packet);
        int first = PacketInput.integer(context, packet);
        int end = PacketInput.integer(context, packet);
        int token = packet.isReadable() ? PacketInput.integer(context, packet) : 0;
        if (token < 0 || packet.isReadable()) throw new IllegalArgumentException("Invalid viewport request identity");
        if (context.getUI().get(id) instanceof VirtualList<?> list) list.publish(new ViewportEvent(revision, first, end, token));
    }

    @Override
    public boolean supportsCompactInput() { return getClass() == ViewportPacketHandler.class; }

    @Override
    public int getID() { return 8; }
}
