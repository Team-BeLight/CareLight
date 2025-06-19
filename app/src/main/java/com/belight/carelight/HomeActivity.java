package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.text.InputType;
import android.view.View;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Toast;
import android.os.Handler;
import android.widget.Button;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.EdgeToEdge;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FieldValue;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class HomeActivity extends AppCompatActivity {

    private static final String TAG = "HomeActivity";

    // --- UI 요소 변수 ---
    private TextView tvRobotInfo, tvRobotLocation, tvUserLocation, tvBatteryStatus, tvMedicationStatus;
    private Button btnGetMedicine, btnRobotCall, btnVoiceChat, btnCleaning;
    private Button btnGotoLocation, btnRegisterLocation, btnDeleteLocation, btnSetUserLocation;

    private TextView tvCurrentHeartRate;
    private Button btnMeasureHeartRate;

    // --- Firebase 변수 ---
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

    private final Handler statusUpdateHandler = new Handler(Looper.getMainLooper());
    private Runnable statusUpdateRunnable;

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

        // 초기화
        initializeFirebase();
        initializeUI();
        loadUserLocation();

        // 리스너 설정
        setupRealtimeListeners();
        setupButtonClickListeners();
        setupHiddenFeatureListeners();
    }

    private void initializeUI() {
        // 상단 카드
        tvRobotInfo = findViewById(R.id.tv_robot_info);
        tvRobotLocation = findViewById(R.id.tv_robot_location);
        tvUserLocation = findViewById(R.id.tv_user_location);
        tvBatteryStatus = findViewById(R.id.tv_battery_status);
        btnSetUserLocation = findViewById(R.id.btn_set_user_location);

        // 중앙 그리드
        btnGetMedicine = findViewById(R.id.btn_get_medicine);
        btnRobotCall = findViewById(R.id.btn_robot_call);
        btnVoiceChat = findViewById(R.id.btn_voice_chat);
        btnCleaning = findViewById(R.id.btn_cleaning);

        // 하단 카드
        btnGotoLocation = findViewById(R.id.btn_goto_location);
        btnRegisterLocation = findViewById(R.id.btn_register_location);
        btnDeleteLocation = findViewById(R.id.btn_delete_location);

        // 약 관련
        tvMedicationStatus = findViewById(R.id.tv_medication_status);

        tvCurrentHeartRate = findViewById(R.id.tv_current_heart_rate);
        btnMeasureHeartRate = findViewById(R.id.btn_measure_heart_rate);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 화면이 다시 보일 때마다 상태 업데이트 및 주기적 체크 시작
        startPeriodicStatusCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 화면이 보이지 않으면 주기적 체크 중지
        stopPeriodicStatusCheck();
    }

    private void initializeFirebase() {
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    private void setupRealtimeListeners() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
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
                String robotId = snapshot.getString("robotId");
                tvRobotInfo.setText(String.format("Care Light (%s)", robotId != null ? robotId : "연결 안됨"));
                Object stateObj = snapshot.get("robotState");
                if (stateObj instanceof Map) {
                    Map<String, Object> robotState = (Map<String, Object>) stateObj;
                    String currentLocation = (String) robotState.get("currentLocation");
                    tvRobotLocation.setText(String.format("로봇 위치: %s", currentLocation != null ? currentLocation : "알 수 없음"));

                    Number battery = (Number) robotState.get("batteryPercentage");
                    Boolean isCharging = (Boolean) robotState.get("isCharging");

                    // isCharging이 null일 경우(초기 상태 등)를 대비하여 false로 기본값 설정
                    boolean charging = (isCharging != null) && isCharging;

                    String batteryText;
                    int percentage = (battery != null) ? battery.intValue() : 0;

                    if (charging) {
                        batteryText = String.format(Locale.getDefault(), "배터리 상태: %d%% (충전 중)", percentage);
                    } else {
                        batteryText = String.format(Locale.getDefault(), "배터리 상태: %d%%", percentage);
                    }
                    tvBatteryStatus.setText(batteryText);

                    List<String> locations = (List<String>) robotState.get("savedLocations");
                    if (locations != null) {
                        this.robotLocations = locations;
                    }

                    String lastTakenDate = snapshot.getString("lastMedicationTakenDate");

                    if (lastTakenDate != null) {
                        updateLastMedicationDate(lastTakenDate);
                        updateMedicationStatusUI();
                    }

                    Number heartRate = snapshot.getLong("heartRate");
                    if (heartRate != null && heartRate.intValue() > 0) {
                        tvCurrentHeartRate.setText(String.format("현재 심박수: %d BPM", heartRate.intValue()));
                    } else {
                        tvCurrentHeartRate.setText("현재 심박수: N/A");
                    }
                }
            } else {
                Log.w(TAG, "User document not found for UID: " + userUid);
            }
        });
    }

    private void setupButtonClickListeners() {
        // 상단 카드 버튼
        btnSetUserLocation.setOnClickListener(v -> showSetUserLocationDialog());

        // 중앙 그리드 버튼
        btnGetMedicine.setOnClickListener(v -> callRobotForTask("약 전달"));

        btnRobotCall.setOnClickListener(v -> callRobotForTask("호출"));

        btnVoiceChat.setOnClickListener(v -> {
            ChatbotDialogFragment chatbotDialog = new ChatbotDialogFragment();
            chatbotDialog.show(getSupportFragmentManager(), chatbotDialog.getTag());
        });

        // 하단 카드 버튼
        btnRegisterLocation.setOnClickListener(v -> showRegisterLocationDialog());
        btnGotoLocation.setOnClickListener(v -> showLocationSelectionDialog());
        btnDeleteLocation.setOnClickListener(v -> showDeleteLocationDialog());

        btnCleaning.setOnClickListener(v -> {
            Toast.makeText(this, "ESP32에 청소 시작을 요청합니다...", Toast.LENGTH_SHORT).show();
            // ESP32의 /single_arm_cleaning 엔드포인트를 호출함.
            triggerEspAction("/manipulator_work_cleaning", "청소하기");
        });

        btnMeasureHeartRate.setOnClickListener(v -> {
            Toast.makeText(this, "ESP32로부터 심박수 정보를 가져옵니다...", Toast.LENGTH_SHORT).show();
            fetchHeartRateFromEsp();
        });
    }

    private void callRobotForTask(String taskName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String userLocation = prefs.getString("user_location", "(지정 안됨)");

        if (userLocation.equals("(지정 안됨)")) {
            Toast.makeText(this, "먼저 '사용자 위치'를 설정해주세요!", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "'" + userLocation + "'(으)로 " + taskName + "을(를) 요청했습니다.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "Calling robot for task '" + taskName + "' to location: " + userLocation);

            Map<String, Object> params = new HashMap<>();
            params.put("location", userLocation);
            params.put("angle", 0); // 로봇이 도착해서 사용자를 바라보도록 0도 설정함.
            sendCommand("goToLocation", userLocation + "(으)로 " + taskName + "을(를) 위해 이동합니다.", params);
        }
    }

    private void showSetUserLocationDialog() {
        if (robotLocations == null || robotLocations.isEmpty()) {
            Toast.makeText(this, "먼저 로봇에 장소를 등록해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        final CharSequence[] items = robotLocations.toArray(new CharSequence[0]);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("사용자 현재 위치를 선택하세요");
        builder.setItems(items, (dialog, itemIndex) -> {
            String selectedLocation = (String) items[itemIndex];
            tvUserLocation.setText(String.format("사용자 위치: %s", selectedLocation));
            saveUserLocation(selectedLocation);
            Toast.makeText(this, "사용자 위치를 '" + selectedLocation + "'(으)로 설정했습니다.", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    private void saveUserLocation(String location) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("user_location", location);
        editor.apply();
    }

    private void loadUserLocation() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedLocation = prefs.getString("user_location", "(지정 안됨)");
        tvUserLocation.setText(String.format("사용자 위치: %s", savedLocation));
    }

    private void showDeleteLocationDialog() {
        if (robotLocations == null || robotLocations.isEmpty()) {
            Toast.makeText(this, "삭제할 장소가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        final CharSequence[] items = robotLocations.toArray(new CharSequence[0]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("삭제할 장소를 선택하세요");
        builder.setItems(items, (dialog, itemIndex) -> {
            String selectedLocation = (String) items[itemIndex];
            // 사용자에게 정말 삭제할 것인지 다시 확인함.
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("장소 삭제 확인")
                    .setMessage("'" + selectedLocation + "' 장소를 정말 삭제하시겠습니까?")
                    .setPositiveButton("삭제", (deleteDialog, which) -> {
                        // "삭제"를 누르면 deleteLocation 명령 전송
                        sendCommand("deleteLocation", "위치 삭제 요청: " + selectedLocation, new HashMap<String, Object>() {{
                            put("locationName", selectedLocation);
                        }});
                    })
                    .setNegativeButton("취소", null)
                    .show();
        });
        builder.show();
    }


    private void showRegisterLocationDialog() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
            .setTitle("현재 위치 등록")
            .setMessage("로봇의 현재 위치를 저장할 이름을 입력하세요.")
            .setView(input)
            .setPositiveButton("저장", (dialog, which) -> {
                String locationName = input.getText().toString().trim();
                if (!locationName.isEmpty()) {
                    sendCommand("saveLocation", "위치 저장 요청: " + locationName, new HashMap<String, Object>() {{
                        put("locationName", locationName);
                    }});
                } else {
                    Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("취소", null)
            .show();
    }

    private void showLocationSelectionDialog() {
        if (robotLocations == null || robotLocations.isEmpty()) {
            Toast.makeText(this, "저장된 장소가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        final CharSequence[] items = robotLocations.toArray(new CharSequence[0]);
        new AlertDialog.Builder(this)
            .setTitle("이동할 장소를 선택하세요")
            .setItems(items, (dialog, itemIndex) -> {
                String selectedLocation = (String) items[itemIndex];
                sendCommand("goToLocation", selectedLocation + "(으)로 이동합니다.", new HashMap<String, Object>() {{
                    put("location", selectedLocation);
                    put("angle", 0);
                }});
            })
            .show();
    }

    public void sendCommand(String command, String message, Map<String, Object> parameters) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        String userUid = currentUser.getUid();
        Map<String, Object> commandData = new HashMap<>();
        commandData.put("command", command);
        commandData.put("message", message);
        if (parameters != null) {
            commandData.put("parameters", parameters);
        }
        commandData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users").document(userUid)
                .update("temiCommand", commandData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Command '" + command + "' sent successfully."))
                .addOnFailureListener(e -> Log.w(TAG, "Error sending command", e));
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

    private void setupHiddenFeatureListeners() {
        setupDebugClickListener();
        setupLogoutClickListener();
    }

    private void startPeriodicStatusCheck() {
        stopPeriodicStatusCheck();
        statusUpdateRunnable = () -> {
            updateMedicationStatusUI();
            statusUpdateHandler.postDelayed(statusUpdateRunnable, 60000); // 1분마다
        };
        statusUpdateHandler.post(statusUpdateRunnable);
    }

    private void stopPeriodicStatusCheck() {
        if (statusUpdateRunnable != null) {
            statusUpdateHandler.removeCallbacks(statusUpdateRunnable);
        }
    }

    private void updateMedicationStatusUI() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String lastTakenDate = prefs.getString("last_medication_date", "");
        String todayDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (todayDate.equals(lastTakenDate)) {
            updateMedicationStatus(true);
            return;
        }

        int medicationHour = prefs.getInt("medication_hour", -1);
        int medicationMinute = prefs.getInt("medication_minute", -1);

        if (medicationHour != -1) {
            Calendar now = Calendar.getInstance();
            Calendar medicationTime = Calendar.getInstance();
            medicationTime.set(Calendar.HOUR_OF_DAY, medicationHour);
            medicationTime.set(Calendar.MINUTE, medicationMinute);
            medicationTime.set(Calendar.SECOND, 0);
            if (now.after(medicationTime)) {
                updateMedicationStatus(false);
            } else {
                updateMedicationStatus(null);
            }
        } else {
            updateMedicationStatus(null);
        }
    }
    private void updateMedicationStatus(Boolean isTaken) {
        String statusText;
        if (isTaken != null && isTaken) {
            statusText = "복용 완료";
            tvMedicationStatus.setText(statusText);
            tvMedicationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else if (isTaken != null) {
            statusText = "복용 전 (시간 지남)";
            tvMedicationStatus.setText("복용 전");
            tvMedicationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else {
            statusText = "복용 전 (대기 중)";
            tvMedicationStatus.setText("복용 전");
            tvMedicationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
        }
        // Firestore에 상태 업데이트
        updateFirestoreMedicationStatus(statusText);
    }

    private void updateFirestoreMedicationStatus(String status) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;

        db.collection("users").document(currentUser.getUid())
                .update("medicationStatus", status)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Successfully updated medication status to: " + status))
                .addOnFailureListener(e -> Log.w(TAG, "Error updating medication status.", e));
    }

    private void updateLastMedicationDate(String date) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString("last_medication_date", date).apply();
    }

    private void fetchHeartRateFromEsp() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String ipAddress = prefs.getString("esp32_ip_address", "");

        if (ipAddress.isEmpty()) {
            Toast.makeText(this, "먼저 디버그 모드에서 ESP32의 IP주소를 설정해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        String requestUrl = "http://" + ipAddress + "/info";
        Request request = new Request.Builder().url(requestUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> Toast.makeText(HomeActivity.this, "ESP32 연결에 실패했습니다.", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful() || responseBody == null) {
                        mainHandler.post(() -> Toast.makeText(HomeActivity.this, "ESP32 응답 오류: " + response.code(), Toast.LENGTH_SHORT).show());
                        return;
                    }

                    String responseString = responseBody.string();
                    JSONObject jsonObject = new JSONObject(responseString);
                    int bpm = jsonObject.optInt("bpm", 0);

                    // Firestore의 heartRate 필드를 업데이트
                    FirebaseUser currentUser = mAuth.getCurrentUser();
                    if (currentUser != null && bpm > 0) {
                        db.collection("users").document(currentUser.getUid())
                                .update("heartRate", bpm)
                                .addOnSuccessListener(aVoid -> Log.d(TAG, "Heart rate updated to Firestore: " + bpm))
                                .addOnFailureListener(err -> Log.w(TAG, "Error updating heart rate", err));
                    }
                } catch (Exception e) { // IOException, JSONException 모두 처리
                    Log.e(TAG, "Failed to process response from ESP32", e);
                }
            }
        });
    }

    private void triggerEspAction(String endpoint, String taskName) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String ipAddress = prefs.getString("esp32_ip_address", "");

        if (ipAddress.isEmpty()) {
            Toast.makeText(this, "먼저 디버그 모드에서 ESP32의 IP주소를 설정해주세요.", Toast.LENGTH_LONG).show();
            return;
        }

        String requestUrl = "http://" + ipAddress + endpoint;
        Request request = new Request.Builder().url(requestUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                mainHandler.post(() -> Toast.makeText(HomeActivity.this, "ESP32 연결에 실패했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                mainHandler.post(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(HomeActivity.this, taskName + " 작업을 시작합니다.", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(HomeActivity.this, taskName + " 작업 요청에 실패했습니다: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                });
                // 응답 본문은 닫음.
                if (response.body() != null) {
                    response.body().close();
                }
            }
        });
    }
}