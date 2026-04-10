package com.skythinker.gptassistant.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CustomModelProfile {
    // 模型接口实际使用的标识。
    public String id = "";
    // 模型在界面上的展示名称。
    public String name = "";
    // 模型声明支持的能力列表。
    public List<String> capabilities = new ArrayList<>();

    // 创建一个空的自定义模型配置。
    public CustomModelProfile() { }

    // 按给定字段创建模型配置。
    public CustomModelProfile(String id, String name, List<String> capabilities) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "" : name.trim();
        this.capabilities = normalizeCapabilities(capabilities);
    }

    // 复制当前模型配置，避免直接共享引用。
    public CustomModelProfile copy() {
        return new CustomModelProfile(id, name, capabilities);
    }

    // 判断模型是否声明了指定能力。
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    // 序列化为本地存储使用的 JSON。
    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.putOpt("id", id);
            jsonObject.putOpt("name", name);
            jsonObject.putOpt("caps", new JSONArray(capabilities));
        } catch (JSONException e) { }
        return jsonObject;
    }

    // 从 JSON 还原模型配置。
    public static CustomModelProfile fromJson(JSONObject jsonObject) {
        CustomModelProfile profile = new CustomModelProfile();
        if(jsonObject == null) {
            return profile;
        }

        profile.id = jsonObject.optString("id", "").trim();
        profile.name = jsonObject.optString("name", "").trim();
        JSONArray capsJson = jsonObject.optJSONArray("caps");
        if(capsJson != null) {
            for(int i = 0; i < capsJson.length(); i++) {
                String capability = capsJson.optString(i, "").trim();
                if(!capability.isEmpty()) {
                    profile.capabilities.add(capability);
                }
            }
        }
        profile.capabilities = normalizeCapabilities(profile.capabilities);
        return profile;
    }

    // 去重并清理能力字段中的空值和空白。
    private static List<String> normalizeCapabilities(List<String> capabilities) {
        Set<String> capabilitySet = new LinkedHashSet<>();
        if(capabilities != null) {
            for(String capability : capabilities) {
                if(capability != null) {
                    String trimmed = capability.trim();
                    if(!trimmed.isEmpty()) {
                        capabilitySet.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(capabilitySet);
    }
}
