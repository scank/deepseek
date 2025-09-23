package com.example.firstapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class AgentListActivity extends AppCompatActivity {
    private ListView agentListView;
    private ConfigCRUD configCRUD;
    private List<deepseek_config> agentList;
    private ChatMessageCRUD chatMessageCRUD;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_list);

        configCRUD = new ConfigCRUD(this);
        configCRUD.open();

        chatMessageCRUD = new ChatMessageCRUD(this); // 添加这行
        chatMessageCRUD.open(); // 打开数据库连接
        agentListView = findViewById(R.id.agentListView);

        // 加载智能体列表
        loadAgentList();

        // 单击事件 - 直接聊天
        agentListView.setOnItemClickListener((parent, view, position, id) -> {
            deepseek_config selectedAgent = agentList.get(position);
            returnToMainActivityWithAgent(selectedAgent);
        });

        // 长按事件 - 显示操作菜单
        agentListView.setOnItemLongClickListener((parent, view, position, id) -> {
            deepseek_config selectedAgent = agentList.get(position);
            showActionMenu(view, selectedAgent);
            return true;
        });
    }
    private void showActionMenu(View anchorView, deepseek_config agent) {
        View menuView = getLayoutInflater().inflate(R.layout.dialog_agent_actions, null);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(menuView)
                .create();

        // 设置对话框显示位置
        Window window = dialog.getWindow();
        if (window != null) {
            // 获取视图在屏幕上的位置
            int[] location = new int[2];
            anchorView.getLocationOnScreen(location);

            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.TOP | Gravity.START;
            params.x = location[0]; // x位置
            params.y = location[1] + anchorView.getHeight(); // y位置（在点击项下方）
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;

            window.setAttributes(params);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        // 删除按钮
        menuView.findViewById(R.id.btn_delete).setOnClickListener(v -> {
            dialog.dismiss();
            deleteAgent(agent);
        });

        // 编辑按钮
        menuView.findViewById(R.id.btn_edit).setOnClickListener(v -> {
            dialog.dismiss();
            editAgent(agent);
        });

        dialog.show();
    }

    private void loadAgentList() {
        agentList = configCRUD.getAllConfigs();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                getAgentDisplayNames()
        );
        agentListView.setAdapter(adapter);
    }
    private void deleteAgent(deepseek_config agent) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除智能体 " + agent.getApiName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    configCRUD.deleteConfig(agent.getId());
                    chatMessageCRUD.deleteMessagesByConfig(agent.getId());

                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("action", "delete");
                    resultIntent.putExtra("deleted_agent_id", agent.getId());
                    setResult(RESULT_OK, resultIntent);

                    loadAgentList();
                    Toast.makeText(this, "已删除智能体及聊天记录", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void editAgent(deepseek_config agent) {
        startActivity(new Intent(this, CreateAgentActivity.class)
                .putExtra("agent_id", agent.getId()));
    }

    private void returnToMainActivityWithAgent(deepseek_config agent) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("selected_agent_id", agent.getId());
        resultIntent.putExtra("action", "switch"); // 添加动作标识
        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private String[] getAgentDisplayNames() {
        String[] names = new String[agentList.size()];
        for (int i = 0; i < agentList.size(); i++) {
            names[i] = agentList.get(i).getApiName();
        }
        return names;
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAgentList();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        configCRUD.close();
        chatMessageCRUD.close();
    }
}
