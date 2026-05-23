# Changelog

All notable changes to this project will be documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.2] - 2026-05-24

### Changed
- **Shadow-DOM piercing.** Mobile YouTube renders its `<video>` inside a
  shadow root, which `document.querySelectorAll('video')` doesn't see.
  v0.1.2 walks all shadow roots recursively when finding video elements
  to fast-forward, so in-video ads get killed even on the mobile player.
- **More ad-detection signals.** Added `[class*="ad-showing"]`,
  `[class*="ad-interrupting"]`, `.ytp-ad-overlay-container`, and
  `.video-ads` so we catch ads in different player states.
- **More skip-button selectors:** `button[class*="skip-ad-button"]` plus
  the existing variants.
- **Network-layer URL-marker blocking.** Drop requests whose URL
  contains `&oad=`, `&adformat=`, `&adunit=`, `&ad_type=`, or
  `&ctier=L` — these mark ad video chunks served from `googlevideo.com`,
  the same host as real content.
- **Faster polling** (250ms → 200ms) so ad video frames flash for less
  than a quarter second before being seeked away.
- **Console diagnostics:** `[WheelTube] ad-killer installed`,
  `[WheelTube] ad state: true/false` log lines so the script is
  debuggable via `chrome://inspect`.
- **Expanded CSS:** generic `[class*="ytp-ad-"]` and
  `[aria-label="Sponsored"|"Promoted"]` selectors plus
  `ytd-in-feed-ad-layout-renderer`, `ytm-search-promotion-renderer`,
  and feature-product overlays.

## [0.1.1] - 2026-05-24

### Changed
- **Major ad-blocking upgrade.** v0.1.0 only had static CSS + host
  filtering, which let in-video ads and many mobile-DOM promo elements
  through. v0.1.1 adds:
  - **Speed-up trick for in-video ads** — when the player is in
    ad-showing state, the ad video's `playbackRate` is set to 16 and
    `currentTime` is seeked to its end. Bulletproof against UI changes.
  - **Auto-click skip buttons** — every variant of `ytp-ad-skip-button*`
    and `ytm-skip-ad-button*` is clicked the moment it appears.
  - **Continuous JS injector** — runs every 250 ms via setInterval, so
    SPA navigations don't lose protection.
  - **MutationObserver** keeps the ad-hiding `<style>` alive even when
    YouTube re-renders the DOM tree.
  - **Comprehensive mobile DOM hiding** — `ytm-companion-ad-renderer`,
    `ytm-promoted-video-renderer`, `ytm-mealbar-promo-renderer`, and
    other mobile-only ad/promo elements that v0.1.0 missed.
- Re-injection happens on both `onPageStarted` and `onPageFinished`,
  so the script is in place before the player initializes.
- Expanded `BLOCKED_PATHS` to include `/get_video_metadata_ads`,
  `/api/stats/qoe`, `/youtubei/v1/player/ad_break`.

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
