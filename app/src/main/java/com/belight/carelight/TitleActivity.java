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

    private boolean isNavigated = false;
    private boolean isSplashTimeOver = false;
    private FirebaseUser lastKnownUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_title);

        mAuth = FirebaseAuth.getInstance();

        // Window Insets 처리
        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        // 스플래시 최소 시간 보장을 위한 핸들러
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            isSplashTimeOver = true;
            Log.d(TAG, "Splash time is over.");
            navigateToNextActivity(lastKnownUser);
        }, SPLASH_TIMEOUT);

        // Firebase 인증 상태 리스너 정의
        // 이 리스너는 처음 연결될 때 현재 인증 상태로 즉시 한 번 호출됨.
        mAuthListener = firebaseAuth -> {
            lastKnownUser = firebaseAuth.getCurrentUser();
            Log.d(TAG, "Auth state changed. User: " + (lastKnownUser != null ? lastKnownUser.getUid() : "null"));

            // 스플래시 시간이 끝난 후에만 화면 전환을 수행
            if (isSplashTimeOver) {
                navigateToNextActivity(lastKnownUser);
            }
        };
    }

    private synchronized void navigateToNextActivity(FirebaseUser user) {
        // 중복 실행을 방지하기 위해 isNavigated 플래그 확인
        if (isNavigated) {
            return;
        }

        // 화면 전환은 스플래시 시간이 끝나야만 가능
        if (!isSplashTimeOver) {
            return;
        }

        isNavigated = true; // 화면 전환 플래그 설정

        Intent intent;
        if (user != null) {
            Log.d(TAG, "Navigating to HomeActivity.");
            intent = new Intent(TitleActivity.this, HomeActivity.class);
        } else {
            Log.d(TAG, "Navigating to LoginActivity.");
            intent = new Intent(TitleActivity.this, LoginActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // 액티비티가 시작될 때 리스너 등록
        mAuth.addAuthStateListener(mAuthListener);
    }

    @Override
    protected void onStop()     {
        super.onStop();
        // 액티비티가 멈출 때 리스너 해제 (메모리 누수 방지)
        if (mAuthListener != null) {
            mAuth.removeAuthStateListener(mAuthListener);
        }
    }
}
