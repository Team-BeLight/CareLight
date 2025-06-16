package com.belight.carelight;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.Button;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue; // FieldValue import 추가

import java.util.ArrayList;
import java.util.HashMap; // HashMap import 추가
import java.util.Locale;
import java.util.List;
import java.util.Map;   // Map import 추가

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // --- UI 요소 변수 ---
    private TextView tvRobotInfo, tvCurrentLocation, tvBatteryStatus;
    private Button btnGotoLocation, btnRegisterLocation, btnRobotCall, btnVoiceChat, btnCleaning;

    // --- Firebase 변수 ---
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // --- 로봇 상태 데이터 ---
    private List<String> robotLocations = new ArrayList<>();

    // --- 디버그 및 로그아웃 관련 변수 ---
    private int debugClickCount = 0;
    private long lastDebugClickTime = 0;
    private int logoutClickCount = 0;
    private long lastLogoutClickTime = 0;
    private static final int REQUIRED_CLICKS = 7;
    private static final long MAX_CLICK_INTERVAL_MS = 500;
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

        // 사용자 데이터 및 로봇 상태 리스너 설정
        setupRealtimeListeners();

        // 디버깅 및 로그아웃 리스너
        setupDebugClickListener();
        setupLogoutClickListener();

        // --- 버튼 클릭 리스너 설정 ---
        btnRegisterLocation.setOnClickListener(v -> showRegisterLocationDialog());
        btnGotoLocation.setOnClickListener(v -> showLocationSelectionDialog());
        btnRobotCall.setOnClickListener(v -> {
            // '보호자' 라는 이름으로 미리 위치를 등록했다고 가정하고 해당 위치로 호출
            Map<String, Object> params = new HashMap<>();
            params.put("location", "보호자"); // 약속된 위치 이름
            params.put("angle", 0);
            sendCommand("goToLocation", "보호자님께 이동합니다.", params);
        });
        // TODO: btnVoiceChat, btnCleaning 에 대한 기능 구현

    }

    private void initializeUI() {
        tvRobotInfo = findViewById(R.id.tv_robot_info);
        tvCurrentLocation = findViewById(R.id.textView6); // XML의 '현재 위치' TextView
        tvBatteryStatus = findViewById(R.id.textView7);   // XML의 '배터리 상태' TextView

        btnGotoLocation = findViewById(R.id.btn_goto_location);
        btnRegisterLocation = findViewById(R.id.btn_register_location);

        btnRobotCall = findViewById(R.id.btn_robot_call);
        btnVoiceChat = findViewById(R.id.btn_voice_chat);
        btnCleaning = findViewById(R.id.btn_cleaning);
    }

    private void setupRealtimeListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            // 로그인 안된 경우 처리 (기존과 동일)
            redirectToLogin();
            return;
        }

        String userUid = currentUser.getUid();
        final DocumentReference userDocRef = db.collection("users").document(userUid);

        userDocRef.addSnapshotListener((snapshot, e) -> {
            if (e != null) {
                Log.w(TAG, "Listen failed.", e);
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                // 사용자 기본 정보 업데이트
                String robotId = snapshot.getString("robotId");
                if (robotId == null) robotId = "정보 없음";
                tvRobotInfo.setText(String.format("Care Light (%s)", robotId));

                // 로봇 상태(robotState) 정보 실시간 업데이트
                Object stateObj = snapshot.get("robotState");
                if (stateObj instanceof Map) {
                    Map<String, Object> robotState = (Map<String, Object>) stateObj;

                    String currentLocation = (String) robotState.get("currentLocation");
                    tvCurrentLocation.setText(String.format("현재 위치: %s", currentLocation != null ? currentLocation : "알 수 없음"));

                    Number battery = (Number) robotState.get("batteryPercentage");
                    tvBatteryStatus.setText(String.format(Locale.getDefault(), "배터리 상태: %d%%", battery != null ? battery.intValue() : 0));

                    // 저장된 위치 목록 업데이트
                    List<String> locations = (List<String>) robotState.get("savedLocations");
                    if (locations != null) {
                        this.robotLocations = locations;
                        Log.d(TAG, "Updated robot locations: " + locations.toString());
                    }
                }
            } else {
                Log.w(TAG, "No such document for user UID: " + userUid);
                tvRobotInfo.setText("사용자 프로필 정보를 찾을 수 없습니다.");
            }
        });
    }


    // [새로 추가됨] '현재 위치 등록' 다이얼로그 표시
    private void showRegisterLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("현재 위치 등록");
        builder.setMessage("로봇의 현재 위치를 저장할 이름을 입력하세요.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("저장", (dialog, which) -> {
            String locationName = input.getText().toString().trim();
            if (!locationName.isEmpty()) {
                Map<String, Object> params = new HashMap<>();
                params.put("locationName", locationName);
                sendCommand("saveLocation", "위치 저장 요청: " + locationName, params);
            } else {
                Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("취소", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    // [새로 추가됨] '장소 선택' 다이얼로그 표시
    private void showLocationSelectionDialog() {
        if (robotLocations == null || robotLocations.isEmpty()) {
            Toast.makeText(this, "저장된 장소가 없습니다. 먼저 장소를 등록해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        final CharSequence[] items = robotLocations.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("이동할 장소를 선택하세요");
        builder.setItems(items, (dialog, itemIndex) -> {
            String selectedLocation = (String) items[itemIndex];
            float angle = 0f; // 기본 회전 각도

            Map<String, Object> params = new HashMap<>();
            params.put("location", selectedLocation);
            params.put("angle", angle);
            sendCommand("goToLocation", selectedLocation + "(으)로 이동합니다.", params);
        });
        builder.show();
    }

    private void sendCommand(String command, String message,  Map<String, Object> parameters) {

        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String userUid = currentUser.getUid();

        // 명령 데이터
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("command", command);
        commandData.put("message", message);
        if (parameters != null) {
            commandData.put("parameters", parameters);
        }
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

    private void redirectToLogin() {
        Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setupDebugClickListener() {
        if (tvRobotInfo != null) {
            tvRobotInfo.setOnClickListener(v -> {
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
        View topCard = findViewById(R.id.cv_top_menu);
        if (topCard != null) {
            topCard.setOnClickListener(v -> {
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