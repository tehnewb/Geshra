package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.PacketInput;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.Component;
import geshra.net.web.ui.Key;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.event.KeyDownEvent;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

/**
 * Decodes a browser key press and publishes it to the target component.
 */
@org.springframework.stereotype.Component
public class KeyDownPacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int componentID = PacketInput.integer(context, packet);
        UI ui = context.getUI();
        Component component = ui.get(componentID);

        if (component == null)
            return;

        int flags = packet.readUnsignedByte();
        boolean repeated = context.isCompactInput() ? (flags & 16) != 0 : flags == 1;
        int modifiers = context.isCompactInput() ? flags & 15 : packet.readUnsignedByte();
        int length = PacketInput.length(context, packet);
        String keyName = packet.readCharSequence(length, StandardCharsets.UTF_8)
                .toString();
        Key key = Key.fromName(keyName);
        component.publish(new KeyDownEvent(key, repeated, modifiers));
    }

    @Override
    public boolean supportsCompactInput() { return getClass() == KeyDownPacketHandler.class; }

    @Override
    public int getID() {
        return 3;
    }
}
