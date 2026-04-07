package com.skythinker.gptassistant.data;

import androidx.annotation.Nullable;

import com.skythinker.gptassistant.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActionRegistry {
    public static final String ACTION_NEW_CHAT = "new_chat";
    public static final String ACTION_TTS = "tts";
    public static final String ACTION_NETWORK = "network";
    public static final String ACTION_AGENT = "agent";
    public static final String ACTION_MULTI_CHAT = "multi_chat";
    public static final String ACTION_VOICE_CHAT = "voice_chat";
    public static final String ACTION_HISTORY = "history";
    public static final String ACTION_SETTINGS = "settings";
    public static final String ACTION_CLOSE = "close";

    public static final int PLACEMENT_PRIMARY = 0;
    public static final int PLACEMENT_SECONDARY = 1;
    public static final int PLACEMENT_HIDDEN = 2;

    public static final int MAX_PRIMARY_ACTION_COUNT = 4;

    // 主界面按钮的默认层级、是否允许自定义、允许出现的位置都集中维护在这里。
    private static final List<MainActionSpec> ACTION_SPECS = Collections.unmodifiableList(Arrays.asList(
            new MainActionSpec(
                    ACTION_NEW_CHAT,
                    R.string.main_action_new_chat,
                    R.drawable.new_chat_btn,
                    0,
                    PLACEMENT_PRIMARY,
                    0,
                    true,
                    false,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY}
            ),
            new MainActionSpec(
                    ACTION_TTS,
                    R.string.main_action_tts,
                    R.drawable.tts_off,
                    R.drawable.tts_off_enable,
                    PLACEMENT_PRIMARY,
                    1,
                    true,
                    true,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_NETWORK,
                    R.string.main_action_network,
                    R.drawable.network_btn,
                    R.drawable.network_btn_enabled,
                    PLACEMENT_PRIMARY,
                    2,
                    true,
                    true,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_AGENT,
                    R.string.main_action_agent,
                    R.drawable.agent_btn,
                    R.drawable.agent_btn_enabled,
                    PLACEMENT_PRIMARY,
                    3,
                    true,
                    true,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_MULTI_CHAT,
                    R.string.main_action_multi_chat,
                    R.drawable.chat_btn,
                    R.drawable.chat_btn_enabled,
                    PLACEMENT_SECONDARY,
                    0,
                    true,
                    false,
                    new int[]{PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_VOICE_CHAT,
                    R.string.main_action_voice_chat,
                    R.drawable.voice_chat_btn,
                    R.drawable.voice_chat_btn_enabled,
                    PLACEMENT_SECONDARY,
                    1,
                    true,
                    true,
                    new int[]{PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_HISTORY,
                    R.string.main_action_history,
                    R.drawable.history_btn,
                    0,
                    PLACEMENT_SECONDARY,
                    2,
                    true,
                    true,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            ),
            new MainActionSpec(
                    ACTION_SETTINGS,
                    R.string.main_action_settings,
                    R.drawable.settings_btn,
                    0,
                    PLACEMENT_SECONDARY,
                    3,
                    true,
                    true,
                    new int[]{PLACEMENT_PRIMARY, PLACEMENT_SECONDARY}
            ),
            new MainActionSpec(
                    ACTION_CLOSE,
                    R.string.main_action_close,
                    R.drawable.close_btn,
                    0,
                    PLACEMENT_SECONDARY,
                    4,
                    true,
                    true,
                    new int[]{PLACEMENT_SECONDARY, PLACEMENT_HIDDEN}
            )
    ));

    private MainActionRegistry() { }

    public static List<MainActionSpec> getAllSpecs() {
        return ACTION_SPECS;
    }

    public static List<MainActionSpec> getCustomizableSpecs() {
        List<MainActionSpec> specs = new ArrayList<>();
        for (MainActionSpec spec : ACTION_SPECS) {
            if (spec.userCustomizable) {
                specs.add(spec);
            }
        }
        return specs;
    }

    @Nullable
    public static MainActionSpec findSpec(String actionId) {
        for (MainActionSpec spec : ACTION_SPECS) {
            if (spec.id.equals(actionId)) {
                return spec;
            }
        }
        return null;
    }

    public static List<MainActionLayoutItem> getDefaultLayout() {
        List<MainActionLayoutItem> items = new ArrayList<>();
        for (MainActionSpec spec : ACTION_SPECS) {
            items.add(new MainActionLayoutItem(spec.id, spec.defaultPlacement, spec.defaultOrder));
        }
        normalizeLayout(items);
        return items;
    }

    public static List<MainActionLayoutItem> getResolvedLayout(String savedLayoutJson) {
        Map<String, MainActionLayoutItem> savedItemMap = parseSavedLayout(savedLayoutJson);
        List<MainActionLayoutItem> items = new ArrayList<>();
        for (MainActionSpec spec : ACTION_SPECS) {
            MainActionLayoutItem savedItem = savedItemMap.get(spec.id);
            MainActionLayoutItem item;
            if (savedItem != null && spec.userCustomizable && spec.supportsPlacement(savedItem.placement)) {
                item = savedItem.copy();
            } else {
                item = new MainActionLayoutItem(spec.id, spec.defaultPlacement, spec.defaultOrder);
            }
            items.add(item);
        }
        normalizeLayout(items);
        return items;
    }

    public static void normalizeLayout(List<MainActionLayoutItem> items) {
        List<MainActionLayoutItem> normalizedItems = new ArrayList<>();
        Map<String, MainActionLayoutItem> itemMap = new LinkedHashMap<>();
        for (MainActionLayoutItem item : items) {
            itemMap.put(item.actionId, item);
        }
        for (MainActionSpec spec : ACTION_SPECS) {
            MainActionLayoutItem item = itemMap.get(spec.id);
            if (item == null) {
                item = new MainActionLayoutItem(spec.id, spec.defaultPlacement, spec.defaultOrder);
            }
            if (!spec.userCustomizable || !spec.supportsPlacement(item.placement)) {
                item.placement = spec.defaultPlacement;
                item.order = spec.defaultOrder;
            }
            normalizedItems.add(item);
        }
        items.clear();
        items.addAll(normalizedItems);
        sortByPlacementAndOrder(items);
        rebuildPlacementOrdersFromCurrentSequence(items);
    }

    public static void sortByPlacementAndOrder(List<MainActionLayoutItem> items) {
        Collections.sort(items, Comparator
                .comparingInt((MainActionLayoutItem item) -> getPlacementRank(item.placement))
                .thenComparingInt(item -> item.order)
                .thenComparingInt(item -> {
                    MainActionSpec spec = findSpec(item.actionId);
                    return spec == null ? Integer.MAX_VALUE : spec.defaultOrder;
                }));
    }

    public static void rebuildPlacementOrdersFromCurrentSequence(List<MainActionLayoutItem> items) {
        int primaryOrder = 0;
        int secondaryOrder = 0;
        int hiddenOrder = 0;
        for (MainActionLayoutItem item : items) {
            if (item.placement == PLACEMENT_PRIMARY) {
                item.order = primaryOrder++;
            } else if (item.placement == PLACEMENT_SECONDARY) {
                item.order = secondaryOrder++;
            } else {
                item.order = hiddenOrder++;
            }
        }
    }

    public static int getPrimaryActionCount(List<MainActionLayoutItem> items) {
        int count = 0;
        for (MainActionLayoutItem item : items) {
            if (item.placement == PLACEMENT_PRIMARY) {
                count++;
            }
        }
        return count;
    }

    public static int getNextOrder(List<MainActionLayoutItem> items, int placement) {
        int maxOrder = -1;
        for (MainActionLayoutItem item : items) {
            if (item.placement == placement) {
                maxOrder = Math.max(maxOrder, item.order);
            }
        }
        return maxOrder + 1;
    }

    public static String toJson(List<MainActionLayoutItem> items) {
        JSONArray jsonArray = new JSONArray();
        try {
            for (MainActionLayoutItem item : items) {
                JSONObject object = new JSONObject();
                object.put("id", item.actionId);
                object.put("placement", item.placement);
                object.put("order", item.order);
                jsonArray.put(object);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return jsonArray.toString();
    }

    private static Map<String, MainActionLayoutItem> parseSavedLayout(String savedLayoutJson) {
        Map<String, MainActionLayoutItem> itemMap = new LinkedHashMap<>();
        if (savedLayoutJson == null || savedLayoutJson.trim().isEmpty()) {
            return itemMap;
        }
        try {
            JSONArray jsonArray = new JSONArray(savedLayoutJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = jsonArray.getJSONObject(i);
                String actionId = object.optString("id");
                if (actionId.isEmpty()) {
                    continue;
                }
                itemMap.put(actionId, new MainActionLayoutItem(
                        actionId,
                        object.optInt("placement", PLACEMENT_SECONDARY),
                        object.optInt("order", i)
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return itemMap;
    }

    private static int getPlacementRank(int placement) {
        if (placement == PLACEMENT_PRIMARY) {
            return 0;
        }
        if (placement == PLACEMENT_SECONDARY) {
            return 1;
        }
        return 2;
    }
}
