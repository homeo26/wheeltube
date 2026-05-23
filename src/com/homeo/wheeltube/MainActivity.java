package com.homeo.wheeltube;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Build;
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
 * Ad blocking is done in two layers:
 *   1. {@link WebViewClient#shouldInterceptRequest} blocks known ad/telemetry hosts
 *      and YouTube ad endpoints by returning empty responses.
 *   2. CSS injected on every page-finish hides ad/promo DOM nodes that slip past.
 *
 * On Android Auto, this activity is what AA projects to the head unit. The
 * {@link WheelMediaBrowserService} is what makes WheelTube show up in AA's
 * launcher; tapping its item starts this activity.
 */
public class MainActivity extends Activity {
    private static final String TAG = "WheelTube";
    private static final String START_URL = "https://m.youtube.com";

    /**
     * Hostnames whose requests are dropped at the network layer. Keep this
     * list small and high-signal to avoid breaking playback. The
     * heavy-lifting on YouTube ads is done by URL-path matching below.
     */
    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "googlesyndication.com",
            "doubleclick.net",
            "googleadservices.com",
            "googletagservices.com",
            "googletagmanager.com",
            "google-analytics.com");

    /**
     * Path fragments inside youtube.com / googlevideo.com that always
     * indicate ad delivery or telemetry, never video data.
     */
    private static final List<String> BLOCKED_PATHS = Arrays.asList(
            "/api/stats/ads",
            "/pagead/",
            "/get_midroll_",
            "/ptracking",
            "/log_event",
            "/youtubei/v1/log_event");

    /** CSS to hide ad/promo elements that pure URL blocking can't catch. */
    private static final String AD_HIDE_CSS =
            "ytd-ad-slot-renderer," +
            "ytd-promoted-sparkles-web-renderer," +
            "ytd-banner-promo-renderer," +
            "ytd-mealbar-promo-renderer," +
            "ytmusic-mealbar-promo-renderer," +
            ".ytp-ad-module," +
            ".ytp-ad-overlay-container," +
            ".ad-container," +
            "ytd-display-ad-renderer," +
            "ytd-promoted-video-renderer," +
            "ytm-promoted-sparkles-web-renderer," +
            "ytm-companion-ad-renderer," +
            "ytm-promoted-video-renderer { display: none !important; }";

    private WebView webView;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // Force landscape — every BYD/AA screen is landscape and YouTube's
        // mobile site picks a much better layout that way.
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        // Keep the screen on while the activity is visible.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Hide system UI for an immersive video experience.
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

        // Pretend to be Chrome rather than the WebView so YouTube serves us
        // its full-fat mobile site instead of a degraded one.
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

    /** Make the back button navigate the WebView's history before exiting. */
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
                    if (lc.equals(h) || lc.endsWith("." + h)) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            Log.v(TAG, "block host: " + host);
                        }
                        return EMPTY;
                    }
                }
            }
            if (path != null) {
                for (String p : BLOCKED_PATHS) {
                    if (path.contains(p)) {
                        Log.v(TAG, "block path: " + path);
                        return EMPTY;
                    }
                }
            }
            return null;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            // Inject ad-hiding CSS.
            String escaped = AD_HIDE_CSS.replace("\\", "\\\\").replace("'", "\\'");
            String js =
                    "(function(){" +
                    "  var s=document.createElement('style');" +
                    "  s.id='wheeltube-style';" +
                    "  s.textContent='" + escaped + "';" +
                    "  document.head && document.head.appendChild(s);" +
                    "})();";
            view.evaluateJavascript(js, null);
        }
    }
}
