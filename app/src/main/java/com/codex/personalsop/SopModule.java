package com.codex.personalsop;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class SopModule {
    static final String CYCLE_DAILY = "daily";
    static final String CYCLE_WEEKLY = "weekly";
    static final int ALL_DAYS = 0b1111111;
    static final int UNSET_TIME = -1;
    static final int UNSET_INTERVAL = 0;

    String id;
    String name;
    String message;
    boolean enabled;
    String cycleType;
    int daysOfWeek;
    int startHour;
    int startMinute;
    int endHour;
    int endMinute;
    int intervalMinutes;
    boolean requiresCompletion;
    boolean usesChecklist;
    List<String> checklistItems;
    boolean testMode;

    SopModule() {
    }

    static SopModule template(String id) {
        SopModule module = new SopModule();
        module.id = id;
        module.name = "";
        module.message = "";
        module.enabled = false;
        module.cycleType = CYCLE_DAILY;
        module.daysOfWeek = 0;
        module.startHour = UNSET_TIME;
        module.startMinute = 0;
        module.endHour = UNSET_TIME;
        module.endMinute = 0;
        module.intervalMinutes = UNSET_INTERVAL;
        module.requiresCompletion = false;
        module.usesChecklist = false;
        module.checklistItems = new ArrayList<>();
        module.testMode = false;
        return module;
    }

    static SopModule blank(String id) {
        return template(id);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        object.put("message", message);
        object.put("enabled", enabled);
        object.put("cycleType", cycleType);
        object.put("daysOfWeek", daysOfWeek);
        object.put("startHour", startHour);
        object.put("startMinute", startMinute);
        object.put("endHour", endHour);
        object.put("endMinute", endMinute);
        object.put("intervalMinutes", intervalMinutes);
        object.put("requiresCompletion", requiresCompletion);
        object.put("usesChecklist", usesChecklist);
        JSONArray items = new JSONArray();
        if (checklistItems != null) {
            for (String item : checklistItems) {
                items.put(item);
            }
        }
        object.put("checklistItems", items);
        object.put("testMode", testMode);
        return object;
    }

    static SopModule fromJson(JSONObject object) {
        SopModule fallback = template(object.optString("id", "module"));
        SopModule module = new SopModule();
        module.id = object.optString("id", fallback.id);
        module.name = object.optString("name", fallback.name);
        module.message = object.optString("message", fallback.message);
        module.enabled = object.optBoolean("enabled", false);
        module.cycleType = object.optString("cycleType", fallback.cycleType);
        module.daysOfWeek = object.optInt("daysOfWeek", fallback.daysOfWeek);
        module.startHour = clamp(object.optInt("startHour", fallback.startHour), UNSET_TIME, 23);
        module.startMinute = clamp(object.optInt("startMinute", fallback.startMinute), 0, 59);
        module.endHour = clamp(object.optInt("endHour", fallback.endHour), UNSET_TIME, 23);
        module.endMinute = clamp(object.optInt("endMinute", fallback.endMinute), 0, 59);
        module.intervalMinutes = clamp(object.optInt("intervalMinutes", fallback.intervalMinutes), UNSET_INTERVAL, 720);
        module.requiresCompletion = object.optBoolean("requiresCompletion", false);
        module.usesChecklist = object.optBoolean("usesChecklist", false);
        module.checklistItems = new ArrayList<>();
        JSONArray items = object.optJSONArray("checklistItems");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                String item = items.optString(i, "").trim();
                if (!item.isEmpty()) {
                    module.checklistItems.add(item);
                }
            }
        }
        module.testMode = object.optBoolean("testMode", false);
        if (!CYCLE_WEEKLY.equals(module.cycleType)) {
            module.cycleType = CYCLE_DAILY;
        }
        return module;
    }

    String windowText() {
        if (startHour == UNSET_TIME || endHour == UNSET_TIME) {
            return "未设置时间";
        }
        return two(startHour) + ":" + two(startMinute) + "-" + two(endHour) + ":" + two(endMinute);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}

