package com.homeo.wheeltube;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;

/**
 * Hosts a fullscreen WebView pointed at m.youtube.com.
 *
 * Ad blocking has THREE layers, in order of strength:
 *
 *   1. {@link WebViewClient#shouldInterceptRequest} drops requests to
 *      known ad/telemetry hosts and ad URL paths. Conservative — we
 *      avoid blocking anything from googlevideo.com because that's
 *      where real video chunks come from.
 *
 *   2. JavaScript "user script" injected on every page event. It
 *      installs idempotently and runs a 300 ms timer that:
 *        - sets `playbackRate = 16` and seeks to the end whenever the
 *          player is in ad-showing state (kills in-video ads in ~0.2 s
 *          regardless of UI changes — the bulletproof uBO trick),
 *        - auto-clicks skip buttons,
 *        - hides ad DOM nodes on every mutation.
 *
 *   3. Static CSS hides static promo/banner/upsell DOM nodes.
 */
public class MainActivity extends Activity {
    private static final String TAG = "WheelTube";
    private static final String START_URL = "https://m.youtube.com";

    /** Network-blocked hostnames. */
    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "googlesyndication.com",
            "doubleclick.net",
            "googleadservices.com",
            "googletagservices.com",
            "googletagmanager.com",
            "google-analytics.com",
            "static.doubleclick.net",
            "ad.doubleclick.net");

    /** URL path fragments that always indicate ads or telemetry. */
    private static final List<String> BLOCKED_PATHS = Arrays.asList(
            "/api/stats/ads",
            "/pagead/",
            "/get_midroll_",
            "/get_video_metadata_ads",
            "/ptracking",
            "/log_event",
            "/youtubei/v1/log_event",
            "/api/stats/qoe",
            "/youtubei/v1/player/ad_break");

    /**
     * The JS user script, injected on every page event. Idempotent —
     * subsequent injections are no-ops. Lives in window scope so SPA
     * navigations don't lose it.
     */
    private static final String AD_KILLER_JS =
        "(function(){" +
        "  if (window.__wt_installed) return;" +
        "  window.__wt_installed = true;" +
        // ---- 1. Static CSS hiding ----
        "  var s = document.createElement('style');" +
        "  s.id = 'wheeltube-style';" +
        "  s.textContent = " +
        "    'ytm-companion-ad-renderer," +
        "     ytm-promoted-video-renderer," +
        "     ytm-promoted-sparkles-text-search-renderer," +
        "     ytm-promoted-sparkles-web-renderer," +
        "     ytm-mealbar-promo-renderer," +
        "     ytm-banner-promo-renderer," +
        "     ytm-statement-banner-renderer," +
        "     ytm-companion-slot," +
        "     ytm-action-companion-ad-renderer," +
        "     ytm-display-ad-renderer," +
        "     ytm-mobile-action-companion-ad-renderer," +
        "     ytd-ad-slot-renderer," +
        "     ytd-promoted-sparkles-web-renderer," +
        "     ytd-promoted-sparkles-text-search-renderer," +
        "     ytd-banner-promo-renderer," +
        "     ytd-mealbar-promo-renderer," +
        "     ytd-display-ad-renderer," +
        "     ytd-promoted-video-renderer," +
        "     ytd-statement-banner-renderer," +
        "     ytd-action-companion-ad-renderer," +
        "     .video-ads," +
        "     .ytp-ad-module," +
        "     .ytp-ad-overlay-container," +
        "     .ytp-ad-overlay-image," +
        "     .ytp-ad-image-overlay," +
        "     .ytp-ad-text-overlay," +
        "     .ad-container," +
        "     #player-ads," +
        "     #masthead-ad," +
        "     ytd-promoted-sparkles-text-search-renderer { display: none !important; }';" +
        "  (document.head || document.documentElement).appendChild(s);" +
        // ---- 2. In-video ad killer ----
        "  function killAds(){" +
        "    try {" +
        // Mobile/desktop: fast-forward any ad video element
        "      var vids = document.querySelectorAll('video');" +
        "      for (var i=0;i<vids.length;i++){" +
        "        var v = vids[i];" +
        "        var pl = v.closest('.html5-video-player') || v.closest('.video-stream');" +
        "        var adShowing = (pl && pl.classList && pl.classList.contains('ad-showing'))" +
        "                     || document.querySelector('.ytp-ad-player-overlay')" +
        "                     || document.querySelector('.ytp-ad-skip-button-container')" +
        "                     || document.querySelector('.ytp-ad-text');" +
        "        if (adShowing && !isNaN(v.duration) && v.duration > 0) {" +
        "          v.currentTime = v.duration;" +
        "          v.playbackRate = 16;" +
        "          v.muted = true;" +
        "        }" +
        "      }" +
        // Skip buttons (desktop + mobile variants)
        "      var skips = document.querySelectorAll(" +
        "        '.ytp-ad-skip-button," +
        "         .ytp-ad-skip-button-modern," +
        "         .ytp-skip-ad-button," +
        "         .ytm-skip-ad-button," +
        "         .ytp-ad-survey-answer-button," +
        "         button.ytp-ad-skip-button-modern');" +
        "      for (var j=0;j<skips.length;j++){" +
        "        try { skips[j].click(); } catch(e){}" +
        "      }" +
        // Hide overlay banners that slip through
        "      var overlays = document.querySelectorAll(" +
        "        '.ytp-ad-overlay-close-button," +
        "         .ytp-ad-overlay-close-container');" +
        "      for (var k=0;k<overlays.length;k++){ try { overlays[k].click(); } catch(e){} }" +
        "    } catch(e){}" +
        "  }" +
        "  setInterval(killAds, 250);" +
        "  killAds();" +
        // ---- 3. Mutation observer keeps style alive across SPA reloads ----
        "  try {" +
        "    var mo = new MutationObserver(function(){" +
        "      if (!document.getElementById('wheeltube-style')) {" +
        "        (document.head || document.documentElement).appendChild(s);" +
        "      }" +
        "    });" +
        "    mo.observe(document.documentElement, {childList:true, subtree:true});" +
        "  } catch(e){}" +
        "})();";

    private WebView webView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersiveFlags();

        webView = new WebView(this);
        configureWebView(webView);
        setContentView(webView);

        webView.loadUrl(START_URL);
    }

    private void configureWebView(WebView wv) {
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        String ua = s.getUserAgentString();
        if (ua != null) {
            ua = ua.replace("; wv", "");
            s.setUserAgentString(ua);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(wv, true);

        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new AdBlockingWebViewClient());
    }

    private void applyImmersiveFlags() {
        int flags = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveFlags();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }

    /* ---------- WebViewClient with ad blocking ---------- */

    private final class AdBlockingWebViewClient extends WebViewClient {
        private final WebResourceResponse EMPTY =
                new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream(new byte[0]));

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
            if (req == null || req.getUrl() == null) return null;
            String host = req.getUrl().getHost();
            String path = req.getUrl().getPath();

            if (host != null) {
                String lc = host.toLowerCase();
                for (String h : BLOCKED_HOSTS) {
                    if (lc.equals(h) || lc.endsWith("." + h)) return EMPTY;
                }
            }
            if (path != null) {
                for (String p : BLOCKED_PATHS) {
                    if (path.contains(p)) return EMPTY;
                }
            }
            return null;
        }

        /** Fires before the first byte is rendered. Inject as early as possible. */
        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            view.evaluateJavascript(AD_KILLER_JS, null);
        }

        /** Re-inject after page-finish in case onPageStarted ran before document.head. */
        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            view.evaluateJavascript(AD_KILLER_JS, null);
        }
    }
}
