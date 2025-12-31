package com.example.firstapp;

import android.util.Log;
import com.google.gson.Gson;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class DeepSeekService {
    private static final String TAG = "DeepSeekService";
    private static final String BASE_URL = "https://api.deepseek.com"; // 改为硅基流动API地址
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 默认配置更新为硅基流动支持的模型
    private static final String DEFAULT_MODEL = "deepseek-ai";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 32768;
    private static final double DEFAULT_TOP_P = 0.7;
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个乐于助人的AI助手";

    private final OkHttpClient client;
    private final Gson gson;
    private final Map<Long, List<Message>> conversationHistories;

    public DeepSeekService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.SECONDS) // 延长超时时间
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.conversationHistories = new HashMap<>();
    }

    public interface DeepSeekCallback {
        void onResponse(String response);
        void onStreamResponse(String partialResponse);
        void onStreamComplete(String fullResponse);
        void onFailure(String error);
    }

    public void chat(String userMessage, deepseek_config config, boolean isStream, DeepSeekCallback callback) {
        if (config == null) {
            callback.onFailure("智能体配置不能为空");
            return;
        }

        long configId = config.getId();
        try {
            List<Message> history = getConversationHistory(configId);
            history.add(new Message("user", userMessage));
            trimConversationHistory(history, config.getMaxTokens());

            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system",
                    !config.getSystemPrompt().isEmpty() ?
                            config.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT));
            messages.addAll(history);

            // 构建硅基流动API请求体
            ChatRequest chatRequest = new ChatRequest();
            chatRequest.model = !config.getModel().isEmpty() ?
                    config.getModel() : DEFAULT_MODEL;
            chatRequest.messages = messages;
            chatRequest.temperature = config.getTemperature() > 0 ?
                    config.getTemperature() : DEFAULT_TEMPERATURE;
            chatRequest.max_tokens = config.getMaxTokens() > 0 ?
                    config.getMaxTokens() : DEFAULT_MAX_TOKENS;
            chatRequest.top_p = config.getTopP() > 0 ?
                    config.getTopP() : DEFAULT_TOP_P;
            chatRequest.stream = isStream;

            Request request = new Request.Builder()
                    .url(BASE_URL + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(chatRequest), JSON))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "API调用失败", e);
                    callback.onFailure("网络错误: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        if (!response.isSuccessful()) {
                            String errorBody = response.body() != null ?
                                    response.body().string() : "无错误详情";
                            throw new IOException("HTTP " + response.code() + ": " + errorBody);
                        }

                        if (isStream) {
                            // 处理流式响应
                            InputStream inputStream = response.body().byteStream();
                            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                            String line;
                            StringBuilder fullResponse = new StringBuilder();

                            while ((line = reader.readLine()) != null) {
                                if (line.isEmpty() || line.startsWith(":")) continue;
                                if (line.equals("data: [DONE]")) break;

                                if (line.startsWith("data: ")) {
                                    String json = line.substring(6);
                                    StreamChatResponse chatResponse = gson.fromJson(json, StreamChatResponse.class);
                                    if (chatResponse.choices != null && !chatResponse.choices.isEmpty()) {
                                        String partial = chatResponse.choices.get(0).delta.content;
                                        if (partial != null) {
                                            fullResponse.append(partial);
                                            callback.onStreamResponse(partial);
                                        }
                                    }
                                }
                            }

                            String aiResponse = fullResponse.toString();
                            getConversationHistory(configId).add(new Message("assistant", aiResponse));
                            callback.onStreamComplete(aiResponse);
                        } else {
                            // 处理非流式响应
                            String responseBody = response.body().string();
                            ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);

                            if (chatResponse.choices == null || chatResponse.choices.isEmpty()) {
                                throw new IOException("API返回空响应");
                            }

                            String aiResponse = chatResponse.choices.get(0).message.content;
                            getConversationHistory(configId).add(new Message("assistant", aiResponse));
                            callback.onResponse(aiResponse);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "处理响应响应错误", e);
                        callback.onFailure(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "创建请求失败", e);
            callback.onFailure("请求错误: " + e.getMessage());
        }
    }

    // 以下方法保持不变...
    public List<Message> getConversationHistory(long configId) {
        if (!conversationHistories.containsKey(configId)) {
            conversationHistories.put(configId, new ArrayList<>());
        }
        return conversationHistories.get(configId);
    }

    private void trimConversationHistory(List<Message> history, int maxTokens) {
        int maxRounds = maxTokens / 50;
        while (history.size() > maxRounds * 2) {
            history.remove(0);
            history.remove(0);
        }
    }

    public void clearHistory(long configId) {
        if (conversationHistories.containsKey(configId)) {
            conversationHistories.get(configId).clear();
        }
    }

    // 内部类保持不变
    private static class ChatRequest {
        String model;
        List<Message> messages;
        double temperature;
        int max_tokens;
        double top_p;
        boolean stream;
    }

    public static class Message {
        String role;
        String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static class ChatResponse {
        List<Choice> choices;
    }

    private static class StreamChatResponse {
        List<StreamChoice> choices;
    }

    private static class StreamChoice {
        StreamMessage delta;
    }

    private static class StreamMessage {
        String content;
    }

    private static class Choice {
        Message message;
    }

    // 保持原有的历史管理方法
    public void clearConversationHistory(long configId) {
        if (conversationHistories.containsKey(configId)) {
            conversationHistories.get(configId).clear();
        }
    }

    public void rebuildConversationHistory(long configId, List<ChatMessage> messages) {
        List<Message> history = new ArrayList<>();
        for (ChatMessage msg : messages) {
            history.add(new Message(
                    msg.isUser() ? "user" : "assistant",
                    msg.getContent()
            ));
        }
        conversationHistories.put(configId, history);
    }
}