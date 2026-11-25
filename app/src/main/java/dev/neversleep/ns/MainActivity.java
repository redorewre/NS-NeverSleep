package dev.neversleep.ns;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.MaterialColors;

import org.json.JSONObject;

/**
 * NS (NeverSleep) — нативная оболочка.
 *
 * Три задачи:
 * 1) отдать интерфейс с https://ns.local/assets/index.html (без file:// — CORS и localStorage работают);
 * 2) передать в веб-интерфейс реальные цвета системы Material You: primary и tertiary —
 *    это два акцента NS. При смене темы/обоев цвета пересылаются заново;
 * 3) открыть системный выбор аудиофайлов для локального плеера (<input type="file">).
 */
public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 1001;
    private WebView web;
    private ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // На Android 12+ накладываем динамическую палитру из обоев поверх Material3-темы.
        // На старых версиях остаётся статичная палитра Material3, её подхватит JS-фолбэк.
        DynamicColors.applyToActivityIfAvailable(this);

        WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .setDomain("ns.local")
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        // Фон приложения фиксированный: серый, почти чёрный.
        web.setBackgroundColor(0xFF111114);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);

        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.addJavascriptInterface(new ColorBridge(), "NSBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pushColors();
            }
        });

        // Локальные аудиофайлы: WebView отдаёт выбранные URI в JavaScript.
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("audio/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(Intent.createChooser(intent, "Выбрать аудио"),
                            FILE_CHOOSER_REQUEST);
                } catch (Exception e) {
                    filePathCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
                return true;
            }
        });

        applySystemBars();
        web.loadUrl("https://ns.local/assets/index.html");
    }

    /** Мост для синхронного чтения палитры из JavaScript. */
    public class ColorBridge {
        @JavascriptInterface
        public String colors() {
            return paletteJson();
        }
    }

    private void pushColors() {
        if (web == null) return;
        web.evaluateJavascript(
                "window.nsApplySystemColors && window.nsApplySystemColors(" + paletteJson() + ")", null);
    }

    private String paletteJson() {
        try {
            JSONObject o = new JSONObject();
            // Акцент №1 — primary, акцент №2 — tertiary. Оба из системной палитры.
            o.put("a1", hex(themeColor(com.google.android.material.R.attr.colorPrimary, 0xFF6750A4)));
            o.put("a1c", hex(themeColor(com.google.android.material.R.attr.colorPrimaryContainer, 0xFFEADDFF)));
            o.put("onA1", hex(themeColor(com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF)));
            o.put("a2", hex(themeColor(com.google.android.material.R.attr.colorTertiary, 0xFF7D5260)));
            o.put("a2c", hex(themeColor(com.google.android.material.R.attr.colorTertiaryContainer, 0xFFFFD8E4)));
            o.put("onA2", hex(themeColor(com.google.android.material.R.attr.colorOnTertiary, 0xFFFFFFFF)));
            o.put("bg", hex(themeColor(com.google.android.material.R.attr.colorSurface, 0xFFFFFBFE)));
            o.put("surface", hex(themeColor(com.google.android.material.R.attr.colorSurfaceVariant, 0xFFE7E0EC)));
            o.put("onSurface", hex(themeColor(com.google.android.material.R.attr.colorOnSurface, 0xFF1D1B20)));
            o.put("muted", hex(themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF49454F)));
            o.put("outline", hex(themeColor(com.google.android.material.R.attr.colorOutline, 0xFFCAC4D0)));
            o.put("dark", (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private int themeColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(attr, value, true);
        if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        try {
            return MaterialColors.getColor(this, attr, fallback);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String hex(int argb) {
        return String.format("#%06X", 0xFFFFFF & argb);
    }

    private void applySystemBars() {
        // Системные панели того же цвета, что и фон приложения, иконки — светлые.
        int dark = 0xFF111114;
        getWindow().setStatusBarColor(dark);
        getWindow().setNavigationBarColor(dark);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && getWindow().getInsetsController() != null) {
            WindowInsetsController c = getWindow().getInsetsController();
            c.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(decor.getSystemUiVisibility() & ~flags);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;

        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                results = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) {
                    results[i] = clip.getItemAt(i).getUri();
                }
            } else if (data.getData() != null) {
                results = new Uri[]{data.getData()};
            }
        }
        if (results != null) {
            for (Uri uri : results) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Палитра могла измениться, пока приложение было в фоне (смена обоев/темы).
        applySystemBars();
        pushColors();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySystemBars();
        pushColors();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && web != null) {
            web.evaluateJavascript(
                    "(function(){try{return !!(window.nsBack && window.nsBack());}catch(e){return false;}})()",
                    value -> {
                        if (!"true".equals(value)) finish();
                    });
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
