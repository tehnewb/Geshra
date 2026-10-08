package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.event.DomEvent;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Decodes bounded native-event name and value snapshots from the browser.
 * Native cancellation has already happened according to the registered browser policy.
 */
public final class DomEventPacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        Component component = context.getUI()
                .get(packet.readInt());
        int nameLength = packet.readUnsignedShort();
        String name = packet.readCharSequence(nameLength, StandardCharsets.UTF_8)
                .toString();
        int valueLength = packet.readUnsignedShort();
        String value = packet.readCharSequence(valueLength, StandardCharsets.UTF_8)
                .toString();
        if (component != null) component.publish(new DomEvent(component, name, value));
    }

    @Override
    public int getID() { return 9; }
}
