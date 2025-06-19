package com.belight.carelight;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.MotionEvent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DebugActivity extends AppCompatActivity {

    private static final String TAG = "DebugActivity";


    // --- UI 변수 선언 ---
    private TextInputEditText etEspIpAddress;
    private Button btnFetchEspInfo;
    private TextView tvEspName, tvEspStatus, tvEspWifiSsid, tvCurrentServoAngle, tvPulseSensorValue;
    private TextInputEditText etServoAngle;
    private Button btnSetServoAngle;
    private Button btnTemiUp, btnTemiDown, btnTemiLeft, btnTemiRight;
    private Button btnTemiUpLeft, btnTemiUpRight, btnTemiDownLeft, btnTemiDownRight;
    private Button btnTemiStop;
    private Button btnResetMedicationStatus;


    // 알람 설정 UI 변수
    private TextView tvWakeupTime, tvMedicationTime;
    private Button btnSetWakeupTime, btnSetMedicationTime, btnSendAlarmCommand;

    // 백그라운드 작업 및 통신 관련 변수
    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler periodicUpdateHandler;
    private Runnable periodicUpdateRunnable;
    private static final long UPDATE_INTERVAL_MS = 1000;
    private boolean isPollingActive = false;
    private String currentEspIpAddress = "";
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;


    // 알람 시간 저장 변수
    private int wakeupHour = -1, wakeupMinute = -1;
    private int medicationHour = -1, medicationMinute = -1;

    private final Handler temiControlHandler = new Handler(Looper.getMainLooper());
    private Runnable temiControlRunnable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        initializeUI();
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initializePeriodicUpdater();
        loadAlarmTimes();
        loadSavedData();

        setupEspControlListeners();
        setupTemiButtonListeners();
        setupAlarmControlListeners();
        setupScenarioDebugListeners();
    }

    private void initializeUI() {
        // UI 요소 초기화
        etEspIpAddress = findViewById(R.id.et_esp_ip_address);
        btnFetchEspInfo = findViewById(R.id.btn_fetch_esp_info);
        tvEspName = findViewById(R.id.tv_esp_name);
        tvEspStatus = findViewById(R.id.tv_esp_status);
        tvEspWifiSsid = findViewById(R.id.tv_esp_wifi_ssid);
        tvCurrentServoAngle = findViewById(R.id.tv_current_servo_angle);
        tvPulseSensorValue = findViewById(R.id.tv_pulse_sensor_value);
        etServoAngle = findViewById(R.id.et_servo_angle);
        btnSetServoAngle = findViewById(R.id.btn_set_servo_angle);

        btnTemiUp = findViewById(R.id.btn_temi_up);
        btnTemiDown = findViewById(R.id.btn_temi_down);
        btnTemiLeft = findViewById(R.id.btn_temi_left);
        btnTemiRight = findViewById(R.id.btn_temi_right);
        btnTemiUpLeft = findViewById(R.id.btn_temi_up_left);
        btnTemiUpRight = findViewById(R.id.btn_temi_up_right);
        btnTemiDownLeft = findViewById(R.id.btn_temi_down_left);
        btnTemiDownRight = findViewById(R.id.btn_temi_down_right);
        btnTemiStop = findViewById(R.id.btn_temi_stop);


        tvWakeupTime = findViewById(R.id.tv_wakeup_time);
        btnSetWakeupTime = findViewById(R.id.btn_set_wakeup_time);
        tvMedicationTime = findViewById(R.id.tv_medication_time);
        btnSetMedicationTime = findViewById(R.id.btn_set_medication_time);
        btnSendAlarmCommand = findViewById(R.id.btn_send_alarm_command);

        btnResetMedicationStatus = findViewById(R.id.btn_reset_medication_status);
    }


    private void startPeriodicUpdates() {
        if (!isPollingActive && !currentEspIpAddress.isEmpty()) {
            Log.d(TAG, "주기적 업데이트 시작 (1초 간격)");
            isPollingActive = true;
            // 이전 콜백이 남아있을 수 있으므로 제거 후 새로 시작
            periodicUpdateHandler.removeCallbacks(periodicUpdateRunnable);
            periodicUpdateHandler.post(periodicUpdateRunnable); // 즉시 한번 실행하고, 이후 Runnable 내부에서 반복 예약
        }
    }

    private void stopPeriodicUpdates() {
        if (isPollingActive) {
            Log.d(TAG, "주기적 업데이트 중지");
        }
        isPollingActive = false;
        periodicUpdateHandler.removeCallbacks(periodicUpdateRunnable);
    }


    @Override
    protected void onResume() {
        super.onResume();
        // IP 주소가 설정되어 있다면 주기적 업데이트 시작
        if (!currentEspIpAddress.isEmpty()) {
            startPeriodicUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 액티비티가 보이지 않을 때 주기적 업데이트 중지
        stopPeriodicUpdates();
        temiControlHandler.removeCallbacksAndMessages(null);
        sendSkidJoyCommand(0, 0); // 안전을 위해 정지 명령 전송
    }


    private boolean isValidIpAddress(String ip) {
        // (이전과 동일)
        String ipPattern = "^([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\." +
                "([01]?\\d\\d?|2[0-4]\\d|25[0-5])$";
        return ip.matches(ipPattern);
    }

    // fetchEspInfoFromServer 메소드에 isPeriodic 파라미터 추가
    private void fetchEspInfoFromServer(String ipAddress, boolean isPeriodic) {
        if (!isPeriodic) {
            mainHandler.post(() -> {
                tvEspName.setText("가져오는 중...");
                tvEspStatus.setText("가져오는 중...");
                tvEspWifiSsid.setText("가져오는 중...");
                tvCurrentServoAngle.setText("가져오는 중...");
                tvPulseSensorValue.setText("가져오는 중..."); // BPM 값도 로딩 중으로
                btnFetchEspInfo.setEnabled(false);
                btnSetServoAngle.setEnabled(false);
                Toast.makeText(DebugActivity.this, "ESP32 정보 요청 중...", Toast.LENGTH_SHORT).show();
            });
        } else {
            Log.d(TAG, "주기적 정보 요청 중...");
        }

        String requestUrl = "http://" + ipAddress + "/info";
        Request request = new Request.Builder().url(requestUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                handleNetworkError(e.getMessage(), isPeriodic);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                ResponseBody responseBody = response.body();
                String responseBodyString;
                if (responseBody == null) { return; }
                try {
                    responseBodyString = responseBody.string();
                } catch (IOException e) { return; }
                finally {
                    responseBody.close();
                }


                if (response.isSuccessful()) {
                    Log.d(TAG, "/info 응답 성공 (주기적: " + isPeriodic + "): " + responseBodyString);
                    try {
                        JSONObject jsonObject = new JSONObject(responseBodyString);
                        final String name = jsonObject.optString("name", "N/A");
                        final String status = jsonObject.optString("status", "N/A");
                        final String wifiSsid = jsonObject.optString("wifi_ssid", "N/A");
                        final int servoAngle = jsonObject.optInt("servo_angle", -1);
                        final int bpmValue = jsonObject.optInt("bpm", -1); // "pulse_sensor_value" 대신 "bpm" 키로 파싱

                        mainHandler.post(() -> {
                            tvEspName.setText(name);
                            tvEspStatus.setText(status);
                            tvEspWifiSsid.setText(wifiSsid);
                            tvCurrentServoAngle.setText(servoAngle == -1 ? "N/A" : String.valueOf(servoAngle) + "도");
                            tvPulseSensorValue.setText(bpmValue <= 0 ? "측정 중..." : String.valueOf(bpmValue) + " BPM");
                            if (!isPeriodic) {
                                Toast.makeText(DebugActivity.this, "정보 업데이트 완료!", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (JSONException e) {
                        Log.e(TAG, "/info JSON 파싱 실패: ", e);
                        handleNetworkError("데이터 형식 오류 (정보)", isPeriodic);
                    }
                } else {
                    Log.e(TAG, "/info 요청 실패: " + response.code() + " " + response.message());
                    handleNetworkError("정보 요청 실패: " + response.code(), isPeriodic);
                }

                if (!isPeriodic) {
                    mainHandler.post(() -> {
                        btnFetchEspInfo.setEnabled(true);
                        btnSetServoAngle.setEnabled(true);
                    });
                }
            }
        });
    }

    private void setServoAngleOnEsp(String ipAddress, int angle) {
        mainHandler.post(() -> {
            btnSetServoAngle.setEnabled(false);
            Toast.makeText(DebugActivity.this, angle + "도로 설정 요청 중...", Toast.LENGTH_SHORT).show();
        });

        String requestUrl = "http://" + ipAddress + "/set_servo?angle=" + angle;
        Request request = new Request.Builder().url(requestUrl).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                handleNetworkError(e.getMessage(), false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                ResponseBody responseBody = response.body();
                String responseBodyString = "";
                try {
                    if (responseBody != null) responseBodyString = responseBody.string();
                } catch (IOException e) { Log.e(TAG, "setServo 응답 본문 읽기 실패: ", e); }
                finally { if (responseBody != null) responseBody.close(); }

                if (response.isSuccessful()) {
                    Log.d(TAG, "/set_servo 응답 성공: " + responseBodyString);
                    mainHandler.post(() -> {
                        tvCurrentServoAngle.setText(String.valueOf(angle) + "도");
                        Toast.makeText(DebugActivity.this, "서보 각도 " + angle + "도로 설정 완료", Toast.LENGTH_SHORT).show();
                    });
                } else {
                    Log.e(TAG, "/set_servo 요청 실패: " + response.code() + " " + response.message());
                    handleNetworkError("서보 각도 설정 실패: " + response.code(), false);
                }
                mainHandler.post(() -> btnSetServoAngle.setEnabled(true));
            }
        });
    }

    private void handleNetworkError(String errorMessage, boolean isPeriodic) {
        mainHandler.post(() -> {
            if (!isPeriodic) {
                tvEspName.setText("오류");
                tvEspStatus.setText("오류");
                tvEspWifiSsid.setText("오류");
                tvCurrentServoAngle.setText("오류");
                tvPulseSensorValue.setText("오류");
                Toast.makeText(DebugActivity.this, "오류: " + errorMessage, Toast.LENGTH_LONG).show();
                btnFetchEspInfo.setEnabled(true);
                btnSetServoAngle.setEnabled(true);
            } else {
                Log.w(TAG, "주기적 업데이트 중 오류: " + errorMessage);
            }
        });
    }

    private void setupTemiButtonListeners() {
        // 이동 속도 설정 (0.0 ~ 1.0)
        final float SPEED = 0.7f;
        final float DIAGONAL_SPEED = (float) (SPEED / Math.sqrt(2)); // 대각선 이동 시 속도 보정

        // 방향 버튼 리스너 설정
        setupDirectionalButton(btnTemiUp, SPEED, 0);                      // 위
        setupDirectionalButton(btnTemiDown, -SPEED, 0);                   // 아래
        setupDirectionalButton(btnTemiLeft, 0, SPEED);                    // 왼쪽
        setupDirectionalButton(btnTemiRight, 0, -SPEED);                  // 오른쪽
        setupDirectionalButton(btnTemiUpLeft, DIAGONAL_SPEED, SPEED);       // 왼위
        setupDirectionalButton(btnTemiUpRight, DIAGONAL_SPEED, -SPEED);     // 오른위
        setupDirectionalButton(btnTemiDownLeft, -DIAGONAL_SPEED, SPEED);    // 왼아래
        setupDirectionalButton(btnTemiDownRight, -DIAGONAL_SPEED, -SPEED);  // 오른아래

        // 정지 버튼은 간단한 클릭 리스너로 설정
        btnTemiStop.setOnClickListener(v -> sendSkidJoyCommand(0, 0));
    }
    private void initializePeriodicUpdater() {
        periodicUpdateHandler = new Handler(Looper.getMainLooper());
        periodicUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPollingActive && !currentEspIpAddress.isEmpty()) {
                    fetchEspInfoFromServer(currentEspIpAddress, true);
                }
                if (isPollingActive) {
                    periodicUpdateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };
    }

    private void setupEspControlListeners() {
        // "정보 갱신" 버튼 클릭 리스너
        btnFetchEspInfo.setOnClickListener(v -> {
            String ipAddress = etEspIpAddress.getText() != null ? etEspIpAddress.getText().toString().trim() : "";
            if (!ipAddress.isEmpty() && isValidIpAddress(ipAddress)) {
                currentEspIpAddress = ipAddress;
                saveEspIpAddress(ipAddress); // IP 주소를 저장함.
                stopPeriodicUpdates();
                fetchEspInfoFromServer(currentEspIpAddress, false);
                startPeriodicUpdates();
            } else {
                stopPeriodicUpdates();
                Toast.makeText(DebugActivity.this, "유효한 ESP32 IP 주소를 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // "각도 설정" 버튼 클릭 리스너
        btnSetServoAngle.setOnClickListener(v -> {
            if (currentEspIpAddress.isEmpty()) {
                Toast.makeText(DebugActivity.this, "먼저 ESP32 IP 주소를 입력하고 정보 갱신을 해주세요.", Toast.LENGTH_LONG).show();
                return;
            }
            String angleStr = etServoAngle.getText() != null ? etServoAngle.getText().toString().trim() : "";
            if (angleStr.isEmpty()) {
                Toast.makeText(DebugActivity.this, "설정할 각도를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int angle = Integer.parseInt(angleStr);
                if (angle >= 0 && angle <= 180) {
                    setServoAngleOnEsp(currentEspIpAddress, angle);
                } else {
                    Toast.makeText(DebugActivity.this, "각도는 0에서 180 사이로 입력해주세요.", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(DebugActivity.this, "유효한 숫자를 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDirectionalButton(Button button, float linear, float angular) {
        if (button == null) return;

        button.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // 이전에 실행 중이던 다른 방향의 작업이 있다면 모두 중지
                    temiControlHandler.removeCallbacksAndMessages(null);

                    // 새로운 방향으로 주기적으로 이동 명령을 보내는 Runnable 생성
                    temiControlRunnable = new Runnable() {
                        @Override
                        public void run() {
                            // 이동 명령 전송
                            sendSkidJoyCommand(linear, angular);
                            // 150ms 후에 자기 자신을 다시 호출하여 반복 실행
                            temiControlHandler.postDelayed(this, 150);
                        }
                    };
                    // 생성한 작업을 즉시 시작
                    temiControlHandler.post(temiControlRunnable);
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 버튼에서 손을 떼거나 터치가 취소되면,
                    // 주기적으로 보내던 작업을 중지
                    temiControlHandler.removeCallbacks(temiControlRunnable);
                    // 로봇에 정지 명령 전송
                    sendSkidJoyCommand(0, 0);
                    break;
            }
            return true; // true를 반환하여 클릭 이벤트와 중복되지 않도록 함
        });
    }

    private void sendSkidJoyCommand(float linear, float angular) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) return;
        String userUid = currentUser.getUid();

        Map<String, Object> params = new HashMap<>();
        params.put("x", linear);
        params.put("y", angular);

        Map<String, Object> commandData = new HashMap<>();
        commandData.put("command", "skidJoy");
        commandData.put("message", String.format("SkidJoy: x=%.2f, y=%.2f", linear, angular));
        commandData.put("parameters", params);
        commandData.put("timestamp", FieldValue.serverTimestamp());

        db.collection("users").document(userUid)
                .update("temiCommand", commandData)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "skidJoy command sent successfully."))
                .addOnFailureListener(e -> Log.w(TAG, "Error sending skidJoy command", e));
    }


    // 디버그: 알람 설정 (시연을 위해서)
    private void setupAlarmControlListeners() {
        // TimePickerDialog 사용해서 구현함
        btnSetWakeupTime.setOnClickListener(v -> {
            showTimePicker("기상 시간 설정", wakeupHour, wakeupMinute, (hour, minute) -> {
                wakeupHour = hour;
                wakeupMinute = minute;
                updateAlarmTimeView(tvWakeupTime, wakeupHour, wakeupMinute);
                saveAlarmTime("wakeup_hour", hour);
                saveAlarmTime("wakeup_minute", minute);
            });
        });

        // TimePickerDialog 사용해서 구현함
        btnSetMedicationTime.setOnClickListener(v -> {
            showTimePicker("약 복용 시간 설정", medicationHour, medicationMinute, (hour, minute) -> {
                medicationHour = hour;
                medicationMinute = minute;
                updateAlarmTimeView(tvMedicationTime, medicationHour, medicationMinute);
                saveAlarmTime("medication_hour", hour);
                saveAlarmTime("medication_minute", minute);
            });
        });

        // 버튼 클릭 시 Firestore로 명령 전송함
        btnSendAlarmCommand.setOnClickListener(v -> {
            if (wakeupHour == -1 || medicationHour == -1) {
                Toast.makeText(this, "기상 시간과 약 복용 시간을 모두 설정해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("wakeupTime", String.format(Locale.US, "%02d:%02d", wakeupHour, wakeupMinute));
            params.put("medicationTime", String.format(Locale.US, "%02d:%02d", medicationHour, medicationMinute));

            sendCommand("setAlarms", "알람 시간 설정", params);

            Toast.makeText(this, "알람 시간을 로봇에게 전송했습니다.", Toast.LENGTH_SHORT).show();
        });
    }

    private void showTimePicker(String title, int initialHour, int initialMinute, OnTimeSelectedListener listener) {
        Calendar c = Calendar.getInstance();
        int currentHour = initialHour != -1 ? initialHour : c.get(Calendar.HOUR_OF_DAY);
        int currentMinute = initialMinute != -1 ? initialMinute : c.get(Calendar.MINUTE);

        TimePickerDialog timePickerDialog = new TimePickerDialog(this,
                (view, hourOfDay, minute) -> listener.onTimeSelected(hourOfDay, minute),
                currentHour, currentMinute, true); // true: 24시간 형식 사용
        timePickerDialog.setTitle(title);
        timePickerDialog.show();
    }

    private void updateAlarmTimeView(TextView textView, int hour, int minute) {
        textView.setText(String.format(Locale.US, "%02d : %02d", hour, minute));
    }

    private void saveAlarmTime(String key, int value) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putInt(key, value).apply();
    }

    // SharedPreferences에서 저장된 알람 시간을 불러오는 메소드
    private void loadAlarmTimes() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        wakeupHour = prefs.getInt("wakeup_hour", -1);
        wakeupMinute = prefs.getInt("wakeup_minute", -1);
        medicationHour = prefs.getInt("medication_hour", -1);
        medicationMinute = prefs.getInt("medication_minute", -1);

        if (wakeupHour != -1) {
            updateAlarmTimeView(tvWakeupTime, wakeupHour, wakeupMinute);
        }
        if (medicationHour != -1) {
            updateAlarmTimeView(tvMedicationTime, medicationHour, medicationMinute);
        }
    }

    // TimePickerDialog의 결과 콜백을 위한 인터페이스
    interface OnTimeSelectedListener {
        void onTimeSelected(int hour, int minute);
    }

    // Firestore에 명령을 전송하는 범용 메소드
    private void sendCommand(String command, String message, Map<String, Object> parameters) {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
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

    private void setupScenarioDebugListeners() {
        btnResetMedicationStatus.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("복용 상태 초기화")
                    .setMessage("정말 모든 복용 기록을 지우고, '복용 전' 상태로 되돌리시겠습니까?")
                    .setPositiveButton("초기화", (dialog, which) -> resetMedicationStatus())
                    .setNegativeButton("취소", null)
                    .show();
        });
    }

    // 약 복용 상태를 초기화하는 메소드 (SharedPreferences 및 Firestore)
    private void resetMedicationStatus() {
        // SharedPreferences의 마지막 복용 날짜 기록 삭제
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().remove("last_medication_date").apply();

        // Firestore의 관련 필드 삭제 및 상태 업데이트
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("medicationStatus", "복용 전 (리셋됨)");
        updates.put("lastMedicationTakenDate", FieldValue.delete()); // 필드 자체를 삭제

        db.collection("users").document(currentUser.getUid())
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "약 복용 상태가 성공적으로 초기화되었습니다.", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "Medication status reset successfully.");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "상태 초기화에 실패했습니다.", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Error resetting medication status", e);
                });
    }

    private void loadSavedData() {
        loadAlarmTimes();
        loadEspIpAddress();
    }

    // SharedPreferences에서 ESP32 IP 주소를 불러와 입력창에 설정함.
    private void loadEspIpAddress() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedIp = prefs.getString("esp32_ip_address", "");
        if (!savedIp.isEmpty()) {
            currentEspIpAddress = savedIp;
            if (etEspIpAddress != null) {
                etEspIpAddress.setText(savedIp);
            }
            // IP가 이미 있으면 바로 정보 폴링 시작
            startPeriodicUpdates();
        }
    }


    // SharedPreferences에 ESP32 IP 주소를 저장함.
    private void saveEspIpAddress(String ipAddress) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putString("esp32_ip_address", ipAddress).apply();
        Log.d(TAG, "ESP32 IP Address saved: " + ipAddress);
    }

}