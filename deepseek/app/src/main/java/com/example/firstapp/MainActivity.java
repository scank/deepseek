package com.example.firstapp;

import static com.google.android.material.internal.ViewUtils.showKeyboard;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.GestureDetectorCompat;
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
    private boolean isMenuShowing = false; // 看是否已经打开
    private Button Myside_R_button;   // 声明控件
    // 左滑菜单
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
    // api调用声明
    private DeepSeekService deepSeekService;
    // 聊天记录
    private ConfigCRUD configCRUD;
    private ChatMessageCRUD chatMessageCRUD;
    private long currentConfigId = -1; // 当前使用的智能体配置ID
    private Button FreshButton;
    private List<ChatMessage> currentChatMessages = new ArrayList<>();

    // 新增：流式输出相关变量
    private boolean isStreamEnabled = false;
    private TextView currentAiMessageView;
    private String currentStreamResponse = "";

    // 新增：手势检测相关变量
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_THRESHOLD = 80; // 滑动触发距离阈值(dp)
    private static final int SWIPE_VELOCITY_THRESHOLD = 5; // 滑动速度阈值
    private float startX;
    private boolean isSliding = false;

    // 新增：保存最后使用智能体ID的常量和工具方法
    private static final String PREFS_NAME = "LastUsedAgentPrefs";
    private static final String KEY_LAST_AGENT_ID = "last_agent_id";
    private static final String KEY_STREAM_ENABLED = "stream_enabled";

    // 新增：悬浮按钮相关变量
    private LinearLayout floatActions;
    private Button btnPrevAnswer;
    private Button btnScrollTop;
    private boolean isFloatActionsVisible = false;
    private static final int SCROLL_THRESHOLD = 300; // 滚动多少距离后显示悬浮按钮

    // 保存最后使用的智能体ID
    private void saveLastUsedAgentId(long agentId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_AGENT_ID, agentId).apply();
    }

    // 获取最后使用的智能体ID（默认-1表示无记录）
    private long getLastUsedAgentId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_AGENT_ID, -1);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // 初始化悬浮按钮
        initFloatActions();

        // 初始化手势检测器
        initGestureDetector();

        // -----------------------------------------------------------
        Myside_R_button = findViewById(R.id.side_R_button);// 找到控件
        Myside_R_button.post(this::initDropdownMenu);// 延迟初始化确保获取正确宽度
        Myside_R_button.setOnClickListener(v -> toggleDropdownMenu());// 绑定点击事件
        initDropdownMenu();// 实现
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

        // 设置滚动监听，用于显示/隐藏悬浮按钮
        setupScrollListener();

        // 关键修改：为对话区域设置触摸监听器
        setupChatAreaTouchListener();

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
        // 关键修改：优先加载最后使用的智能体，而非直接加载最新创建的
        long lastAgentId = getLastUsedAgentId();
        deepseek_config targetConfig = null;

        // 1. 尝试加载最后使用的智能体
        if (lastAgentId != -1) {
            targetConfig = configCRUD.getConfigById(lastAgentId);
        }

        // 2. 若没有最后使用记录，再加载最新创建的智能体（保持原有逻辑）
        if (targetConfig == null) {
            targetConfig = configCRUD.getLatestConfig();
        }

        // 3. 加载目标智能体
        if (targetConfig != null) {
            switchAgent(targetConfig.getId());
        } else {
            currentAgentTextView.setText("当前智能体：未选择");
        }

        // 加载流式输出设置
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isStreamEnabled = prefs.getBoolean(KEY_STREAM_ENABLED, false);

        // ----------------------------------------------------------------
        FreshButton = findViewById(R.id.Fresh_button);
        FreshButton.setOnClickListener(v -> {
            if (currentConfigId == -1) {
                Toast.makeText(this, "请先选择智能体再刷新", Toast.LENGTH_SHORT).show();
            } else {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("确认刷新")
                        .setMessage("确定要刷新当前对话吗？这将清空当前会话并重新加载历史记录")
                        .setPositiveButton("确定", (dialog, which) -> {
                            refreshCurrentConversation();
                        })
                        .setNegativeButton("取消", null)
                        .show();
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

    // 初始化悬浮按钮
    private void initFloatActions() {
        floatActions = findViewById(R.id.float_actions);
        btnPrevAnswer = findViewById(R.id.btn_prev_answer);
        btnScrollTop = findViewById(R.id.btn_scroll_top);

        // 设置按钮点击事件
        btnScrollTop.setOnClickListener(v -> scrollToTop());
        btnPrevAnswer.setOnClickListener(v -> scrollToPreviousConversation());
    }

    // 设置滚动监听器，控制悬浮按钮显示/隐藏
    private void setupScrollListener() {
        scrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            // 当滚动距离超过阈值且是向下滚动时显示悬浮按钮
            if (scrollY > SCROLL_THRESHOLD && scrollY < oldScrollY && !isFloatActionsVisible) {
                showFloatActions();
            }
            // 当滚动到顶部或向上滚动时隐藏悬浮按钮
            else if ((scrollY <= SCROLL_THRESHOLD || scrollY > oldScrollY) && isFloatActionsVisible) {
                hideFloatActions();
            }
        });
    }

    // 显示悬浮按钮
    private void showFloatActions() {
        floatActions.setVisibility(View.VISIBLE);
        isFloatActionsVisible = true;
        // 淡入动画
        floatActions.animate()
                .alpha(1.0f)
                .setDuration(300)
                .start();
    }

    // 隐藏悬浮按钮
    private void hideFloatActions() {
        // 淡出动画
        floatActions.animate()
                .alpha(0.0f)
                .setDuration(300)
                .withEndAction(() -> {
                    floatActions.setVisibility(View.GONE);
                    isFloatActionsVisible = false;
                })
                .start();
    }

    /**
     * 修正：滚动到最开始的AI消息位置（而非ScrollView顶部）
     */
    private void scrollToTop() {
        if (chatContainer.getChildCount() == 0) {
            Toast.makeText(this, "暂无聊天记录", Toast.LENGTH_SHORT).show();
            return;
        }

        // 遍历所有消息，找到第一条AI消息（靠左显示的消息）
        for (int i = 0; i < chatContainer.getChildCount(); i++) {
            View child = chatContainer.getChildAt(i);
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) child.getLayoutParams();

            // 判断是否是AI消息（靠左显示：startToStart绑定到父布局）
            if (params != null && params.startToStart == ConstraintLayout.LayoutParams.PARENT_ID) {
                int[] targetLocation = new int[2];
                child.getLocationOnScreen(targetLocation);

                // 计算滚动目标位置（预留顶部边距）
                int targetScrollY = targetLocation[1] - scrollView.getPaddingTop() - getStatusBarHeight() - 40;
                scrollView.smoothScrollTo(0, Math.max(0, targetScrollY));
                hideFloatActions();
                return;
            }
        }

        // 若没有AI消息，默认滚动到顶部
        scrollView.smoothScrollTo(0, 0);
        hideFloatActions();
    }

    /**
     * 修正：滚动到当前可见区域的上一个完整对话（用户提问+AI回复）
     */
    private void scrollToPreviousConversation() {
        if (chatContainer.getChildCount() == 0) {
            Toast.makeText(this, "暂无聊天记录", Toast.LENGTH_SHORT).show();
            return;
        }

        int currentScrollY = scrollView.getScrollY();
        int scrollViewHeight = scrollView.getHeight();
        int currentMessageIndex = -1;

        // 步骤1：找到当前滚动位置可见区域内的"当前消息"（优先选择可见区域偏下的消息）
        for (int i = 0; i < chatContainer.getChildCount(); i++) {
            View child = chatContainer.getChildAt(i);
            int[] location = new int[2];
            child.getLocationOnScreen(location);

            // 转换为相对于ScrollView的坐标
            int viewTop = location[1] - scrollView.getPaddingTop() - getStatusBarHeight();
            int viewBottom = viewTop + child.getHeight();

            // 判断消息是否在可见区域内
            boolean isVisible = (viewBottom > currentScrollY) && (viewTop < currentScrollY + scrollViewHeight);
            if (isVisible) {
                currentMessageIndex = i;
                // 继续遍历，找到可见区域内最后一个消息（更贴近用户当前查看位置）
            }
        }

        // 极端情况：未找到可见消息，默认从最后一条开始
        if (currentMessageIndex == -1) {
            currentMessageIndex = chatContainer.getChildCount() - 1;
        }

        // 步骤2：从当前消息位置向前查找完整对话（AI消息+对应的用户消息）
        int targetAiIndex = -1;
        // 先找到当前位置前最近的AI消息
        for (int i = currentMessageIndex - 1; i >= 0; i--) {
            View child = chatContainer.getChildAt(i);
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) child.getLayoutParams();

            // 判断是否是AI消息（靠左显示）
            if (params != null && params.startToStart == ConstraintLayout.LayoutParams.PARENT_ID) {
                targetAiIndex = i;
                break;
            }
        }

        // 如果找到AI消息，再向前找对应的用户消息（组成完整对话）
        if (targetAiIndex != -1) {
            for (int i = targetAiIndex - 1; i >= 0; i--) {
                View child = chatContainer.getChildAt(i);
                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) child.getLayoutParams();

                // 判断是否是用户消息（靠右显示）
                if (params != null && params.endToEnd == ConstraintLayout.LayoutParams.PARENT_ID) {
                    // 滚动到用户消息位置（完整对话的起点）
                    int[] targetLocation = new int[2];
                    child.getLocationOnScreen(targetLocation);

                    // 计算滚动目标位置（使对话在屏幕中间偏上）
                    int targetScrollY = targetLocation[1] - scrollView.getPaddingTop() - getStatusBarHeight() - (scrollViewHeight / 3);
                    scrollView.smoothScrollTo(0, Math.max(0, targetScrollY));
                    hideFloatActions();
                    return;
                }
            }

            // 如果没有找到对应的用户消息，直接滚动到AI消息
            View targetView = chatContainer.getChildAt(targetAiIndex);
            int[] targetLocation = new int[2];
            targetView.getLocationOnScreen(targetLocation);
            int targetScrollY = targetLocation[1] - scrollView.getPaddingTop() - getStatusBarHeight() - (scrollViewHeight / 3);
            scrollView.smoothScrollTo(0, Math.max(0, targetScrollY));
            hideFloatActions();
        } else {
            Toast.makeText(this, "没有更早的对话", Toast.LENGTH_SHORT).show();
            scrollToTop();
        }
    }

    // 获取状态栏高度（辅助计算滚动位置）
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    // 关键修改：为对话区域设置触摸监听器
    @SuppressLint("ClickableViewAccessibility")
    private void setupChatAreaTouchListener() {
        scrollView.setOnTouchListener((v, event) -> {
            // 将触摸事件传递给手势检测器
            return gestureDetector.onTouchEvent(event);
        });
    }

    // 初始化手势检测器（修复空指针异常）
    private void initGestureDetector() {
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                // 关键修复：空值检查，避免NullPointerException
                if (e1 == null || e2 == null) {
                    return false;
                }

                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                // 检测从左向右的水平滑动（距离和速度达标）
                if (Math.abs(diffX) > Math.abs(diffY)
                        && diffX > SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (!isLeftMenuShowing) {
                        toggleLeftSlideMenu(); // 显示左侧菜单
                    }
                    return true;
                }
                // 检测从右向左滑动关闭菜单
                else if (Math.abs(diffX) > Math.abs(diffY)
                        && diffX < -SWIPE_THRESHOLD
                        && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (isLeftMenuShowing) {
                        toggleLeftSlideMenu(); // 隐藏左侧菜单
                    }
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                startX = e.getX();
                isSliding = false;
                return true; // 必须返回true以接收后续事件
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // 限制仅左侧边缘可触发滑动（50dp范围内）
                if (startX < getResources().getDimensionPixelSize(R.dimen.slide_edge_width)) {
                    isSliding = true;
                }
                return super.onScroll(e1, e2, distanceX, distanceY);
            }
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
            inputField.post(() -> {
                inputField.requestFocus();  // 重新获取焦点
                showKeyboard(inputField);  // 强制显示键盘
            });

            // 重置流式响应变量
            currentStreamResponse = "";
            currentAiMessageView = null;

            // 传递config对象给DeepSeekService
            deepSeekService.chat(text, config, isStreamEnabled, new DeepSeekService.DeepSeekCallback() {
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
                public void onStreamResponse(String partialResponse) {
                    runOnUiThread(() -> {
                        currentStreamResponse += partialResponse;
                        if (currentAiMessageView == null) {
                            // 首次收到流数据时创建消息视图
                            currentAiMessageView = addMessage(currentStreamResponse, false);
                        } else {
                            // 后续流数据更新现有视图
                            currentAiMessageView.setText(currentStreamResponse);
                        }
                        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                    });
                }

                @Override
                public void onStreamComplete(String fullResponse) {
                    runOnUiThread(() -> {
                        // 流结束时保存完整消息
                        ChatMessage aiMessage = new ChatMessage(
                                fullResponse,
                                false,
                                System.currentTimeMillis(),
                                currentConfigId
                        );
                        chatMessageCRUD.createMessage(aiMessage);
                        currentAiMessageView = null;
                        currentStreamResponse = "";
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

        // 关键修改：将数据库记录同步到DeepSeekService的对话历史中
        deepSeekService.rebuildConversationHistory(currentConfigId, messages);

        // 滚动到底部
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private TextView addMessage(String text, boolean isUser) {
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
            public void onDestroyActionMode(ActionMode mode) {
            }
        });

        // 设置布局参数
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

        return textView;
    }

    private void toggleLeftSlideMenu() {
        if (isLeftMenuShowing) {
            leftSlideMenu.dismiss();
            scrollView.setEnabled(true); // 菜单隐藏时恢复滚动
        } else {
            // 从左侧滑出
            leftSlideMenu.showAtLocation(
                    mainLayout,
                    Gravity.START, // 关键！从左侧开始对齐
                    0,
                    0
            );
            isLeftMenuShowing = true;
            scrollView.setEnabled(false); // 菜单显示时禁用滚动，避免冲突
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

        // 初始化流式输出开关
        Switch streamSwitch = menuLeftView.findViewById(R.id.stream_switch);
        streamSwitch.setChecked(isStreamEnabled);
        streamSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isStreamEnabled = isChecked;
            // 保存状态到SharedPreferences
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_STREAM_ENABLED, isChecked)
                    .apply();
            Toast.makeText(MainActivity.this,
                    isChecked ? "已开启流式输出" : "已关闭流式输出",
                    Toast.LENGTH_SHORT).show();
        });

        // 3. 配置PopupWindow
        leftSlideMenu = new PopupWindow(
                menuLeftView,
                (int) (getScreenWidth() * 0.7), // 宽度为屏幕70%
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

        // 设置关闭监听
        leftSlideMenu.setOnDismissListener(() -> {
            coverWindow.dismiss();
            isLeftMenuShowing = false; // 状态更新
            scrollView.setEnabled(true); // 恢复滚动
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
                scrollView.setEnabled(false); // 禁用滚动
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
            int menuWidth = (int) (Myside_R_button.getWidth() * 2);

            // 获取屏幕尺寸
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            // 确保不超过屏幕右边界
            int maxRight = metrics.widthPixels - menuWidth;
            int finalX = Math.max(location[0] - (menuWidth - Myside_R_button.getWidth()) / 2, 0);
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
            case 1:
                clearChatHistory();
                break; // 聊天记录已经删除
            case 2:
                toggleAppTheme();
                break; // 聊天记录已经清空
        }
    }

    private void clearChatHistory() {
        if (currentConfigId != -1) {
            chatMessageCRUD.deleteMessagesByConfig(currentConfigId);
            chatContainer.removeAllViews();
            lastMessageId = View.NO_ID;
            // 同步清空服务端历史
            deepSeekService.clearHistory(currentConfigId);
            Toast.makeText(this, "聊天记录已删除", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "未删除聊天记录", Toast.LENGTH_SHORT).show();
        }
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
        // 新增：保存当前智能体为最后使用的智能体
        saveLastUsedAgentId(configId);

        // 原有代码保持不变
        deepseek_config config = configCRUD.getConfigById(configId);
        if (config == null) return;
        // 3. 更新界面显示的智能体名称
        currentAgentTextView.setText("当前智能体：" + config.getApiName());
        // 4. 清空当前聊天显示
        chatContainer.removeAllViews();
        lastMessageId = View.NO_ID;
        // 5. 加载新智能体的历史消息
        loadChatHistory();
        // 6. 通知DeepSeekService切换对话历史
        deepSeekService.clearHistory(configId);
        List<ChatMessage> messages = chatMessageCRUD.getMessagesByConfig(configId);
        deepSeekService.rebuildConversationHistory(configId, messages);
    }

    private void showRegretDialog() {
        if (currentConfigId == -1) {
            Toast.makeText(this, "请先选择智能体", Toast.LENGTH_SHORT).show();
            return;
        }
        // 获取当前智能体配置和名称
        deepseek_config currentConfig = configCRUD.getConfigById(currentConfigId);
        String agentName = currentConfig != null && currentConfig.getApiName() != null
                ? currentConfig.getApiName()
                : "AI"; // 兜底默认名称

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
            String prefix = msg.isUser() ? "👤 [" : agentName + "  [";
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
                    List<ChatMessage> validMessages = currentChatMessages.subList(0, which + 1);
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
                // 新增：清除保存的无效智能体ID
                saveLastUsedAgentId(-1);
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