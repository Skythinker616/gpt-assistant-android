package com.skythinker.gptassistant.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.skythinker.gptassistant.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class ModelCatalog {

    // 视觉输入能力标记。
    public static final String CAPABILITY_VISION = "vision";
    // 工具调用能力标记。
    public static final String CAPABILITY_TOOL = "tool";
    // 思考内容能力标记。
    public static final String CAPABILITY_THINKING = "thinking";
    // 首次启动时写入的默认模型列表。
    private static final List<String> INITIAL_MODEL_IDS = Arrays.asList(
            "gpt-3.5-turbo",
            "gpt-4",
            "gpt-4-turbo",
            "gpt-4o",
            "gpt-4o-mini"
    );

    public static class KnownModelInfo {
        // 模型展示名称。
        public final String displayName;
        // 模型已知能力。
        public final List<String> capabilities;

        // 记录内置模型的展示信息。
        public KnownModelInfo(String displayName, List<String> capabilities) {
            this.displayName = displayName;
            this.capabilities = capabilities == null ? Collections.emptyList() : new ArrayList<>(capabilities);
        }
    }

    public static class ModelOption {
        // 实际保存的模型 ID。
        public final String modelId;
        // Spinner 中显示的文本。
        public final String displayText;

        // 构造一个模型下拉项。
        public ModelOption(String modelId, String displayText) {
            this.modelId = modelId;
            this.displayText = displayText;
        }

        @Override
        // 让 Spinner 直接显示准备好的文案。
        public String toString() {
            return displayText;
        }
    }

    private static final Map<String, KnownModelInfo> KNOWN_MODEL_MAP = buildKnownModelMap();

    // 工具类不需要实例化。
    private ModelCatalog() { }

    // 生成当前可选模型列表，并补上额外指定模型。
    public static List<ModelOption> buildModelOptions(Context context, @Nullable String extraModelId) {
        ArrayList<ModelOption> options = new ArrayList<>();
        LinkedHashSet<String> addedModelIds = new LinkedHashSet<>();

        // 自定义模型按用户维护顺序优先进入列表。
        List<CustomModelProfile> customProfiles = GlobalDataHolder.getCustomModelProfiles();
        if(customProfiles != null) {
            for(CustomModelProfile profile : customProfiles) {
                addModelOption(options, addedModelIds, profile.id);
            }
        }
        // 额外补上当前正在使用但列表里缺失的模型，避免 Spinner 丢选中项。
        if(extraModelId != null && !extraModelId.isEmpty()) {
            addModelOption(options, addedModelIds, extraModelId);
        }
        return options;
    }

    // 获取模型的展示名称，优先使用用户自定义名称。
    public static String getDisplayName(String modelId) {
        if(modelId == null || modelId.isEmpty()) {
            return "";
        }

        CustomModelProfile profile = findCustomModel(modelId);
        if(profile != null && !profile.name.isEmpty()) {
            return profile.name;
        }

        // 自定义名称为空时，再退回内置名称或原始 modelId。
        KnownModelInfo knownModelInfo = getKnownModelInfo(modelId);
        return knownModelInfo == null ? modelId : knownModelInfo.displayName;
    }

    // 获取模型在列表中的展示文本。
    public static String getDisplayText(String modelId) {
        return getDisplayName(modelId);
    }

    // 判断模型是否支持视觉输入。
    public static boolean supportsVision(String modelId) {
        return supportsCapability(modelId, CAPABILITY_VISION);
    }

    // 判断模型是否支持工具调用。
    public static boolean supportsTools(String modelId) {
        return supportsCapability(modelId, CAPABILITY_TOOL);
    }

    // 判断模型是否支持思考内容。
    public static boolean supportsThinking(String modelId) {
        return supportsCapability(modelId, CAPABILITY_THINKING);
    }

    @Nullable
    // 读取内置模型的静态元信息。
    public static KnownModelInfo getKnownModelInfo(String modelId) {
        if(modelId == null || modelId.isEmpty()) {
            return null;
        }
        return KNOWN_MODEL_MAP.get(modelId);
    }

    // 用内置默认能力生成一条可编辑的模型配置。
    public static CustomModelProfile createProfileWithKnownDefaults(String modelId) {
        KnownModelInfo knownModelInfo = getKnownModelInfo(modelId);
        if(knownModelInfo == null) {
            return new CustomModelProfile(modelId, "", Collections.emptyList());
        }
        // 默认名称仍然交给显示层兜底，配置里只补充已知能力。
        return new CustomModelProfile(modelId, "", knownModelInfo.capabilities);
    }

    // 构造首次启动时的默认模型配置列表。
    public static List<CustomModelProfile> buildInitialModelProfiles() {
        ArrayList<CustomModelProfile> profiles = new ArrayList<>();
        for(String modelId : INITIAL_MODEL_IDS) {
            profiles.add(createProfileWithKnownDefaults(modelId));
        }
        return profiles;
    }

    @Nullable
    // 在用户自定义模型列表中查找指定模型。
    public static CustomModelProfile findCustomModel(String modelId) {
        if(modelId == null || modelId.isEmpty()) {
            return null;
        }

        List<CustomModelProfile> profiles = GlobalDataHolder.getCustomModelProfiles();
        if(profiles == null) {
            return null;
        }
        for(CustomModelProfile profile : profiles) {
            if(modelId.equals(profile.id)) {
                return profile;
            }
        }
        return null;
    }

    // 向下拉列表追加一个未重复的模型项。
    private static void addModelOption(List<ModelOption> options,
                                       LinkedHashSet<String> addedModelIds,
                                       String modelId) {
        if(modelId == null || modelId.isEmpty() || !addedModelIds.add(modelId)) {
            return;
        }
        options.add(new ModelOption(modelId, getDisplayText(modelId)));
    }

    // 按优先级判断模型是否支持某项能力。
    private static boolean supportsCapability(String modelId, String capability) {
        if(modelId == null || modelId.isEmpty()) {
            return false;
        }

        CustomModelProfile profile = findCustomModel(modelId);
        if(profile != null) {
            return profile.hasCapability(capability);
        }

        // 没有自定义配置时，再使用内置能力表兜底。
        KnownModelInfo knownModelInfo = getKnownModelInfo(modelId);
        return knownModelInfo != null && knownModelInfo.capabilities.contains(capability);
    }

    // 构建内置模型信息映射表。
    private static Map<String, KnownModelInfo> buildKnownModelMap() {
        HashMap<String, KnownModelInfo> map = new HashMap<>();

        // OpenAI
		addKnownModel(map, "gpt-3.5-turbo", "GPT-3.5 Turbo");
		addKnownModel(map, "gpt-4", "GPT-4");
		addKnownModel(map, "gpt-4-turbo", "GPT-4 Turbo", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gpt-4o", "GPT-4o", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gpt-4o-mini", "GPT-4o mini", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gpt-4.1", "GPT-4.1", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gpt-4.1-mini", "GPT-4.1 mini", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gpt-4.1-nano", "GPT-4.1 nano", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "o1", "OpenAI o1", CAPABILITY_THINKING);
		addKnownModel(map, "o3", "OpenAI o3", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "o4-mini", "OpenAI o4-mini", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5", "GPT-5", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5-mini", "GPT-5 mini", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5-nano", "GPT-5 nano", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5.4", "GPT-5.4", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5.4-mini", "GPT-5.4 mini", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5.4-nano", "GPT-5.4 nano", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gpt-5.4-pro", "GPT-5.4 Pro", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);

		// DeepSeek
		addKnownModel(map, "deepseek-chat", "DeepSeek Chat", CAPABILITY_TOOL);
		addKnownModel(map, "deepseek-reasoner", "DeepSeek Reasoner", CAPABILITY_TOOL, CAPABILITY_THINKING);

		// Qwen / 阿里云百炼
		addKnownModel(map, "qwen-turbo", "Qwen Turbo", CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen-plus", "Qwen Plus", CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen-max", "Qwen Max", CAPABILITY_TOOL);
		addKnownModel(map, "qwen-vl-max", "Qwen VL Max", CAPABILITY_VISION);
		addKnownModel(map, "qwen3-max", "Qwen3 Max", CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen3.5-plus", "Qwen3.5 Plus", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen3.5-flash", "Qwen3.5 Flash", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen3.6-plus", "Qwen3.6 Plus", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen3-coder-plus", "Qwen3 Coder Plus", CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "qwen3-coder-next", "Qwen3 Coder Next", CAPABILITY_TOOL, CAPABILITY_THINKING);

		// Anthropic
		addKnownModel(map, "claude-3-5-sonnet", "Claude 3.5 Sonnet", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "claude-3-5-haiku", "Claude 3.5 Haiku", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "claude-3-7-sonnet", "Claude 3.7 Sonnet", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-opus-4", "Claude Opus 4", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-sonnet-4", "Claude Sonnet 4", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-opus-4-1", "Claude Opus 4.1", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-opus-4-5", "Claude Opus 4.5", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-sonnet-4-5", "Claude Sonnet 4.5", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-haiku-4-5", "Claude Haiku 4.5", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-opus-4-6", "Claude Opus 4.6", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "claude-sonnet-4-6", "Claude Sonnet 4.6", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);

		// Google Gemini
		addKnownModel(map, "gemini-2.0-flash", "Gemini 2.0 Flash", CAPABILITY_VISION, CAPABILITY_TOOL);
		addKnownModel(map, "gemini-2.5-pro", "Gemini 2.5 Pro", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gemini-2.5-flash", "Gemini 2.5 Flash", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);
		addKnownModel(map, "gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", CAPABILITY_VISION, CAPABILITY_TOOL, CAPABILITY_THINKING);

        return map;
    }

    // 向内置模型表注册一条模型定义。
    private static void addKnownModel(Map<String, KnownModelInfo> map,
                                      String modelId,
                                      String displayName,
                                      String... capabilities) {
        map.put(modelId, new KnownModelInfo(displayName, Arrays.asList(capabilities)));
    }
}
