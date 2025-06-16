package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FieldValue;
//import com.google.firebase.firestore.FieldValue;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class SignupProfileActivity extends AppCompatActivity {

    private static final String TAG = "SignupProfileActivity";

    private EditText etProfileName, etProfileAge, etProfilePhoneNumber;
    private EditText etEmergencyName, etEmergencyRelation, etEmergencyPhone;
    private Spinner spinnerHealthStatus, spinnerRobotId, spinnerRobotStatus;
    private EditText etProfileHeartRate;
    private Button btnCompleteSignup;

    private FirebaseFirestore db;
    private String firebaseAuthUid; // Firebase Auth에서 넘어온 UID (문서 ID로 사용)
    private String userEmail;

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{3}-\\d{3,4}-\\d{4}$");

    // 콜백 인터페이스 정의
    interface OnCustomIdGeneratedListener {
        void onSuccess(String customId);
        void onFailure(Exception e);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup_profile);

        db = FirebaseFirestore.getInstance();

        Intent intent = getIntent();
        firebaseAuthUid = intent.getStringExtra("USER_UID"); // Firebase Auth UID
        userEmail = intent.getStringExtra("USER_EMAIL");

        if (firebaseAuthUid == null || userEmail == null) {
            Toast.makeText(this, "사용자 정보를 가져오지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Auth UID or Email is null. Auth UID: " + firebaseAuthUid + ", Email: " + userEmail);
            finish();
            return;
        }

        initializeUI();
        setupSpinners();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profile_main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        btnCompleteSignup.setOnClickListener(v -> {
            if (validateInputs()) {

                btnCompleteSignup.setEnabled(false); // 중복 클릭 방지
                Toast.makeText(this, "ID 생성 중...", Toast.LENGTH_SHORT).show();

                getNextCustomUserId(new OnCustomIdGeneratedListener() {
                    @Override
                    public void onSuccess(String customUserId) {
                        saveProfileDataToFirestore(customUserId);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // 로딩 표시 종료
                        btnCompleteSignup.setEnabled(true);
                        Log.e(TAG, "Failed to generate custom user ID", e);
                        Toast.makeText(SignupProfileActivity.this, "사용자 ID 생성 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void initializeUI() {
        etProfileName = findViewById(R.id.et_profile_name);
        etProfileAge = findViewById(R.id.et_profile_age);
        etProfilePhoneNumber = findViewById(R.id.et_profile_phone_number);
        etEmergencyName = findViewById(R.id.et_emergency_name);
        etEmergencyRelation = findViewById(R.id.et_emergency_relation);
        etEmergencyPhone = findViewById(R.id.et_emergency_phone);
        spinnerHealthStatus = findViewById(R.id.spinner_health_status);
        etProfileHeartRate = findViewById(R.id.et_profile_heart_rate);
        spinnerRobotId = findViewById(R.id.spinner_robot_id);
        spinnerRobotStatus = findViewById(R.id.spinner_robot_status);
        btnCompleteSignup = findViewById(R.id.btn_complete_signup);
    }

    private void setupSpinners() {
        ArrayAdapter<CharSequence> healthAdapter = ArrayAdapter.createFromResource(this,
                R.array.health_status_options, android.R.layout.simple_spinner_item);
        healthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHealthStatus.setAdapter(healthAdapter);
        spinnerHealthStatus.setSelection(getIndex(spinnerHealthStatus, "Normal"));


        List<String> robotIdOptions = List.of("unknown");
        ArrayAdapter<String> robotIdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, robotIdOptions);
        robotIdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRobotId.setAdapter(robotIdAdapter);
        spinnerRobotId.setSelection(0);


        List<String> robotStatusOptions = List.of("unknown");
        ArrayAdapter<String> robotStatusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, robotStatusOptions);
        robotStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRobotStatus.setAdapter(robotStatusAdapter);
        spinnerRobotStatus.setSelection(0);

    }

    private int getIndex(Spinner spinner, String myString) {
        for (int i=0;i<spinner.getCount();i++){
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(myString)){
                return i;
            }
        }
        return 0;
    }

    private boolean validateInputs() {
        if (TextUtils.isEmpty(etProfileName.getText().toString().trim())) {
            etProfileName.setError("이름을 입력해주세요.");
            etProfileName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(etProfileAge.getText().toString().trim())) {
            etProfileAge.setError("나이를 입력해주세요.");
            etProfileAge.requestFocus();
            return false;
        }
        try {
            Integer.parseInt(etProfileAge.getText().toString().trim());
        } catch (NumberFormatException e) {
            etProfileAge.setError("유효한 나이를 입력해주세요.");
            etProfileAge.requestFocus();
            return false;
        }

        String phoneNumber = etProfilePhoneNumber.getText().toString().trim();
        if (TextUtils.isEmpty(phoneNumber)) {
            etProfilePhoneNumber.setError("휴대폰 번호를 입력해주세요.");
            etProfilePhoneNumber.requestFocus();
            return false;
        }
        if (!PHONE_PATTERN.matcher(phoneNumber).matches()) {
            etProfilePhoneNumber.setError("유효한 휴대폰 번호 형식(010-0000-0000)으로 입력해주세요.");
            etProfilePhoneNumber.requestFocus();
            return false;
        }


        // 비상 연락처 (최소 1개 필수)
        if (TextUtils.isEmpty(etEmergencyName.getText().toString().trim())) {
            etEmergencyName.setError("비상 연락처 이름을 입력해주세요.");
            etEmergencyName.requestFocus();
            return false;
        }
        if (TextUtils.isEmpty(etEmergencyRelation.getText().toString().trim())) {
            etEmergencyRelation.setError("비상 연락처 관계를 입력해주세요.");
            etEmergencyRelation.requestFocus();
            return false;
        }
        String emergencyPhoneNumber = etEmergencyPhone.getText().toString().trim();
        if (TextUtils.isEmpty(emergencyPhoneNumber)) {
            etEmergencyPhone.setError("비상 연락처 번호를 입력해주세요.");
            etEmergencyPhone.requestFocus();
            return false;
        }
        if (!PHONE_PATTERN.matcher(emergencyPhoneNumber).matches()) {
            etEmergencyPhone.setError("유효한 휴대폰 번호 형식(010-0000-0000)으로 입력해주세요.");
            etEmergencyPhone.requestFocus();
            return false;
        }


        if (TextUtils.isEmpty(etProfileHeartRate.getText().toString().trim())) {
            etProfileHeartRate.setError("초기 심박수를 입력해주세요 (기본 0).");
            etProfileHeartRate.requestFocus();
            return false;
        }
        try {
            Integer.parseInt(etProfileHeartRate.getText().toString().trim());
        } catch (NumberFormatException e) {
            etProfileHeartRate.setError("유효한 심박수를 입력해주세요.");
            etProfileHeartRate.requestFocus();
            return false;
        }
        return true;
    }

    // 새로운 USR-XXXXX ID를 생성하는 메소드 (id 필드)
    private void getNextCustomUserId(OnCustomIdGeneratedListener listener) {
        final DocumentReference counterRef = db.collection("counters").document("userCounter");

        db.runTransaction((Transaction.Function<String>) transaction -> {
                    DocumentSnapshot counterSnapshot = transaction.get(counterRef);
                    long nextNumber = 1; // 카운터 필드가 없을 경우 기본 시작 번호

                    if (counterSnapshot.exists()) {
                        Long lastNumber = counterSnapshot.getLong("lastAssignedNumber");
                        if (lastNumber != null) {
                            nextNumber = lastNumber + 1;
                        }
                    }

                    // 카운터 업데이트 or 새로 생성
                    Map<String, Object> newCounterValue = new HashMap<>();
                    newCounterValue.put("lastAssignedNumber", nextNumber);
                    transaction.set(counterRef, newCounterValue); // set으로 하면 문서가 없어도 생성됨

                    return String.format(Locale.US, "USR-%05d", nextNumber);
                }).addOnSuccessListener(listener::onSuccess)
                .addOnFailureListener(listener::onFailure);
    }


    private void saveProfileDataToFirestore(String customUserId) { // 파라미터로 customUserId 받음
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("authUid", firebaseAuthUid); // Firebase Auth UID 저장 (내부 식별용)
        userProfile.put("id", customUserId);    // 새로 생성된 USR-XXXXX 형식 ID 저장
        userProfile.put("accountEmail", userEmail);
        userProfile.put("name", etProfileName.getText().toString().trim());
        userProfile.put("age", Integer.parseInt(etProfileAge.getText().toString().trim()));
        userProfile.put("phoneNumber", etProfilePhoneNumber.getText().toString().trim());

        Map<String, String> emergencyContact1 = new HashMap<>();
        emergencyContact1.put("name", etEmergencyName.getText().toString().trim());
        emergencyContact1.put("relation", etEmergencyRelation.getText().toString().trim());
        emergencyContact1.put("phone", etEmergencyPhone.getText().toString().trim());
        List<Map<String, String>> emergencyContactsList = new ArrayList<>();
        emergencyContactsList.add(emergencyContact1);
        userProfile.put("emergencyContacts", emergencyContactsList);

        userProfile.put("healthStatus", spinnerHealthStatus.getSelectedItem().toString());
        userProfile.put("heartRate", Integer.parseInt(etProfileHeartRate.getText().toString().trim()));
        userProfile.put("lastActivity", "신규 가입됨");
        userProfile.put("robotId", spinnerRobotId.getSelectedItem().toString());
        userProfile.put("robotStatus", spinnerRobotStatus.getSelectedItem().toString());
        userProfile.put("needAttention", false);
        userProfile.put("status", "Normal");

        // 통신을 위한 temiCommand 필드 초기값 설정
        Map<String, Object> initialCommand = new HashMap<>();
        initialCommand.put("command", "none");
        initialCommand.put("message", "No command");
        initialCommand.put("timestamp", FieldValue.serverTimestamp());
        userProfile.put("temiCommand", initialCommand);

        // 로봇 상태 보고를 위한 robotState 필드 초기값 설정
        Map<String, Object> initialRobotState = new HashMap<>();
        initialRobotState.put("currentLocation", "Not available"); // 현재 위치
        initialRobotState.put("batteryPercentage", -1); // 배터리 퍼센트
        initialRobotState.put("statusMessage", "Initializing"); // 로봇 상태 메시지 (예: "충전 중", "경로 이동 중")
        initialRobotState.put("savedLocations", new ArrayList<String>()); // 저장된 위치 목록 (빈 리스트로 초기화)
        userProfile.put("robotState", initialRobotState);

        // createdAt, updatedAt 설정
        userProfile.put("createdAt", FieldValue.serverTimestamp());
        userProfile.put("updatedAt", FieldValue.serverTimestamp());

        // Firestore에 저장 (문서 ID는 Firebase Auth UID를 사용)
        db.collection("users").document(firebaseAuthUid) // 문서 ID는 firebaseAuthUid 사용
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    // 로딩 표시 종료
                    btnCompleteSignup.setEnabled(true);
                    Log.d(TAG, "User profile with initial command successfully written!");
                    Toast.makeText(SignupProfileActivity.this, "회원가입이 완료되었습니다!", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(SignupProfileActivity.this, LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    // 로딩 표시 종료
                    btnCompleteSignup.setEnabled(true);
                    Log.w(TAG, "Error writing user profile to Firestore for AuthUID: " + firebaseAuthUid, e);
                    Toast.makeText(SignupProfileActivity.this, "프로필 저장에 실패했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}