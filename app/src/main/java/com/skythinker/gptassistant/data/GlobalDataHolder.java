package com.skythinker.gptassistant.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.skythinker.gptassistant.BuildConfig;
import com.skythinker.gptassistant.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

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
    private static int gptMaxContextNum;
    // 用户维护的自定义模型配置列表。
    private static List<CustomModelProfile> customModelProfiles = null;
    // 音量键操作的业务开关，需与系统无障碍状态共同生效。
    private static boolean volumeKeyEnabled;
    // 首次启动时的 API 引导弹窗是否已显示。
    private static boolean onboardingApiPromptShown;
    // 首次启动时的音量键引导弹窗是否已显示。
    private static boolean onboardingVolumePromptShown;
    // 复用历史字段，保存主界面上次的 TTS / 多轮对话开关状态。
    private static boolean defaultEnableTts;
    private static boolean defaultEnableMultiChat;
    private static int selectedTab;
    private static boolean enableInternetAccess;
    private static int webMaxCharCount;
    private static boolean onlyLatestWebResult;
    private static boolean limitVisionSize;
    private static boolean autoSaveHistory;
    private static boolean useGitee;
    private static boolean agentMode;
    private static String latestVersion;
    private static long lastUpdateCheckTime;
    // 主界面功能按钮布局的持久化 JSON。
    private static String mainActionLayout;
    private static SharedPreferences sp = null;

    public static void init(Context context) {
        sp = context.getSharedPreferences("gpt_assistant", Context.MODE_PRIVATE);
        boolean isFreshInstall = sp.getAll().isEmpty();
        loadTabDataList();
        if(tabDataList == null) {
            tabDataList = new ArrayList<>();
        }
        if(tabDataList.size() == 0) {
            tabDataList.addAll(getDefaultTabDataList(context));
            if(tabDataList.size() == 0) {
                tabDataList.add(buildEmptyQaTab(context));
            }
            saveTabDataList();
        }
        loadAsrSelection();
        loadBaiduAsrInfo();
        loadGptApiInfo();
        loadModelParams();
        loadVolumeKeySetting(isFreshInstall);
        loadOnboardingSetting(isFreshInstall);
        loadTtsSetting();
        loadMultiChatSetting();
        loadSelectedTab();
        loadFunctionSetting();
        loadVisionSetting();
        loadHistorySetting();
        loadOnlineResourceSetting();
        loadAgentModeSetting();
        loadUpdateSetting();
        loadMainActionLayout();
    }

    public static List<PromptTabData> getTabDataList() {
        return tabDataList;
    }

    public static void saveTabDataList() {
        if(tabDataList == null) {
            tabDataList = new ArrayList<>();
        }
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

    static class TabDataInputStream extends ObjectInputStream {
        public TabDataInputStream(ByteArrayInputStream bais) throws IOException {
            super(bais);
        }

        @Override
        protected Class<?> resolveClass(java.io.ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (desc.getName().equals("com.skythinker.gptassistant.PromptTabData")) { // old package name
                Log.d("GlobalDataHolder", "resolved old PromptTabData class");
                return PromptTabData.class;
            }
            return super.resolveClass(desc);
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
            tabDataList = (List<PromptTabData>) (new TabDataInputStream(bais).readObject());
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
            tabDataList = new ArrayList<>();
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

    // 加载大模型接口配置，并兼容旧版本模型存储格式。
    public static void loadGptApiInfo() {
        gptApiHost = sp.getString("gpt_api_host", "https://api.openai.com/");
        gptApiKey = sp.getString("gpt_api_key", "");
        gptModel = sp.getString("gpt_model", "");
        gptModel = gptModel.replaceAll("\\*+$", "");
        customModelProfiles = new ArrayList<>();
        boolean modelProfilesInitialized = sp.getBoolean("model_profiles_initialized", false);

        String customModelProfilesJson = sp.getString("custom_model_profiles", "");
        if(!customModelProfilesJson.isEmpty()) {
            try {
                JSONArray jsonArray = new JSONArray(customModelProfilesJson);
                for(int i = 0; i < jsonArray.length(); i++) {
                    CustomModelProfile profile = CustomModelProfile.fromJson(jsonArray.optJSONObject(i));
                    if(!profile.id.isEmpty()) {
                        customModelProfiles.add(profile);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if(!sp.getString("custom_models", "").isEmpty()) {
            // 兼容旧版本的本地设置，导入后立即转为新结构存储。
            String oldCustomModels = sp.getString("custom_models", "");
            for(String oldModel : oldCustomModels.split(";")) {
                String trimmedModel = oldModel.trim();
                if(trimmedModel.isEmpty()) {
                    continue;
                }
                ArrayList<String> capabilities = new ArrayList<>();
                if(trimmedModel.endsWith("*")) {
                    trimmedModel = trimmedModel.replaceAll("\\*+$", "");
                    capabilities.add(ModelCatalog.CAPABILITY_VISION);
                }
                CustomModelProfile profile = ModelCatalog.createProfileWithKnownDefaults(trimmedModel);
                profile.capabilities.addAll(capabilities); // 保留旧版*号显式声明的视觉能力
                customModelProfiles.add(profile);
            }
            customModelProfiles = sanitizeCustomModelProfiles(customModelProfiles);
            saveGptApiInfo(gptApiHost, gptApiKey, gptModel, customModelProfiles);
            return;
        } else if(!modelProfilesInitialized) {
            // 首次使用时写入一组可编辑的默认模型，后续用户删除后不再自动恢复。
            customModelProfiles = new ArrayList<>(ModelCatalog.buildInitialModelProfiles());
            if(gptModel.isEmpty() && customModelProfiles.size() > 0) {
                gptModel = customModelProfiles.get(0).id;
            }
            saveGptApiInfo(gptApiHost, gptApiKey, gptModel, customModelProfiles);
            sp.edit().putBoolean("model_profiles_initialized", true).apply();
            return;
        }

        customModelProfiles = sanitizeCustomModelProfiles(customModelProfiles);
        if(gptModel.isEmpty() && customModelProfiles.size() > 0) {
            gptModel = customModelProfiles.get(0).id;
            saveGptApiInfo(gptApiHost, gptApiKey, gptModel, customModelProfiles);
        } else if(gptModel.isEmpty()) {
            gptModel = "gpt-3.5-turbo";
        }
    }

    // 保存大模型接口配置和自定义模型列表。
    public static void saveGptApiInfo(String host, String key, String model, List<CustomModelProfile> customModelList) {
        gptApiHost = host;
        gptApiKey = key;
        gptModel = model == null ? "" : model.replaceAll("\\*+$", "");
        customModelProfiles = sanitizeCustomModelProfiles(customModelList);
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("gpt_api_host", gptApiHost);
        editor.putString("gpt_api_key", gptApiKey);
        editor.putString("gpt_model", gptModel);
        JSONArray jsonArray = new JSONArray();
        for(CustomModelProfile profile : customModelProfiles) {
            jsonArray.put(profile.toJson());
        }
        editor.putString("custom_model_profiles", jsonArray.toString());
        editor.apply();
    }

    public static void loadModelParams() {
        gptTemperature = sp.getFloat("model_temperature", 0.5f);
        gptMaxContextNum = sp.getInt("max_context_num", 10);
    }

    public static void saveModelParams(float temperature, int maxContextNum) {
        gptTemperature = temperature;
        gptMaxContextNum = maxContextNum;
        SharedPreferences.Editor editor = sp.edit();
        editor.putFloat("model_temperature", gptTemperature);
        editor.putInt("max_context_num", gptMaxContextNum);
        editor.apply();
    }

    // 加载音量键操作开关；旧版本升级默认保持开启并等待后续同步。
    public static void loadVolumeKeySetting(boolean isFreshInstall) {
        if(sp.contains("volume_key_enabled")) {
            volumeKeyEnabled = sp.getBoolean("volume_key_enabled", false);
        } else {
            volumeKeyEnabled = !isFreshInstall;
            sp.edit().putBoolean("volume_key_enabled", volumeKeyEnabled).apply();
        }
    }

    public static void saveVolumeKeySetting(boolean enabled) {
        volumeKeyEnabled = enabled;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("volume_key_enabled", volumeKeyEnabled);
        editor.apply();
    }

    // 加载首次引导状态；旧版本升级默认视为已引导，避免打扰老用户。
    public static void loadOnboardingSetting(boolean isFreshInstall) {
        if(sp.contains("onboarding_api_prompt_shown")) {
            onboardingApiPromptShown = sp.getBoolean("onboarding_api_prompt_shown", false);
        } else {
            onboardingApiPromptShown = !isFreshInstall;
            sp.edit().putBoolean("onboarding_api_prompt_shown", onboardingApiPromptShown).apply();
        }
        if(sp.contains("onboarding_volume_prompt_shown")) {
            onboardingVolumePromptShown = sp.getBoolean("onboarding_volume_prompt_shown", false);
        } else {
            onboardingVolumePromptShown = !isFreshInstall;
            sp.edit().putBoolean("onboarding_volume_prompt_shown", onboardingVolumePromptShown).apply();
        }
    }

    public static void saveOnboardingApiPromptShown(boolean shown) {
        onboardingApiPromptShown = shown;
        sp.edit().putBoolean("onboarding_api_prompt_shown", onboardingApiPromptShown).apply();
    }

    public static void saveOnboardingVolumePromptShown(boolean shown) {
        onboardingVolumePromptShown = shown;
        sp.edit().putBoolean("onboarding_volume_prompt_shown", onboardingVolumePromptShown).apply();
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
        defaultEnableMultiChat = sp.getBoolean("default_enable_multi_chat", true);
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

    public static void loadOnlineResourceSetting() {
        useGitee = sp.getBoolean("use_gitee", Locale.getDefault().getLanguage().equals("zh"));
    }

    public static void saveOnlineResourceSetting(boolean gitee) {
        useGitee = gitee;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("use_gitee", useGitee);
        editor.apply();
    }

    public static void loadAgentModeSetting() {
        agentMode = sp.getBoolean("agent_mode", true);
    }

    public static void saveAgentModeSetting(boolean enabled) {
        agentMode = enabled;
        SharedPreferences.Editor editor = sp.edit();
        editor.putBoolean("agent_mode", agentMode);
        editor.apply();
    }

    public static void loadUpdateSetting() {
        latestVersion = sp.getString("latest_version", BuildConfig.VERSION_NAME);
        lastUpdateCheckTime = sp.getLong("last_update_check_time", 0);
    }

    public static void saveUpdateSetting(String latestVersionName) {
        latestVersion = latestVersionName;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("latest_version", latestVersion);
        editor.apply();
    }

    public static void saveUpdateCheckTime(long updateCheckTime) {
        lastUpdateCheckTime = updateCheckTime;
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong("last_update_check_time", lastUpdateCheckTime);
        editor.apply();
    }

    // 读取主界面按钮布局配置。
    public static void loadMainActionLayout() {
        mainActionLayout = sp.getString("main_action_layout", "");
    }

    // 保存主界面按钮布局配置。
    public static void saveMainActionLayout(String layoutJson) {
        mainActionLayout = layoutJson;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("main_action_layout", mainActionLayout);
        editor.apply();
    }

    // 清空自定义按钮布局，回退到默认布局。
    public static void resetMainActionLayout() {
        saveMainActionLayout("");
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

    // 获取当前自定义模型列表。
    public static List<CustomModelProfile> getCustomModelProfiles() { return customModelProfiles; }

    public static float getGptTemperature() {return gptTemperature; }

    public static int getGptMaxContextNum() { return gptMaxContextNum; }

    public static boolean getVolumeKeyEnabled() { return volumeKeyEnabled; }

    public static boolean getOnboardingApiPromptShown() { return onboardingApiPromptShown; }

    public static boolean getOnboardingVolumePromptShown() { return onboardingVolumePromptShown; }

    public static boolean getDefaultEnableTts() { return defaultEnableTts; }

    public static boolean getDefaultEnableMultiChat() { return defaultEnableMultiChat; }

    public static int getSelectedTab() { return selectedTab; }

    public static boolean getEnableInternetAccess() { return enableInternetAccess; }

    public static int getWebMaxCharCount() { return webMaxCharCount; }

    public static boolean getOnlyLatestWebResult() { return onlyLatestWebResult; }

    public static boolean getLimitVisionSize() { return limitVisionSize; }

    public static boolean getAutoSaveHistory() { return autoSaveHistory; }

    public static boolean getUseGitee() { return useGitee; }

    public static boolean getAgentMode() { return agentMode; }

    public static String getLatestVersion() { return latestVersion; }

    public static long getLastUpdateCheckTime() { return lastUpdateCheckTime; }

    // 获取主界面按钮布局配置。
    public static String getMainActionLayout() { return mainActionLayout; }

    // 清理模型配置中的重复、空值和多余空白。
    private static List<CustomModelProfile> sanitizeCustomModelProfiles(List<CustomModelProfile> profileList) {
        LinkedHashMap<String, CustomModelProfile> profileMap = new LinkedHashMap<>();
        if(profileList != null) {
            for(CustomModelProfile profile : profileList) {
                if(profile == null || profile.id == null) {
                    continue;
                }
                CustomModelProfile normalizedProfile = profile.copy();
                normalizedProfile.id = normalizedProfile.id.trim();
                normalizedProfile.name = normalizedProfile.name == null ? "" : normalizedProfile.name.trim();
                normalizedProfile.capabilities = new ArrayList<>(normalizedProfile.capabilities);
                if(normalizedProfile.id.isEmpty()) {
                    continue;
                }
                profileMap.put(normalizedProfile.id, normalizedProfile);
            }
        }
        return new ArrayList<>(profileMap.values());
    }

    // 从资源文件读取默认模板，供首次启动和手动恢复时复用。
    public static List<PromptTabData> getDefaultTabDataList(Context context) {
        ArrayList<PromptTabData> defaultTabs = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(readRawText(context, getDefaultTemplateResId()));
            for(int i = 0; i < jsonArray.length(); i++) {
                JSONObject jsonObject = jsonArray.optJSONObject(i);
                if(jsonObject == null) {
                    continue;
                }
                String title = jsonObject.optString("title", "").trim();
                String prompt = jsonObject.optString("prompt", "");
                if(title.isEmpty() && prompt.isEmpty()) {
                    continue;
                }
                if(title.isEmpty()) {
                    title = context.getString(R.string.text_default_tab_title);
                }
                defaultTabs.add(new PromptTabData(title, prompt));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return defaultTabs;
    }

    // 生成一个空白的“问答”模板，避免模板列表被删空后无法继续使用。
    public static PromptTabData buildEmptyQaTab(Context context) {
        return new PromptTabData(context.getString(R.string.text_default_tab_title), "");
    }

    // 获取默认模板的资源 ID，根据系统语言自动适配中英文。
    private static int getDefaultTemplateResId() {
        if(Locale.getDefault().getLanguage().equals("zh")) {
            return R.raw.default_templates_zh;
        }
        return R.raw.default_templates;
    }

    // 按 UTF-8 读取内置模板 JSON，避免默认模板继续散落在代码里。
    private static String readRawText(Context context, int resId) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getResources().openRawResource(resId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {
            String line;
            while((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
