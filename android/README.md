# Android export

This is Geshra's reusable **server-connected Android application**. Java routes, authentication,
application logic and data continue to run on your Geshra server. Android renders the same UI with
the system WebView and connects to the same-origin WebSocket endpoint. There is no embedded JVM,
Spring server or offline execution of Java callbacks in the APK.

## Requirements

- Android 8.0/API 26 or newer, with an updated Android System WebView/Chrome **126 or newer**.
  The app checks the installed engine and displays update guidance instead of loading a partially
  supported UI. Minimum SDK is not a claim that every device/engine combination was tested.
- Build tools: JDK 17 or 21, Gradle 8.13 (wrapper included in an export), Android SDK platform 36
  and build-tools 35.0.0. Android Gradle Plugin 8.13.2, AndroidX Activity 1.11.0 and Core 1.17.0
  are pinned. Set `ANDROID_HOME` or create local, uncommitted `local.properties` with `sdk.dir`.
- A reachable public **HTTPS** Geshra endpoint, with valid TLS and HTTPS-to-WebSocket upgrade
  forwarding. For example, `https://app.example.com/showcase` and same-origin `/ws`. A reverse
  proxy must preserve the original host/scheme used by Geshra's origin and cookie policy.
- An application-owned release signing key for distributable signed releases. No signing key,
  passwords or server credentials belong in this project or exported archives.

## Export from a Geshra checkout

Run at the repository root (Windows: `gradlew.bat`):

```sh
./gradlew exportAndroid \
  -PandroidServerUrl=https://app.example.com/ \
  -PandroidApplicationId=com.example.myapp \
  '-PandroidAppName=My application'
```

`build/exports/geshra-android.zip` is a standalone Android Studio project with the Gradle wrapper,
native source, tests, pinned build configuration and public `export.properties`. Extraction does
not require the Geshra repository or Node. The source/template allowlist excludes arbitrary local
files, caches, build output and signing material. Public export settings contain no authentication
secrets: do not put credentials or bearer tokens in the URL or public build properties.

Supported public properties:

| Property | Meaning |
| --- | --- |
| `androidServerUrl` | Required HTTPS server URL and initial route for exported release builds |
| `androidDebugServerUrl` | Optional debug-only HTTP/HTTPS override; never becomes the release URL |
| `androidApplicationId` | Lowercase dotted package identity; default `geshra.android.example` |
| `androidAppName` | Launcher label; default `Geshra` |
| `androidVersionCode` | Positive integer Android version code; default `1` |
| `androidVersionName` | Display version; default `1.0.0` |

Command-line properties override exported public defaults. Building directly from the repository
also works with `./gradlew -p android ...`; the initial debug URL defaults to an intentionally
unreachable documentation address until configured. Release builds reject that placeholder.

## Build APKs and Android App Bundles

Inside the extracted project:

```sh
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease :app:bundleRelease
```

Outputs are `app/build/outputs/apk/debug/app-debug.apk`,
`app/build/outputs/apk/release/` and `app/build/outputs/bundle/release/app-release.aab`.
Debug APKs use Android's local developer key. Unsigned release output is not ready to distribute.

For a signed release, set all four environment variables **outside** project files:

```text
GESHRA_ANDROID_KEYSTORE=/absolute/path/to/your-release-key.jks
GESHRA_ANDROID_KEY_ALIAS=your-key-alias
GESHRA_ANDROID_STORE_PASSWORD=<your secret>
GESHRA_ANDROID_KEY_PASSWORD=<your secret>
```

Then run the release tasks. Missing partial signing configuration fails clearly; no automatic
fallback signs a release with the debug key. Own and back up your production signing key. Play
publishing, developer registration, app signing enrollment and store review are application-owner
steps; the export task does not publish anything or guarantee Play approval.

## Local Android development

Start your Geshra application on host port 4060. In an Android emulator, `10.0.2.2` addresses the
host; `localhost` addresses the emulator itself.

```sh
./gradlew -p android :app:assembleDebug \
  -PandroidDebugServerUrl=http://10.0.2.2:4060/showcase
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n geshra.android.example/geshra.android.MainActivity
```

HTTP is allowed only by the **debug** manifest. Release configuration always uses HTTPS and
disallows cleartext and mixed content. No certificate-validation bypass is provided. For a real
phone, configure a reachable development/LAN URL or production HTTPS URL; emulator loopback is not
a physical-phone deployment address. WebView debugging is enabled only in debug builds.

## Host behavior

| Capability | Contract |
| --- | --- |
| Forms, layouts, dialogs/popovers, virtual lists, SVG/canvas | Existing server components and runtime; no alternate native component implementation |
| Authentication | First-party WebView cookies, HttpOnly and existing CSRF rules; third-party cookies disabled |
| Navigation | Exact parsed scheme/host/effective-port trust; external HTTP links open the system browser only after a user gesture |
| Back | Exit fullscreen first, then WebView route history, then finish the activity |
| Rotation and IME | Same live document on configuration changes; system bars, cutouts and keyboard insets applied |
| Process/activity recreation | Reload a validated saved route; never deserialize old server-owned DOM/component IDs |
| Background/resume | WebView pause/resume, cookie flush and existing Geshra session recovery |
| Initial network/TLS/HTTP failure | Native retry screen; no certificate bypass or automatic crash/reload loop |
| Renderer death | Destroy the exact failed WebView and create a fresh one only after Retry |
| File upload | System open-document picker, accept/multiple hints, readable external content URIs, no broad storage permission |
| Download | Same-origin authenticated stream to a user-selected document; bounded redirects and buffer; canceled on destruction |
| JavaScript dialogs | Origin-checked alert/confirm/prompt/before-unload with cancellation on document replacement |
| New windows | Route user-created windows through policy; temporary routing window cannot run scripts or open a second server session |
| Audio/video | Native controls, deliberate playback and fullscreen custom-view support |

Downloads have connect/read deadlines and best-effort native connection cancellation; ownership
tests do not prove that every platform DNS/connect phase aborts immediately. Interrupted writes
can leave an incomplete user-selected file. This is not a persistent background-download service.
Queued picker callbacks are canceled on navigation/destruction and cannot be delivered to a newer
document. Android's chooser callback does **not** expose the requesting frame origin; top-level
origin validation is not a guarantee about an embedded third-party frame. Only user-granted
external providers may be selected; app-owned provider UID references are rejected.

Camera/microphone recording, geolocation, arbitrary native intents, `file://` pages, a JavaScript
native-object bridge and third-party identity-provider/native OAuth integrations are not silently
enabled. Geshra's ordinary file uploads and media playback do not need recording permissions.
Applications requiring those additional native capabilities need an explicit permission and
platform integration rather than a blanket grant. The server-connected app requires connectivity;
retaining the last page is not offline execution of server actions.

## Verified HTTPS app links

The launcher activity is single-task and validates incoming app links against the same complete
configured origin before loading them. The manifest requests HTTPS verification for the production
server host. To enable Android's verified link routing, the site owner must publish
`https://YOUR_HOST/.well-known/assetlinks.json` with the release certificate fingerprint:

```json
[{"relation":["delegate_permission/common.handle_all_urls"],"target":{"namespace":"android_app","package_name":"com.example.myapp","sha256_cert_fingerprints":["YOUR:RELEASE:CERTIFICATE:SHA256"]}}]
```

Use your actual signing certificate (or Play App Signing certificate when applicable), not a
private key or password. Exporting does not modify your website or claim domain verification has
already succeeded. Untrusted deep links are ignored; explicit same-origin links reuse the single
activity instead of competing for one Geshra session from multiple WebViews.

## Tests

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest \
  -PandroidDebugServerUrl=http://10.0.2.2:4060/showcase
```

Connected tests require a running Geshra showcase server and a device/emulator. `ANDROID_SERIAL`
selects the target when multiple devices are present. The repository's source-compliance test
also parses maintained Android Java sources. Test reports distinguish actual Android WebView
execution from desktop browser viewport tests. See the repository Android test report for the
devices, engine versions, artifacts, exact checks and remaining unverified cases in [TESTING.md](TESTING.md).

Primary references: [Android WebView](https://developer.android.com/develop/ui/views/layout/webapps/webview),
[URI validation](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading),
[file chooser](https://developer.android.com/reference/android/webkit/WebChromeClient.FileChooserParams),
[AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes),
[Android App Links](https://developer.android.com/training/app-links).
