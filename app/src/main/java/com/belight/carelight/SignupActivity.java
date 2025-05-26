package com.belight.carelight; // 실제 패키지명으로 되어 있는지 확인

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private static final String TAG = "SignupActivity";

    private EditText editTextSignupEmail;
    private EditText editTextSignupPassword;
    private EditText editTextSignupConfirmPassword;
    private Button buttonSignupSubmit;
    private TextView textViewGoToLogin;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_signup);

        // Firebase 인스턴스 초기화
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // UI 요소 참조
        editTextSignupEmail = findViewById(R.id.et_signup_email);
        editTextSignupPassword = findViewById(R.id.et_signup_password);
        editTextSignupConfirmPassword = findViewById(R.id.et_signup_confirm_password);
        buttonSignupSubmit = findViewById(R.id.btn_signup_submit);
        textViewGoToLogin = findViewById(R.id.tv_go_to_login_from_signup);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Feat: 가입하기
        buttonSignupSubmit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSignUp();
            }
        });

        // Feat: Login Activity로 이동
        textViewGoToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // 현재 activity 종료하면 이전 activity로 이동됨
            }
        });
    }

    private void performSignUp() {
        String email = editTextSignupEmail.getText().toString().trim();
        String password = editTextSignupPassword.getText().toString().trim();
        String confirmPassword = editTextSignupConfirmPassword.getText().toString().trim();

        // Feat: 입력 값 유효성 검사
        if (TextUtils.isEmpty(email)) {
            editTextSignupEmail.setError("이메일을 입력해주세요.");
            editTextSignupEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            editTextSignupPassword.setError("비밀번호를 입력해주세요.");
            editTextSignupPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            editTextSignupPassword.setError("비밀번호는 6자 이상이어야 합니다.");
            editTextSignupPassword.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(confirmPassword)) {
            editTextSignupConfirmPassword.setError("비밀번호 확인을 입력해주세요.");
            editTextSignupConfirmPassword.requestFocus();
            return;
        }
        if (!password.equals(confirmPassword)) {
            editTextSignupConfirmPassword.setError("비밀번호가 일치하지 않습니다.");
            editTextSignupConfirmPassword.requestFocus();
            // 두 비밀번호 필드 초기화 또는 한쪽만
            editTextSignupPassword.setText("");
            editTextSignupConfirmPassword.setText("");
            return;
        }

        // Feat: Firebase Authentication으로 사용자 생성
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "createUserWithEmail:success");
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            if (firebaseUser != null) {
                                // Firestore에 사용자 정보 저장
                                saveUserProfileToFirestore(firebaseUser.getUid(), email);
                            } else {
                                Toast.makeText(SignupActivity.this, "사용자 정보를 가져오는데 실패했습니다.", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w(TAG, "createUserWithEmail:failure", task.getException());
                            Toast.makeText(SignupActivity.this, "회원가입 실패: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // Feat: Firebase에 data 저장
    private void saveUserProfileToFirestore(String userId, String email) {
        Map<String, Object> userProfile = new HashMap<>();
        userProfile.put("userID", userId); // Firebase Auth UID
        userProfile.put("accountEmail", email);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss z", Locale.getDefault());
        String formattedDate = sdf.format(new Date());
        userProfile.put("createdAt", formattedDate);
        userProfile.put("updatedAt", formattedDate);

        db.collection("users").document(userId) // 문서 ID를 사용자의 UID로 설정
                .set(userProfile)
                .addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Log.d(TAG, "User profile successfully written to Firestore.");
                        Toast.makeText(SignupActivity.this, "회원가입 및 프로필 저장 성공!", Toast.LENGTH_SHORT).show();

                        Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                        // 로그인 화면으로 돌아갈 때 이전 액티비티 스택을 정리
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra("EMAIL_FROM_SIGNUP", email); // 로그인 화면에 이메일 자동 채움
                        startActivity(intent);
                        finish();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Log.w(TAG, "Error writing user profile to Firestore", e);
                        Toast.makeText(SignupActivity.this, "프로필 저장 실패: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }
}