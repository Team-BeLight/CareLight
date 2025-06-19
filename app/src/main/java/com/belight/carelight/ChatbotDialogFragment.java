package com.belight.carelight;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    private OkHttpClient client;

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

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);

        addBotMessage("안녕하세요, 저는 어르신의 다정한 대화 친구 'Care Light'예요. 무엇을 도와드릴까요?");

        btnSendChat.setOnClickListener(v -> sendMessage());
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
                            updateLastBotMessage(botResponse);
                            setUiEnabled(true);
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