package com.belight.carelight;

import android.os.Bundle;

import android.content.Intent;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.util.Log;
import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.AuthResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private TextView clickableTextView;

    private static final String TAG = "LoginActivity";

    private EditText editTextEmailAddress;
    private EditText editTextPassword;
    private Button buttonLogin;
    private Button buttonSignup;
    private FirebaseFirestore db;
    private FirebaseAuth mAuth; // FirebaseAuth 인스턴스 변수




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        // Firebase Firestore 인스턴스 가져오기
        db = FirebaseFirestore.getInstance();

        // UI 요소 참조 (activity_login.xml의 ID와 일치해야 함)
        editTextEmailAddress = findViewById(R.id.et_email_address);
        editTextPassword = findViewById(R.id.et_pw);
        buttonLogin = findViewById(R.id.btn_login);
        buttonSignup = findViewById(R.id.btn_sign_up);

        mAuth = FirebaseAuth.getInstance();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Feat: Login
        buttonLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String email = editTextEmailAddress.getText().toString().trim();
                String password = editTextPassword.getText().toString().trim();

                if (email.isEmpty() || password.isEmpty()) {
                    Toast.makeText(LoginActivity.this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                firebaseAuthLogin(email, password);
            }
        });

        // Feat: 비밀번호 찾기 참조하기
        clickableTextView = findViewById(R.id.tv_find_pw);
        clickableTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 비밀번호 찾기 텍스트 클릭 되었을 때
                Toast.makeText(LoginActivity.this, "텍스트뷰가 클릭됨!", Toast.LENGTH_SHORT).show();
            }
        });


        // Feat: 회원가입
        buttonSignup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignupAuthActivity.class);
                startActivity(intent);
            }
        });


    }
    private void firebaseAuthLogin(String email, String password) {
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            // 로그인 성공
                            Log.d(TAG, "signInWithEmail:success");
                            FirebaseUser user = mAuth.getCurrentUser();
                            Toast.makeText(LoginActivity.this, "Firebase 인증 성공!", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
                            intent.putExtra("USER_UID", user.getUid());
                            startActivity(intent);
                            finish();
                        } else {
                            // 로그인 실패
                            Log.w(TAG, "signInWithEmail:failure", task.getException());
                            Toast.makeText(LoginActivity.this, "Firebase 인증 실패: " + task.getException().getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

}