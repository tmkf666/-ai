package com.mocitianxia.ai;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.security.MessageDigest;
import java.util.List;
import java.util.concurrent.Executor;

public class LockActivity extends AppCompatActivity {

    private EditText etPassword;
    private LockPatternView lockPatternView;
    private Button btnAction;
    private TextView tvTitle, tvSubtitle;
    private ImageView ivLockBg;
    private ImageView ivFingerprint;

    private SharedPreferences sp;
    private int authMode = -1;
    private boolean isSettingMode = false;

    private String currentGestureHash = "";
    private String firstSavedPassword = "";
    private String firstSavedGesture = "";

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean isFingerprintHardwareSupported = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sp = getSharedPreferences("AppSecurity", MODE_PRIVATE);
        authMode = sp.getInt("auth_mode", -1);

        if (authMode == -2) {
            goToNext();
            return;
        }

        setContentView(R.layout.activity_lock);

        ivLockBg = findViewById(R.id.iv_lock_bg);
        SharedPreferences spSetting = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String bgUriString = spSetting.getString("splash_bg_uri", null);
        if (bgUriString != null) {
            try {
                ivLockBg.setImageURI(Uri.parse(bgUriString));
                ivLockBg.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {
            }
        }

        etPassword = findViewById(R.id.et_password);
        lockPatternView = findViewById(R.id.lockPatternView);
        btnAction = findViewById(R.id.btn_action);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        ivFingerprint = findViewById(R.id.iv_fingerprint);

        lockPatternView.setOnPatternListener(new LockPatternView.OnPatternListener() {
            @Override
            public void onPatternStart() {
                currentGestureHash = "";
            }

            @Override
            public void onPatternDetected(List<LockPatternView.Point> pattern) {
                if (pattern.size() < 4) {
                    Toast.makeText(LockActivity.this, "至少连接4个点", Toast.LENGTH_SHORT).show();
                    currentGestureHash = "";
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (LockPatternView.Point p : pattern) {
                        sb.append(p.index);
                    }
                    currentGestureHash = sha256(sb.toString());
                }
            }
        });

        btnAction.setOnClickListener(v -> handleAction());
        initBiometricConfig();

        if (authMode == -1) {
            showModeSelectionDialog();
        } else {
            isSettingMode = false;
            setupUI();
        }
    }

    private void initBiometricConfig() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK;
        int canAuth = biometricManager.canAuthenticate(authenticators);

        if (canAuth == BiometricManager.BIOMETRIC_SUCCESS
                || canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            isFingerprintHardwareSupported = true;
        }

        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED
                        && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    Toast.makeText(LockActivity.this, "指纹错误: " + errString, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                if (!isSettingMode) {
                    Toast.makeText(LockActivity.this, "验证成功", Toast.LENGTH_SHORT).show();
                    goToNext();
                }
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(LockActivity.this, "指纹不匹配，请重试", Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("指纹解锁")
                .setSubtitle("请验证指纹以进入末辞AI")
                .setNegativeButtonText("使用密码/手势")
                .setAllowedAuthenticators(authenticators)
                .build();

        ivFingerprint.setOnClickListener(v -> {
            int currentCanAuth = biometricManager.canAuthenticate(authenticators);
            if (currentCanAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                biometricPrompt.authenticate(promptInfo);
            } else if (currentCanAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                Toast.makeText(this, "请先在手机系统设置中录入指纹！", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "当前设备不支持指纹或硬件不可用", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showModeSelectionDialog() {
        if (isFingerprintHardwareSupported) {
            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    Toast.makeText(LockActivity.this,
                            "检测到您的手机支持指纹，完成基础设置后即可使用指纹解锁！",
                            Toast.LENGTH_LONG).show(), 500);
        }

        String[] modes = {"仅使用密码", "仅使用手势", "密码 + 手势双重验证", "暂不设置（直接进入）"};
        new AlertDialog.Builder(this)
                .setTitle("选择安全验证方式")
                .setItems(modes, (dialog, which) -> {
                    if (which == 3) {
                        authMode = -2;
                        sp.edit().putInt("auth_mode", -2).apply();
                        goToNext();
                        return;
                    }
                    authMode = which;
                    sp.edit().putInt("auth_mode", authMode).apply();
                    isSettingMode = true;
                    new Handler(Looper.getMainLooper()).postDelayed(this::setupUI, 100);
                })
                .setCancelable(false)
                .show();
    }

    private void setupUI() {
        etPassword.setText("");
        etPassword.setVisibility(authMode == 1 ? View.GONE : View.VISIBLE);
        lockPatternView.setVisibility(authMode == 0 ? View.GONE : View.VISIBLE);
        lockPatternView.resetPoints();
        currentGestureHash = "";

        if (isSettingMode) {
            tvTitle.setText("安全初始化");
            tvSubtitle.setText("请设置您的" + (authMode == 0 ? "密码" : authMode == 1 ? "手势" : "密码和手势"));
            btnAction.setText("确认设置");
            ivFingerprint.setVisibility(View.GONE);
        } else {
            tvTitle.setText("身份验证");
            tvSubtitle.setText("请输入您的" + (authMode == 0 ? "密码" : authMode == 1 ? "手势" : "密码和手势"));
            btnAction.setText("解锁");
            ivFingerprint.setVisibility(isFingerprintHardwareSupported ? View.VISIBLE : View.GONE);
        }
    }

    private void handleAction() {
        String pass = etPassword.getText().toString().trim();
        String passHash = pass.isEmpty() ? "" : sha256(pass);

        if (authMode == 0 || authMode == 2) {
            if (pass.length() < 6) {
                Toast.makeText(this, "密码不能少于6位", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (authMode == 1 || authMode == 2) {
            if (TextUtils.isEmpty(currentGestureHash)) {
                Toast.makeText(this, "请绘制完整手势", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (isSettingMode) {
            if (TextUtils.isEmpty(firstSavedPassword) && TextUtils.isEmpty(firstSavedGesture)) {
                firstSavedPassword = passHash;
                firstSavedGesture = currentGestureHash;
                tvSubtitle.setText("请再次确认输入");
                etPassword.setText("");
                lockPatternView.resetPoints();
                currentGestureHash = "";
            } else {
                boolean passMatch = (authMode == 1) || firstSavedPassword.equals(passHash);
                boolean gestureMatch = (authMode == 0) || firstSavedGesture.equals(currentGestureHash);

                if (passMatch && gestureMatch) {
                    sp.edit().putString("password_hash", passHash).apply();
                    sp.edit().putString("gesture_hash", currentGestureHash).apply();
                    Toast.makeText(this, "设置成功！下次打开可使用指纹", Toast.LENGTH_SHORT).show();
                    goToNext();
                } else {
                    Toast.makeText(this, "两次输入不一致，请重新设置", Toast.LENGTH_SHORT).show();
                    firstSavedPassword = "";
                    firstSavedGesture = "";
                    setupUI();
                }
            }
        } else {
            String savedPassHash = sp.getString("password_hash", "");
            String savedGestureHash = sp.getString("gesture_hash", "");

            boolean passValid = (authMode == 1) || sha256(pass).equals(savedPassHash);
            boolean gestureValid = (authMode == 0) || currentGestureHash.equals(savedGestureHash);

            if (passValid && gestureValid) {
                goToNext();
            } else {
                Toast.makeText(this, "验证失败，请重试", Toast.LENGTH_SHORT).show();
                etPassword.setText("");
                lockPatternView.setError();
                currentGestureHash = "";
            }
        }
    }

    private void goToNext() {
        Intent intent = new Intent(LockActivity.this, SplashActivity.class);
        Intent source = getIntent();
        if (source != null) {
            if (source.getAction() != null) {
                intent.setAction(source.getAction());
            }
            if (source.getData() != null) {
                intent.setData(source.getData());
            }
            if (source.getExtras() != null) {
                intent.putExtras(source.getExtras());
            }
        }
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private String sha256(String base) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(base.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}