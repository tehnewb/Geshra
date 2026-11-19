package geshra.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Streams one user-requested same-origin download to Android's create-document picker without
 * storage permissions or whole-file buffering. Cookie headers never follow cross-origin or
 * cleartext redirects. Work may continue while the activity is backgrounded, but destruction
 * cancels it; this is deliberately not an unattended/background download service. Picker and
 * worker ownership are bounded and late results cannot attach to another document's request.
 */
final class BrowserDownloads implements DownloadListener {
    /**
     * Bounds network redirect work without relying on credential-propagating automatic redirects.
     */
    private static final int MAXIMUM_REDIRECTS = 5;
    /**
     * Reusable-size streaming buffer bounds Java memory independently of download length.
     */
    private static final int BUFFER_BYTES = 16384;

    private final MainActivity activity; // Activity and content-provider permission owner.
    private final ServerOrigin origin; // Exact server origin permitted to receive cookies.
    private final ContentResolver documents; // Thread-safe access to user-granted document streams.
    private final ExecutorService worker = Executors.newSingleThreadExecutor(); // One stream at a time; no unbounded work queue is accepted.
    private DownloadRequest pending; // Request associated with the open system save dialog.
    private boolean busy; // Covers the picker and transfer until completion on the main thread.
    private long documentGeneration; // Save-dialog result belongs only to the requesting document.
    private final DownloadConnection connectionOwner = new DownloadConnection(); // Cross-thread publication and cancellation boundary.

    /**
     * Creates an activity-scoped downloader; no worker task starts until the user chooses a file.
     * @param activity native picker and lifecycle owner
     * @param origin exact server origin
     */
    BrowserDownloads(MainActivity activity, ServerOrigin origin) {
        this.activity = activity;
        this.origin = origin;
        documents = activity.getContentResolver();
    }

    @Override
    public void onDownloadStart(String address, String userAgent, String disposition, String mimeType, long length) {
        if (connectionOwner.isClosed() || busy || !activity.nativeUiAllowed() || !origin.contains(address)) {
            activity.notice(R.string.download_failed);
            return;
        }
        String type = mimeType != null && mimeType.matches("[a-zA-Z0-9!#$&^_.+-]+/[a-zA-Z0-9!#$&^_.+-]+") ? mimeType : "application/octet-stream";
        String filename = URLUtil.guessFileName(address, disposition, type);
        if (filename == null || filename.isBlank()) filename = "download";
        filename = filename.replaceAll("[\\\\/\\p{Cntrl}]", "_");
        if (filename.length() > 160) filename = filename.substring(0, 160);
        pending = new DownloadRequest(address, userAgent, CookieManager.getInstance().getCookie(address), type, filename);
        documentGeneration = activity.documentGeneration();
        busy = true;
        Intent request = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE);
        request.setType(type);
        request.putExtra(Intent.EXTRA_TITLE, filename);
        try {
            activity.chooseDownload(request);
        } catch (ActivityNotFoundException failure) {
            pending = null;
            busy = false;
            activity.notice(R.string.download_failed);
        }
    }

    /**
     * Transfers only to a readable system-granted content destination after deliberate user action.
     * @param result Android save-dialog result code
     * @param data result containing the selected writable document URI
     */
    void receive(int result, Intent data) {
        DownloadRequest request = pending;
        pending = null;
        if (connectionOwner.isClosed() || request == null || !activity.isCurrentDocument(documentGeneration) || result != Activity.RESULT_OK || data == null || data.getData() == null) {
            busy = false;
            return;
        }
        Uri destination = data.getData();
        if (!DocumentUris.external(activity, destination)) {
            busy = false;
            activity.notice(R.string.download_failed);
            return;
        }
        try {
            worker.execute(() -> transfer(request, destination));
        } catch (RejectedExecutionException failure) {
            busy = false;
            activity.notice(R.string.download_failed);
        }
    }

    /**
     * Opens a bounded redirect chain, then streams to the user-selected destination.
     * @param request captured same-origin download configuration
     * @param destination user-granted content document
     */
    private void transfer(DownloadRequest request, Uri destination) {
        boolean successful = false;
        HttpURLConnection opened = null;
        try {
            String address = request.address();
            for (int redirects = 0; redirects <= MAXIMUM_REDIRECTS; redirects++) {
                if (connectionOwner.isClosed() || !origin.contains(address)) throw new IOException("Download origin changed.");
                URL target = URI.create(address)
                        .toURL();
                opened = (HttpURLConnection) target.openConnection();
                if (!connectionOwner.publish(opened)) throw new IOException("Download canceled.");
                opened.setInstanceFollowRedirects(false);
                opened.setConnectTimeout(15000);
                opened.setReadTimeout(30000);
                if (request.userAgent() != null) opened.setRequestProperty("User-Agent", request.userAgent());
                if (request.cookie() != null) opened.setRequestProperty("Cookie", request.cookie());
                if (connectionOwner.isClosed()) throw new IOException("Download canceled.");
                int status = opened.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String location = opened.getHeaderField("Location");
                    connectionOwner.release(opened);
                    opened = null;
                    if (location == null || redirects == MAXIMUM_REDIRECTS) throw new IOException("Invalid download redirect.");
                    address = URI.create(address)
                            .resolve(location)
                            .toASCIIString();
                    continue;
                }
                if (status < 200 || status >= 300) throw new IOException("Server rejected the download.");
                try (InputStream input = opened.getInputStream(); OutputStream output = documents.openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("No writable document stream.");
                    byte[] buffer = new byte[BUFFER_BYTES];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (connectionOwner.isClosed() || Thread.currentThread().isInterrupted()) throw new IOException("Download canceled.");
                        output.write(buffer, 0, count);
                    }
                }
                successful = true;
                break;
            }
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            // Do not include URL/cookie/provider details from arbitrary exception messages in logs.
            Log.w("GeshraAndroid", "Download did not complete (" + failure.getClass().getSimpleName() + ")");
        } finally {
            if (opened != null) connectionOwner.release(opened);
            boolean result = successful && !connectionOwner.isClosed();
            activity.runOnUiThread(() -> {
                busy = false;
                if (!connectionOwner.isClosed()) activity.notice(result ? R.string.download_complete : R.string.download_failed);
            });
        }
    }

    /**
     * Cancels the owned stream and picker request without retaining session credentials.
     */
    void close() {
        pending = null;
        connectionOwner.close();
        worker.shutdownNow();
    }
}
