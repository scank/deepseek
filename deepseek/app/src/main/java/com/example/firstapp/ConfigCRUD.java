// 文件2: ConfigCRUD.java - 数据库操作类
package com.example.firstapp;

import static com.example.firstapp.ConfigDatabase.COLUMN_ID;
import static com.example.firstapp.ConfigDatabase.TABLE_CONFIG;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class ConfigCRUD {
    private SQLiteDatabase database;
    private ConfigDatabase dbHelper;
    private final String[] allColumns = {
            COLUMN_ID,
            ConfigDatabase.COLUMN_API_KEY,
            ConfigDatabase.COLUMN_MODEL,
            ConfigDatabase.COLUMN_TEMPERATURE,
            ConfigDatabase.COLUMN_MAX_TOKENS,
            ConfigDatabase.COLUMN_TOP_P,
            ConfigDatabase.COLUMN_API_NAME,
            ConfigDatabase.COLUMN_SYSTEM_PROMPT
    };

    public ConfigCRUD(Context context) {
        dbHelper = new ConfigDatabase(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    // 创建配置
    public deepseek_config createConfig(deepseek_config config) {
        ContentValues values = new ContentValues();
        values.put(ConfigDatabase.COLUMN_API_KEY, config.getApiKey());
        values.put(ConfigDatabase.COLUMN_MODEL, config.getModel());
        values.put(ConfigDatabase.COLUMN_TEMPERATURE, config.getTemperature());
        values.put(ConfigDatabase.COLUMN_MAX_TOKENS, config.getMaxTokens());
        values.put(ConfigDatabase.COLUMN_TOP_P, config.getTopP());
        values.put(ConfigDatabase.COLUMN_API_NAME, config.getApiName());
        values.put(ConfigDatabase.COLUMN_SYSTEM_PROMPT, config.getSystemPrompt());

        long insertId = database.insert(TABLE_CONFIG, null, values);
        config.setId(insertId); // 需要在deepseek_config类中添加id字段和setter方法
        return config;
    }

    // 获取最新配置（假设只保留一个配置）
    public deepseek_config getLatestConfig() {
        Cursor cursor = database.query(TABLE_CONFIG,
                allColumns, null, null, null, null,
                COLUMN_ID + " DESC", "1");

        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                return cursorToConfig(cursor);
            }
            cursor.close();
        }
        return null;
    }

    // 更新配置
    public int updateConfig(deepseek_config config) {
        ContentValues values = new ContentValues();
        values.put(ConfigDatabase.COLUMN_API_KEY, config.getApiKey());
        values.put(ConfigDatabase.COLUMN_MODEL, config.getModel());
        values.put(ConfigDatabase.COLUMN_TEMPERATURE, config.getTemperature());
        values.put(ConfigDatabase.COLUMN_MAX_TOKENS, config.getMaxTokens());
        values.put(ConfigDatabase.COLUMN_TOP_P, config.getTopP());
        values.put(ConfigDatabase.COLUMN_API_NAME, config.getApiName());
        values.put(ConfigDatabase.COLUMN_SYSTEM_PROMPT, config.getSystemPrompt());

        return database.update(TABLE_CONFIG, values,
                COLUMN_ID + " = ?",
                new String[] { String.valueOf(config.getId()) });
    }

    // 删除配置
    public void deleteConfig(long id) {
        database.delete(TABLE_CONFIG,
                COLUMN_ID + " = ?",
                new String[] { String.valueOf(id) });
    }

    // 清空所有配置
    public void deleteAllConfigs() {
        database.delete(TABLE_CONFIG, null, null);
    }

    // 转换Cursor到对象
    private deepseek_config cursorToConfig(Cursor cursor) {
        deepseek_config config = new deepseek_config();
        config.setId(cursor.getLong(0));
        config.setApiKey(cursor.getString(1));
        config.setModel(cursor.getString(2));
        config.setTemperature(cursor.getDouble(3));
        config.setMaxTokens(cursor.getInt(4));
        config.setTopP(cursor.getDouble(5));
        config.setApiName(cursor.getString(6));
        config.setSystemPrompt(cursor.getString(7));
        return config;
    }
    // 在 ConfigCRUD 类中添加这个方法
    public List<deepseek_config> getAllConfigs() {
        List<deepseek_config> configs = new ArrayList<>();
        Cursor cursor = database.query(TABLE_CONFIG, allColumns, null, null, null, null, null);
        while (cursor.moveToNext()) {
            configs.add(cursorToConfig(cursor));
        }
        cursor.close();
        return configs;
    }
    public deepseek_config getConfigById(long id) {
        Cursor cursor = database.query(
                TABLE_CONFIG,
                allColumns,
                COLUMN_ID + " = ?",
                new String[]{String.valueOf(id)},
                null, null, null);

        if (cursor != null) {
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                return cursorToConfig(cursor);
            }
            cursor.close();
        }
        return null;
    }

}