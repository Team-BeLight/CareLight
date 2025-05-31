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
import com.google.firebase.firestore.FirebaseFirestore;

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
    private String userUid;
    private String userEmail;

    // 전화번호 유효성 검사를 위한 정규 표현식
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\d{3}-\\d{3,4}-\\d{4}$");


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup_profile);

        db = FirebaseFirestore.getInstance();

        // Intent로부터 UID와 이메일 받기
        Intent intent = getIntent();
        userUid = intent.getStringExtra("USER_UID");
        userEmail = intent.getStringExtra("USER_EMAIL");

        if (userUid == null || userEmail == null) {
            Toast.makeText(this, "사용자 정보를 가져오지 못했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "UID or Email is null. UID: " + userUid + ", Email: " + userEmail);
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
                saveProfileDataToFirestore();
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
        // 초기 건강 상태 스피너 설정 (기존 코드 유지)
        ArrayAdapter<CharSequence> healthAdapter = ArrayAdapter.createFromResource(this,
                R.array.health_status_options, android.R.layout.simple_spinner_item);
        healthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerHealthStatus.setAdapter(healthAdapter);
        // 필요시 healthStatus의 기본값 설정:
        // spinnerHealthStatus.setSelection(getIndex(spinnerHealthStatus, "Normal")); // "Normal"이 arrays.xml에 정의된 값과 일치해야 함

        // 로봇 ID 스피너 설정: "unknown"만 포함하도록 변경
        List<String> robotIdOptions = List.of("unknown"); // "unknown"만 리스트에 추가
        ArrayAdapter<String> robotIdAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, robotIdOptions);
        robotIdAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRobotId.setAdapter(robotIdAdapter);
        spinnerRobotId.setSelection(0); // 유일한 아이템이므로 항상 "unknown"이 선택됨
        // spinnerRobotId.setEnabled(false); // 선택 사항: 사용자가 다른 값을 선택할 수 없도록 스피너를 비활성화할 수 있습니다.

        // 로봇 상태 스피너 설정: "unknown"만 포함하도록 변경
        // R.array.robot_status_options 대신 프로그래밍 방식으로 리스트 생성
        List<String> robotStatusOptions = List.of("unknown"); // "unknown"만 리스트에 추가 (DB 저장 값과 일관되게 소문자로)
        ArrayAdapter<String> robotStatusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, robotStatusOptions);
        robotStatusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRobotStatus.setAdapter(robotStatusAdapter);
        spinnerRobotStatus.setSelection(0); // 유일한 아이템이므로 항상 "unknown"이 선택됨
        // spinnerRobotStatus.setEnabled(false); // 선택 사항: 사용자가 다른 값을 선택할 수 없도록 스피너를 비활성화할 수 있습니다.
    }

    // 스피너에서 특정 값의 인덱스를 찾는 헬퍼 메소드
    private int getIndex(Spinner spinner, String myString){
        for (int i=0;i<spinner.getCount();i++){
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(myString)){
                return i;
            }
        }
        return 0; // 기본값 또는 찾지 못했을 경우
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

        // 스피너 값은 기본값이 있으므로 별도 빈 값 체크는 생략 가능 (선택에 따라)
        return true;
    }

    private void saveProfileDataToFirestore() {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("userID", userUid); // Firebase Auth UID
        userProfile.put("accountEmail", userEmail);
        userProfile.put("name", etProfileName.getText().toString().trim());
        userProfile.put("age", Integer.parseInt(etProfileAge.getText().toString().trim()));
        userProfile.put("phoneNumber", etProfilePhoneNumber.getText().toString().trim());

        // 비상 연락처 (우선 하나만)
        Map<String, String> emergencyContact1 = new HashMap<>();
        emergencyContact1.put("name", etEmergencyName.getText().toString().trim());
        emergencyContact1.put("relation", etEmergencyRelation.getText().toString().trim());
        emergencyContact1.put("phone", etEmergencyPhone.getText().toString().trim());
        // 여러 개일 경우 List<Map<String, String>> 형태로 저장
        List<Map<String, String>> emergencyContactsList = new ArrayList<>();
        emergencyContactsList.add(emergencyContact1);
        userProfile.put("emergencyContacts", emergencyContactsList);


        userProfile.put("healthStatus", spinnerHealthStatus.getSelectedItem().toString()); // 기본 "Normal"
        userProfile.put("heartRate", Integer.parseInt(etProfileHeartRate.getText().toString().trim())); // 기본 0
        userProfile.put("lastActivity", "신규 가입됨");
        userProfile.put("robotId", spinnerRobotId.getSelectedItem().toString()); // 기본 "unknown"
        userProfile.put("robotStatus", spinnerRobotStatus.getSelectedItem().toString()); // 기본 "unknown"
        userProfile.put("needAttention", false); // 예시 기본값 (필요시 조정)
        userProfile.put("status", "Normal"); // 예시 기본값 (필요시 조정)


        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss z", Locale.getDefault());
        String formattedDate = sdf.format(new Date());
        userProfile.put("createdAt", formattedDate);
        userProfile.put("updatedAt", formattedDate);
        // 또는 서버 타임스탬프 사용:
        // userProfile.put("createdAt", FieldValue.serverTimestamp());
        // userProfile.put("updatedAt", FieldValue.serverTimestamp());

        // Firestore에 저장
        db.collection("users").document(userUid)
                .set(userProfile)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "User profile successfully written to Firestore for UID: " + userUid);
                    Toast.makeText(SignupProfileActivity.this, "회원가입이 완료되었습니다!", Toast.LENGTH_LONG).show();
                    // 메인 액티비티 또는 로그인 액티비티로 이동
                    Intent intent = new Intent(SignupProfileActivity.this, LoginActivity.class); // 또는 MainActivity.class
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK); // 모든 이전 액티비티 종료
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Error writing user profile to Firestore for UID: " + userUid, e);
                    Toast.makeText(SignupProfileActivity.this, "프로필 저장에 실패했습니다: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }
}