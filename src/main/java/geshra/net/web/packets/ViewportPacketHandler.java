package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
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
        int id = packet.readInt();
        int revision = packet.readInt();
        int first = packet.readInt();
        int end = packet.readInt();
        if (context.getUI().get(id) instanceof VirtualList<?> list) list.publish(new ViewportEvent(revision, first, end));
    }

    @Override
    public int getID() { return 8; }
}
