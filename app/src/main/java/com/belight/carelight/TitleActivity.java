package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth; // Firebase Auth
import com.google.firebase.auth.FirebaseUser; // Firebase User

public class TitleActivity extends AppCompatActivity {

    private static final String TAG = "TitleActivity"; // 로그 태그
    private static final int SPLASH_TIMEOUT = 2000; // 2000ms

    private FirebaseAuth mAuth; // FirebaseAuth 인스턴스 변수

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_title); // activity_title.xml 레이아웃 사용

        // FirebaseAuth 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();

        View mainView = findViewById(R.id.main);
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                return insets;
            });
        }


        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                // 현재 로그인된 사용자 확인
                FirebaseUser currentUser = mAuth.getCurrentUser();
                Intent intent;

                if (currentUser != null) {
                    // 사용자가 로그인되어 있는 경우 -> HomeActivity로 이동
                    Log.d(TAG, "User " + currentUser.getEmail() + " is already logged in. Navigating to HomeActivity.");
                    intent = new Intent(TitleActivity.this, HomeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                } else {
                    // 사용자가 로그인되어 있지 않은 경우 -> LoginActivity로 이동
                    Log.d(TAG, "No user logged in. Navigating to LoginActivity.");
                    intent = new Intent(TitleActivity.this, LoginActivity.class);
                     intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                }
                startActivity(intent);
                finish();
            }
        }, SPLASH_TIMEOUT);
    }
}