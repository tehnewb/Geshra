package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.ValueComponent;
import geshra.net.web.ui.event.ValueChangeEvent;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Decodes an input value and publishes the change after updating component state.
 */
@Component
public class ValueChangePacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int componentID = packet.readInt();

        SessionContext session = SessionContext.get();
        if (session == null) return;
        UI ui = session.getUI();

        if (ui.get(componentID) instanceof ValueComponent component) {
            int valueLen = packet.readUnsignedShort();
            Object oldValue = component.getValue();
            Object newValue = component.deconstruct(packet.readCharSequence(valueLen, StandardCharsets.UTF_8).toString());
            component.publish(new ValueChangeEvent(component, oldValue, newValue));
        }
    }

    @Override
    public int getID() {
        return 4;
    }
}
