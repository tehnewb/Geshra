package geshra.android;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Hosts exactly one live Geshra document using the system WebView. Java application code remains
 * on the configured server; the host grants no JavaScript/native object bridge or broad storage
 * access. Browser cookies own authentication and Geshra retains its existing WebSocket protocol.
 * Rotation resizes the same document. Process recreation reloads a validated route instead of
 * replaying serialized DOM/component identities. Renderer failures require an explicit retry.
 * File dialogs, JavaScript dialogs and downloads are activity-owned and canceled on destruction.
 */
public final class MainActivity extends ComponentActivity {
    /**
     * Diagnostic tag never includes cookies, passwords or public endpoint query parameters.
     */
    private static final String TAG = "GeshraAndroid";
    /**
     * Chromium baseline for the native dialog, popover and modern form/runtime features.
     */
    private static final int MINIMUM_WEBVIEW_MAJOR = 126;
    /**
     * Only validated route state survives Android process recreation.
     */
    private static final String SAVED_ROUTE = "geshra.route";

    private ServerOrigin origin; // Exact trusted application origin.
    private FrameLayout root; // Activity-owned content and fullscreen host.
    private volatile WebView browser; // Sole document, compared by the IO-thread interception guard.
    private LinearLayout errorPanel; // Native retry UI independent of network content.
    private TextView errorMessage; // Localized explanation without server diagnostics.
    private ProgressBar progress; // Top-edge document loading indicator.
    private BrowserChrome chrome; // Dialog, picker and fullscreen ownership.
    private BrowserFileChooser fileChooser; // At most one pending system document request.
    private BrowserDownloads downloads; // Bounded authenticated download operation.
    private ActivityResultLauncher<Intent> pickerLauncher; // System picker result registration.
    private ActivityResultLauncher<Intent> downloadLauncher; // Save-document result registration.
    private String currentRoute; // Latest committed trusted route for restoration/retry.
    private volatile boolean destroyed; // Prevents late UI/IO callbacks from using a dead activity.
    private long documentGeneration; // Distinguishes native callback ownership across page loads.
    private boolean resumed; // Native dialogs/intents may only open while the activity is foregrounded.

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        origin = new ServerOrigin(BuildConfig.SERVER_URL, BuildConfig.DEBUG);
        currentRoute = origin.startUrl();
        if (state != null && origin.contains(state.getString(SAVED_ROUTE))) currentRoute = state.getString(SAVED_ROUTE);
        Uri incoming = getIntent()
                .getData();
        if (state == null && incoming != null && origin.contains(incoming.toString())) currentRoute = incoming.toString();
        pickerLauncher = registerForActivityResult(new StartActivityForResult(), result -> fileChooser.receive(result.getResultCode(), result.getData()));
        downloadLauncher = registerForActivityResult(new StartActivityForResult(), result -> downloads.receive(result.getResultCode(), result.getData()));
        fileChooser = new BrowserFileChooser(this);
        downloads = new BrowserDownloads(this, origin);
        buildContent();
        getOnBackPressedDispatcher()
                .addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (chrome != null && chrome.hideFullscreen()) return;
                if (browser != null && browser.canGoBack()) browser.goBack();
                else finish();
            }
        });
        openDocument();
    }

    /**
     * Builds native loading/error UI and applies system-bar, cutout and keyboard insets once.
     */
    private void buildContent() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        setContentView(root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets occupied = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            view.setPadding(occupied.left, occupied.top, occupied.right, occupied.bottom);
            return insets;
        });
        errorPanel = new LinearLayout(this);
        errorPanel.setOrientation(LinearLayout.VERTICAL);
        errorPanel.setGravity(Gravity.CENTER);
        errorPanel.setClickable(true);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        errorPanel.setPadding(padding, padding, padding, padding);
        errorPanel.setBackgroundColor(Color.WHITE);
        errorMessage = new TextView(this);
        errorMessage.setGravity(Gravity.CENTER);
        errorMessage.setTextSize(18);
        errorPanel.addView(errorMessage, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button retry = new Button(this);
        retry.setText(R.string.retry);
        retry.setOnClickListener(view -> openDocument());
        errorPanel.addView(retry);
        root.addView(errorPanel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        root.addView(progress, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.round(3 * getResources().getDisplayMetrics().density), Gravity.TOP));
    }

    /**
     * Creates a fresh browser only when needed, otherwise retries the latest trusted document.
     * No automatic retry loop can repeatedly recreate a crashed renderer or bypass TLS failure.
     */
    private void openDocument() {
        if (destroyed) return;
        PackageInfo webViewPackage = WebView.getCurrentWebViewPackage();
        if (!supportedWebView(webViewPackage)) {
            showError(R.string.webview_update);
            return;
        }
        if (browser == null) {
            try {
                browser = new WebView(this);
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
                WebSettings settings = browser.getSettings();
                settings.setJavaScriptEnabled(true);
                settings.setDomStorageEnabled(true);
                settings.setSupportZoom(true);
                settings.setBuiltInZoomControls(true);
                settings.setDisplayZoomControls(false);
                settings.setSafeBrowsingEnabled(true);
                settings.setAllowFileAccess(false);
                settings.setAllowContentAccess(false);
                settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                settings.setMediaPlaybackRequiresUserGesture(true);
                settings.setSupportMultipleWindows(true);
                settings.setJavaScriptCanOpenWindowsAutomatically(false);
                settings.setTextZoom(Math.round(100 * getResources().getConfiguration().fontScale));
                CookieManager.getInstance()
                        .setAcceptCookie(true);
                CookieManager.getInstance()
                        .setAcceptThirdPartyCookies(browser, false);
                chrome = new BrowserChrome(this, fileChooser, origin, browser);
                browser.setWebViewClient(new BrowserClient(this, origin));
                browser.setWebChromeClient(chrome);
                browser.setDownloadListener(downloads);
                root.addView(browser, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            } catch (RuntimeException failure) {
                Log.e(TAG, "Unable to initialize the system WebView", failure);
                discardBrowser(false);
                showError(R.string.webview_update);
                return;
            }
        }
        errorPanel.setVisibility(View.GONE);
        browser.setVisibility(View.VISIBLE);
        browser.onResume();
        progress.setVisibility(View.VISIBLE);
        browser.loadUrl(currentRoute);
    }

    /**
     * Checks the engine before loading features unavailable in outdated system packages.
     * @param installed installed system browser package, or null when unavailable
     * @return whether its Chromium major version meets the documented runtime baseline
     */
    private static boolean supportedWebView(PackageInfo installed) {
        /*
         * Check the installed engine instead of assuming an OS version implies current Web APIs.
         */
        if (installed == null || installed.versionName == null) return false;
        String version = installed.versionName;
        int end = version.indexOf('.');
        if (end < 1) return false;
        try {
            return Integer.parseInt(version.substring(0, end)) >= MINIMUM_WEBVIEW_MAJOR;
        } catch (NumberFormatException failure) {
            // A vendor version with no Chromium prefix cannot establish feature compatibility.
            return false;
        }
    }

    /**
     * Records committed in-app history without trusting arbitrary restored or redirected URLs.
     * @param address browser history's latest URL
     */
    void recordRoute(String address) { if (origin.contains(address)) currentRoute = address; }

    /**
     * Completes the loading indicator without dismissing an error already reported for the page.
     */
    void documentFinished() { progress.setVisibility(View.GONE); }

    /**
     * Cancels results owned by the previous document before a new page can open native UI.
     */
    void documentStarted() {
        documentGeneration++;
        if (chrome != null) chrome.close();
    }

    /**
     * Captures the document identity before native picker/popup work leaves the callback stack.
     * @return monotonically increasing identity local to this activity instance
     */
    long documentGeneration() { return documentGeneration; }

    /**
     * Rejects asynchronous work from a replaced document or destroyed activity.
     * @param generation original requesting document's identity
     * @return whether the same trusted document still owns the activity
     */
    boolean isCurrentDocument(long generation) { return !destroyed && documentGeneration == generation && trustedDocument(); }

    /**
     * Rejects callbacks from a renderer or routing window that no longer owns this activity.
     * @param candidate exact WebView supplied by an Android callback
     * @return whether the browser is still the activity's current document instance
     */
    boolean isCurrentBrowser(WebView candidate) { return !destroyed && browser == candidate; }

    /**
     * Displays retry controls while preserving the last safe route and existing cookie store.
     * @param message localized user-facing failure description
     */
    void showError(int message) {
        if (destroyed) return;
        if (chrome != null) chrome.close();
        if (browser != null) {
            browser.setVisibility(View.INVISIBLE);
            browser.onPause();
        }
        errorMessage.setText(message);
        errorPanel.setVisibility(View.VISIBLE);
        errorPanel.bringToFront();
        progress.setVisibility(View.GONE);
    }

    /**
     * Tears down the failed renderer and waits for a deliberate user retry.
     * @param failed callback's exact failed browser instance
     */
    void rendererGone(WebView failed) {
        if (failed != browser) return;
        discardBrowser(true);
        showError(R.string.renderer_error);
    }

    /**
     * Uses in-app navigation only for exact origins; external HTTP links require a user gesture.
     * @param address link or popup destination
     * @param userGesture whether a real page gesture initiated this navigation
     */
    void navigate(String address, boolean userGesture) {
        if (destroyed) return;
        NavigationDecision decision = origin.classify(address);
        if (decision == NavigationDecision.IN_APP) {
            recordRoute(address);
            if (browser != null) browser.loadUrl(address);
        } else if (decision == NavigationDecision.EXTERNAL && userGesture && resumed) {
            try {
                Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(address));
                open.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(open);
            } catch (ActivityNotFoundException failure) {
                notice(R.string.link_unavailable);
            }
        } else notice(R.string.link_unavailable);
    }

    /**
     * Returns the activity-owned root solely for fullscreen content attachment.
     * @return live native root container
     */
    FrameLayout contentRoot() { return root; }

    /**
     * Reports the current document origin before exposing any picker or native dialog action.
     * @return whether a live trusted application document owns the browser
     */
    boolean trustedDocument() { return browser != null && browser.getVisibility() == View.VISIBLE && errorPanel.getVisibility() != View.VISIBLE && origin.contains(browser.getUrl()); }

    /**
     * Native UI may not be opened by timers/callbacks after the activity is backgrounded.
     * Picker results use document ownership instead because Android delivers them before Resume.
     * @return whether the trusted visible document currently owns the foreground activity
     */
    boolean nativeUiAllowed() { return resumed && !destroyed && trustedDocument(); }

    /**
     * Launches the registered document picker without arbitrary chooser-supplied native intents.
     * @param request app-constructed open-document intent
     */
    void chooseFile(Intent request) { pickerLauncher.launch(request); }

    /**
     * Launches the app-constructed create-document operation for one download.
     * @param request save-dialog intent
     */
    void chooseDownload(Intent request) { downloadLauncher.launch(request); }

    /**
     * Shows a short localized status only while this activity is alive.
     * @param message localized status resource
     */
    void notice(int message) {
        if (destroyed) return;
        Toast.makeText(this, message, Toast.LENGTH_LONG)
                .show();
    }

    /**
     * Cancels native callbacks before releasing the document and its renderer resources.
     * @param rendererLost whether the failed browser must not receive ordinary rendering calls
     */
    private void discardBrowser(boolean rendererLost) {
        if (chrome != null) chrome.close();
        chrome = null;
        if (browser == null) return;
        WebView discarded = browser;
        browser = null;
        if (!rendererLost) discarded.stopLoading();
        root.removeView(discarded);
        if (!rendererLost) {
            discarded.setWebChromeClient(null);
            discarded.setDownloadListener(null);
            discarded.removeAllViews();
        }
        discarded.destroy();
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (browser != null) browser.onPause();
        CookieManager.getInstance()
                .flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (browser != null && errorPanel.getVisibility() != View.VISIBLE) {
            browser.onResume();
            browser.evaluateJavascript("window.dispatchEvent(new Event('pageshow'))", null);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri address = intent.getData();
        if (address != null && origin.contains(address.toString())) {
            currentRoute = address.toString();
            openDocument();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        if (browser != null) browser.getSettings()
                .setTextZoom(Math.round(100 * configuration.fontScale));
        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (browser != null) recordRoute(browser.getUrl());
        state.putString(SAVED_ROUTE, currentRoute);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        if (fileChooser != null) fileChooser.cancel();
        if (downloads != null) downloads.close();
        discardBrowser(false);
        super.onDestroy();
    }
}
