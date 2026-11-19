package geshra.android;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Process;

/**
 * Defines the native document boundary shared by upload and save dialogs. Read/write permission
 * alone does not establish safety: an app can access its own private providers, whose authorities
 * need not resemble its package name. Provider UID ownership is checked before opening a stream.
 */
final class DocumentUris {
    /**
     * Prevents construction of a stateless provider-ownership policy.
     */
    private DocumentUris() { }

    /**
     * Accepts only resolved external content providers, never files, network URLs or own-UID data.
     * @param context application's package and provider visibility context
     * @param uri reference returned by an Android document selection
     * @return whether the reference crosses the intended external document boundary
     */
    static boolean external(Context context, Uri uri) {
        /*
         * Resolve actual provider ownership instead of relying on authority spelling; Android's
         * user-granted document access supplies provider visibility for the selected URI.
         */
        if (uri == null || !"content".equals(uri.getScheme()) || uri.getAuthority() == null) return false;
        ProviderInfo provider = context.getPackageManager()
                .resolveContentProvider(uri.getAuthority(), 0);
        return provider != null && provider.applicationInfo != null && provider.applicationInfo.uid != Process.myUid();
    }
}
