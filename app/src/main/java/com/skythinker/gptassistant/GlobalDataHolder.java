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
import java.util.Arrays;
import java.util.List;

public class GlobalDataHolder {
    private static List<PromptTabData> tabDataList = null;
    private static boolean asrUseWhisper;
    private static boolean asrUseGoogle;
    private static boolean asrUseBaidu;
    private static String asrAppId;
    private static String asrApiKey;
    private static String asrSecretKey;
    private static boolean asrUseRealTime;
    private static String gptApiHost;
    private static String gptApiKey;
    private static String gptModel;
    private static float gptTemperature;
    private static List<String> customModels = null;
    private static boolean checkAccessOnStart;
    private static boolean defaultEnableTts;
    private static boolean defaultEnableMultiChat;
    private static int selectedTab;
    private static boolean enableInternetAccess;
    private static int webMaxCharCount;
    private static boolean onlyLatestWebResult;
    private static boolean limitVisionSize;
    private static boolean autoSaveHistory;
    private static SharedPreferences sp = null;

    public static void init(Context context) {
        sp = context.getSharedPreferences("gpt_assistant", Context.MODE_PRIVATE);
        loadTabDataList();
        if(tabDataList.size() == 0) {
            tabDataList.add(new PromptTabData(context.getString(R.string.text_default_tab_title), context.getString(R.string.text_default_tab_content)));
            saveTabDataList();
        }
        loadAsrSelection();
        loadBaiduAsrInfo();
        loadGptApiInfo();
        loadModelParams();
        loadStartUpSetting();
        loadTtsSetting();
        loadMultiChatSetting();
        loadSelectedTab();
        loadFunctionSetting();
        loadVisionSetting();
        loadHistorySetting();
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

    public static void loadAsrSelection() {
        asrUseWhisper = sp.getBoolean("asr_use_whisper", false);
        asrUseBaidu = sp.getBoolean("asr_use_baidu", false);
        asrUseGoogle = sp.getBoolean("asr_use_google", false);
    }

    public static void saveAsrSelection(boolean useWhisper, boolean useBaidu, boolean useGoogle) {
        asrUseWhisper = useWhisper;
        asrUseBaidu = useBaidu;
        asrUseGoogle = useGoogle;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("asr_use_whisper", asrUseWhisper);
        editor.putBoolean("asr_use_baidu", asrUseBaidu);
        editor.putBoolean("asr_use_google", asrUseGoogle);
        editor.apply();
    }

    public static void loadBaiduAsrInfo() {
        asrAppId = sp.getString("asr_app_id", "");
        asrApiKey = sp.getString("asr_api_key", "");
        asrSecretKey = sp.getString("asr_secret_key", "");
        asrUseRealTime = sp.getBoolean("asr_use_real_time", false);
    }

    public static void saveBaiduAsrInfo(String appId, String apiKey, String secretKey, boolean useRealTime) {
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
        gptApiHost = sp.getString("gpt_api_host", "https://api.openai.com/");
        gptApiKey = sp.getString("gpt_api_key", "");
        gptModel = sp.getString("gpt_model", "gpt-3.5-turbo");
        customModels = new ArrayList<>(Arrays.asList(sp.getString("custom_models", "").split(";")));
        customModels.removeIf(String::isEmpty);
    }

    public static void saveGptApiInfo(String host, String key, String model, List<String> customModelList) {
        gptApiHost = host;
        gptApiKey = key;
        gptModel = model;
        customModels = customModelList;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("gpt_api_host", gptApiHost);
        editor.putString("gpt_api_key", gptApiKey);
        editor.putString("gpt_model", gptModel);
        editor.putString("custom_models", String.join(";", customModels));
        editor.apply();
    }

    public static void loadModelParams() {
        gptTemperature = sp.getFloat("model_temperature", 0.5f);
    }

    public static void saveModelParams(float temperature) {
        gptTemperature = temperature;
        SharedPreferences.Editor editor = sp.edit();
        editor.putFloat("model_temperature", gptTemperature);
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

    public static void loadSelectedTab() {
        selectedTab = sp.getInt("selected_tab", -1);
    }

    public static void saveSelectedTab(int tab) {
        selectedTab = tab;
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("selected_tab", selectedTab);
        editor.apply();
    }

    public static void loadFunctionSetting() {
        enableInternetAccess = sp.getBoolean("enable_internet", false);
        webMaxCharCount = sp.getInt("web_max_char_count", 2000);
        onlyLatestWebResult = sp.getBoolean("only_latest_web_result", false);
    }

    public static void saveFunctionSetting(boolean enableInternet, int maxCharCount, boolean onlyLatest) {
        enableInternetAccess = enableInternet;
        webMaxCharCount = maxCharCount;
        onlyLatestWebResult = onlyLatest;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("enable_internet", enableInternetAccess);
        editor.putInt("web_max_char_count", webMaxCharCount);
        editor.putBoolean("only_latest_web_result", onlyLatestWebResult);
        editor.apply();
    }

    public static void loadVisionSetting() {
        limitVisionSize = sp.getBoolean("limit_vision_size", false);
    }

    public static void saveVisionSetting(boolean limitSize) {
        limitVisionSize = limitSize;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("limit_vision_size", limitVisionSize);
        editor.apply();
    }

    public static void loadHistorySetting() {
        autoSaveHistory = sp.getBoolean("auto_save_history", true);
    }

    public static void saveHistorySetting(boolean autoSave) {
        autoSaveHistory = autoSave;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("auto_save_history", autoSaveHistory);
        editor.apply();
    }

    public static boolean getAsrUseWhisper() { return asrUseWhisper; }

    public static boolean getAsrUseGoogle() { return asrUseGoogle; }

    public static boolean getAsrUseBaidu() { return asrUseBaidu; }

    public static String getAsrAppId() { return asrAppId; }

    public static String getAsrApiKey() { return asrApiKey; }

    public static String getAsrSecretKey() { return asrSecretKey; }

    public static boolean getAsrUseRealTime() { return asrUseRealTime; }

    public static String getGptApiHost() { return gptApiHost; }

    public static String getGptApiKey() { return gptApiKey; }

    public static String getGptModel() { return gptModel; }

    public static List<String> getCustomModels() { return customModels; }

    public static float getGptTemperature() {return gptTemperature; }

    public static boolean getCheckAccessOnStart() { return checkAccessOnStart; }

    public static boolean getDefaultEnableTts() { return defaultEnableTts; }

    public static boolean getDefaultEnableMultiChat() { return defaultEnableMultiChat; }

    public static int getSelectedTab() { return selectedTab; }

    public static boolean getEnableInternetAccess() { return enableInternetAccess; }

    public static int getWebMaxCharCount() { return webMaxCharCount; }

    public static boolean getOnlyLatestWebResult() { return onlyLatestWebResult; }

    public static boolean getLimitVisionSize() { return limitVisionSize; }

    public static boolean getAutoSaveHistory() { return autoSaveHistory; }
}
