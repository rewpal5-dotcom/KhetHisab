package com.khethisab.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * KhetHisab — Android shell for https://friendly-hisab-tracker.lovable.app/
 *
 * v1.2
 *  - offline screen: friendly picture ("Internet ON karein") instead of the browser's
 *    error page, so the site URL is never shown to the user
 *  - Google sign-in: a real Chrome user-agent, pop-up windows allowed, and the Google
 *    consent page opens in the phone's browser (where the user is already signed in)
 *    then comes back to the app through a deep link
 *  - progress bar, back navigation, zoom lock, edge-to-edge safe, downloads
 */
public class MainActivity extends Activity {

    private static final String START_URL = "https://friendly-hisab-tracker.lovable.app/";
    private static final String OWN_HOST = "lovable.app";

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Mobile Safari/537.36";

    private WebView web;
    private ProgressBar bar;
    private View offlineView;
    private boolean loadFailed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#ffffff"));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgress(0);
        root.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));

        offlineView = buildOfflineView();
        offlineView.setVisibility(View.GONE);
        root.addView(offlineView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);

        /* Android 15+ edge-to-edge */
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets b = insets.getInsets(
                            WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = b.top; bottom = b.bottom; left = b.left; right = b.right;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
                v.setPadding(left, top, right, bottom);
                return insets;
            }
        });
        root.requestApplyInsets();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUserAgentString(UA);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        /* Google login often happens in a pop-up window — allow it */
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);

        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHorizontalScrollBarEnabled(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int p) {
                bar.setProgress(p);
                bar.setVisibility(p >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                /* pop-up login windows open inside the same WebView */
                WebView.HitTestResult r = view.getHitTestResult();
                String data = r == null ? null : r.getExtra();
                if (data != null) {
                    web.loadUrl(data);
                }
                return false;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                loadFailed = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                bar.setVisibility(View.GONE);
                if (!loadFailed) {
                    offlineView.setVisibility(View.GONE);
                    web.setVisibility(View.VISIBLE);
                }
                polishPage(view);
            }

            @Override
            public void onReceivedError(WebView view, int code, String description, String failingUrl) {
                if (failingUrl != null && failingUrl.startsWith("http")) showOffline();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request != null && request.isForMainFrame()) showOffline();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url);
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimeType, long contentLength) {
                try {
                    String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
                    req.setMimeType(mimeType);
                    req.addRequestHeader("User-Agent", userAgent);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) req.addRequestHeader("Cookie", cookie);
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(req);
                    Toast.makeText(MainActivity.this, "Download: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
            }
        });

        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(START_URL);
        }
    }

    /** Friendly offline screen — picture + retry, no site URL anywhere. */
    private View buildOfflineView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.WHITE);

        ImageView img = new ImageView(this);
        img.setImageResource(R.drawable.offline_screen);
        img.setAdjustViewBounds(true);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        ip.setMargins(dp(10), dp(10), dp(10), 0);
        box.addView(img, ip);

        Button retry = new Button(this);
        retry.setText("Dobara koshish karein");
        retry.setAllCaps(false);
        retry.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0, dp(4), 0, dp(26));
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { retryLoad(); }
        });
        box.addView(retry, bp);

        return box;
    }

    private void showOffline() {
        loadFailed = true;
        bar.setVisibility(View.GONE);
        web.setVisibility(View.GONE);      // hides the browser error page (and its URL)
        offlineView.setVisibility(View.VISIBLE);
    }

    private void retryLoad() {
        loadFailed = false;
        offlineView.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        bar.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        web.loadUrl(START_URL);
    }

    /** Hides the "Edit with Lovable" badge. */
    private void polishPage(WebView view) {
        String js = "(function(){"
                + "if(document.getElementById('__kh_style'))return;"
                + "var st=document.createElement('style');st.id='__kh_style';"
                + "st.innerHTML='a[href*=\"lovable.dev\"]{display:none !important;}"
                + "[id*=\"lovable-badge\"],[class*=\"lovable-badge\"],[data-lovable-badge],"
                + "#lovable-badge,.lovable-badge{display:none !important;}"
                + "html,body{overflow-x:hidden !important;}';"
                + "(document.head||document.documentElement).appendChild(st);"
                + "try{var all=document.querySelectorAll('a,div,span');"
                + "for(var i=0;i<all.length;i++){var el=all[i];var t=(el.textContent||'').trim().toLowerCase();"
                + "if(t.length>0&&t.length<40&&(t==='edit with'||t.indexOf('edit with lovable')>=0||t.indexOf('built with lovable')>=0)){"
                + "el.style.display='none';if(el.parentElement&&getComputedStyle(el.parentElement).position==='fixed'){el.parentElement.style.display='none';}"
                + "}}}catch(e){}"
                + "})();";
        view.evaluateJavascript(js, null);
    }

    private boolean handleUrl(String url) {
        if (url == null) return false;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();

            /* Google / Lovable consent pages: open in the phone's browser, where the user
               is already signed in with their own Gmail. The redirect comes back to this
               app through the intent filter in AndroidManifest. */
            if (host.contains("accounts.google.com") || host.contains("lovable.dev")
                    || host.contains("googleusercontent.com") && url.contains("oauth")) {
                try {
                    Intent i = new Intent(Intent.ACTION_VIEW, uri);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                    return true;
                } catch (Exception ignored) { }
            }

            if (host.endsWith(OWN_HOST) || host.endsWith("supabase.co") || host.endsWith("googleapis.com")
                    || host.endsWith("gstatic.com") || host.endsWith("google.com")) {
                return false;
            }
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
        return true;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        /* OAuth redirect coming back from the browser */
        if (intent != null && intent.getData() != null && web != null) {
            String u = intent.getData().toString();
            if (u.startsWith("http")) {
                loadFailed = false;
                offlineView.setVisibility(View.GONE);
                web.setVisibility(View.VISIBLE);
                web.loadUrl(u);
            }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (offlineView != null && offlineView.getVisibility() == View.VISIBLE) {
                retryLoad();
                return true;
            }
            if (web != null && web.canGoBack()) {
                web.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
        }
        super.onDestroy();
    }
}
