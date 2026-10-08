package geshra.net.web;

import geshra.net.web.SessionContext;
import io.netty.buffer.ByteBuf;

/**
 * Decodes one browser packet opcode on the session event-loop thread. Implementations consume the payload from the supplied buffer without retaining it; the caller owns buffer release.
 */
public interface PacketHandler {

    /**
     * Consumes this opcode payload and applies it to the supplied session. The caller retains ownership of the buffer.
     *
     * @param context context supplied to this operation
     * @param packet packet supplied to this operation
     */
    void handlePacket(SessionContext context, ByteBuf packet);

    /**
     * Returns the unsigned browser opcode dispatched to this handler.
     * @return the resulting id value
     */
    int getID();

}
