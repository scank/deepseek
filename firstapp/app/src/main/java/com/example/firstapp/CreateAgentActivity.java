package com.example.firstapp;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class CreateAgentActivity extends AppCompatActivity {
    private Spinner modelSpinner;
    private EditText temperatureEditText;
    private EditText top_pEditText;
    private EditText nameEditText;
    private EditText systemPromptEditText;
    private Button saveAgentButton;
    private ConfigCRUD configCRUD;
    private long editingAgentId = -1; // -1表示新建，否则是编辑
    private SharedPreferences sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_agent);

        // 初始化SharedPreferences
        sharedPref = getSharedPreferences("AppPrefs", MODE_PRIVATE);

        // 检查是否是编辑模式
        editingAgentId = getIntent().getLongExtra("agent_id", -1);

        // 初始化数据库操作
        configCRUD = new ConfigCRUD(this);
        configCRUD.open();

        // 初始化视图
        modelSpinner = findViewById(R.id.modelSpinner);
        // 设置默认选中项（可选）
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.model_names,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        modelSpinner.setAdapter(adapter);
        temperatureEditText = findViewById(R.id.temperatureEditText);
        top_pEditText = findViewById(R.id.top_pEditText);
        nameEditText = findViewById(R.id.nameEditText);
        systemPromptEditText = findViewById(R.id.systemPromptEditText);
        saveAgentButton = findViewById(R.id.saveAgentButton);

        saveAgentButton.setOnClickListener(v -> saveAgent());

        if (editingAgentId != -1) {
            // 加载现有配置
            loadAgentConfig(editingAgentId);
            saveAgentButton.setText("更新智能体");
        }
    }

    private void loadAgentConfig(long id) {
        deepseek_config config = configCRUD.getConfigById(id);
        if (config != null) {
            ArrayAdapter adapter = (ArrayAdapter) modelSpinner.getAdapter();
            int position = adapter.getPosition(config.getModel());
            modelSpinner.setSelection(position >= 0 ? position : 0);
            temperatureEditText.setText(String.valueOf(config.getTemperature()));
            top_pEditText.setText(String.valueOf(config.getTopP()));
            nameEditText.setText(config.getApiName());
            systemPromptEditText.setText(config.getSystemPrompt());

            // 不显示API Key（因为使用全局统一Key）
        }
    }

    private void saveAgent() {
        try {
            // 获取用户输入
            String model = modelSpinner.getSelectedItem().toString();
            double temperature = Double.parseDouble(temperatureEditText.getText().toString().trim());
            double topP = Double.parseDouble(top_pEditText.getText().toString().trim());
            String name = nameEditText.getText().toString().trim();
            String systemPrompt = systemPromptEditText.getText().toString().trim();

            // 验证输入
            if (model.isEmpty() || name.isEmpty()) {
                Toast.makeText(this, "模型名称和智能体名称不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            // 获取全局API Key
            String globalApiKey = sharedPref.getString("global_api_key", "");
            if (globalApiKey.isEmpty()) {
                Toast.makeText(this, "请先在设置中配置API Key", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建/更新配置对象（自动使用全局API Key）
            deepseek_config config = new deepseek_config(
                    globalApiKey, // 使用全局Key
                    model,
                    temperature,
                    8000, // maxTokens
                    topP,
                    name,
                    systemPrompt
            );

            if (editingAgentId != -1) {
                // 更新现有配置
                config.setId(editingAgentId);
                configCRUD.updateConfig(config);
                Toast.makeText(this, "智能体更新成功", Toast.LENGTH_SHORT).show();
            } else {
                // 创建新配置
                configCRUD.createConfig(config);
                Toast.makeText(this, "智能体创建成功", Toast.LENGTH_SHORT).show();
            }

            finish();
        } catch (NumberFormatException e) {
            Toast.makeText(this, "请输入有效的数值", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        configCRUD.close();
    }
}
