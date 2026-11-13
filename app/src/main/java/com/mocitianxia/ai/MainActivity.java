package com.mocitianxia.ai;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private WebView webView;
    private TextView tvEmptyState;

    private final String TARGET_URL = "https://ai.mocitianxia.xyz/index.html?is_app=1";
    private final String VERSION_API = "https://ai.mocitianxia.xyz/version.php";

    private SharedPreferences spSetting;
    private SharedPreferences spSecurity;

    private ActivityResultLauncher<String[]> pickImageLauncher;
    private ActivityResultLauncher<String[]> requestPermissionLauncher;
    private ValueCallback<Uri[]> mFilePathCallback;
    private ActivityResultLauncher<Intent> fileChooserLauncher;

    private AlertDialog statusDialog;
    private boolean isCheckingUpdate = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        spSetting = getSharedPreferences("AppSettings", MODE_PRIVATE);
        spSecurity = getSharedPreferences("AppSecurity", MODE_PRIVATE);

        boolean isNightMode = spSetting.getBoolean("night_mode", false);
        AppCompatDelegate.setDefaultNightMode(
                isNightMode ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );

        applyScreenshotSetting(spSetting.getBoolean("disable_screenshot", true));
        setContentView(R.layout.activity_main);

        initViews();
        setupLaunchers();
        initWebView();
        checkAndRequestPermissions();
        handleIntent(getIntent());

        new Handler(Looper.getMainLooper()).postDelayed(this::checkAppUpdate, 1500);
    }

    private void initViews() {
        drawerLayout = findViewById(R.id.drawer_layout);
        webView = findViewById(R.id.webView);
        tvEmptyState = findViewById(R.id.tv_empty_state);

        findViewById(R.id.btn_menu).setOnClickListener(v ->
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));

        findViewById(R.id.fab_start).setOnClickListener(v -> openWebView());

        findViewById(R.id.btn_search).setOnClickListener(v ->
                Toast.makeText(this, "搜索功能开发中", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_clear).setOnClickListener(v -> {
            webView.clearCache(true);
            webView.clearHistory();
            Toast.makeText(this, "缓存已清理", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.menu_favorite).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "收藏", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.menu_history).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "历史记录", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.menu_manual).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "使用手册", Toast.LENGTH_SHORT).show();
        });
        findViewById(R.id.menu_premium).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            Toast.makeText(this, "高级版本", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.menu_settings).setOnClickListener(v -> {
            drawerLayout.closeDrawers();
            showAppSettingsDialog();
        });
    }

    private void openWebView() {
        if (webView == null) return;
        tvEmptyState.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        if (webView.getUrl() == null) {
            applyPageEnterEffect(webView);
            webView.loadUrl(TARGET_URL);
        }
    }

    private void showAppSettingsDialog() {
        String[] items = {"切换夜间模式", "重置安全验证", "退出当前账号"};
        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        boolean isDark = spSetting.getBoolean("night_mode", false);
                        spSetting.edit().putBoolean("night_mode", !isDark).apply();
                        AppCompatDelegate.setDefaultNightMode(
                                !isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
                        );
                        recreate();
                    } else if (which == 1) {
                        spSecurity.edit()
                                .remove("auth_mode")
                                .remove("password_hash")
                                .remove("gesture_hash")
                                .apply();
                        Intent intent = new Intent(this, LockActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finishAffinity();
                    } else if (which == 2) {
                        startActivity(new Intent(this, LockActivity.class));
                        finishAffinity();
                    }
                }).show();
    }

    private void applyPageEnterEffect(View view) {
        view.setAlpha(0f);
        view.setTranslationY(200f);
        view.animate().alpha(1f).translationY(0f).setDuration(800).start();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if ("com.mocitianxia.ai.ACTION_SCAN".equals(action)) {
            openWebView();
            Toast.makeText(this, "请使用网页内扫码功能", Toast.LENGTH_SHORT).show();
            return;
        }
        if ("com.mocitianxia.ai.ACTION_UPDATE".equals(action)) {
            checkAppUpdate();
            return;
        }
        if ("com.mocitianxia.ai.ACTION_ABOUT".equals(action)) {
            showAboutDialog();
            return;
        }

        Uri data = intent.getData();
        if (data == null) return;

        String url = data.toString();

        if (url.startsWith("mocitianxia://login_success")) {
            Toast.makeText(this, "授权成功，正在同步登录状态...", Toast.LENGTH_SHORT).show();
            String sid = data.getQueryParameter("sid");
            if (sid == null) sid = "";

            if (webView != null) {
                webView.clearCache(true);
                if (!sid.isEmpty()) {
                    CookieManager.getInstance().setCookie(
                            "https://ai.mocitianxia.xyz",
                            "PHPSESSID=" + sid
                    );
                    CookieManager.getInstance().flush();
                    webView.loadUrl("https://ai.mocitianxia.xyz/index.html?is_app=1&PHPSESSID=" + sid);
                } else {
                    webView.loadUrl("https://ai.mocitianxia.xyz/index.html?is_app=1");
                }
                tvEmptyState.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
            }
        } else if (url.startsWith("mocitianxia://scan_login")) {
            String token = data.getQueryParameter("token");
            if (token == null) token = "";

            if (!token.isEmpty() && webView != null) {
                Toast.makeText(this, "正在确认扫码...", Toast.LENGTH_SHORT).show();
                String jsCode = "fetch('scan_auth.php?action=confirm', {" +
                        "method: 'POST'," +
                        "headers: {'Content-Type': 'application/x-www-form-urlencoded'}," +
                        "body: 'token=" + token + "'" +
                        "}).then(r => r.json()).then(d => {" +
                        "  if (d.code === 0) { alert('扫码登录确认成功！'); }" +
                        "  else { alert('确认失败: ' + d.msg); }" +
                        "}).catch(e => alert('网络错误'));";
                webView.evaluateJavascript(jsCode, null);
            }
        }
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("关于末辞AI")
                .setMessage("末辞AI\n版本 1.0")
                .setPositiveButton("确定", null)
                .show();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        String userAgent = settings.getUserAgentString();
        settings.setUserAgentString(userAgent.replace("; wv", ""));

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request.getUrl().toString(), view);
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url, view);
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                WebView.HitTestResult result = view.getHitTestResult();
                String data = result.getExtra();
                if (data != null && (data.startsWith("http://") || data.startsWith("https://"))) {
                    view.loadUrl(data);
                    return true;
                }
                return super.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView webView,
                    ValueCallback<Uri[]> filePathCallback,
                    FileChooserParams fileChooserParams
            ) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                intent.setType("*/*");
                try {
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(() -> request.grant(request.getResources()));
            }
        });
    }

    private boolean handleUrl(String url, WebView view) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return false;
        }

        if (url.startsWith("wtloginmqq://")
                || url.startsWith("mqqapi://")
                || url.startsWith("mqqwpa://")) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.tencent.mobileqq");
                startActivity(intent);
            } catch (Exception e) {
                try {
                    startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME));
                } catch (Exception ex) {
                    Toast.makeText(this, "未安装手机QQ或版本不支持", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }

        if (url.startsWith("mocitianxia://login_success")
                || url.startsWith("mocitianxia://scan_login")) {
            handleIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            return true;
        }

        if (url.startsWith("googlechrome://") || url.contains("schemacallback=")) {
            return true;
        }

        try {
            startActivity(Intent.parseUri(url, Intent.URI_INTENT_SCHEME));
        } catch (Exception ignored) {
        }
        return true;
    }

    private void checkAppUpdate() {
        if (isCheckingUpdate) return;
        if (isFinishing() || isDestroyed()) return;

        isCheckingUpdate = true;
        showStatusDialog("正在检查更新...", R.drawable.ic_svg_loading, true);

        new Thread(() -> {
            try {
                URL url = new URL(VERSION_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setUseCaches(false);
                conn.setRequestProperty("Cache-Control", "no-cache");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream())
                    );
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    int serverVersionCode = json.getInt("versionCode");
                    String versionName = json.getString("versionName");
                    String updateLog = json.getString("updateLog");
                    String downloadUrl = json.getString("downloadUrl");
                    boolean forceUpdate = json.optBoolean("forceUpdate", false);

                    int currentVersionCode = getPackageManager()
                            .getPackageInfo(getPackageName(), 0)
                            .versionCode;

                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            isCheckingUpdate = false;
                            return;
                        }
                        if (serverVersionCode > currentVersionCode) {
                            closeStatusDialog();
                            showUpdateDialog(versionName, updateLog, downloadUrl, forceUpdate);
                        } else {
                            updateStatusDialog("当前已是最新版本", R.drawable.ic_svg_success);
                            new Handler(Looper.getMainLooper())
                                    .postDelayed(this::closeStatusDialog, 1500);
                        }
                        isCheckingUpdate = false;
                    });
                } else {
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) {
                            isCheckingUpdate = false;
                            return;
                        }
                        updateStatusDialog("服务器响应异常", R.drawable.ic_svg_error);
                        new Handler(Looper.getMainLooper())
                                .postDelayed(this::closeStatusDialog, 2000);
                        isCheckingUpdate = false;
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        isCheckingUpdate = false;
                        return;
                    }
                    updateStatusDialog("网络加载失败", R.drawable.ic_svg_error);
                    new Handler(Looper.getMainLooper())
                            .postDelayed(this::closeStatusDialog, 2000);
                    isCheckingUpdate = false;
                });
            }
        }).start();
    }

    private void showStatusDialog(String text, int iconRes, boolean isLoading) {
        if (statusDialog != null && statusDialog.isShowing()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_update_status, null);
        builder.setView(view);
        builder.setCancelable(false);
        statusDialog = builder.create();

        if (statusDialog.getWindow() != null) {
            statusDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivIcon = view.findViewById(R.id.iv_status_icon);
        TextView tvText = view.findViewById(R.id.tv_status_text);
        ivIcon.setImageResource(iconRes);
        tvText.setText(text);
        statusDialog.show();

        if (isLoading) {
            android.animation.ObjectAnimator rotateAnim =
                    android.animation.ObjectAnimator.ofFloat(ivIcon, "rotation", 0f, 360f);
            rotateAnim.setDuration(1000);
            rotateAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            rotateAnim.setInterpolator(new android.view.animation.LinearInterpolator());
            rotateAnim.start();
        }
    }

    private void updateStatusDialog(String text, int iconRes) {
        if (statusDialog != null && statusDialog.isShowing()) {
            ImageView ivIcon = statusDialog.findViewById(R.id.iv_status_icon);
            TextView tvText = statusDialog.findViewById(R.id.tv_status_text);
            if (ivIcon != null && tvText != null) {
                ivIcon.animate().cancel();
                ivIcon.setRotation(0f);
                ivIcon.setImageResource(iconRes);
                tvText.setText(text);
            }
        }
    }

    private void closeStatusDialog() {
        if (statusDialog != null && statusDialog.isShowing()) {
            statusDialog.dismiss();
            statusDialog = null;
        }
    }

    private void showUpdateDialog(
            String versionName,
            String updateLog,
            String downloadUrl,
            boolean forceUpdate
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_update_custom, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialog.setCancelable(!forceUpdate);
        dialog.setCanceledOnTouchOutside(!forceUpdate);

        TextView tvTitle = view.findViewById(R.id.tv_update_title);
        TextView tvVersion = view.findViewById(R.id.tv_update_version);
        TextView tvLog = view.findViewById(R.id.tv_update_log);

        tvTitle.setText(forceUpdate ? "发现重要更新" : "发现新版本");
        tvVersion.setText("v" + versionName);
        tvLog.setText(updateLog);

        view.findViewById(R.id.btn_update_confirm).setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "无法打开下载链接", Toast.LENGTH_SHORT).show();
            }
            if (forceUpdate) {
                dialog.dismiss();
                finishAffinity();
            } else {
                dialog.dismiss();
            }
        });

        View btnCancel = view.findViewById(R.id.btn_update_cancel);
        if (forceUpdate) {
            btnCancel.setVisibility(View.GONE);
        } else {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void setupLaunchers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                }
        );

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean isGranted = false;
                    for (Boolean granted : result.values()) {
                        if (granted) isGranted = true;
                    }
                    if (!isGranted) {
                        Toast.makeText(
                                this,
                                "拒绝了相机或存储权限，扫码和上传图片功能将不可用",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );

        fileChooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (mFilePathCallback == null) return;

                    Uri[] results = null;
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = result.getData().getClipData().getItemAt(i).getUri();
                            }
                        } else if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        }
                    }

                    mFilePathCallback.onReceiveValue(results);
                    mFilePathCallback = null;
                }
        );
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkAndRequestPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_MEDIA_IMAGES
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        }

        boolean needCamera = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED;

        boolean needStorage = !hasStoragePermission();

        if (needCamera || needStorage) {
            requestPermissionLauncher.launch(permissions);
        }
    }

    private void applyScreenshotSetting(boolean disableScreenshot) {
        if (disableScreenshot) {
            getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
            drawerLayout.closeDrawers();
        } else if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadDataWithBaseURL(null, "", "text/html", "utf-8", null);
            webView.clearHistory();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}