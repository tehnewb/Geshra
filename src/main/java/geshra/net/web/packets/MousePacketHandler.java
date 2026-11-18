package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.PacketInput;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.MouseButton;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.event.ClickEvent;
import io.netty.buffer.ByteBuf;

/**
 * Decodes browser click coordinates and modifier keys for the target component.
 */
@org.springframework.stereotype.Component
public class MousePacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int componentID = PacketInput.integer(context, packet);

        UI ui = context.getUI();
        Component component = ui.get(componentID);
        if (component == null)
            return;

        int flags = packet.readUnsignedByte();
        int button = context.isCompactInput() ? flags & 7 : flags;
        int clientX = PacketInput.coordinate(context, packet);
        int clientY = PacketInput.coordinate(context, packet);
        int pageX = PacketInput.coordinate(context, packet);
        int pageY = PacketInput.coordinate(context, packet);
        int screenX = PacketInput.coordinate(context, packet);
        int screenY = PacketInput.coordinate(context, packet);
        int modifiers = context.isCompactInput() ? flags >>> 3 : packet.readUnsignedByte();

        component.publish(new ClickEvent(component, MouseButton.fromCode(button), clientX, clientY, pageX, pageY, screenX, screenY, modifiers));
    }

    @Override
    public boolean supportsCompactInput() { return getClass() == MousePacketHandler.class; }

    @Override
    public int getID() {
        return 1;
    }
}
