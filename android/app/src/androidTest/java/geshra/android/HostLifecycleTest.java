package geshra.android;

import android.R;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.SystemClock;
import android.net.Uri;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebView.VisualStateCallback;
import android.widget.Button;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Runs the real Android activity and system WebView against the documented local Geshra server.
 * These are device/emulator integration tests, not desktop viewport emulation. They verify a
 * server-produced document, hardened native settings, blocked URL schemes, rotation continuity
 * and fresh-document process-style activity restoration. The server must be reachable at the
 * debug endpoint configured when the APK was built.
 */
@RunWith(AndroidJUnit4.class)
public final class HostLifecycleTest {
    /**
     * A WebView touch opens a real new-window request, routes it through the unattached temporary
     * window and preserves the main browser's Back history. JavaScript does not synthesize a click.
     * @throws Exception if popup routing or Back navigation fails to finish within the deadline
     */
    @Test
    public void routesUserPopupAndPreservesBackHistory() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            evaluate(scenario, "(()=>{const a=document.createElement('a');a.href='/login';a.target='_blank';a.textContent='Native popup test';a.style.cssText='position:fixed;left:16px;top:16px;width:200px;height:48px;z-index:2147483647;background:white';document.body.appendChild(a);return true})()");
            ArrayBlockingQueue<Boolean> rendered = new ArrayBlockingQueue<>(1);
            scenario.onActivity(activity -> findBrowser(activity.findViewById(R.id.content)).postVisualStateCallback(1, new VisualStateCallback() {
                @Override
                public void onComplete(long request) { rendered.offer(true); }
            }));
            assertTrue("Popup target did not render", Boolean.TRUE.equals(rendered.poll(5, TimeUnit.SECONDS)));
            float scale = Float.parseFloat(evaluate(scenario, "devicePixelRatio"));
            scenario.onActivity(activity -> {
                WebView browser = findBrowser(activity.findViewById(R.id.content));
                long now = SystemClock.uptimeMillis();
                MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 32 * scale, 32 * scale, 0);
                MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, 32 * scale, 32 * scale, 0);
                try {
                    browser.dispatchTouchEvent(down);
                    browser.dispatchTouchEvent(up);
                } finally {
                    down.recycle();
                    up.recycle();
                }
            });
            awaitScript(scenario, "location.pathname==='/login' && document.querySelector('input[autocomplete=username]')!==null", "true");
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
        }
    }

    /**
     * Loads actual server-created components without granting file access or mixed content.
     * @throws Exception if the WebView does not become ready within the bounded wait
     */
    @Test
    public void rendersTheServerOwnedShowcaseWithHardenedSettings() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            assertEquals("\"Geshra UI showcase\"", evaluate(scenario, "document.title"));
            scenario.onActivity(activity -> {
                WebView browser = findBrowser(activity.findViewById(R.id.content));
                assertNotNull(browser);
                WebSettings settings = browser.getSettings();
                assertTrue(settings.getJavaScriptEnabled());
                assertTrue(settings.getDomStorageEnabled());
                assertFalse(settings.getAllowFileAccess());
                assertFalse(settings.getAllowContentAccess());
                assertFalse(settings.getJavaScriptCanOpenWindowsAutomatically());
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, settings.getMixedContentMode());
            });
        }
    }

    /**
     * Keeps the current JS document on rotation but rebuilds it after activity/process restoration.
     * @throws Exception if route restoration or a WebView callback times out
     */
    @Test
    public void rotationKeepsTheDocumentAndRecreationBuildsAFreshOne() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            assertEquals("true", evaluate(scenario, "window.__geshraAndroidRotationMarker=true"));
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
            long deadline = SystemClock.uptimeMillis() + 10000;
            ArrayBlockingQueue<Boolean> landscape = new ArrayBlockingQueue<>(1);
            boolean rotated = false;
            while (SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity(activity -> landscape.offer(activity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE));
                if (Boolean.TRUE.equals(landscape.poll(2, TimeUnit.SECONDS))) {
                    rotated = true;
                    break;
                }
                SystemClock.sleep(100);
            }
            assertTrue("The emulator did not enter landscape", rotated);
            assertEquals("true", evaluate(scenario, "window.__geshraAndroidRotationMarker===true"));
            scenario.onActivity(activity -> activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
            scenario.recreate();
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            assertEquals("true", evaluate(scenario, "typeof window.__geshraAndroidRotationMarker==='undefined'"));
            assertEquals("true", evaluate(scenario, "location.pathname==='/showcase'"));
        }
    }

    /**
     * Dangerous native link targets and non-gesture external navigation cannot replace the app.
     * @throws Exception if the browser does not return an observable location
     */
    @Test
    public void rejectsPrivilegedSchemesAndAutomaticExternalLinks() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            String original = evaluate(scenario, "location.href");
            scenario.onActivity(activity -> {
                activity.navigate("javascript:document.body.textContent='wrong'", true);
                activity.navigate("file:///data/data/private", true);
                activity.navigate("content://private/documents", true);
                activity.navigate("intent://other-app", true);
                activity.navigate("https://external.example.test/", false);
            });
            assertEquals(original, evaluate(scenario, "location.href"));
            assertEquals("48", evaluate(scenario, "document.querySelectorAll('[data-types]').length"));
        }
    }

    /**
     * Late callbacks from an old browser cannot cancel or alter its replacement's document.
     * @throws Exception if replacement route loading does not complete
     */
    @Test
    public void ignoresRetiredBrowserCallbacksAfterReplacement() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            AtomicReference<WebView> retired = new AtomicReference<>();
            AtomicReference<BrowserClient> callbacks = new AtomicReference<>();
            scenario.onActivity(activity -> {
                WebView original = findBrowser(activity.findViewById(R.id.content));
                retired.set(original);
                callbacks.set((BrowserClient) original.getWebViewClient());
                activity.rendererGone(original);
                Button retry = findButton(activity.findViewById(R.id.content));
                assertNotNull(retry);
                assertTrue(retry.isShown());
                retry.performClick();
            });
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            scenario.onActivity(activity -> {
                WebView replacement = findBrowser(activity.findViewById(R.id.content));
                long generation = activity.documentGeneration();
                BrowserClient old = callbacks.get();
                WebView original = retired.get();
                old.onPageStarted(original, BuildConfig.SERVER_URL, null);
                old.doUpdateVisitedHistory(original, "http://10.0.2.2:4060/login", false);
                old.onPageFinished(original, BuildConfig.SERVER_URL);
                old.onReceivedError(original, null, null);
                old.onReceivedHttpError(original, null, null);
                assertTrue(old.shouldOverrideUrlLoading(original, (WebResourceRequest) null));
                assertEquals(403, old.shouldInterceptRequest(original, (WebResourceRequest) null).getStatusCode());
                old.onRenderProcessGone(original, null);
                assertEquals(generation, activity.documentGeneration());
                assertSame(replacement, findBrowser(activity.findViewById(R.id.content)));
                assertFalse(findButton(activity.findViewById(R.id.content)).isShown());
            });
            scenario.recreate();
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
        }
    }

    /**
     * Crashes the task-owned renderer using Android's documented test URL and recovers by Retry.
     * @throws Exception if renderer cleanup or the replacement document does not complete
     */
    @Test
    public void recoversFromAnActualRendererCrashOnlyAfterRetry() throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            scenario.onActivity(activity -> findBrowser(activity.findViewById(R.id.content)).loadUrl("chrome://crash"));
            ArrayBlockingQueue<Boolean> stopped = new ArrayBlockingQueue<>(1);
            boolean recovered = false;
            long deadline = SystemClock.uptimeMillis() + 15000;
            do {
                scenario.onActivity(activity -> stopped.offer(findBrowser(activity.findViewById(R.id.content)) == null && findButton(activity.findViewById(R.id.content)).isShown()));
                if (Boolean.TRUE.equals(stopped.poll(2, TimeUnit.SECONDS))) {
                    recovered = true;
                    break;
                }
                SystemClock.sleep(100);
            } while (SystemClock.uptimeMillis() < deadline);
            assertTrue("Renderer death did not show native retry UI", recovered);
            scenario.onActivity(activity -> findButton(activity.findViewById(R.id.content)).performClick());
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
        }
    }

    /**
     * Validated incoming links reuse the existing activity while foreign origins are ignored.
     * @throws Exception if the linked route does not load within the deadline
     */
    @Test
    public void validatesIncomingLinksAndReusesOneActivity() throws Exception {
        Intent initial = new Intent(ApplicationProvider.getApplicationContext(), MainActivity.class);
        initial.setAction(Intent.ACTION_VIEW);
        initial.setData(Uri.parse(BuildConfig.SERVER_URL.replace("/showcase", "/login")));
        initial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(initial)) {
            awaitScript(scenario, "location.pathname==='/login' && document.querySelector('input[autocomplete=username]')!==null", "true");
            AtomicReference<MainActivity> same = new AtomicReference<>();
            scenario.onActivity(activity -> {
                same.set(activity);
                Intent show = new Intent(activity, MainActivity.class);
                show.setAction(Intent.ACTION_VIEW);
                show.setData(Uri.parse(BuildConfig.SERVER_URL));
                activity.startActivity(show);
            });
            awaitScript(scenario, "document.querySelectorAll('[data-types]').length===48", "true");
            scenario.onActivity(activity -> {
                assertSame(same.get(), activity);
                Intent rejected = new Intent(activity, MainActivity.class);
                rejected.setAction(Intent.ACTION_VIEW);
                rejected.setData(Uri.parse("https://outside.example.test/"));
                activity.onNewIntent(rejected);
            });
            assertEquals("true", evaluate(scenario, "location.pathname==='/showcase'"));
            // ActivityScenario matches lifecycle events by the launch intent, including its data URI.
            scenario.onActivity(activity -> {
                activity.setIntent(initial);
                activity.finish();
            });
        }
    }

    /**
     * Finds the native Retry control, including when its parent panel is currently hidden.
     * @param view activity view subtree
     * @return retry button or null when this subtree does not contain one
     */
    private Button findButton(View view) {
        if (view instanceof Button button) return button;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                Button found = findButton(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Finds the sole actual system browser rather than assuming an implementation-specific ID.
     * @param view current native view subtree
     * @return the live WebView, or null when the subtree contains none
     */
    private WebView findBrowser(View view) {
        if (view instanceof WebView browser) return browser;
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) {
                WebView found = findBrowser(group.getChildAt(index));
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Runs one observation on the UI thread and waits off-thread for its JSON result.
     * @param scenario live host activity
     * @param script read-only observation or explicit test marker assignment
     * @return WebView's JSON-encoded JavaScript result
     * @throws Exception if the system browser callback does not arrive
     */
    private String evaluate(ActivityScenario<MainActivity> scenario, String script) throws Exception {
        ArrayBlockingQueue<String> result = new ArrayBlockingQueue<>(1);
        scenario.onActivity(activity -> {
            WebView browser = findBrowser(activity.findViewById(R.id.content));
            assertNotNull(browser);
            browser.evaluateJavascript(script, result::offer);
        });
        String value = result.poll(5, TimeUnit.SECONDS);
        assertNotNull("No WebView observation callback", value);
        return value;
    }

    /**
     * Waits for actual server-produced DOM with a deadline rather than fixed startup sleeps.
     * @param scenario live host activity
     * @param script ready-state observation
     * @param expected expected JSON value
     * @throws Exception if readiness cannot be established before the deadline
     */
    private void awaitScript(ActivityScenario<MainActivity> scenario, String script, String expected) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 20000;
        String last;
        do {
            last = evaluate(scenario, script);
            if (expected.equals(last)) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        assertEquals("Server document did not become ready", expected, last);
    }
}
