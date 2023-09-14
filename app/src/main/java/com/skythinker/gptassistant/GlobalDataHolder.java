package com.skythinker.gptassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

public class GlobalDataHolder {
    private static List<PromptTabData> tabDataList = null;
    private static String asrAppId;
    private static String asrApiKey;
    private static String asrSecretKey;
    private static boolean asrUseRealTime;
    private static String gptApiHost;
    private static String gptApiKey;
    private static boolean gpt4Enable;
    private static boolean checkAccessOnStart;
    private static boolean defaultEnableTts;
    private static boolean defaultEnableMultiChat;
    private static SharedPreferences sp = null;

    public static void init(Context context) {
        sp = context.getSharedPreferences("gpt_assistant", Context.MODE_PRIVATE);
        loadTabDataList();
        if(tabDataList.size() == 0) {
            tabDataList.add(new PromptTabData("问答", "请回答这个问题"));
            saveTabDataList();
        }
        loadAsrInfo();
        loadGptApiInfo();
        loadStartUpSetting();
        loadTtsSetting();
        loadMultiChatSetting();
    }

    public static List<PromptTabData> getTabDataList() {
        return tabDataList;
    }

    public static void saveTabDataList() {
        SharedPreferences.Editor editor = sp.edit();
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(tabDataList);
            String base64 = Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
            editor.putString("tab_data_list", base64);
            editor.apply();
            Log.d("saveTabDataList", "saved");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadTabDataList() {
        String base64 = sp.getString("tab_data_list", "");
        if (base64.equals("")) {
            tabDataList = new ArrayList<>();
            return;
        }
        byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            tabDataList = (List<PromptTabData>) (new ObjectInputStream(bais).readObject());
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadAsrInfo() {
        asrAppId = sp.getString("asr_app_id", "");
        asrApiKey = sp.getString("asr_api_key", "");
        asrSecretKey = sp.getString("asr_secret_key", "");
        asrUseRealTime = sp.getBoolean("asr_use_real_time", false);
    }

    public static void saveAsrInfo(String appId, String apiKey, String secretKey, boolean useRealTime) {
        asrApiKey = apiKey;
        asrAppId = appId;
        asrSecretKey = secretKey;
        asrUseRealTime = useRealTime;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("asr_app_id", asrAppId);
        editor.putString("asr_api_key", asrApiKey);
        editor.putString("asr_secret_key", asrSecretKey);
        editor.putBoolean("asr_use_real_time", asrUseRealTime);
        editor.apply();
    }

    public static void loadGptApiInfo() {
        gptApiHost = sp.getString("gpt_api_host", "");
        gptApiKey = sp.getString("gpt_api_key", "");
        gpt4Enable = sp.getBoolean("gpt4_enable", false);
    }

    public static void saveGptApiInfo(String host, String key, boolean gpt4) {
        gptApiHost = host;
        gptApiKey = key;
        gpt4Enable = gpt4;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("gpt_api_host", gptApiHost);
        editor.putString("gpt_api_key", gptApiKey);
        editor.putBoolean("gpt4_enable", gpt4Enable);
        editor.apply();
    }

    public static void loadStartUpSetting() {
        checkAccessOnStart = sp.getBoolean("check_access_on_start", true);
    }

    public static void saveStartUpSetting(boolean checkAccess) {
        checkAccessOnStart = checkAccess;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("check_access_on_start", checkAccessOnStart);
        editor.apply();
    }

    public static void loadTtsSetting() {
        defaultEnableTts = sp.getBoolean("tts_enable", true);
    }

    public static void saveTtsSetting(boolean enable) {
        defaultEnableTts = enable;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("tts_enable", defaultEnableTts);
        editor.apply();
    }

    public static void loadMultiChatSetting() {
        defaultEnableMultiChat = sp.getBoolean("default_enable_multi_chat", false);
    }

    public static void saveMultiChatSetting(boolean defaultEnable) {
        defaultEnableMultiChat = defaultEnable;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("default_enable_multi_chat", defaultEnableMultiChat);
        editor.apply();
    }

    public static String getAsrAppId() { return asrAppId; }

    public static String getAsrApiKey() { return asrApiKey; }

    public static String getAsrSecretKey() { return asrSecretKey; }

    public static boolean getAsrUseRealTime() { return asrUseRealTime; }

    public static String getGptApiHost() { return gptApiHost; }

    public static String getGptApiKey() { return gptApiKey; }

    public static boolean getGpt4Enable() { return gpt4Enable; }

    public static boolean getCheckAccessOnStart() { return checkAccessOnStart; }

    public static boolean getDefaultEnableTts() { return defaultEnableTts; }

    public static boolean getDefaultEnableMultiChat() { return defaultEnableMultiChat; }
}
