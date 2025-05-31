package com.belight.carelight; // 본인의 패키지 이름으로 정확히 변경해주세요

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class DebugActivity extends AppCompatActivity {

    private static final String TAG = "DebugActivity";

    private TextInputEditText etEspIpAddress;
    private Button btnFetchEspInfo;
    private TextView tvEspName;
    private TextView tvEspStatus;
    private TextView tvEspWifiSsid;
    private TextView tvCurrentServoAngle;
    private TextView tvPulseSensorValue;
    private TextInputEditText etServoAngle;
    private Button btnSetServoAngle;

    private final OkHttpClient client = new OkHttpClient();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String currentEspIpAddress = "";

    // 주기적 업데이트를 위한 핸들러 및 Runnable
    private Handler periodicUpdateHandler;
    private Runnable periodicUpdateRunnable;
    private static final long UPDATE_INTERVAL_MS = 1000; // 1초 간격
    private boolean isPollingActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

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

        // 주기적 업데이트 핸들러 및 Runnable 초기화
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

        // "정보 갱신" 버튼 클릭 리스너
        btnFetchEspInfo.setOnClickListener(v -> {
            String ipAddress = "";
            if (etEspIpAddress.getText() != null) {
                ipAddress = etEspIpAddress.getText().toString().trim();
            }

            if (!ipAddress.isEmpty() && isValidIpAddress(ipAddress)) {
                currentEspIpAddress = ipAddress; // IP 주소 저장 및 업데이트
                stopPeriodicUpdates(); // 기존 폴링 중지 (IP가 변경될 수 있으므로)
                fetchEspInfoFromServer(currentEspIpAddress, false); // 수동 요청이므로 Toast 표시
                startPeriodicUpdates(); // 새 IP로 폴링 시작
            } else {
                stopPeriodicUpdates(); // 유효하지 않은 IP이므로 폴링 중지
                Toast.makeText(DebugActivity.this, "유효한 ESP32 IP 주소를 입력해주세요.", Toast.LENGTH_SHORT).show();
            }
        });

        // "각도 설정" 버튼 클릭 리스너 (이전과 동일)
        btnSetServoAngle.setOnClickListener(v -> {
            if (currentEspIpAddress.isEmpty()) {
                Toast.makeText(DebugActivity.this, "먼저 ESP32 IP 주소를 입력하고 정보 갱신을 해주세요.", Toast.LENGTH_LONG).show();
                return;
            }
            String angleStr = "";
            if (etServoAngle.getText() != null) {
                angleStr = etServoAngle.getText().toString().trim();
            }

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
                if (responseBody == null) { /* ... */ return; }
                try {
                    responseBodyString = responseBody.string();
                } catch (IOException e) { /* ... */ return; }
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
}