package com.example.firstapp;

public class deepseek_config
{
    private long id; // 新增字段
    private String apiKey;//api的秘钥
    private String model;//使用哪一个模型
    private double temperature;//temperature的大小
    private int maxTokens;//最大输出token
    private double topP; //和temperature差不多
    private String apiname;//api的称呼
    private String systemPrompt; //智能体设定

    public deepseek_config() {
    }
    public deepseek_config(String apiKey, String model, double temperature,
                           int maxTokens, double topP, String apiname, String systemPrompt) {
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.topP = topP;
        this.apiname = apiname;
        this.systemPrompt = systemPrompt;
    }
    // Getter 方法
    public long getId() { return id; }
    public String getApiKey() { return apiKey; }
    public String getModel() { return model; }
    public double getTemperature() { return temperature; }
    public int getMaxTokens() { return maxTokens; }
    public double getTopP() { return topP; }
    public String getApiName() { return apiname; }
    public String getSystemPrompt() { return systemPrompt; }
    // Setter 方法
    public void setId(long id) { this.id = id; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public void setModel(String model) { this.model = model; }
    public void setTemperature(double temperature) { this.temperature = temperature; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    public void setTopP(double topP) { this.topP = topP; }
    public void setApiName(String apiname) { this.apiname = apiname; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}




