package com.skythinker.gptassistant.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CustomModelProfile {
    public String id = "";
    public String name = "";
    public List<String> capabilities = new ArrayList<>();

    public CustomModelProfile() { }

    public CustomModelProfile(String id, String name, List<String> capabilities) {
        this.id = id == null ? "" : id.trim();
        this.name = name == null ? "" : name.trim();
        this.capabilities = normalizeCapabilities(capabilities);
    }

    public CustomModelProfile copy() {
        return new CustomModelProfile(id, name, capabilities);
    }

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    public JSONObject toJson() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.putOpt("id", id);
            jsonObject.putOpt("name", name);
            jsonObject.putOpt("caps", new JSONArray(capabilities));
        } catch (JSONException e) { }
        return jsonObject;
    }

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
