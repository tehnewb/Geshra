package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
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
        int componentID = packet.readInt();

        UI ui = context.getUI();
        Component component = ui.get(componentID);
        if (component == null)
            return;

        int button = packet.readUnsignedByte();
        int clientX = packet.readShort();
        int clientY = packet.readShort();
        int pageX = packet.readShort();
        int pageY = packet.readShort();
        int screenX = packet.readShort();
        int screenY = packet.readShort();
        int modifiers = packet.readUnsignedByte();

        component.publish(new ClickEvent(component, MouseButton.fromCode(button), clientX, clientY, pageX, pageY, screenX, screenY, modifiers));
    }

    @Override
    public int getID() {
        return 1;
    }
}
