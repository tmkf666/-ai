package com.mocitianxia.ai;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable jumpRunnable;
    private boolean isJumped = false;
    private ObjectAnimator breathingAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView ivBg = findViewById(R.id.iv_splash_bg);
        ImageView ivPlane = findViewById(R.id.iv_plane);
        TextView tvLogo = findViewById(R.id.tv_splash_logo);
        TextView tvSkip = findViewById(R.id.tv_skip);

        SharedPreferences sp = getSharedPreferences("AppSettings", MODE_PRIVATE);
        String bgUriString = sp.getString("splash_bg_uri", null);
        if (bgUriString != null) {
            try {
                ivBg.setImageURI(Uri.parse(bgUriString));
                ivBg.setVisibility(View.VISIBLE);
            } catch (Exception ignored) {
            }
        }

        ivPlane.setTranslationX(-350f);
        ivPlane.setTranslationY(400f);
        ivPlane.setScaleX(0.3f);
        ivPlane.setScaleY(0.3f);
        ivPlane.setRotation(-45f);

        ObjectAnimator planeX = ObjectAnimator.ofFloat(ivPlane, "translationX", -350f, 0f);
        ObjectAnimator planeY = ObjectAnimator.ofFloat(ivPlane, "translationY", 400f, 0f);
        ObjectAnimator planeScaleX = ObjectAnimator.ofFloat(ivPlane, "scaleX", 0.3f, 1.1f, 1.0f);
        ObjectAnimator planeScaleY = ObjectAnimator.ofFloat(ivPlane, "scaleY", 0.3f, 1.1f, 1.0f);
        ObjectAnimator planeAlpha = ObjectAnimator.ofFloat(ivPlane, "alpha", 0f, 1f);
        ObjectAnimator planeRotate = ObjectAnimator.ofFloat(ivPlane, "rotation", -45f, 0f);

        AnimatorSet flyInSet = new AnimatorSet();
        flyInSet.playTogether(planeX, planeY, planeScaleX, planeScaleY, planeAlpha, planeRotate);
        flyInSet.setDuration(1200);
        flyInSet.setInterpolator(new DecelerateInterpolator(1.5f));

        ObjectAnimator textAlpha = ObjectAnimator.ofFloat(tvLogo, "alpha", 0f, 1f);
        ObjectAnimator textY = ObjectAnimator.ofFloat(tvLogo, "translationY", 30f, 0f);
        AnimatorSet textSet = new AnimatorSet();
        textSet.playTogether(textAlpha, textY);
        textSet.setDuration(800);
        textSet.setStartDelay(300);
        textSet.setInterpolator(new DecelerateInterpolator());

        flyInSet.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                breathingAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        ivPlane,
                        android.animation.PropertyValuesHolder.ofFloat("translationY", 0f, -20f, 0f),
                        android.animation.PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.05f, 1.0f),
                        android.animation.PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.05f, 1.0f)
                );
                breathingAnimator.setDuration(2000);
                breathingAnimator.setRepeatCount(ValueAnimator.INFINITE);
                breathingAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                breathingAnimator.start();
            }
        });

        flyInSet.start();
        textSet.start();

        jumpRunnable = () -> {
            if (!isJumped) {
                isJumped = true;
                Intent next = new Intent(SplashActivity.this, MainActivity.class);
                Intent source = getIntent();
                if (source != null) {
                    if (source.getAction() != null) {
                        next.setAction(source.getAction());
                    }
                    if (source.getData() != null) {
                        next.setData(source.getData());
                    }
                    if (source.getExtras() != null) {
                        next.putExtras(source.getExtras());
                    }
                }
                startActivity(next);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        };
        handler.postDelayed(jumpRunnable, 3000);

        tvSkip.setOnClickListener(v -> {
            handler.removeCallbacks(jumpRunnable);
            jumpRunnable.run();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (breathingAnimator != null) {
            breathingAnimator.cancel();
            breathingAnimator = null;
        }
    }
}