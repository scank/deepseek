package com.example.firstapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;

public class ChatMessageCRUD {
    private SQLiteDatabase database;
    private ConfigDatabase dbHelper;

    public ChatMessageCRUD(Context context) {
        dbHelper = new ConfigDatabase(context);
    }

    public void open() {
        database = dbHelper.getWritableDatabase();
    }

    public void close() {
        dbHelper.close();
    }

    public ChatMessage createMessage(ChatMessage message) {
        ContentValues values = new ContentValues();
        values.put(ConfigDatabase.COLUMN_CONTENT, message.getContent());
        values.put(ConfigDatabase.COLUMN_IS_USER, message.isUser() ? 1 : 0);
        values.put(ConfigDatabase.COLUMN_TIMESTAMP, message.getTimestamp());
        values.put(ConfigDatabase.COLUMN_CONFIG_ID, message.getConfigId());

        long id = database.insert(ConfigDatabase.TABLE_CHAT, null, values);
        message.setId(id);
        return message;
    }

    public List<ChatMessage> getMessagesByConfig(long configId) {
        List<ChatMessage> messages = new ArrayList<>();
        String selection = ConfigDatabase.COLUMN_CONFIG_ID + " = ?";
        String[] selectionArgs = {String.valueOf(configId)};

        Cursor cursor = database.query(
                ConfigDatabase.TABLE_CHAT,
                null,
                selection,
                selectionArgs,
                null, null,
                ConfigDatabase.COLUMN_TIMESTAMP + " ASC"); // 按时间升序排列

        while (cursor.moveToNext()) {
            ChatMessage message = new ChatMessage();
            message.setId(cursor.getLong(0));
            message.setContent(cursor.getString(1));
            message.setUser(cursor.getInt(2) == 1);
            message.setTimestamp(cursor.getLong(3));
            message.setConfigId(cursor.getLong(4));
            messages.add(message);
        }
        cursor.close();
        return messages;
    }

    public void deleteMessagesByConfig(long configId) {
        database.delete(
                ConfigDatabase.TABLE_CHAT,
                ConfigDatabase.COLUMN_CONFIG_ID + " = ?",
                new String[]{String.valueOf(configId)});
    }
    /**
     * 删除指定时间点之后的消息
     */
    public void deleteMessagesAfterTimestamp(long configId, long timestamp) {
        database.delete(
                ConfigDatabase.TABLE_CHAT,
                ConfigDatabase.COLUMN_CONFIG_ID + " = ? AND " +
                        ConfigDatabase.COLUMN_TIMESTAMP + " > ?",
                new String[]{String.valueOf(configId), String.valueOf(timestamp)}
        );
    }

}
