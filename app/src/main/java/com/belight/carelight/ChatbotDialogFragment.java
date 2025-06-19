package com.belight.carelight;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ChatbotDialogFragment extends BottomSheetDialogFragment {

    private static final String TAG = "ChatbotDialog";
    private RecyclerView rvChatMessages;
    private EditText etChatInput;
    private ImageButton btnSendChat;

    private ImageButton btnVoiceInput;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    private OkHttpClient client;

    private final ActivityResultLauncher<Intent> sttLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    ArrayList<String> results = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (results != null && !results.isEmpty()) {
                        String spokenText = results.get(0);

                        // 인식된 텍스트를 UI에 표시하고 즉시 API 호출
                        if (!spokenText.trim().isEmpty()) {
                            addUserMessage(spokenText);
                            addBotMessage("생각 중...");
                            setUiEnabled(false);
                            callPerplexityApi();
                        }
                    }
                }
            });


    // 마이크 권한 요청을 처리하는 런처
    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    launchStt(); // 권한이 허용되면 음성 인식 시작
                } else {
                    Toast.makeText(getContext(), "음성 입력을 사용하려면 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_chatbot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        rvChatMessages = view.findViewById(R.id.rv_chat_messages);
        etChatInput = view.findViewById(R.id.et_chat_input);
        btnSendChat = view.findViewById(R.id.btn_send_chat);
        btnVoiceInput = view.findViewById(R.id.btn_voice_input);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);

        addBotMessage("안녕하세요, 저는 어르신의 다정한 대화 친구 'Care Light'예요. 무엇을 도와드릴까요?");

        btnSendChat.setOnClickListener(v -> sendMessage());

        btnVoiceInput.setOnClickListener(v -> checkPermissionAndStartStt());
    }

    private void sendMessage() {
        String messageText = etChatInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            addUserMessage(messageText);
            etChatInput.setText("");
            addBotMessage("생각 중...");
            setUiEnabled(false);
            callPerplexityApi();
        }
    }

    private void checkPermissionAndStartStt() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // 권한이 이미 허용된 경우
            launchStt();
        } else {
            // 권한이 없는 경우, 권한 요청
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    // 안드로이드 내장 음성 인식 액티비티를 실행하는 메소드
    private void launchStt() {
        Intent sttIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        sttIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN);
        sttIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "말씀해주세요...");
        try {
            sttLauncher.launch(sttIntent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "음성 인식을 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void callPerplexityApi() {
        if (BuildConfig.PERPLEXITY_API_KEY == null || BuildConfig.PERPLEXITY_API_KEY.isEmpty()) {
            handleApiError("API 키가 설정되지 않았습니다. local.properties 파일을 확인해주세요.");
            return;
        }

        MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
        JSONObject jsonBody;

        try {
            // PromptFormatter를 사용해 전체 대화 기록을 바탕으로 요청 본문을 생성함.
            jsonBody = PromptFormatter.createApiRequestBody(messageList);
        } catch (JSONException e) {
            Log.e(TAG, "JSON Exception: ", e);
            handleApiError("요청 생성 중 오류가 발생했습니다.");
            return;
        }

        RequestBody body = RequestBody.create(jsonBody.toString(), mediaType);
        Request request = new Request.Builder()
                .url("https://api.perplexity.ai/chat/completions")
                .post(body)
                .addHeader("accept", "application/json")
                .addHeader("authorization", "Bearer " + BuildConfig.PERPLEXITY_API_KEY)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                handleApiError("네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (ResponseBody responseBody = response.body()) {
                    String responseString = (responseBody != null) ? responseBody.string() : "";
                    if (!response.isSuccessful()) {
                        Log.e(TAG, "API Call Unsuccessful: " + response.code());
                        Log.e(TAG, "Error Body: " + responseString);
                        handleApiError("API 호출에 실패했습니다. (코드: " + response.code() + ")");
                        return;
                    }

                    JSONObject jsonResponse = new JSONObject(responseString);
                    String botResponse = jsonResponse.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content");

                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            // 챗봇 UI에 봇의 답변을 업데이트함.
                            updateLastBotMessage(botResponse);
                            setUiEnabled(true);

                            // 로봇의 답변을 음성으로 말하도록 command를 보냄
                            if (getActivity() instanceof HomeActivity) {
                                ((HomeActivity) getActivity()).sendCommand("speak", botResponse, null);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Response Parsing Error: ", e);
                    handleApiError("응답 해석 중 오류가 발생했습니다.");
                }
            }
        });
    }

    private void handleApiError(String errorMessage) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                updateLastBotMessage(errorMessage);
                setUiEnabled(true);
            });
        }
    }

    private void addUserMessage(String message) {
        messageList.add(new ChatMessage(message, true));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.scrollToPosition(messageList.size() - 1);
    }

    private void addBotMessage(String message) {
        messageList.add(new ChatMessage(message, false));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        rvChatMessages.scrollToPosition(messageList.size() - 1);
    }

    private void updateLastBotMessage(String message) {
        if (!messageList.isEmpty()) {
            int lastIndex = messageList.size() - 1;
            ChatMessage lastMessage = messageList.get(lastIndex);
            if (!lastMessage.isUser()) {
                messageList.set(lastIndex, new ChatMessage(message, false));
                chatAdapter.notifyItemChanged(lastIndex);
            } else {
                addBotMessage(message);
            }
        }
    }

    private void setUiEnabled(boolean enabled) {
        etChatInput.setEnabled(enabled);
        btnSendChat.setEnabled(enabled);
    }
}