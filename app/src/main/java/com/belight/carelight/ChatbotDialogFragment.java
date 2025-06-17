package com.belight.carelight;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.util.ArrayList;
import java.util.List;

public class ChatbotDialogFragment extends BottomSheetDialogFragment {

    private RecyclerView rvChatMessages;
    private EditText etChatInput;
    private ImageButton btnSendChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_chatbot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvChatMessages = view.findViewById(R.id.rv_chat_messages);
        etChatInput = view.findViewById(R.id.et_chat_input);
        btnSendChat = view.findViewById(R.id.btn_send_chat);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);

        // 시작 메시지 추가
        addBotMessage("안녕하세요! 무엇을 도와드릴까요?");

        btnSendChat.setOnClickListener(v -> sendMessage());
    }

    private void sendMessage() {
        String messageText = etChatInput.getText().toString().trim();
        if (!messageText.isEmpty()) {
            // 사용자 메시지 추가
            addUserMessage(messageText);
            etChatInput.setText("");

            // TODO: 여기에 Perplexity API 호출 로직을 추가해야 함. 사용법은 Docs 봐야할 듯.
            // 지금은 임시로 봇 응답을 시뮬레이션함.
            simulateBotResponse(messageText);
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

    private void simulateBotResponse(String userMessage) {
        // 1초 후에 봇이 응답하는 것처럼 보이게 함.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            addBotMessage("'" + userMessage + "'에 대한 응답입니다. API 연동이 필요합니다.");
        }, 1000);
    }
}