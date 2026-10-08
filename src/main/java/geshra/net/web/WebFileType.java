package geshra.net.web;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * File extensions and MIME types used by the static asset server.
 */
public enum WebFileType {
    /**
     * JavaScript source.
     */
    JAVASCRIPT(".js", "application/javascript; charset=UTF-8"),
    /**
     * HTML document.
     */
    HTML(".html", "text/html; charset=UTF-8"),
    /**
     * CSS stylesheet.
     */
    CSS(".css", "text/css; charset=UTF-8"),
    /**
     * JSON document.
     */
    JSON(".json", "application/json; charset=UTF-8"),
    /**
     * SVG image.
     */
    SVG(".svg", "image/svg+xml"),
    /**
     * PNG image.
     */
    PNG(".png", "image/png"),
    /**
     * JPG image.
     */
    JPG(".jpg", "image/jpeg"),
    /**
     * JPEG image.
     */
    JPEG(".jpeg", "image/jpeg"),
    /**
     * Unknown file format.
     */
    OTHER("", "application/octet-stream");

    private final String extension; // Filename suffix used to identify this resource type.
    private final String contentType; // HTTP Content-Type emitted for this resource type.

    /**
     * Associates a filename suffix with its MIME type and filesystem loading strategy.
     *
     * @param extension extension supplied to this operation
     * @param contentType HTTP response MIME type
     */
    WebFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    /**
     * Returns the filename extension, including its leading dot.
     * @return extension or an empty string for the fallback type
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Returns the HTTP content type for this format.
     * @return MIME type and optional charset
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * Reads file bytes without altering application assets.
     * @param path filesystem path
     * @return original file content
     * @throws IOException if reading fails
     */
    public byte[] load(Path path) throws IOException { return Files.readAllBytes(path); }

    /**
     * Resolves the type from a filename's extension, ignoring case.
     * @param filename filename or path
     * @return matching format or {@link #OTHER}
     */
    public static WebFileType fromFilename(String filename) {
        /*
         * Resolve known suffixes without allocating filesystem metadata or opening the resource.
         */
        String lower = filename.toLowerCase(Locale.ROOT);
        for (WebFileType type : values()) {
            if (!type.extension.isEmpty() && lower.endsWith(type.extension)) return type;
        }
        return OTHER;
    }
}
