package geshra.net.web.ui.components;

/**
 * Callback interface for processing uploaded files.
 */
public interface FileUploadHandler {
    /**
     * Called when a file is uploaded.
     *
     * @param fileName the name of the uploaded file
     * @param data     the raw byte content of the uploaded file
     */
    void handle(String fileName, byte[] data);
}
