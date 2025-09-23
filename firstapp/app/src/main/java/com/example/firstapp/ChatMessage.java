package com.example.firstapp;

public class ChatMessage {
    private long id;
    private String content;
    private boolean isUser; // true表示用户消息，false表示AI回复
    private long timestamp;
    private long configId; // 关联的智能体配置ID

    // 构造方法、getter和setter
    public ChatMessage() {}

    public ChatMessage(String content, boolean isUser, long timestamp, long configId) {
        this.content = content;
        this.isUser = isUser;
        this.timestamp = timestamp;
        this.configId = configId;
    }

    // 省略getter和setter...
    public long getId() {return id;}
    public String getContent() {return content;}
    public boolean isUser() {return isUser;}
    public long getTimestamp() {return timestamp;}
    public long getConfigId() {return configId;}
    public void setId(long id) {this.id = id;}
    public void setContent(String content) {this.content = content;}
    public void setUser(boolean user) {isUser = user;}
    public void setTimestamp(long timestamp) {this.timestamp = timestamp;}
    public void setConfigId(long configId) {this.configId = configId;}
}
