package com.example.firstapp;

import static com.google.android.material.internal.ViewUtils.showKeyboard;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SELECT_AGENT = 1;
    private TextView currentAgentTextView; // 显示当前智能体的TextView
    // 下拉控件声明
    private PopupWindow dropdownMenu;
    private boolean isMenuShowing = false; //看是否已经打开
    private Button Myside_R_button;   //声明控件
    //左滑菜单
    private PopupWindow leftSlideMenu;
    private View menuLeftView;
    private boolean isLeftMenuShowing = false;
    private Button Myside_L_button;
    private ViewGroup mainLayout;
    // 对话控件声明
    private ConstraintLayout chatContainer;
    private EditText inputField;
    private Button sendButton;
    private NestedScrollView scrollView; // 关键修改
    private int lastMessageId = View.NO_ID;
    //api调用声明
    private DeepSeekService deepSeekService;
    //聊天记录
    private ConfigCRUD configCRUD;
    private ChatMessageCRUD chatMessageCRUD;
    private long currentConfigId = -1; // 当前使用的智能体配置ID
    private Button FreshButton;
    private List<ChatMessage> currentChatMessages = new ArrayList<>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        // -----------------------------------------------------------
        Myside_R_button = findViewById(R.id.side_R_button);//找到控件
        Myside_R_button.post(this::initDropdownMenu);// 延迟初始化确保获取正确宽度
        Myside_R_button.setOnClickListener(v -> toggleDropdownMenu());// 绑定点击事件
        initDropdownMenu();//实现
        // -----------------------------------------------------------
        Myside_L_button = findViewById(R.id.side_L_button);
        mainLayout = findViewById(R.id.main); // 假设根布局的id是main
        Myside_L_button.setOnClickListener(v -> toggleLeftSlideMenu());
        initLeftSlideMenu(); // 初始化左侧侧滑菜单
        // ----------------------------------------------------------------
        chatContainer = findViewById(R.id.chat_container);// 初始化控件
        inputField = findViewById(R.id.dzh_chat);
        sendButton = findViewById(R.id.Enter_button);
        scrollView = findViewById(R.id.scrollView);
        sendButton.setOnClickListener(v -> sendMessage());// 发送按钮点击监听
        // ----------------------------------------------------------------
        deepSeekService = new DeepSeekService(); // 初始化
        // ----------------------------------------------------------------
        configCRUD = new ConfigCRUD(this);
        configCRUD.open();
        chatMessageCRUD = new ChatMessageCRUD(this);
        chatMessageCRUD.open();
        loadChatHistory();// 加载当前配置的聊天记录
        // ----------------------------------------------------------------
        currentAgentTextView = findViewById(R.id.currentAgentTextView);
        // 初始化时尝试加载默认智能体
        deepseek_config defaultConfig = configCRUD.getLatestConfig();
        if (defaultConfig != null) {
            switchAgent(defaultConfig.getId());
        }
        // ----------------------------------------------------------------
        FreshButton = findViewById(R.id.Fresh_button);
        FreshButton.setOnClickListener(v -> {
            if (currentConfigId == -1) {
                Toast.makeText(this, "请先选择智能体再刷新", Toast.LENGTH_SHORT).show();
            } else {
                refreshCurrentConversation();
            }
        });
        // ----------------------------------------------------------------
        Button regretButton = findViewById(R.id.regret_button);
        regretButton.setOnClickListener(v -> showRegretDialog());
        // ----------------------------------------------------------------
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }
    private void sendMessage() {
        currentChatMessages.clear();

        String text = inputField.getText().toString().trim();
        if (!text.isEmpty() && currentConfigId != -1) { // 确保已选择智能体
            // 获取当前智能体配置
            deepseek_config config = configCRUD.getConfigById(currentConfigId);
            if (config == null) {
                Toast.makeText(this, "智能体配置加载失败", Toast.LENGTH_SHORT).show();
                return;
            }
            // 保存用户消息到数据库
            ChatMessage userMessage = new ChatMessage(text, true, System.currentTimeMillis(), currentConfigId);
            chatMessageCRUD.createMessage(userMessage);

            addMessage(text, true);
            inputField.setText("");
            // 3. 关键！重新获取焦点并保持键盘打开
//            inputField.post(() -> {
//                inputField.requestFocus();  // 重新获取焦点
//                showKeyboard(inputField);  // 强制显示键盘
//            });

            // 传递config对象给DeepSeekService
            deepSeekService.chat(text, config, new DeepSeekService.DeepSeekCallback(){
                @Override
                public void onResponse(String response) {
                    runOnUiThread(() -> {
                        // 保存AI回复到数据库
                        ChatMessage aiMessage = new ChatMessage(
                                response,
                                false,
                                System.currentTimeMillis(),
                                currentConfigId
                        );
                        chatMessageCRUD.createMessage(aiMessage);
                        addMessage(response, false);
                    });
                }

                @Override
                public void onFailure(String error) {
                    runOnUiThread(() -> {
                        String errorMsg = "错误: " + error;
                        // 保存错误信息到数据库
                        ChatMessage errorMessage = new ChatMessage(
                                errorMsg,
                                false,
                                System.currentTimeMillis(),
                                currentConfigId
                        );
                        chatMessageCRUD.createMessage(errorMessage);

                        addMessage(errorMsg, false);
                        Toast.makeText(
                                MainActivity.this,
                                "API调用失败: " + error,
                                Toast.LENGTH_SHORT
                        ).show();
                    });
                }
            });
        } else if (currentConfigId == -1) {
            Toast.makeText(this, "请先选择智能体", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadChatHistory() {
        if (currentConfigId == -1) return;

        List<ChatMessage> messages = chatMessageCRUD.getMessagesByConfig(currentConfigId);
        chatContainer.removeAllViews();
        lastMessageId = View.NO_ID;

        for (ChatMessage message : messages) {
            addMessage(message.getContent(), message.isUser());
        }

        // 滚动到底部
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void addMessage(String text, boolean isUser) {
        // 创建消息文本视图
        TextView textView = new TextView(this);
        textView.setId(View.generateViewId());
        textView.setText(text);
        textView.setTextSize(16);
        textView.setPadding(32, 16, 32, 16);
        textView.setMaxWidth((int) (getScreenWidth() * 0.7));
        textView.setTextIsSelectable(true); // 关键设置：允许文本选择

        // 添加长按复制菜单
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                // 创建复制菜单项
                menu.add(0, android.R.id.copy, 0, "复制");
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == android.R.id.copy) {
                    // 复制选中文本到剪贴板
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("AI回复", textView.getText().subSequence(
                            textView.getSelectionStart(),
                            textView.getSelectionEnd()
                    ));
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(MainActivity.this, "已复制", Toast.LENGTH_SHORT).show();
                    mode.finish();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {}
        });

        // 设置布局参数（保持原有布局代码）
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
        );

        // 根据消息类型设置约束
        if (isUser) {
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.startToStart = ConstraintLayout.LayoutParams.UNSET;
            params.horizontalBias = 1.0f;
            textView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_END);
        } else {
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET;
            params.horizontalBias = 0.0f;
            textView.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        }

        // 设置垂直约束
        if (lastMessageId != View.NO_ID) {
            params.topToBottom = lastMessageId;
            params.topMargin = 16;
        } else {
            params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
        }

        // 添加视图到容器
        chatContainer.addView(textView, params);
        lastMessageId = textView.getId();

        // 自动滚动到底部
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        inputField.post(() -> {
            inputField.requestFocus();
            showKeyboard(inputField);
        });
    }
    private void toggleLeftSlideMenu() {
        if (isLeftMenuShowing) {
            leftSlideMenu.dismiss();
        } else {
            // 从左侧滑出
            leftSlideMenu.showAtLocation(
                    mainLayout,
                    Gravity.START, // 关键！从左侧开始对齐
                    0,
                    0
            );
            isLeftMenuShowing = true;
        }
    }
    private void initLeftSlideMenu() {
        // 1. 加载菜单布局
        LayoutInflater inflater = LayoutInflater.from(this);
        menuLeftView = inflater.inflate(R.layout.menu_left_slide, null);

        // 2. 初始化菜单控件
        Button btnSettings = menuLeftView.findViewById(R.id.btn_settings);
        Button btnTags = menuLeftView.findViewById(R.id.btn_tags);
        Button btn_new = menuLeftView.findViewById(R.id.btn_new);
        // 3. 配置PopupWindow
        leftSlideMenu = new PopupWindow(
                menuLeftView,
                (int)(getScreenWidth() * 0.7), // 宽度为屏幕70%
                ViewGroup.LayoutParams.MATCH_PARENT,
                true
        );

        // 4. 设置参数
        leftSlideMenu.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        leftSlideMenu.setElevation(16f);
        leftSlideMenu.setAnimationStyle(R.style.LeftSlideAnimation);

        // 5. 绑定点击事件
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, ApiKeySettingActivity.class));
            leftSlideMenu.dismiss();
        });
        // 修改左侧菜单点击事件
        btnTags.setOnClickListener(v -> {
            startActivityForResult(
                    new Intent(MainActivity.this, AgentListActivity.class),
                    REQUEST_SELECT_AGENT
            );
            leftSlideMenu.dismiss();
        });
        btn_new.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, CreateAgentActivity.class));
            leftSlideMenu.dismiss();
        });

        // 添加蒙版（半透明覆盖层）
        View coverView = new View(this);
        coverView.setBackgroundColor(Color.argb(128, 0, 0, 0)); // 半透明黑色
        PopupWindow coverWindow = new PopupWindow(
                coverView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );

        // 设置关闭监听（关键修复）
        leftSlideMenu.setOnDismissListener(() -> {
            coverWindow.dismiss();
            isLeftMenuShowing = false; // 状态更新
        });

        // 修改按钮点击逻辑
        Myside_L_button.setOnClickListener(v -> {
            if (!isLeftMenuShowing) {
                // 显示蒙版和菜单
                coverWindow.showAtLocation(mainLayout, Gravity.FILL, 0, 0);
                leftSlideMenu.showAtLocation(
                        mainLayout,
                        Gravity.START,
                        0,
                        0
                );
                isLeftMenuShowing = true;
            } else {
                leftSlideMenu.dismiss();
            }
        });

        // 点击蒙版关闭菜单
        coverView.setOnClickListener(v -> leftSlideMenu.dismiss());
    }

    // 获取屏幕宽度
    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private void initDropdownMenu() {
        // 加载菜单布局
        View menuView = LayoutInflater.from(this).inflate(R.layout.menu_dropdown, null);

        // 初始化按钮控件
        Button delect_button = menuView.findViewById(R.id.delect_button);
        Button empty_button = menuView.findViewById(R.id.empty_button);

        dropdownMenu = new PopupWindow(
                menuView,
                Myside_R_button.getWidth(),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );

        // 设置通用参数
        dropdownMenu.setElevation(16f);
        dropdownMenu.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dropdownMenu.setOutsideTouchable(true);

        // 设置消失监听
        dropdownMenu.setOnDismissListener(() -> isMenuShowing = false);
        // 设置按钮点击监听
        delect_button.setOnClickListener(v -> {
            handleMenuItemClick(1);
            dropdownMenu.dismiss();
        });

        empty_button.setOnClickListener(v -> {
            handleMenuItemClick(2);
            dropdownMenu.dismiss();
        });
    }

    private void toggleDropdownMenu() {
        if (dropdownMenu == null) return;

        if (isMenuShowing) {
            // 如果菜单正在显示，则关闭
            dropdownMenu.dismiss();
        } else {
            // 如果菜单未显示，则计算位置并显示
            int[] location = new int[2];
            Myside_R_button.getLocationOnScreen(location);

            // 动态计算菜单宽度（示例为按钮宽度的1.5倍）
            int menuWidth = (int)(Myside_R_button.getWidth() * 2);

            // 获取屏幕尺寸
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            // 确保不超过屏幕右边界
            int maxRight = metrics.widthPixels - menuWidth;
            int finalX = Math.max(location[0] - (menuWidth - Myside_R_button.getWidth())/2, 0);
            finalX = Math.min(finalX, maxRight);

            // 更新PopupWindow宽度
            dropdownMenu.setWidth(menuWidth);

            // 显示菜单
            dropdownMenu.showAtLocation(
                    Myside_R_button,
                    Gravity.NO_GRAVITY,
                    finalX,  // 调整后的X坐标
                    location[1] + Myside_R_button.getHeight() + 8
            );
            isMenuShowing = true;
        }
    }

    // 处理菜单项点击
    private void handleMenuItemClick(int itemId) {
        switch (itemId) {
            case 1: clearChatHistory();break; // 聊天记录已经删除
            case 2: toggleAppTheme();break; // 聊天记录已经清空
        }
    }

    private void clearChatHistory() {
        if (currentConfigId != -1) {
            chatMessageCRUD.deleteMessagesByConfig(currentConfigId);
            chatContainer.removeAllViews();
            lastMessageId = View.NO_ID;
            Toast.makeText(this, "聊天记录已删除", Toast.LENGTH_SHORT).show();
        }
        else {Toast.makeText(this,"未删除聊天记录",Toast.LENGTH_SHORT).show();}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_AGENT && resultCode == RESULT_OK) {
            String action = data.getStringExtra("action");
            if ("switch".equals(action)) {
                long selectedAgentId = data.getLongExtra("selected_agent_id", -1);
                if (selectedAgentId != -1) {
                    switchAgent(selectedAgentId);
                }
            } else {
                // 如果是删除操作后返回，检查当前智能体是否已被删除
                checkCurrentAgentValidity();
            }
        }
    }

    private void toggleAppTheme() {
        // 实现主题切换逻辑
        Toast.makeText(this, "这个按钮还没写功能", Toast.LENGTH_SHORT).show();
    }
    private void refreshCurrentConversation() {
        // 安全校验（虽然点击事件已处理，但双重保险）
        if (currentConfigId == -1) return;
        // 1. 清空数据库记录
        chatMessageCRUD.deleteMessagesByConfig(currentConfigId);
        // 2. 清空服务端历史
        deepSeekService.clearHistory(currentConfigId);
        // 3. 清空UI显示
        chatContainer.removeAllViews();
        lastMessageId = View.NO_ID;
        // 4. 显示智能体名称的反馈
        String agentName = configCRUD.getConfigById(currentConfigId).getApiName();
        Toast.makeText(this, "已重置与 " + agentName + " 的对话", Toast.LENGTH_SHORT).show();
        // 5. 可选：自动重新发送系统提示
        String systemPrompt = configCRUD.getConfigById(currentConfigId).getSystemPrompt();
        if (!systemPrompt.isEmpty()) {
            addMessage("【系统提示】" + systemPrompt, false);
        }
    }
    @SuppressLint("SetTextI18n")
    private void switchAgent(long configId) {
        // 1. 更新当前智能体ID
        this.currentConfigId = configId;
        // 2. 获取智能体配置
        deepseek_config config = configCRUD.getConfigById(configId);
        if (config == null) return;
        // 3. 更新界面显示的智能体名称
        currentAgentTextView.setText("当前智能体：" + config.getApiName());
        // 4. 清空当前聊天显示
        chatContainer.removeAllViews();
        lastMessageId = View.NO_ID;
        // 5. 加载新智能体的历史消息
        loadChatHistory();
        // 6. 通知DeepSeekService切换对话历史（如果需要）
        deepSeekService.clearHistory(configId); // 确保使用新的对话历史
    }
    private void showRegretDialog() {
        if (currentConfigId == -1) {
            Toast.makeText(this, "请先选择智能体", Toast.LENGTH_SHORT).show();
            return;
        }

        currentChatMessages = chatMessageCRUD.getMessagesByConfig(currentConfigId);
        if (currentChatMessages.isEmpty()) {
            Toast.makeText(this, "当前没有聊天记录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 创建带时间戳的预览
        String[] items = new String[currentChatMessages.size()];
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());

        for (int i = 0; i < currentChatMessages.size(); i++) {
            ChatMessage msg = currentChatMessages.get(i);
            String time = sdf.format(new Date(msg.getTimestamp()));
            String prefix = msg.isUser() ? "👤[" : "🤖[";
            String content = prefix + time + "] " +
                    (msg.getContent().length() > 15 ?
                            msg.getContent().substring(0, 15) + "..." : msg.getContent());
            items[i] = content;
        }

        new AlertDialog.Builder(this)
                .setTitle("回溯到指定位置（之后内容将被删除）")
                .setItems(items, (dialog, which) -> {
                    // 1. 删除数据库记录
                    long selectedTimestamp = currentChatMessages.get(which).getTimestamp();
                    chatMessageCRUD.deleteMessagesAfterTimestamp(currentConfigId, selectedTimestamp);
                    // 2. 重建服务端记忆（保留回溯点之前的记录）
                    List<ChatMessage> validMessages = currentChatMessages.subList(0, which+1);
                    deepSeekService.rebuildConversationHistory(currentConfigId, validMessages);
                    // 3. 刷新界面
                    loadChatHistory();
                    Toast.makeText(this, "已回溯到选定位置", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }
    private void checkCurrentAgentValidity() {
        if (currentConfigId != -1) {
            deepseek_config config = configCRUD.getConfigById(currentConfigId);
            if (config == null) {
                // 当前智能体已被删除，切换到默认或空状态
                currentConfigId = -1;
                currentAgentTextView.setText("当前智能体：未选择");
                chatContainer.removeAllViews();
            }
        }
    }
    private void showKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        }
    }
}
