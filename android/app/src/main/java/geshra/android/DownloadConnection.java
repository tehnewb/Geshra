package geshra.android;

import java.net.HttpURLConnection;

/**
 * Owns cross-thread publication and cancellation of the single active download connection.
 * Publication after closure is rejected and disconnected immediately. Monitor sections contain
 * no network operations, so activity destruction never waits for a connect/read timeout while
 * acquiring this owner. Callers recheck closure before initiating network IO and honor timeouts.
 */
final class DownloadConnection {
    private HttpURLConnection active; // Exact connection published by the worker, or null.
    private volatile boolean closed; // Terminal cancellation read without locking in copy loops.

    /**
     * Publishes a connection only while the activity's transfer owner remains open.
     * @param connection unconnected worker-created request
     * @return whether network work may proceed
     */
    boolean publish(HttpURLConnection connection) {
        synchronized (this) {
            if (!closed) {
                if (active != null) throw new IllegalStateException("A download connection is already published.");
                active = connection;
                return true;
            }
        }
        connection.disconnect();
        return false;
    }

    /**
     * Reads terminal cancellation before connecting, copying or scheduling completion feedback.
     * @return whether activity destruction has closed transfer ownership
     */
    boolean isClosed() { return closed; }

    /**
     * Releases the exact completed/redirected connection without disturbing a replacement.
     * @param connection request whose IO has ended
     */
    void release(HttpURLConnection connection) {
        synchronized (this) {
            if (active == connection) active = null;
        }
        connection.disconnect();
    }

    /**
     * Ends transfer ownership before taking a snapshot of the currently published connection.
     */
    void close() {
        HttpURLConnection canceled;
        synchronized (this) {
            closed = true;
            canceled = active;
            active = null;
        }
        if (canceled != null) canceled.disconnect();
    }
}
