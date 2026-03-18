package com.kiiakira.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String BASE_URL = "https://kii-akira-backen-production-f0d7.up.railway.app";

    private WebView webView;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;
    private RelativeLayout offlineLayout;
    private ValueCallback<Uri[]> fileUploadCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0b0c10"));
        getWindow().setNavigationBarColor(Color.parseColor("#0b0c10"));
        setContentView(R.layout.activity_main);

        webView       = findViewById(R.id.webView);
        swipeRefresh  = findViewById(R.id.swipeRefresh);
        progressBar   = findViewById(R.id.progressBar);
        offlineLayout = findViewById(R.id.offlineLayout);

        setupWebView();
        setupSwipeRefresh();

        // Handle deep link if app was opened from Discord redirect
        if (getIntent() != null && getIntent().getData() != null) {
            String url = getIntent().getData().toString();
            if (url.startsWith(BASE_URL)) {
                webView.loadUrl(url);
                return;
            }
        }

        if (isOnline()) loadApp();
        else showOffline();
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString() + " KiiAkiraApp/1.0");

        CookieManager c = CookieManager.getInstance();
        c.setAcceptCookie(true);
        c.setAcceptThirdPartyCookies(webView, true);

        webView.addJavascriptInterface(new AppBridge(), "AndroidApp");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();

                // Your Railway site — always stay inside WebView
                if (url.startsWith(BASE_URL)) {
                    return false;
                }

                // Discord OAuth pages — load inside WebView so login works
                // and the callback redirects back to BASE_URL inside the app
                if (url.startsWith("https://discord.com/oauth2") ||
                    url.startsWith("https://discord.com/api/oauth2") ||
                    url.startsWith("https://discord.com/login") ||
                    url.contains("discord.com/oauth2/authorize")) {
                    return false; // stay in WebView
                }

                // Everything else — open in external browser
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    // ignore if no browser available
                }
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                offlineLayout.setVisibility(View.GONE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                if (!isOnline()) showOffline();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress == 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams) {
                fileUploadCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), 1001);
                } catch (Exception e) {
                    fileUploadCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(Color.parseColor("#5865f2"), Color.parseColor("#f0b429"));
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#17191f"));
        swipeRefresh.setOnRefreshListener(() -> {
            if (isOnline()) webView.reload();
            else swipeRefresh.setRefreshing(false);
        });
    }

    public void onRetryClick(View view) {
        if (isOnline()) loadApp();
    }

    private void loadApp() {
        offlineLayout.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(BASE_URL);
    }

    private void showOffline() {
        webView.setVisibility(View.GONE);
        offlineLayout.setVisibility(View.VISIBLE);
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo n = cm.getActiveNetworkInfo();
        return n != null && n.isConnected();
    }

    private class AppBridge {
        @JavascriptInterface
        public String getDeviceInfo() { return "android"; }

        @JavascriptInterface
        public void vibrate() {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null) v.vibrate(30);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && fileUploadCallback != null) {
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getDataString() != null) {
                results = new Uri[]{ Uri.parse(data.getDataString()) };
            }
            fileUploadCallback.onReceiveValue(results);
            fileUploadCallback = null;
        }
    }

    @Override protected void onResume() { super.onResume(); webView.onResume(); }
    @Override protected void onPause() { super.onPause(); webView.onPause(); CookieManager.getInstance().flush(); }
    @Override protected void onDestroy() { super.onDestroy(); webView.destroy(); }
}
