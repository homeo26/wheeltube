# WheelTube

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Android 9+](https://img.shields.io/badge/Android-9.0+-3DDC84.svg)](https://developer.android.com)
[![Status](https://img.shields.io/badge/status-alpha-orange.svg)](#)
[![No Gradle](https://img.shields.io/badge/Build-no%20Gradle-blue.svg)](#building-from-source)

YouTube on Android Auto, in a tiny WebView wrapper.
Ad-free out of the box. ReVanced-style features without any
ReVanced patches or YouTube APK modifications.

> **Heads up.** Watching video while driving is illegal in most
> places, including Jordan. WheelTube is for parked-car use:
> while charging, in traffic jams, in the passenger seat, etc.
> Don't be reckless.

## Status

Alpha (v0.1.0). Currently includes:

- ✅ Fullscreen WebView pointing at `m.youtube.com`
- ✅ Visible in Android Auto's launcher (media app)
- ✅ Network-level ad blocking (host + path filter)
- ✅ DOM-level ad/promo hiding (CSS injection)
- ✅ Persistent login (cookies survive)
- ✅ Hardware back button navigation
- ⏳ SponsorBlock (planned for v0.2.0)
- ⏳ Return YouTube Dislike (planned for v0.2.0)
- ⏳ Background audio when AA disconnects (planned for v0.3.0)

## How it works

1. WheelTube installs on the **phone** (not the head unit).
2. AA inspects its `MediaBrowserService` and accepts it as a
   "media" category app — so AA's launcher shows it.
3. Tapping it triggers `MediaSession.Callback.onPlayFromMediaId`,
   which starts the fullscreen `MainActivity`.
4. AA projects whatever Activity is in the foreground to the head
   unit (and from there to your BYD screen via Headunit Revived).
5. The Activity's WebView loads `m.youtube.com` and applies the
   ad-blocking filter list in real time.

The trick is that AA's `UX_RESTRICTIONS_NO_VIDEO` enforcement only
gates specific media playback APIs (MediaCodec, SurfaceView with
secure flag, etc.). Plain `WebView` rendering is not gated, which
is why this approach works at all.

## Install

1. Download the latest **WheelTube.apk** from the
   [Releases page](../../releases/latest).
2. Sideload it onto your phone (Telegram, email, USB cable, AAAD).
   Allow "Install unknown apps" if prompted.
3. Open WheelTube once from the phone's app drawer to sign in to
   YouTube. Cookies persist after that.
4. Connect the phone to Android Auto (USB or wireless via
   Headunit Revived).
5. WheelTube will appear in AA's launcher. Tap it. Enjoy.

## Building from source

Requirements:

- JDK 11+ (tested with Corretto 21)
- Android SDK build-tools (`aapt2`, `d8`, `zipalign`, `apksigner`)
- Android SDK platform jar (`android.jar`, any API ≥ 28)

```bash
git clone https://github.com/homeo26/wheeltube.git
cd wheeltube
./build.sh
adb install -r build/wheeltube.apk
```

No Gradle, no Android Studio. The script handles the full
`aapt2 → javac → d8 → zipalign → apksigner` pipeline. Output is
`build/wheeltube.apk` (~17 KB).

Override SDK locations if needed:

```bash
SDK=/path/to/android/sdk BT_VER=36.0.0 PLATFORM=android-36 ./build.sh
```

## Project layout

```
.
├── AndroidManifest.xml
├── LICENSE                            MIT
├── CHANGELOG.md
├── README.md                          this file
├── build.sh                           one-command rebuild
├── res/
│   ├── values/strings.xml
│   └── xml/automotive_app_desc.xml    declares <uses name="media"/>
└── src/com/homeo/wheeltube/
    ├── MainActivity.java              WebView + ad blocking
    └── WheelMediaBrowserService.java  AA media-app entry point
```

## Customising

- **Ad blocking lists** are at the top of `MainActivity.java`
  (`BLOCKED_HOSTS`, `BLOCKED_PATHS`) and the CSS string
  `AD_HIDE_CSS`. Add anything missing from your region.
- **Start URL** is `START_URL` in `MainActivity.java`. Default:
  `https://m.youtube.com`. Could be swapped for a privacy-front
  like Invidious if you'd rather.
- **User-Agent** is stripped of the WebView's `; wv` suffix so
  YouTube serves the proper mobile site. Tweak if needed.

## Compatibility caveats

- AA changes its app-acceptance rules every few releases; this
  approach is what works as of mid-2026 but may break in future.
- Android 9 or newer required (matches the BYD Dilink Di2.1H
  target).
- Some Premium-only features (e.g. native PiP, ad-free in the
  app) won't appear because we use the website, not the app —
  but ad blocking already covers most of what people want.
- This is not affiliated with the ReVanced project. The "ReVanced
  style" features here are reimplementations at the WebView layer.

## Privacy

The app makes only network calls to `*.youtube.com`,
`*.googlevideo.com`, and other endpoints required for video
playback. Ad/telemetry endpoints are blocked. No analytics, no
crash reporting, no telemetry from WheelTube itself. Source is
~340 lines across two files.

## License

MIT — see [LICENSE](LICENSE).
