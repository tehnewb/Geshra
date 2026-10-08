package geshra.net.web.packets;

import geshra.net.web.PacketHandler;
import geshra.net.web.SessionContext;
import geshra.net.web.ui.components.FileUploader;
import geshra.net.web.ui.components.FileUploadHandler;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Decodes an uploaded filename and content and invokes the target FileUploader callback.
 */
@Component
public class FileUploadPacketHandler implements PacketHandler {

    @Override
    public void handlePacket(SessionContext context, ByteBuf packet) {
        if (packet.readableBytes() < 10)
            throw new IllegalArgumentException("Incomplete upload header");
        int componentID = packet.readInt();
        int fileNameLength = packet.readUnsignedShort();
        int fileLength = packet.readInt();
        if (fileLength < 0 || fileNameLength > packet.readableBytes() || fileLength != packet.readableBytes() - fileNameLength)
            throw new IllegalArgumentException("Upload length does not match its payload");
        String fileName = packet.readCharSequence(fileNameLength, StandardCharsets.UTF_8)
                .toString();

        if (context.getUI().get(componentID) instanceof FileUploader component) {
            FileUploadHandler handler = component.getHandler();
            if (handler == null) return;
            byte[] data = new byte[fileLength];
            packet.readBytes(data);
            handler.handle(fileName, data);
        }
    }

    @Override
    public int getID() {
        return 6;
    }

}
