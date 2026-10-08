package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.ValueComponent;
import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;

/**
 * Synchronizes browser-sanitized programmatic values without synthesizing user input callbacks.
 * Range clamping, radio peer changes, and native select defaults remain browser-authoritative.
 */
public final class ValueSyncPacketHandler implements PacketHandler {
    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        int id = packet.readInt();
        int length = packet.readUnsignedShort();
        String value = packet.readCharSequence(length, StandardCharsets.UTF_8)
                .toString();
        if (context.getUI().get(id) instanceof ValueComponent component) component.synchronizeValue(component.deconstruct(value));
    }

    @Override
    public int getID() { return 10; }
}
