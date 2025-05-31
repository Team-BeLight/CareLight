package com.belight.carelight;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "HomeActivity"; // 로그 태그

    private TextView robotInfoText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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

        // TextView 참조
        robotInfoText = findViewById(R.id.tv_robot_info);

        // 사용자 데이터 로드 및 표시
        loadAndDisplayUserData();
    }

    private void loadAndDisplayUserData() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            String userUid = currentUser.getUid(); // 현재 로그인된 사용자의 UID 가져오기
            Log.d(TAG, "Current user UID: " + userUid);

            DocumentReference userDocRef = db.collection("users").document(userUid);

            userDocRef.get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            if (documentSnapshot.exists()) {
                                Log.d(TAG, "User document found for UID: " + userUid);
                                String robotId = documentSnapshot.getString("robotId");
                                String robotStatus = documentSnapshot.getString("robotStatus");

                                // Firestore에서 가져온 값이 null일 경우 기본값 처리
                                if (robotId == null) {
                                    robotId = "정보 없음"; // 또는 "unknown" 등
                                    Log.w(TAG, "'robotId' field is null or missing.");
                                }
                                if (robotStatus == null) {
                                    robotStatus = "정보 없음"; // 또는 "unknown" 등
                                    Log.w(TAG, "'robotStatus' field is null or missing.");
                                }

                                String displayText = String.format(Locale.getDefault(), "%s 상태: %s", robotId, robotStatus);
                                robotInfoText.setText(displayText);
                                Log.d(TAG, "Displaying data: " + displayText);

                            } else {
                                Log.w(TAG, "No such document for user UID: " + userUid);
                                robotInfoText.setText("사용자 프로필 정보를 찾을 수 없습니다.");
                                // 이 경우는 SignupProfileActivity에서 데이터 저장이 제대로 안됐을 수 있습니다.
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.e(TAG, "Error fetching user document for UID: " + userUid, e);
                            robotInfoText.setText("정보 로딩 실패");
                            Toast.makeText(HomeActivity.this, "사용자 정보 로딩에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        }
                    });

        } else {
            // 사용자가 로그인되어 있지 않은 경우 (이론적으로 TitleActivity에서 처리되어야 함)
            Log.e(TAG, "User is not logged in. Redirecting to LoginActivity.");
            robotInfoText.setText("로그인이 필요합니다.");
            // LoginActivity로 리디렉션
            Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}