package com.example.firstapp;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class ConfigDatabase extends SQLiteOpenHelper {
    // 数据库基本信息
    private static final String DATABASE_NAME = "config.db";
    private static final int DATABASE_VERSION = 2; // 版本升级，新增聊天表

    //===== 智能体配置表结构 =====//
    public static final String TABLE_CONFIG = "deepseek_configs";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_API_KEY = "api_key";
    public static final String COLUMN_MODEL = "model";
    public static final String COLUMN_TEMPERATURE = "temperature";
    public static final String COLUMN_MAX_TOKENS = "max_tokens";
    public static final String COLUMN_TOP_P = "top_p";
    public static final String COLUMN_API_NAME = "api_name";
    public static final String COLUMN_SYSTEM_PROMPT = "system_prompt";

    // 智能体配置表建表语句
    private static final String CREATE_TABLE_CONFIG =
            "CREATE TABLE " + TABLE_CONFIG + "("
                    + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_API_KEY + " TEXT NOT NULL,"
                    + COLUMN_MODEL + " TEXT NOT NULL,"
                    + COLUMN_TEMPERATURE + " REAL DEFAULT 0.7,"
                    + COLUMN_MAX_TOKENS + " INTEGER DEFAULT 2000,"
                    + COLUMN_TOP_P + " REAL DEFAULT 1.0,"
                    + COLUMN_API_NAME + " TEXT,"
                    + COLUMN_SYSTEM_PROMPT + " TEXT)";

    //===== 聊天消息表结构 =====//
    public static final String TABLE_CHAT = "chat_messages";
    public static final String COLUMN_MSG_ID = "_id";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_IS_USER = "is_user";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_CONFIG_ID = "config_id";

    // 聊天消息表建表语句（含外键约束）
    private static final String CREATE_TABLE_CHAT =
            "CREATE TABLE " + TABLE_CHAT + "("
                    + COLUMN_MSG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_CONTENT + " TEXT NOT NULL,"
                    + COLUMN_IS_USER + " INTEGER NOT NULL," // 0:AI回复, 1:用户消息
                    + COLUMN_TIMESTAMP + " INTEGER NOT NULL,"
                    + COLUMN_CONFIG_ID + " INTEGER,"
                    + "FOREIGN KEY(" + COLUMN_CONFIG_ID + ") REFERENCES "
                    + TABLE_CONFIG + "(" + COLUMN_ID + ") ON DELETE CASCADE)";

    public ConfigDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        // 启用外键支持（需在每次数据库连接后执行）
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("PRAGMA foreign_keys = ON;");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 创建两张表
        db.execSQL(CREATE_TABLE_CONFIG);
        db.execSQL(CREATE_TABLE_CHAT);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 版本升级策略
        if (oldVersion < 2) {
            // 从版本1升级到版本2：新增聊天表
            db.execSQL(CREATE_TABLE_CHAT);
        }
        // 未来更多版本升级可继续添加条件判断
        // if (oldVersion < 3) { ... }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        // 每次打开数据库时启用外键约束
        if (!db.isReadOnly()) {
            db.execSQL("PRAGMA foreign_keys = ON;");
        }
    }
}
