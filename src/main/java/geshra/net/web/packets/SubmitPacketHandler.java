package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.PacketInput;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.UI;
import geshra.net.web.ui.components.Form;
import geshra.net.web.ui.event.SubmitEvent;
import io.netty.buffer.ByteBuf;
import org.springframework.stereotype.Component;

/**
 * Decodes submitted form field values and publishes a component SubmitEvent.
 */
@Component
public class SubmitPacketHandler implements PacketHandler {

    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int componentID = PacketInput.integer(context, packet);

        if (context == null)
            return;
        UI ui = context.getUI();

        if (ui.get(componentID) instanceof Form form) {
            form.publish(new SubmitEvent(form.getValues()));
        }

    }

    @Override
    public boolean supportsCompactInput() { return getClass() == SubmitPacketHandler.class; }

    @Override
    public int getID() {
        return 5;
    }

}
