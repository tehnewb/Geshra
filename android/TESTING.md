# Android export verification

This report records feature and correctness checks on 2026-11-18. It is not a performance
benchmark. Release builds used the synthetic endpoint `https://app.example.test/showcase`;
native development tests used an isolated Geshra demo and loopback-only proxy on host port 4060.
Neither address is a deployed production application.

## Build and desktop checks

| Check | Result |
| --- | --- |
| Root Java suite, including Android source compliance | 74 tests passed |
| Spring example consumer suite | 2 tests passed |
| Android URL-policy and download-connection ownership unit tests | 10 tests passed |
| Android debug lint | 0 errors; 8 warnings (dependency versions, JavaScript requirement, backup configuration and an unused string) |
| Debug APK, R8 release APK and release AAB | Built successfully |
| Export extracted outside the Android source project | Debug/release APK and AAB builds passed without the repository or Node |
| Release manifest | No debuggable or cleartext opt-in |
| Signing integration in a separate extracted project | APK signature verified; AAB reported `jar verified` using the local Android debug certificate solely as a test credential |
| Repeated export with identical settings and source | Identical SHA256 |
| Invalid production URLs | HTTP, URL credentials and fragment configurations rejected |
| Literal app labels | Apostrophes, quotation marks, ampersands and angle brackets survived resource generation and AAPT |
| Chrome and Firefox regression suites | Protocol, components, authentication/session, bootstrap/cache and navigation checks passed |
| Native-intent anchor regression | 14 cases passed per browser; real fragment Back/Forward preserved the document without route packets |

The signed test artifacts are not production releases: the test certificate is Android's developer
certificate, and its self-signed/timestamp warnings are expected. A release owner must configure
their own endpoint, application identity and signing key. No store upload or domain-verification
claim is part of these checks.

## Native execution

Native acceptance evidence is recorded separately from desktop browser tests. The Android 16/API 36
phone emulator uses Android System WebView 133.0.6943.137. Tests include the actual 48-sample Geshra
showcase, rotation and activity recreation, rejected URI schemes, stale callbacks from a retired
WebView, deliberate renderer death with native Retry, and incoming-link handling.

The final debug APK SHA256 is `d61d10b9fe07458e7f5a97f74771647863c8d3403de8a37ff16f8b6e5c7b71cc`.
All seven lifecycle instrumentation tests passed against that APK. The incoming-link test performs
real Android intent delivery for an accepted link and directly invokes rejection for a foreign
link. Its cleanup restores the launch intent before finishing: AndroidX ActivityScenario filters
lifecycle events by that intent's data URI, which legitimate deep-link delivery changes. Earlier
runs failed during cleanup, after their behavior assertions; the final corrected run completed.

Independent native testing reproduced a new-window routing defect in the preceding build:
`View.post` retained callbacks on a temporary WebView that was intentionally never attached.
Routing now uses the main Looper, and only the owning current popup can perform cleanup/navigation.
The seventh native regression waits for a rendered target, dispatches touch events through the
actual WebView, checks the new-window destination, and returns through native Back history.
FUNCTIONAL TESTER independently repeated the original ADB tap on the final `d61d10b9…` APK:
the main window reached the ready 48-card showcase, native Back returned to the fixture page,
and target enumeration showed one attached window with no orphan popup.

Independent native functional tests used the preceding APK (`b2f23051…`), before the final
download save-result generation guard. They verified actual DocumentsUI selection/cancellation,
a single upload acknowledged by Java at 15 bytes, real authentication with an open WebSocket and
HttpOnly session cookie, native alert/confirm/prompt results, and an authenticated save with exact
38-byte contents. Destroying the activity during a 64 MiB synthetic download closed the actual
native transfer at 376,832 bytes with completion false while retaining the same app process.
Multiple native file selection worked; delivery of both selected files to Java was not independently
verified. Final-build stale save-result rejection is supported by source review rather than a
separate native picker-race test.

An initially white replacement-activity screenshot was transient: subsequent fresh debug-target
inspection found the complete visible 48-card document, and a native screenshot showed the rendered
showcase without a reload. No persistent replacement defect or precise transient cause was proven.
An earlier backend outage was a fixture error caused by rebuilding its running JAR; callback/upload
checks were repeated successfully after restarting an isolated copy.

An Android 15/API 35 tablet with WebView 124 correctly displayed the engine-update screen. That
native screen remained readable in portrait, landscape and 150% text, with complete status icons.
Full showcase testing on that outdated engine was correctly blocked.

The compatible Android 16/API 36 Pixel Tablet used WebView 133 and the preceding `9212df7e…` APK.
VISUAL TESTER saved 96 native screenshots covering all 48 cards in portrait and landscape, inspected
all portrait contact sheets and representative landscape frames, and found no confirmed blocking
layout defect in those inspected frames. The full landscape manual review remains incomplete.
Modal, popover, native select, representative 150% text and settled portrait IME resizing were
verified. Both launcher and app showed complete tablet status icons. These layout captures precede
the popup-only routing fix and are not a claim about every interaction, media state or device.

Do not interpret the implemented feature matrix as a claim that every Android version, device,
provider or accessibility service was exercised. Minimum SDK 26 is a compatibility target; the
native engine guard separately requires WebView 126 or newer.

## Reproduction

Use the build commands in [README.md](README.md). From the repository, publish the library locally
and build the Spring example before starting the test backend. Copy its executable JAR to a
test-owned path before launching it: rebuilding a JAR that a running Spring process still uses can
break lazy dependency loading.

`android-tests/network-fixture.cjs` forwards real Geshra HTTP/WebSocket traffic and supplies explicit
native-host acceptance pages and authenticated download fixtures. It listens only on host loopback,
records bounded synthetic transfer counters, and never records request cookies. Its default backend
port is 4062 and frontend/frame ports are 4060/4061. It is development tooling, not a deployment proxy.

Run the connected suite with a running showcase backend and explicit `ANDROID_SERIAL`. Native
system picker actions must be tested through DocumentsUI; setting an input's files from desktop
automation does not validate Android's picker or content-provider integration.
