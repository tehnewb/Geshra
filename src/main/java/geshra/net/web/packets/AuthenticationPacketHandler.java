package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import geshra.net.web.TextPacketEncoder;
import geshra.net.web.ui.BrowserJson;
import io.netty.buffer.ByteBuf;
import java.util.Map;

/**
 * Acknowledges browser receipt of the HTTP cookie before invoking a server-verified Java callback.
 * The nonce is only a correlation value; identity and authentication results stay on the server.
 */
public final class AuthenticationPacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int nonce = packet.readInt();
        context.send(TextPacketEncoder.encode(context.getChannel(), 6, BrowserJson.encode(Map.of("action", "ack", "request", nonce))));
        context.completeAuthentication(nonce);
    }

    @Override
    public int getID() { return 11; }
}
