package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    private TextView robotInfoText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // 디버그 모드 진입을 위한 변수들
    private int robotInfoClickCount = 0;
    private long lastRobotInfoClickTime = 0;
    private static final int REQUIRED_CLICKS_FOR_DEBUG = 7;
    private static final long MAX_CLICK_INTERVAL_MS = 500;

    // 현재 표시 중인 토스트 메시지를 관리하기 위한 변수
    private Toast currentDebugToast = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        robotInfoText = findViewById(R.id.tv_robot_info);

        loadAndDisplayUserData();
        setupDebugClickListener();
    }

    private void loadAndDisplayUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userUid = currentUser.getUid();
            Log.d(TAG, "Current user UID: " + userUid);
            DocumentReference userDocRef = db.collection("users").document(userUid);

            userDocRef.get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Log.d(TAG, "User document found for UID: " + userUid);
                            String robotId = documentSnapshot.getString("robotId");
                            String robotStatus = documentSnapshot.getString("robotStatus");

                            if (robotId == null) robotId = "정보 없음";
                            if (robotStatus == null) robotStatus = "정보 없음";

                            String displayText = String.format(Locale.getDefault(), "%s 상태: %s", robotId, robotStatus);
                            robotInfoText.setText(displayText);
                            Log.d(TAG, "Displaying data: " + displayText);
                        } else {
                            Log.w(TAG, "No such document for user UID: " + userUid);
                            robotInfoText.setText("사용자 프로필 정보를 찾을 수 없습니다.");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error fetching user document for UID: " + userUid, e);
                        robotInfoText.setText("정보 로딩 실패");
                        Toast.makeText(HomeActivity.this, "사용자 정보 로딩에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    });
        } else {
            Log.e(TAG, "User is not logged in. Redirecting to LoginActivity.");
            robotInfoText.setText("로그인이 필요합니다.");
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void setupDebugClickListener() {
        if (robotInfoText != null) {
            robotInfoText.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();

                // 마지막 클릭으로부터 너무 오랜 시간이 지났으면 카운트 및 토스트 초기화
                if (currentTime - lastRobotInfoClickTime > MAX_CLICK_INTERVAL_MS) {
                    robotInfoClickCount = 0;
                    if (currentDebugToast != null) {
                        currentDebugToast.cancel(); // 이전 토스트 취소
                        currentDebugToast = null;   // 참조 제거
                    }
                }

                robotInfoClickCount++;
                lastRobotInfoClickTime = currentTime;

                // 새로운 토스트를 표시하기 전에 이전 토스트가 있다면 취소
                if (currentDebugToast != null) {
                    currentDebugToast.cancel();
                }

                if (robotInfoClickCount == REQUIRED_CLICKS_FOR_DEBUG) {
                    robotInfoClickCount = 0; // 카운트 초기화
                    currentDebugToast = Toast.makeText(HomeActivity.this, "디버그 모드로 진입합니다.", Toast.LENGTH_SHORT);
                    currentDebugToast.show();

                    Intent intent = new Intent(HomeActivity.this, DebugActivity.class);
                    startActivity(intent);
                } else if (robotInfoClickCount < REQUIRED_CLICKS_FOR_DEBUG) {
                    int remainingClicks = REQUIRED_CLICKS_FOR_DEBUG - robotInfoClickCount;
                    if (remainingClicks > 0) {
                        String message = String.format(Locale.getDefault(), "%d번 더 클릭하면 디버그 모드로 진입합니다.", remainingClicks);
                        currentDebugToast = Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT);
                        currentDebugToast.show();
                    }
                } else {
                    robotInfoClickCount = 0;
                    if (currentDebugToast != null) {
                        currentDebugToast.cancel();
                        currentDebugToast = null;
                    }
                }
            });
        }
    }
}