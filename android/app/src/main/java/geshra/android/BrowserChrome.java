package geshra.android;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebView.WebViewTransport;
import android.widget.EditText;
import android.widget.FrameLayout;

/**
 * Owns native dialogs, file selection, popup routing and fullscreen media for one document.
 * Browser capture/location permissions are denied rather than implicitly equating playback with
 * permission to record. Every pending dialog/result is completed or canceled during teardown.
 * Dialog callbacks provide a frame URL and are checked individually. Android's chooser callback
 * exposes only the top-level WebView, not the requesting frame; user document selection and
 * provider-UID checks remain mandatory even when the top-level application origin is trusted.
 */
final class BrowserChrome extends WebChromeClient {
    private final MainActivity activity; // Native lifecycle and view hierarchy owner.
    private final BrowserFileChooser chooser; // Activity's sole picker-result owner.
    private final ServerOrigin origin; // Exact document origin for native operations.
    private final WebView browser; // Exact document instance that owns this callback client.
    private AlertDialog dialog; // At most one displayed JavaScript dialog.
    private JsResult pendingDialog; // Uncompleted browser result, or null after completion.
    private EditText prompt; // Prompt input only for the currently active dialog.
    private View fullscreen; // Custom media view attached above the browser.
    private CustomViewCallback fullscreenCallback; // Callback completed once on fullscreen exit.
    private WebView popup; // Script-disabled temporary window used only to route its URL.

    /**
     * Connects browser-owned UI requests to activity-owned native resources.
     * @param activity host for intents, errors and fullscreen views
     * @param chooser bounded system-file-picker controller
     * @param origin trusted application origin
     * @param browser exact browser instance attached to this callback client
     */
    BrowserChrome(MainActivity activity, BrowserFileChooser chooser, ServerOrigin origin, WebView browser) {
        this.activity = activity;
        this.chooser = chooser;
        this.origin = origin;
        this.browser = browser;
    }

    @Override
    public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams parameters) {
        if (!activity.isCurrentBrowser(view) || !activity.nativeUiAllowed()) {
            callback.onReceiveValue(null);
            return true;
        }
        return chooser.open(callback, parameters);
    }

    @Override
    public boolean onJsAlert(WebView view, String address, String message, JsResult result) {
        return showDialog(address, message, null, result, false);
    }

    @Override
    public boolean onJsConfirm(WebView view, String address, String message, JsResult result) {
        return showDialog(address, message, null, result, true);
    }

    @Override
    public boolean onJsPrompt(WebView view, String address, String message, String defaultValue, JsPromptResult result) {
        return showDialog(address, message, defaultValue == null ? "" : defaultValue, result, true);
    }

    @Override
    public boolean onJsBeforeUnload(WebView view, String address, String message, JsResult result) {
        return showDialog(address, message, null, result, true);
    }

    /**
     * Shows one trusted native JS dialog, canceling an older callback before replacement.
     * @param address frame requesting the dialog
     * @param message application-provided display message
     * @param defaultValue prompt default, or null for alert/confirmation
     * @param result browser callback completed exactly once
     * @param cancellable whether the dialog offers Cancel
     * @return true because the callback is either owned or explicitly rejected
     */
    private boolean showDialog(String address, String message, String defaultValue, JsResult result, boolean cancellable) {
        if (!activity.isCurrentBrowser(browser) || !origin.contains(address) || !activity.nativeUiAllowed()) {
            result.cancel();
            return true;
        }
        finishDialog(false);
        pendingDialog = result;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.app_name)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (closed, which) -> finishDialog(true))
                .setOnCancelListener(closed -> finishDialog(false));
        if (cancellable) builder.setNegativeButton(android.R.string.cancel, (closed, which) -> finishDialog(false));
        if (defaultValue != null) {
            prompt = new EditText(activity);
            prompt.setSingleLine(true);
            prompt.setText(defaultValue);
            builder.setView(prompt);
        }
        dialog = builder.show();
        return true;
    }

    /**
     * Clears ownership before calling back so reentrant browser UI cannot reuse an old result.
     * @param confirmed whether the user accepted the active dialog
     */
    private void finishDialog(boolean confirmed) {
        JsResult completed = pendingDialog;
        AlertDialog dismissed = dialog;
        String entered = prompt == null ? "" : prompt.getText().toString();
        pendingDialog = null;
        dialog = null;
        prompt = null;
        if (dismissed != null) dismissed.dismiss();
        if (completed == null) return;
        if (!confirmed) completed.cancel();
        else if (completed instanceof JsPromptResult textResult) textResult.confirm(entered);
        else completed.confirm();
    }

    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (fullscreen != null || !activity.isCurrentBrowser(browser) || !activity.nativeUiAllowed()) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreen = view;
        fullscreenCallback = callback;
        activity.contentRoot()
                .addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onHideCustomView() { hideFullscreen(); }

    /**
     * Removes the exact activity-owned custom view and completes its callback once.
     * @return whether fullscreen content consumed Back
     */
    boolean hideFullscreen() {
        if (fullscreen == null) return false;
        View removed = fullscreen;
        CustomViewCallback completed = fullscreenCallback;
        fullscreen = null;
        fullscreenCallback = null;
        activity.contentRoot()
                .removeView(removed);
        if (completed != null) completed.onCustomViewHidden();
        return true;
    }

    @Override
    public boolean onCreateWindow(WebView view, boolean dialogWindow, boolean userGesture, Message resultMessage) {
        if (!userGesture || !activity.isCurrentBrowser(view) || !activity.nativeUiAllowed()) return false;
        closePopup();
        popup = new WebView(activity);
        popup.getSettings()
                .setJavaScriptEnabled(false);
        popup.getSettings()
                .setAllowFileAccess(false);
        popup.getSettings()
                .setAllowContentAccess(false);
        popup.setWebViewClient(new PopupClient(this, activity.documentGeneration()));
        ((WebViewTransport) resultMessage.obj).setWebView(popup);
        resultMessage.sendToTarget();
        return true;
    }

    /**
     * Disposes the script-disabled routing window before replacing it or destroying the host.
     */
    void closePopup() {
        if (popup == null) return;
        WebView removed = popup;
        popup = null;
        removed.stopLoading();
        removed.destroy();
    }

    /**
     * Routes only the captured live popup; an old posted callback cannot close its replacement.
     * @param window exact temporary WebView that captured this destination
     * @param address still-untrusted new-window destination
     * @param generation original parent document identity
     */
    void routePopup(WebView window, String address, long generation) {
        if (popup != window || !activity.isCurrentDocument(generation)) return;
        closePopup();
        activity.navigate(address, true);
    }

    @Override
    public void onPermissionRequest(PermissionRequest request) { request.deny(); }

    @Override
    public void onGeolocationPermissionsShowPrompt(String address, GeolocationPermissions.Callback callback) { callback.invoke(address, false, false); }

    @Override
    public boolean onConsoleMessage(ConsoleMessage message) {
        // Application console output may contain user data; release hosts never copy it to logcat.
        return !BuildConfig.DEBUG;
    }

    /**
     * Cancels all native/browser callback ownership when the document's lifetime ends.
     */
    void close() {
        finishDialog(false);
        hideFullscreen();
        closePopup();
        chooser.cancel();
    }
}
