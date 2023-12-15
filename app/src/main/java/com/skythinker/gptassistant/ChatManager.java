package com.skythinker.gptassistant;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import cn.hutool.crypto.digest.MD5;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;

@SuppressLint("Range")
public class ChatManager{

    // 用于存储一条聊天消息
    public static class ChatMessage {
        static private Context context;
        static public void setContext(Context context) { ChatMessage.context = context; }

        // 用于标记消息的角色
        public enum ChatRole {
            SYSTEM,
            USER,
            ASSISTANT,
            FUNCTION;

            public static ChatRole fromName(String name) {
                for (ChatRole role : ChatRole.values()) {
                    if (role.name().equals(name)) {
                        return role;
                    }
                }
                return null;
            }
        }

        public ChatRole role;
        public String contentText;
        public String contentImageBase64;
        private String imageUuid;
        public String functionName;

        public ChatMessage(ChatRole role) { this.role = role; }

        public ChatMessage setText(String text) {
            this.contentText = text;
            return this;
        }

        public ChatMessage setImage(String base64) {
            this.contentImageBase64 = base64;
            this.imageUuid = UUID.randomUUID().toString();
            return this;
        }

        public void deleteImageFile() {
            if(imageUuid != null) {
                File file = new File(getImagePath(imageUuid));
                if(file.exists()) {
                    file.delete();
                }
            }
        }

        public ChatMessage setFunction(String name) {
            this.functionName = name;
            return this;
        }

        static public String getImagePath(String uuid) {
            return context.getFilesDir().getAbsolutePath() + "/images/" + uuid + ".jpg";
        }

        // 将消息转换为json
        public JSONObject toJson() {
            if(imageUuid != null) { // 将图片数据保存到文件中，只存储uuid
                try {
                    File file = new File(getImagePath(imageUuid));
                    if(!file.exists()) {
                        file.getParentFile().mkdirs();
                        file.createNewFile();
                        FileOutputStream fos = new FileOutputStream(file);
                        fos.write(Base64.decode(contentImageBase64, Base64.NO_WRAP));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            JSONObject json = new JSONObject();
            json.putOpt("role", role.name())
                    .putOpt("text", contentText)
                    .putOpt("image", imageUuid)
                    .putOpt("function", functionName);
            return json;
        }

        // 从json中读取消息
        public static ChatMessage fromJson(JSONObject json, boolean loadImage) {
            ChatMessage msg = new ChatMessage(ChatRole.fromName(json.getStr("role", "USER")));
            msg.contentText = json.getStr("text", null);
            msg.imageUuid = json.getStr("image", null);
            msg.functionName = json.getStr("function", null);

            if(loadImage && msg.imageUuid != null) { // 从文件中读取图片数据
                try {
                    File file = new File(getImagePath(msg.imageUuid));
                    byte[] imageBytes = new byte[(int) file.length()];
                    FileInputStream fis = new FileInputStream(file);
                    fis.read(imageBytes);
                    msg.contentImageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return msg;
        }
    }

    // 用于存储一轮对话中的一组聊天消息
    public static class MessageList extends ArrayList<ChatMessage> {
        private ChatManager manager;
        public long conversationId;

        public MessageList(ChatManager manager, long conversationId) {
            this.manager = manager;
            this.conversationId = conversationId;
        }

        public void update() { manager.updateMessages(conversationId, this); } // 更新数据库

        public boolean addWithoutUpdate(ChatMessage chatMessage) { return super.add(chatMessage); } // 添加条目，但不更新数据库

        @Override
        public boolean add(ChatMessage chatMessage) {
            boolean result = super.add(chatMessage);
            update();
            return result;
        }

        @Override
        public ChatMessage remove(int index) {
            if(index >= 0 && index < size())
                get(index).deleteImageFile();
            ChatMessage result = super.remove(index);
            update();
            return result;
        }

        @Override
        public boolean remove(@Nullable Object obj) {
            if(obj instanceof ChatMessage)
                ((ChatMessage) obj).deleteImageFile();
            boolean result = super.remove(obj);
            update();
            return result;
        }

        @Override
        public ChatMessage set(int index, ChatMessage element) {
            ChatMessage result = super.set(index, element);
            update();
            return result;
        }

        public void deleteAllImageFiles() {
            for(ChatMessage msg : this) {
                msg.deleteImageFile();
            }
        }

        public JSONArray toJson() {
            JSONArray json = new JSONArray();
            for(ChatMessage msg : this) {
                json.put(msg.toJson());
            }
            return json;
        }

        public static MessageList fromJson(ChatManager manager, long conversationId, JSONArray json, boolean loadImages) {
            MessageList list = new MessageList(manager, conversationId);
            for(int i = 0; i < json.size(); i++) {
                list.addWithoutUpdate(ChatMessage.fromJson(json.getJSONObject(i), loadImages));
            }
            return list;
        }
    }

    // 一轮聊天的信息
    public static class Conversation {
        public long id;
        public LocalDateTime time;
        public String title;
        public MessageList messages;
    }

    // 数据库管理器
    private class DatabaseHelper extends SQLiteOpenHelper {
        final static private String databaseName = "chat.db";
        final static private String tableName = "conversations";
        final static private int version = 1;
        public DatabaseHelper(Context context) {
            super(context, databaseName, null, version);
        }

        @Override
        public void onCreate(SQLiteDatabase sqLiteDatabase) {
            String sql = "CREATE TABLE " + tableName + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "time TEXT," +
                    "title TEXT," +
                    "messages TEXT" +
                    ");";
            sqLiteDatabase.execSQL(sql);
        }

        @Override
        public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) { }
    }

    private DatabaseHelper dbHelper;
    private SQLiteDatabase db;

    public ChatManager(Context context) {
        dbHelper = new DatabaseHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    public void destroy() { db.close(); }

    // 获取数据库会话数量
    public long getConversationCount() {
        Cursor cursor = db.query(DatabaseHelper.tableName, new String[]{"COUNT(*)"}, null, null, null, null, null);
        cursor.moveToFirst();
        return cursor.getLong(0);
    }

    // 从数据库游标中读取会话信息
    private Conversation getConversationByCursor(Cursor cursor, boolean loadImages) {
        Conversation conversation = new Conversation();
        conversation.id = cursor.getLong(cursor.getColumnIndex("id"));
        conversation.time = LocalDateTime.parse(cursor.getString(cursor.getColumnIndex("time")), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        conversation.title = cursor.getString(cursor.getColumnIndex("title"));
        conversation.messages = MessageList.fromJson(this, conversation.id, new JSONArray(cursor.getString(cursor.getColumnIndex("messages"))), loadImages);
        return conversation;
    }
    private Conversation getConversationByCursor(Cursor cursor) {
        return getConversationByCursor(cursor, true);
    }

    // 根据会话ID获取会话
    public Conversation getConversation(long id) {
        Cursor cursor = db.query(DatabaseHelper.tableName, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor.moveToFirst()) {
            return getConversationByCursor(cursor);
        }
        return null;
    }

    // 根据会话在数据库中的位置获取会话（按时间倒序）
    public Conversation getConversationAtPosition(int position) {
        Cursor cursor = db.query(DatabaseHelper.tableName, null, null, null, null, null, "id DESC", String.valueOf(position) + ",1");
        if (cursor.moveToFirst()) {
            return getConversationByCursor(cursor);
        }
        return null;
    }

    // 获取所有会话（按时间倒序）
    public List<Conversation> getAllConversations() {
        Cursor cursor = db.query(DatabaseHelper.tableName, null, null, null, null, null, "id DESC");
        List<Conversation> conversations = new ArrayList<>();
        while (cursor.moveToNext()) {
            Conversation conversation = getConversationByCursor(cursor);
            conversations.add(conversation);
        }
        return conversations;
    }

    // 新建会话并添加到数据库
    public Conversation newConversation() {
        Conversation conversation = new Conversation();
        conversation.time = LocalDateTime.now();
        conversation.title = "新会话";

        ContentValues values = new ContentValues();
        values.put("time", conversation.time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        values.put("title", conversation.title);
        values.put("messages", new JSONArray().toString());

        conversation.id = db.insert(DatabaseHelper.tableName, null, values);
        conversation.messages = new MessageList(this, conversation.id);
        return conversation;
    }

    // 更新会话的聊天消息
    public void updateMessages(long id, MessageList messages) {
        ContentValues values = new ContentValues();
        values.put("messages", messages.toJson().toString());
        db.update(DatabaseHelper.tableName, values, "id=?", new String[]{String.valueOf(id)});
    }

    // 更新会话的标题
    public void updateTitle(long id, String title) {
        ContentValues values = new ContentValues();
        values.put("title", title);
        db.update(DatabaseHelper.tableName, values, "id=?", new String[]{String.valueOf(id)});
    }

    // 删除指定ID的会话
    public void removeConversation(long id) {
        Cursor cursor = db.query(DatabaseHelper.tableName, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (cursor.moveToFirst()) {
            Conversation conversation = getConversationByCursor(cursor, false);
            conversation.messages.deleteAllImageFiles();
        }
        db.delete(DatabaseHelper.tableName, "id=?", new String[]{String.valueOf(id)});
    }

    // 删除所有会话
    public void removeAllConversations(boolean includeLatest) {
        if(includeLatest) {
            File imageDir = new File(ChatMessage.getImagePath("abc")).getParentFile(); // 删除所有图片文件
            if(imageDir.exists()) {
                for(File file : imageDir.listFiles()) {
                    file.delete();
                }
            }
            db.delete(DatabaseHelper.tableName, null, null);
        } else {
            Cursor cursor = db.query(DatabaseHelper.tableName, null, null, null, null, null, "id DESC");
            if (cursor.moveToFirst()) {
                Conversation conversation = getConversationByCursor(cursor, false); // 获取最新会话的图片MD5列表
                List<String> imageMD5s = new ArrayList<>();
                for(ChatMessage message : conversation.messages) {
                    if(message.imageUuid != null) {
                        imageMD5s.add(message.imageUuid);
                    }
                }
                File imageDir = new File(ChatMessage.getImagePath("abc")).getParentFile(); // 删除所有不匹配的图片文件
                if(imageDir.exists()) {
                    for(File file : imageDir.listFiles()) {
                        if(!imageMD5s.contains(file.getName().replace(".jpg", ""))) {
                            file.delete();
                        }
                    }
                }
                db.delete(DatabaseHelper.tableName, "id!=?", new String[]{String.valueOf(conversation.id)});
            }
        }
    }

    // 删除所有空会话
    public void removeEmptyConversations(boolean includeLatest) {
        if(includeLatest) {
            db.delete(DatabaseHelper.tableName, "messages=?", new String[]{"[]"});
        } else {
            Cursor cursor = db.query(DatabaseHelper.tableName, null, null, null, null, null, "id DESC");
            if (cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndex("id"));
                db.delete(DatabaseHelper.tableName, "id!=? AND messages=?", new String[]{String.valueOf(id), "[]"});
            }
        }
    }
}
