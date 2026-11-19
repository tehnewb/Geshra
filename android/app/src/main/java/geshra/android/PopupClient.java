package geshra.android;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/**
 * Routes a user-created new-window target through the same origin policy as ordinary links.
 * This temporary window has scripting disabled and never becomes a second Geshra session.
 */
final class PopupClient extends WebViewClient {
    private final BrowserChrome owner; // Owner of the temporary window's lifetime.
    private boolean routed; // Prevents duplicate callback delivery for the same initial target.
    private final long generation; // Parent document identity captured before asynchronous routing.
    private final Handler callbacks = new Handler(Looper.getMainLooper()); // Temporary windows are never attached, so View.post cannot dispatch their work.

    /**
     * Attaches one user-gesture-created popup to the existing application document.
     * @param owner temporary window owner
     * @param generation requesting parent document identity
     */
    PopupClient(BrowserChrome owner, long generation) {
        this.owner = owner;
        this.generation = generation;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        route(view, request.getUrl().toString());
        return true;
    }

    @Override
    public void onPageStarted(WebView view, String address, Bitmap icon) {
        if (!"about:blank".equals(address)) route(view, address);
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (request.isForMainFrame()) {
            String address = request.getUrl()
                    .toString();
            callbacks.post(() -> route(view, address));
        }
        return new WebResourceResponse("text/plain", "UTF-8", 403, "Routed", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }

    /**
     * Stops the temporary load and defers destruction outside its WebView callback stack.
     * @param view exact temporary window receiving the target
     * @param address popup destination, still subject to the main origin policy
     */
    private void route(WebView view, String address) {
        if (routed) return;
        routed = true;
        callbacks.post(() -> owner.routePopup(view, address, generation));
    }
}
