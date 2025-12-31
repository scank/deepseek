package com.example.firstapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class ApiKeySettingActivity extends AppCompatActivity {
    private EditText apiKeyEditText;
    private Button saveButton;
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api_key_setting);

        // 初始化SharedPreferences
        sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);

        apiKeyEditText = findViewById(R.id.apiKeyEditText);
        saveButton = findViewById(R.id.saveApiKeyButton);

        // 加载已保存的API Key
        String savedApiKey = sharedPref.getString("global_api_key", "");
        apiKeyEditText.setText(savedApiKey);

        saveButton.setOnClickListener(v -> saveApiKey());
    }

    private void saveApiKey() {
        String newApiKey = apiKeyEditText.getText().toString().trim();
        if (!newApiKey.isEmpty()) {
            // 保存到SharedPreferences
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("global_api_key", newApiKey);
            editor.apply();

            // 更新数据库中所有智能体的API Key
            updateAllAgentsApiKey(newApiKey);

            Toast.makeText(this, "API Key已保存并更新", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, "API Key不能为空", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAllAgentsApiKey(String newApiKey) {
        ConfigCRUD configCRUD = new ConfigCRUD(this);
        configCRUD.open();

        try {
            List<deepseek_config> allConfigs = configCRUD.getAllConfigs();
            if (allConfigs != null) {
                for (deepseek_config config : allConfigs) {
                    config.setApiKey(newApiKey);
                    configCRUD.updateConfig(config);
                }
            }
        } finally {
            configCRUD.close();
        }
    }
}
