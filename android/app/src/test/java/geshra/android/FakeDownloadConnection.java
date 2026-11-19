package geshra.android;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;

/**
 * Records cancellation without opening sockets so lifecycle races can be repeated deterministically.
 */
final class FakeDownloadConnection extends HttpURLConnection {
    private volatile boolean disconnected; // Cross-thread observable cancellation outcome.

    /**
     * Creates an unconnected request with a fixed non-routable documentation endpoint.
     * @throws IOException if the constant test URL cannot be represented
     */
    FakeDownloadConnection() throws IOException { super(URI.create("https://app.example.test/file").toURL()); }

    @Override
    public void disconnect() { disconnected = true; }

    @Override
    public boolean usingProxy() { return false; }

    @Override
    public void connect() { connected = true; }

    /**
     * Reports the real cancellation side effect instead of inspecting the owner's private fields.
     * @return whether the fake network request received disconnect
     */
    boolean disconnected() { return disconnected; }
}
