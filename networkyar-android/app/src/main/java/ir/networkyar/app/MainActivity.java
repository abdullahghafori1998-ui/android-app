package ir.networkyar.app;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String TERMUX_PACKAGE = "com.termux";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final String CONTROLLER = TERMUX_HOME + "/lan-messenger/control-panel/termux/controller-start.sh";
    private static final String HOME_URL = "http://127.0.0.1:8081/home.html";
    private static final String RUN_PERMISSION = "com.termux.permission.RUN_COMMAND";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private TextView status;
    private WebView webView;
    private View setupView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingWebPermission;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(8, 127, 112));
        getWindow().setNavigationBarColor(Color.rgb(8, 48, 58));
        showSetup();
        if (getPreferences(MODE_PRIVATE).getBoolean("setup_done", false)) startNetworkYar();
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.RIGHT); t.setTextDirection(View.TEXT_DIRECTION_RTL); t.setPadding(dp(4),dp(7),dp(4),dp(7));
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(15);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(8,127,112)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)); p.setMargins(0,dp(6),0,dp(6));
        b.setLayoutParams(p); b.setOnClickListener(v -> action.run()); return b;
    }

    private void showSetup() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(242,247,247));
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(20),dp(24),dp(20),dp(24));
        TextView title = text("شبکه‌یار",28,Color.rgb(8,90,82)); title.setGravity(Gravity.CENTER); content.addView(title);
        TextView subtitle = text("اجرای مسنجر و پنل شبکه‌یار داخل خود برنامه",14,Color.DKGRAY); subtitle.setGravity(Gravity.CENTER); content.addView(subtitle);
        TextView steps = text("برای ارتباط امن برنامه با Termux، فقط یک‌بار این آماده‌سازی لازم است:\n\n۱) Termux را باز کن و فرمان زیر را در آن اجرا کن تا اجازه اجرای فرمان‌های خارجی فعال شود.\n\n۲) در تنظیمات اندروید، برای برنامه «شبکه‌یار» مجوز «Run commands in Termux environment» را فعال کن.\n\n۳) مطمئن شو نسخه Termux به‌روز است و پروژه در مسیر ~/lan-messenger قرار دارد.",14,Color.rgb(35,55,62));
        steps.setBackgroundColor(Color.WHITE); steps.setPadding(dp(15),dp(15),dp(15),dp(15)); content.addView(steps);
        Button copy = button("کپی فرمان آماده‌سازی Termux", this::copySetupCommand); content.addView(copy);
        content.addView(button("بازکردن Termux", this::openTermux));
        content.addView(button("اعطای مجوز اجرای فرمان", this::requestRunPermission));
        content.addView(button("بازکردن تنظیمات مجوز شبکه‌یار", this::openOwnAppSettings));
        content.addView(button("اجرای شبکه‌یار", this::startNetworkYar));
        status = text("آماده‌سازی یک‌باره را انجام بده؛ سپس اجرای شبکه‌یار را بزن.",12,Color.rgb(90,105,110)); content.addView(status);
        scroll.addView(content); root.addView(scroll); setContentView(root); setupView = root;
    }

    private void copySetupCommand() {
        String command = "mkdir -p ~/.termux; (grep -q '^allow-external-apps=' ~/.termux/termux.properties 2>/dev/null && sed -i 's/^allow-external-apps=.*/allow-external-apps=true/' ~/.termux/termux.properties || echo 'allow-external-apps=true' >> ~/.termux/termux.properties); termux-reload-settings";
        ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("Termux setup", command));
        Toast.makeText(this,"فرمان کپی شد؛ آن را در Termux جای‌گذاری و اجرا کن.",Toast.LENGTH_LONG).show();
    }

    private void openTermux() {
        Intent i = getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE);
        if (i == null) { setStatus("Termux نصب نیست یا نسخه نصب‌شده قابل شناسایی نیست."); return; }
        startActivity(i);
    }

    private void requestRunPermission() {
        if (checkSelfPermission(RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED) { setStatus("مجوز اجرای فرمان قبلاً فعال است."); return; }
        try { requestPermissions(new String[]{RUN_PERMISSION}, 701); }
        catch (Exception e) { openOwnAppSettings(); }
    }

    private void openOwnAppSettings() {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
        startActivity(i);
    }

    private void setStatus(String message) { if (status != null) status.setText(message); }

    private void startNetworkYar() {
        if (getPackageManager().getLaunchIntentForPackage(TERMUX_PACKAGE) == null) {
            setStatus("Termux پیدا نشد. ابتدا Termux سازگار را نصب کن."); return;
        }
        if (checkSelfPermission(RUN_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            setStatus("مجوز اجرای فرمان Termux فعال نیست. از دکمه اعطای مجوز استفاده کن."); openOwnAppSettings(); return;
        }
        try {
            Intent command = new Intent();
            command.setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService");
            command.setAction("com.termux.RUN_COMMAND");
            command.putExtra("com.termux.RUN_COMMAND_PATH", BASH);
            command.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", "bash " + CONTROLLER});
            command.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
            command.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
            startService(command);
            setStatus("درخواست راه‌اندازی ارسال شد؛ در حال اتصال به پنل داخلی…");
            waitForController(0);
        } catch (SecurityException e) {
            setStatus("Termux اجازه اجرای فرمان خارجی را نداده است. allow-external-apps را در Termux فعال کن و مجوز شبکه‌یار را بده.");
        } catch (Exception e) {
            setStatus("اجرای Termux ناموفق بود: " + e.getMessage());
        }
    }

    private void waitForController(int attempt) {
        worker.execute(() -> {
            boolean ready = false;
            try {
                HttpURLConnection c = (HttpURLConnection)new URL(HOME_URL).openConnection();
                c.setConnectTimeout(900); c.setReadTimeout(900); c.setRequestMethod("GET");
                ready = c.getResponseCode() == 200; c.disconnect();
            } catch (Exception ignored) { }
            final boolean ok = ready;
            handler.post(() -> {
                if (ok) showWebApp();
                else if (attempt < 24) handler.postDelayed(() -> waitForController(attempt + 1), 650);
                else setStatus("پنل پاسخ نداد. بررسی کن پوشه ~/lan-messenger و فایل controller-start.sh وجود داشته باشند و فرمان آماده‌سازی Termux اجرا شده باشد.");
            });
        });
    }

    private void showWebApp() {
        webView = new WebView(this);
        WebSettings s = webView.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMediaPlaybackRequiresUserGesture(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE); s.setSupportZoom(false);
        webView.setBackgroundColor(Color.rgb(242,247,247));
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) Toast.makeText(MainActivity.this,"بارگذاری صفحه ناموفق بود؛ اتصال سرور را بررسی کن.",Toast.LENGTH_LONG).show();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null); fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT); pick.addCategory(Intent.CATEGORY_OPENABLE); pick.setType("image/*");
                startActivityForResult(Intent.createChooser(pick,"انتخاب تصویر QR"),702); return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                boolean wantsCamera = false; for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                if (wantsCamera && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    pendingWebPermission = request; requestPermissions(new String[]{Manifest.permission.CAMERA},703);
                } else request.grant(request.getResources());
            }
        });
        setContentView(webView); webView.loadUrl(HOME_URL);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 703 && pendingWebPermission != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) pendingWebPermission.grant(pendingWebPermission.getResources());
            else pendingWebPermission.deny(); pendingWebPermission = null;
        }
        if (requestCode == 701 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) setStatus("مجوز فعال شد. اکنون اجرای شبکه‌یار را بزن.");
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 702 && fileCallback != null) {
            Uri[] result = (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) ? new Uri[]{data.getData()} : null;
            fileCallback.onReceiveValue(result); fileCallback = null;
        }
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() { super.onDestroy(); worker.shutdownNow(); if (webView != null) webView.destroy(); }
}
