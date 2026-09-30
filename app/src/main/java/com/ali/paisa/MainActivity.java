package com.ali.paisa;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.webkit.WebViewAssetLoader;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView web;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        schedule();

        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
        });
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    // Har ghante chalta hai, internet ho ya na ho (koi network shart nahi).
    private void schedule() {
        PeriodicWorkRequest w = new PeriodicWorkRequest.Builder(PaisaWorker.class, 1, TimeUnit.HOURS).build();
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork("streak_hourly", ExistingPeriodicWorkPolicy.UPDATE, w);
    }

    class Bridge {
        @JavascriptInterface
        public void saveState(String json) {
            getSharedPreferences("paisa", MODE_PRIVATE).edit().putString("state", json).apply();
            try {
                if (new JSONObject(json).optBoolean("achieved")) {
                    getSystemService(NotificationManager.class).cancel(PaisaWorker.NOTIF_ID);
                }
            } catch (Exception ignored) { }
        }

        @JavascriptInterface
        public String testNotification() {
            return PaisaWorker.show(MainActivity.this, true);
        }

        @JavascriptInterface
        public void shareText(final String t) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, t);
                    startActivity(Intent.createChooser(i, "Backup bhejo"));
                }
            });
        }
    }
}
