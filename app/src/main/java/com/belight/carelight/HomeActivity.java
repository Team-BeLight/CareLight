package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

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
import com.google.firebase.firestore.FieldValue; // FieldValue import 추가

import java.util.HashMap; // HashMap import 추가
import java.util.Locale;
import java.util.Map;   // Map import 추가

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // 기존 UI 및 Firebase 변수들
    // UI 요소 변수
    private TextView robotInfoText, textView7;
    private Button btnMedicine, btnRobotCall, btnVoiceChat, btnCleaning;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // 디버그 모드 진입을 위한 변수들
    private int debugClickCount = 0;
    private long lastDebugClickTime = 0;

    private int logoutClickCount = 0;
    private long lastLogoutClickTime = 0;

    private static final int REQUIRED_CLICKS = 7;
    private static final long MAX_CLICK_INTERVAL_MS = 500;

    // 현재 표시 중인 카운트다운 토스트 메시지를 관리하기 위한 변수
    private Toast currentCountdownToast = null;

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


        // Firebase 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI 요소 초기화
        initializeUI();

        // 사용자 데이터 로드 및 표시
        loadAndDisplayUserData();

        // 디버깅  리스너
        setupDebugClickListener();
        setupLogoutClickListener();

//        btnMedicine.setOnClickListener(v -> sendCommand("showToast", "약 관리 기능이 요청되었습니다."));
//        btnRobotCall.setOnClickListener(v -> sendCommand("showToast", "로봇 호출 기능이 요청되었습니다."));
//        btnVoiceChat.setOnClickListener(v -> sendCommand("showToast", "대화하기 기능이 요청되었습니다."));
//        btnCleaning.setOnClickListener(v -> sendCommand("showToast", "청소하기 기능이 요청되었습니다."));
//        btnRobotCall.setOnClickListener(v -> sendCommand("speak", "보호자님께서 호출하셨습니다."));
//        btnCleaning.setOnClickListener(v -> sendCommand("startCleaning", "청소를 시작하겠습니다."));
//        btnVoiceChat.setOnClickListener(v -> sendCommand("startVoiceChat", "Temi와 대화를 시작합니다."));
//        btnMedicine.setOnClickListener(v -> sendCommand("getMedicine", "약 관리 기능이 요청되었습니다."));
        btnMedicine.setOnClickListener(v -> {
            // 1. 파라미터 맵 생성
            Map<String, Object> params = new HashMap<>();
            params.put("location", "Room1");
            params.put("angle", 90); // 90도 오른쪽 회전

            // 2. 새로운 구조로 sendCommand 호출
            sendCommand("getMedicine", "약 가지러 이동합니다.", params);
        });

    }

    private void initializeUI() {
        robotInfoText = findViewById(R.id.tv_robot_info);
        textView7 = findViewById(R.id.textView7);

        // 새로 추가된 버튼들 참조
        btnMedicine = findViewById(R.id.btn_medicine);
        btnRobotCall = findViewById(R.id.btn_robot_call);
        btnVoiceChat = findViewById(R.id.btn_voice_chat);
        btnCleaning = findViewById(R.id.btn_cleaning);
    }

    private void sendCommand(String command, String message,  Map<String, Object> parameters) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
//             Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
//             intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//             startActivity(intent);
            return;
        }

        String userUid = currentUser.getUid();

        // 명령 데이터
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("command", command);
        commandData.put("message", message);
        commandData.put("parameters", parameters);
        commandData.put("timestamp", FieldValue.serverTimestamp());

        // 'temiCommand' 필드 업데이트
        db.collection("users").document(userUid)
                .update("temiCommand", commandData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(HomeActivity.this, "Temi에 명령을 전송했습니다.", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Command '" + command + "' sent successfully.");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(HomeActivity.this, "명령 전송에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Error sending command '" + command + "'", e);
                });
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

                if (currentTime - lastDebugClickTime > MAX_CLICK_INTERVAL_MS) {
                    debugClickCount = 0;
                    if (currentCountdownToast != null) {
                        currentCountdownToast.cancel();
                        currentCountdownToast = null;
                    }
                }

                debugClickCount++;
                lastDebugClickTime = currentTime;

                if (currentCountdownToast != null) {
                    currentCountdownToast.cancel();
                }

                if (debugClickCount == REQUIRED_CLICKS) {
                    debugClickCount = 0;
                    currentCountdownToast = Toast.makeText(HomeActivity.this, "디버그 모드로 진입합니다.", Toast.LENGTH_SHORT);
                    currentCountdownToast.show();

                    Intent intent = new Intent(HomeActivity.this, DebugActivity.class);
                    startActivity(intent);
                } else if (debugClickCount < REQUIRED_CLICKS) {
                    int remainingClicks = REQUIRED_CLICKS - debugClickCount;
                    String message = String.format(Locale.getDefault(), "%d번 더 클릭하면 디버그 모드로 진입합니다.", remainingClicks);
                    currentCountdownToast = Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT);
                    currentCountdownToast.show();
                } else {
                    debugClickCount = 0;
                }
            });
        }
    }

    private void setupLogoutClickListener() {
        if (textView7 != null) {
            textView7.setOnClickListener(v -> {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastLogoutClickTime > MAX_CLICK_INTERVAL_MS) {
                    logoutClickCount = 0;
                    if (currentCountdownToast != null) {
                        currentCountdownToast.cancel();
                        currentCountdownToast = null;
                    }
                }

                logoutClickCount++;
                lastLogoutClickTime = currentTime;

                if (currentCountdownToast != null) {
                    currentCountdownToast.cancel();
                }

                if (logoutClickCount == REQUIRED_CLICKS) {
                    logoutClickCount = 0;
                    performLogout();
                } else if (logoutClickCount < REQUIRED_CLICKS) {
                    int remainingClicks = REQUIRED_CLICKS - logoutClickCount;
                    String message = String.format(Locale.getDefault(), "로그아웃까지 %d번 남았습니다.", remainingClicks);
                    currentCountdownToast = Toast.makeText(HomeActivity.this, message, Toast.LENGTH_SHORT);
                    currentCountdownToast.show();
                } else {
                    logoutClickCount = 0;
                }
            });
        }
    }

    // Feat: 로그아웃
    private void performLogout() {
        mAuth.signOut();
        Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}