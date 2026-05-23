# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-05-24

### Added
- Initial alpha release.
- Fullscreen `WebView` Activity that loads `m.youtube.com` in landscape.
- Android Auto media-app metadata (`automotive_app_desc.xml`) and a
  `MediaBrowserService` stub so AA shows WheelTube in its launcher.
- Network-layer ad blocking via `WebViewClient.shouldInterceptRequest`
  against a curated host + path list (Google ad/telemetry endpoints,
  YouTube `/pagead/`, `/api/stats/ads`, midroll, ptracking, log_event).
- DOM-layer ad/promo hiding via injected CSS on every page-finish
  (`ytd-ad-slot-renderer`, `ytd-promoted-*-renderer`, `.ytp-ad-module`,
  `.ytp-ad-overlay-container`, etc.).
- Chrome-like User-Agent override so YouTube serves the real mobile UI.
- Persistent cookies (login survives across sessions).
- `Theme.NoTitleBar.Fullscreen` + immersive system UI flags for
  edge-to-edge rendering.
- Hardware back button navigates the WebView's history before exiting.
- Pure-Java implementation; no Gradle, no Kotlin, no third-party
  dependencies. Builds with the Android SDK command-line tools only
  (`build.sh`).
