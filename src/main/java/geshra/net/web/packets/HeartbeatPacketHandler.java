package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import io.netty.buffer.ByteBuf;
import org.springframework.stereotype.Component;

/**
 * Keeps an open browser session alive while the user is idle on a page.
 * <p>
 * The WebSocket handler touches the {@link SessionContext} before dispatching this packet, so this
 * handler intentionally does not need to mutate UI state or send a response. Its purpose is only to
 * give the browser a tiny packet it can send periodically so buttons and forms do not become dead
 * after the page has been sitting open for a long time.
 */
@Component
public class HeartbeatPacketHandler implements PacketHandler {

    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        context.touch();
    }

    @Override
    public int getID() {
        return 7;
    }
}
