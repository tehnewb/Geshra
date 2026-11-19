package geshra.android;

import android.graphics.Bitmap;
import android.net.http.SslError;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/**
 * Enforces the configured top-level origin independently of native-initiated navigation, browser
 * redirects and restored route state. Subresource errors do not replace a working application.
 * TLS errors are always canceled; renderer death is handled by an explicit native retry screen.
 */
final class BrowserClient extends WebViewClient {
    private final MainActivity activity; // Owner of the single live document and retry UI.
    private final ServerOrigin origin; // Immutable trusted application origin.

    /**
     * Binds callbacks to the activity that owns this exact document.
     * @param activity lifecycle and native UI owner
     * @param origin exact trusted application origin
     */
    BrowserClient(MainActivity activity, ServerOrigin origin) {
        this.activity = activity;
        this.origin = origin;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        if (!activity.isCurrentBrowser(view)) return true;
        String address = request.getUrl()
                .toString();
        if (!request.isForMainFrame()) return !origin.allowsFrame(address);
        if (origin.contains(address)) return false;
        activity.navigate(address, request.hasGesture());
        return true;
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        if (!activity.isCurrentBrowser(view) || request.isForMainFrame() && !origin.contains(request.getUrl().toString()))
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        return null;
    }

    @Override
    public void onPageStarted(WebView view, String address, Bitmap icon) {
        if (!activity.isCurrentBrowser(view)) return;
        activity.documentStarted();
        if (!origin.contains(address)) {
            view.stopLoading();
            activity.showError(R.string.link_unavailable);
        }
    }

    @Override
    public void doUpdateVisitedHistory(WebView view, String address, boolean reload) { if (activity.isCurrentBrowser(view)) activity.recordRoute(address); }

    @Override
    public void onPageFinished(WebView view, String address) { if (activity.isCurrentBrowser(view)) activity.documentFinished(); }

    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        if (activity.isCurrentBrowser(view) && request.isForMainFrame()) activity.showError(R.string.connection_error);
    }

    @Override
    public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
        if (activity.isCurrentBrowser(view) && request.isForMainFrame()) activity.showError(R.string.server_error);
    }

    @Override
    public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
        handler.cancel();
        if (activity.isCurrentBrowser(view) && origin.contains(error.getUrl())) activity.showError(R.string.secure_connection_error);
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        activity.rendererGone(view);
        return true;
    }
}
