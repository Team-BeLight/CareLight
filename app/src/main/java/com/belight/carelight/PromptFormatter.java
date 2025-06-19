package com.belight.carelight;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class PromptFormatter {
    private static JSONObject createSystemMessage() throws JSONException {
        JSONObject systemMessage = new JSONObject();
        systemMessage.put("role", "system");

        // 챗봇의 페르소나와 행동 지침을 상세하게 정의함.
        String content = "당신은 'Care Light'라는 이름을 가진, 어르신을 위한 따뜻한 대화 친구 로봇입니다. " +
                "당신의 최우선 목표는 정보를 검색하여 제공하는 것이 아니라, 친근하고 상냥한 손주나 손녀처럼 다정하게 대화를 나누는 것입니다. " +
                "다음 규칙을 반드시, 예외 없이 지켜주세요: " +
                "1. 항상 존댓말을 사용하고, 상대방을 존중하는 부드러운 말투('-요', '-니다' 체)를 사용하세요. " +
                "2. 간단한 인사에는 절대로 사전적 의미나 어원을 설명하지 말고, 실제 사람처럼 자연스럽게 안부를 물으며 대화를 시작해야 합니다. " +
                "3. 대화가 끊기지 않도록 가끔씩 어르신의 안부를 묻거나 관련된 가벼운 질문을 던져주세요. " +
                "4. 어려운 단어 대신, 이해하기 쉬운 단어를 사용해 간결하게 답변하세요. " +
                "5. 절대로 응답에 `**`와 같은 마크다운 서식이나 `[1]`, `[2]`와 같은 인용 번호를 포함하지 마세요. 모든 응답은 순수한 텍스트여야 합니다.";

        systemMessage.put("content", content);
        return systemMessage;
    }

    /**
     * API 요청 본문(Body) 전체를 생성함.
     * @param fullMessageHistory 전체 대화를 기록함.
     * @return API 요청에 사용할 JSONObject 사용함.
     */
    public static JSONObject createApiRequestBody(List<ChatMessage> fullMessageHistory) throws JSONException {
        JSONObject jsonBody = new JSONObject();
        JSONArray messagesArray = new JSONArray();

        // 시스템 메시지를 추가함.
        messagesArray.put(createSystemMessage());

        // 모델에게 올바른 대화의 예시를 직접 보여줌 (Few-Shot Prompting 기법을 적용해 봄. -> 적용 안 하면 봇이 행동 지침에 위배되는 답변을 간혹가다가 출력하는 경우가 존재함.)
        JSONObject exampleUserMessage = new JSONObject();
        exampleUserMessage.put("role", "user");
        exampleUserMessage.put("content", "안녕");
        messagesArray.put(exampleUserMessage);

        JSONObject exampleBotMessage = new JSONObject();
        exampleBotMessage.put("role", "assistant");
        exampleBotMessage.put("content", "안녕하세요, 어르신! 오늘 하루는 어떠셨어요?");
        messagesArray.put(exampleBotMessage);


        // 실제 대화 기록을 추가함.
        // 첫 인사말과 '생각 중...' 메시지는 제외함.
        for (int i = 1; i < fullMessageHistory.size(); i++) {
            ChatMessage chatMessage = fullMessageHistory.get(i);
            if (chatMessage.getMessage().equals("생각 중...")) continue;

            JSONObject messageJson = new JSONObject();
            messageJson.put("role", chatMessage.isUser() ? "user" : "assistant");
            messageJson.put("content", chatMessage.getMessage());
            messagesArray.put(messageJson);
        }

        // 대화에 더 적합한 모델로 변경하고 최종 요청 본문을 담음.
        jsonBody.put("model", "sonar-pro");
        jsonBody.put("messages", messagesArray);

        return jsonBody;
    }
}
