package com.skythinker.gptassistant.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import com.skythinker.gptassistant.BuildConfig;
import com.skythinker.gptassistant.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
    // 主界面功能按钮布局的持久化 JSON。
    private static String mainActionLayout;
    private static SharedPreferences sp = null;

    public static void init(Context context) {
        sp = context.getSharedPreferences("gpt_assistant", Context.MODE_PRIVATE);
        boolean isFreshInstall = sp.getAll().isEmpty();
        loadTabDataList();
        if(tabDataList.size() == 0) {
            tabDataList.addAll(buildInitialTabDataList(context));
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
    }

    public static void saveUpdateSetting(String latestVersionName) {
        latestVersion = latestVersionName;
        SharedPreferences.Editor editor = sp.edit();
        editor.putString("latest_version", latestVersion);
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

    // 构建首次安装时预置的推荐模板列表。
    private static List<PromptTabData> buildInitialTabDataList(Context context) {
        boolean isChinese = Locale.getDefault().getLanguage().equals("zh");
        ArrayList<PromptTabData> initialTabs = new ArrayList<>();
        String translateTitle = isChinese ? "翻译" : "Translate";
        String summaryTitle = isChinese ? "总结" : "Summarize";
        String writingTitle = isChinese ? "撰写" : "Writing";
        String attachmentTitle = isChinese ? "附件" : "Attachment Review";
        String webSearchTitle = isChinese ? "搜索" : "Web Search";
        String voiceTitle = isChinese ? "语音" : "Voice Chat";
        initialTabs.add(new PromptTabData(
                context.getString(R.string.text_default_tab_title),
                context.getString(R.string.text_default_tab_content)
        ));
        initialTabs.add(new PromptTabData(translateTitle, buildDefaultTranslatePrompt(isChinese)));
        initialTabs.add(new PromptTabData(summaryTitle, buildDefaultSummaryPrompt(isChinese)));
        initialTabs.add(new PromptTabData(writingTitle, buildDefaultWritingPrompt(isChinese)));
        initialTabs.add(new PromptTabData(attachmentTitle, buildDefaultAttachmentPrompt(isChinese)));
        initialTabs.add(new PromptTabData(webSearchTitle, buildDefaultWebSearchPrompt(isChinese)));
        initialTabs.add(new PromptTabData(voiceTitle, buildDefaultVoicePrompt(isChinese)));
        return initialTabs;
    }

    private static String buildDefaultTranslatePrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "@network false",
                    "@chat false",
                    "@select 目标语言|简体中文|英语|日语|韩语|俄语|法语|德语|阿拉伯语",
                    "@select 翻译语气|正式的|口语的",
                    "\"\"\"",
                    "你是一个专业的翻译员，在用户每次提问时，你需要识别出用户所使用的语言，然后使用${翻译语气}语气翻译为${目标语言}，不要解释其他内容。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "@network false",
                "@chat false",
                "@select Target language|Simplified Chinese|English|Japanese|Korean|Russian|French|German|Arabic",
                "@select Tone|Formal|Casual",
                "\"\"\"",
                "You are a professional translator. For each user message, detect the source language and translate it into ${Target language} with a ${Tone} tone. Do not add explanations."
        );
    }

    private static String buildDefaultSummaryPrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "@select 格式|单个摘要段落|主题+列出观点|按时间线分点总结|仅关键字",
                    "@select 语言|简体中文|繁体中文|英文|日文|韩文|法文|俄文|德文",
                    "\"\"\"",
                    "你是一个擅长理解和总结文段的AI助手。助手会阅读用户发送的文段，然后以【${格式}】的格式进行概括，以帮助用户理解文段的主要内容。助手会删去不必要的细节，并使用${语言}进行回复。助手不会对输出的内容进行任何解释。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "@select Format|Single summary paragraph|Topic + key points|Timeline bullets|Keywords only",
                "@select Language|English|Simplified Chinese|Traditional Chinese|Japanese|Korean|French|Russian|German",
                "\"\"\"",
                "You are an AI assistant who is good at reading and summarizing text. Read the user-provided content and summarize it in the format of [${Format}] to help the user quickly understand the main ideas. Remove unnecessary details and reply in ${Language}. Do not add explanations."
        );
    }

    private static String buildDefaultWritingPrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "@select 格式|文章|朋友圈文案|小红书分享|微博文章|群公告|邮件|大纲|广告|评论|消息",
                    "@select 语气|正式|口语化|专业|幽默|热情",
                    "@select 长度|[较短]100字以内|[中等]300字左右|[较长]500字以上",
                    "@select 语言|简体中文|繁体中文|英文|日文|韩文|法文|俄文|德文",
                    "\"\"\"",
                    "你是一个${格式}撰写助手，助手需要根据用户输入的需求，使用${语气}的语气，用${语言}生成一份${长度}的${格式}，不要解释其他内容。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "@select Format|Article|Social post|Short-form share|Microblog post|Announcement|Email|Outline|Ad copy|Comment|Message",
                "@select Tone|Formal|Conversational|Professional|Humorous|Warm",
                "@select Length|[Short]Within 100 words|[Medium]About 300 words|[Long]Over 500 words",
                "@select Language|English|Simplified Chinese|Traditional Chinese|Japanese|Korean|French|Russian|German",
                "\"\"\"",
                "You are a ${Format} writing assistant. Based on the user's request, write a ${Length} ${Format} in ${Language} with a ${Tone} tone. Do not add explanations."
        );
    }

    private static String buildDefaultAttachmentPrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "@select 输出方式|直接回答问题|先总结再回答|仅提取关键点",
                    "\"\"\"",
                    "你是一个附件阅读助手。用户可能会附带文档、图片或复制文本。",
                    "请优先依据用户提供的附件和文本内容进行分析。",
                    "如果用户提出了明确问题，就按“${输出方式}”处理后作答；",
                    "如果用户没有明确问题，就先概括核心内容，再给出关键信息。",
                    "如果信息不足，请明确指出缺少什么，不要编造。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "@select Output style|Answer the question directly|Summarize first, then answer|Extract key points only",
                "\"\"\"",
                "You are an attachment-reading assistant. The user may provide documents, images, or pasted text.",
                "Always prioritize the provided attachments and text when analyzing the request.",
                "If the user asks a clear question, respond using the \"${Output style}\" style.",
                "If there is no clear question, summarize the core content first and then provide the most important information.",
                "If the information is insufficient, clearly say what is missing instead of making things up."
        );
    }

    private static String buildDefaultWebSearchPrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "@network true",
                    "@speak false",
                    "@select 搜索平台|[百度]百度搜索(https://www.baidu.com/s?wd=xxx)|[必应]必应搜索(https://www.bing.com/search?q=xxx)|[谷歌]谷歌搜索(https://www.google.com/search?q=xxx)",
                    "\"\"\"",
                    "你是一个可以联网的资料收集助手。助手会通过${搜索平台}查找用户的提问，然后根据搜索结果页面的内容回答用户的问题，并且附上前三个搜索结果链接。助手会在搜索前对关键字进行URL编码，保证访问链接中没有空格等非法字符。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "@network true",
                "@speak false",
                "@select Search engine|[Baidu]Baidu Search(https://www.baidu.com/s?wd=xxx)|[Bing]Bing Search(https://www.bing.com/search?q=xxx)|[Google]Google Search(https://www.google.com/search?q=xxx)",
                "\"\"\"",
                "You are a research assistant with web access. Use ${Search engine} to search the user's question, then answer based on the search result page and include the first three useful result links. URL-encode search keywords before visiting the page so the generated links do not contain illegal characters such as spaces."
        );
    }

    private static String buildDefaultVoicePrompt(boolean isChinese) {
        if(isChinese) {
            return String.join("\n",
                    "\"\"\"",
                    "@system true",
                    "\"\"\"",
                    "你是一个语音聊天助手，你会接收用户的提问，并做出回答。用户将使用语音识别进行输入，因此你需要识别出提问中可能存在的错误并针对纠正后的问题进行回答。如果提问中的错误过多，你可以以没听清为由请用户重新提问。你的回复将使用TTS朗读给用户，因此你需要使用纯文本进行回复，不要输出任何含有markdown格式的内容。"
            );
        }
        return String.join("\n",
                "\"\"\"",
                "@system true",
                "\"\"\"",
                "You are a voice chat assistant. The user's input comes from speech recognition, so you should infer and correct likely recognition mistakes before answering. If the input is too unclear, ask the user to repeat it. Your reply will be read aloud by TTS, so use plain text only and avoid markdown formatting."
        );
    }
}
