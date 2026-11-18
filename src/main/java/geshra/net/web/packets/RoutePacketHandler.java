package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.PacketInput;
import geshra.net.web.SessionContext;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Decodes a navigation path and asks the session route registry to load it.
 */
@Component
public class RoutePacketHandler implements PacketHandler {

    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int length = PacketInput.length(context, packet);
        String path = packet.readCharSequence(length, StandardCharsets.UTF_8)
                .toString();
        context.handleRoute(path);
    }

    @Override
    public boolean supportsCompactInput() { return getClass() == RoutePacketHandler.class; }

    @Override
    public int getID() {
        return 0;
    }

}
