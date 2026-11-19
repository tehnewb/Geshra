package geshra.android;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient.FileChooserParams;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Bridges ordinary Geshra uploads to Android's storage access framework without filesystem
 * permissions or file:// access. Only readable content URIs returned by the user's system
 * picker are forwarded. Results and cancellation complete one callback exactly once; late
 * results after document teardown are ignored. Camera capture is not implicitly authorized.
 */
final class BrowserFileChooser {
    /**
     * Caps hostile or accidental result lists before allocating browser upload metadata.
     */
    private static final int MAXIMUM_SELECTED_FILES = 64;

    private final MainActivity activity; // Owner of the registered Android document picker.
    private ValueCallback<Uri[]> pending; // Sole callback awaiting a system picker result.
    private boolean multiple; // Whether the active HTML input permits multiple files.
    private boolean launched; // Old picker results cannot complete a newer document's callback.
    private long documentGeneration; // Exact document that requested the active upload.

    /**
     * Creates an activity-scoped chooser without retaining arbitrary native intents.
     * @param activity native result and lifecycle owner
     */
    BrowserFileChooser(MainActivity activity) { this.activity = activity; }

    /**
     * Starts an openable document selection using normalized HTML accept MIME types.
     * @param callback browser upload result callback
     * @param parameters HTML file input configuration supplied by WebView
     * @return true because callback ownership is always handled here
     */
    boolean open(ValueCallback<Uri[]> callback, FileChooserParams parameters) {
        if (launched) {
            callback.onReceiveValue(null);
            return true;
        }
        pending = callback;
        documentGeneration = activity.documentGeneration();
        multiple = parameters.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE;
        Set<String> accepted = new LinkedHashSet<>();
        for (String entry : parameters.getAcceptTypes()) {
            for (String candidate : entry.split(",")) {
                String type = normalizeType(candidate);
                if (type != null) accepted.add(type);
            }
        }
        Intent request = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        request.addCategory(Intent.CATEGORY_OPENABLE);
        request.setType(accepted.size() == 1 ? accepted.iterator().next() : "*/*");
        if (accepted.size() > 1) request.putExtra(Intent.EXTRA_MIME_TYPES, accepted.toArray(new String[0]));
        request.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
        try {
            launched = true;
            activity.chooseFile(request);
        } catch (ActivityNotFoundException failure) {
            launched = false;
            activity.notice(R.string.file_unavailable);
            cancel();
        }
        return true;
    }

    /**
     * Validates the entire selected set before exposing any URI to browser content.
     * @param result Android activity result code
     * @param data picker result, or null for cancellation
     */
    void receive(int result, Intent data) {
        launched = false;
        if (pending == null) return;
        if (result != Activity.RESULT_OK || data == null || !activity.isCurrentDocument(documentGeneration)) {
            cancel();
            return;
        }
        ArrayList<Uri> selected = new ArrayList<>();
        ClipData clips = data.getClipData();
        if (clips != null) {
            if (clips.getItemCount() > (multiple ? MAXIMUM_SELECTED_FILES : 1)) {
                reject();
                return;
            }
            for (int index = 0; index < clips.getItemCount(); index++) selected.add(clips.getItemAt(index).getUri());
        } else if (data.getData() != null) selected.add(data.getData());
        if (selected.isEmpty()) {
            cancel();
            return;
        }
        for (Uri uri : selected) {
            if (!readable(uri)) {
                reject();
                return;
            }
        }
        ValueCallback<Uri[]> completed = pending;
        pending = null;
        completed.onReceiveValue(selected.toArray(new Uri[0]));
    }

    /**
     * Rejects filesystem, network, ambiguous and application-private provider references.
     * @param uri user-selected content provider reference
     * @return whether a readable user-granted document can be opened
     */
    private boolean readable(Uri uri) {
        if (!DocumentUris.external(activity, uri)) return false;
        ContentResolver resolver = activity.getContentResolver();
        try (AssetFileDescriptor file = resolver.openAssetFileDescriptor(uri, "r")) {
            return file != null;
        } catch (IOException | SecurityException | IllegalArgumentException failure) {
            Log.w("GeshraAndroid", "Selected document is not readable", failure);
            return false;
        }
    }

    /**
     * Reports an inaccessible selection without leaking provider identifiers to page scripts.
     */
    private void reject() {
        activity.notice(R.string.file_unavailable);
        cancel();
    }

    /**
     * Completes pending upload ownership on user cancellation, navigation or activity teardown.
     */
    void cancel() {
        ValueCallback<Uri[]> canceled = pending;
        pending = null;
        if (canceled != null) canceled.onReceiveValue(null);
    }

    /**
     * Accepts conventional MIME tokens or resolves a filename extension without trusting an intent.
     * @param candidate one HTML accept token
     * @return normalized MIME type, or null when unsupported/malformed
     */
    private static String normalizeType(String candidate) {
        /*
         * Accept only MIME hints and extensions rather than forwarding page-supplied intents.
         */
        String value = candidate.trim()
                .toLowerCase(Locale.ROOT);
        if (value.startsWith(".")) return MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(value.substring(1));
        return value.matches("[a-z0-9!#$&^_.+*-]+/[a-z0-9!#$&^_.+*-]+") ? value : null;
    }
}
