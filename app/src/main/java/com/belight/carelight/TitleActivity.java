package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class TitleActivity extends AppCompatActivity {

    private static final String TAG = "TitleActivity";
    private static final int SPLASH_TIMEOUT = 2000; // 스플래시 화면 최소 표시 시간

    private FirebaseAuth mAuth;
    private FirebaseAuth.AuthStateListener mAuthListener;
    private boolean navigated = false; // 중복 네비게이션 방지 플래그
    private Handler handler;
    private Runnable navigationRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_title);

        mAuth = FirebaseAuth.getInstance();
        handler = new Handler(Looper.getMainLooper());

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        mAuthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                // AuthStateListener는 여러 번 호출될 수 있으므로, 한 번만 네비게이션 로직을 실행하도록 처리
                // 또한, 스플래시 타임아웃 전에 너무 빨리 네비게이션 되는 것을 방지할 수도 있음 (현재는 즉시 반응)
                Log.d(TAG, "AuthStateListener: onAuthStateChanged triggered.");
                FirebaseUser user = firebaseAuth.getCurrentUser();
                // 핸들러를 통해 SPLASH_TIMEOUT 이후에 네비게이션을 실행하도록 예약된 작업이 있다면 취소
                if (navigationRunnable != null) {
                    handler.removeCallbacks(navigationRunnable);
                }
                // 네비게이션 실행
                navigateToNextActivity(user);
            }
        };

        // 스플래시 타임아웃 후 네비게이션 실행 (네트워크가 느리거나 AuthStateListener가 늦게 반응할 경우 대비)
        navigationRunnable = new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Handler: SPLASH_TIMEOUT reached.");
                // AuthStateListener가 이미 네비게이션 했다면 중복 실행 방지
                if (!navigated) {
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    navigateToNextActivity(currentUser);
                }
            }
        };
    }

    private synchronized void navigateToNextActivity(FirebaseUser currentUser) {
        if (navigated) {
            return; // 이미 네비게이션이 진행되었다면 중복 실행 방지
        }
        navigated = true; // 네비게이션 플래그 설정

        Intent intent;
        if (currentUser != null) {
            Log.d(TAG, "Navigating to HomeActivity. User: " + currentUser.getEmail());
            intent = new Intent(TitleActivity.this, HomeActivity.class);
        } else {
            Log.d(TAG, "Navigating to LoginActivity. No user logged in.");
            intent = new Intent(TitleActivity.this, LoginActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 리스너 등록 전에 navigated 플래그 초기화 (액티비티가 다시 시작될 경우 대비)
        navigated = false;
        mAuth.addAuthStateListener(mAuthListener);
        Log.d(TAG, "AuthStateListener added.");
        // 스플래시 타임아웃 예약
        handler.postDelayed(navigationRunnable, SPLASH_TIMEOUT);
        Log.d(TAG, "Navigation runnable posted with delay.");
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
            Log.d(TAG, "AuthStateListener removed.");
        }
        // 핸들러 콜백 제거
        if (navigationRunnable != null) {
            handler.removeCallbacks(navigationRunnable);
            Log.d(TAG, "Navigation runnable removed.");
        }
    }
}