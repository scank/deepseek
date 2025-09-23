package com.example.firstapp;

import android.util.Log;
import com.google.gson.Gson;
import java.io.IOException;
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
    private static final String BASE_URL = "https://api.deepseek.com/v1 ";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // 默认配置（当智能体未设置时使用）
    private static final String DEFAULT_MODEL = "deepseek-chat";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2000;
    private static final double DEFAULT_TOP_P = 1.0;
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个乐于助人的AI助手";

    private final OkHttpClient client;
    private final Gson gson;
    private final Map<Long, List<Message>> conversationHistories; // 按configId隔离对话历史

    public DeepSeekService() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(90, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();
        this.gson = new Gson();
        this.conversationHistories = new HashMap<>();
    }

    public interface DeepSeekCallback {
        void onResponse(String response);
        void onFailure(String error);
    }

    /**
     * 发送聊天消息（使用智能体配置）
     * @param userMessage 用户输入
     * @param config 智能体配置（包含model/temperature等参数）
     * @param callback 回调接口
     */
    public void chat(String userMessage, deepseek_config config, DeepSeekCallback callback) {
        if (config == null) {
            callback.onFailure("智能体配置不能为空");
            return;
        }

        long configId = config.getId();
        try {
            // 获取或创建当前智能体的对话历史
            //List<Message> history = getConversationHistory(configId);
            List<Message> history = getConversationHistory(config.getId()); // 自动按configId隔离
            // 添加用户消息到历史记录
            history.add(new Message("user", userMessage));
            trimConversationHistory(history, config.getMaxTokens());

            // 构建消息列表（优先使用配置中的systemPrompt）
            List<Message> messages = new ArrayList<>();
            messages.add(new Message("system",
                    !config.getSystemPrompt().isEmpty() ?
                            config.getSystemPrompt() : DEFAULT_SYSTEM_PROMPT));
            messages.addAll(history);

            // 构建请求参数（使用配置值或默认值）
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

            // 打印调试信息
            Log.d(TAG, String.format(
                    "调用API - 模型: %s, Temperature: %.1f, Top_p: %.1f, MaxTokens: %d",
                    chatRequest.model, chatRequest.temperature,
                    chatRequest.top_p, chatRequest.max_tokens));

            // 构建请求
            Request request = new Request.Builder()
                    .url(BASE_URL + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(gson.toJson(chatRequest), JSON))
                    .build();

            // 异步执行请求
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

                        String responseBody = response.body().string();
                        ChatResponse chatResponse = gson.fromJson(responseBody, ChatResponse.class);

                        if (chatResponse.choices == null || chatResponse.choices.isEmpty()) {
                            throw new IOException("API返回空响应");
                        }

                        String aiResponse = chatResponse.choices.get(0).message.content;
                        // 添加AI回复到历史记录
                        getConversationHistory(configId).add(new Message("assistant", aiResponse));
                        callback.onResponse(aiResponse);
                    } catch (Exception e) {
                        Log.e(TAG, "处理响应错误", e);
                        callback.onFailure(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "创建请求失败", e);
            callback.onFailure("请求错误: " + e.getMessage());
        }
    }

    /**
     * 获取指定智能体的对话历史
     */
    public  List<Message> getConversationHistory(long configId) {
        if (!conversationHistories.containsKey(configId)) {
            conversationHistories.put(configId, new ArrayList<>());
        }
        return conversationHistories.get(configId);
    }

    /**
     * 清理过长的对话历史（基于token数估算）
     */
    private void trimConversationHistory(List<Message> history, int maxTokens) {
        // 简单实现：限制对话轮次（实际应根据token数计算）
        int maxRounds = maxTokens / 50; // 假设每轮对话约50个token
        while (history.size() > maxRounds * 2) { // 每轮包含用户和AI两条消息
            history.remove(0); // 移除最早的用户消息
            history.remove(0); // 移除对应的AI回复
        }
    }

    /**
     * 清空指定智能体的对话历史
     */
    public void clearHistory(long configId) {
        if (conversationHistories.containsKey(configId)) {
            conversationHistories.get(configId).clear();
        }
    }

    // 内部数据结构
    private static class ChatRequest {
        String model;
        List<Message> messages;
        double temperature;
        int max_tokens;
        double top_p;
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

    private static class Choice {
        Message message;
    }
    // 添加清除指定智能体对话历史的方法
    public void clearConversationHistory(long configId) {
        if (conversationHistories.containsKey(configId)) {
            conversationHistories.get(configId).clear();
        }
    }

    // 修改删除方法更名以明确功能
    public void truncateConversationFrom(long configId, int messageIndex) {
        List<Message> history = getConversationHistory(configId);
        if (history != null && messageIndex < history.size()) {
            history.subList(messageIndex, history.size()).clear();
        }
    }

    // 添加这个方法 - 重建对话历史
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

    // 修改clear方法更名以明确功能
    public void clearHistoryAfter(long configId, int index) {
        List<Message> history = getConversationHistory(configId);
        if (history != null && index < history.size()) {
            history.subList(index, history.size()).clear();
        }
    }

}