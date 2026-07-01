package com.codex.personalsop;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

final class SopModuleStore {
    private static final String KEY_MODULES_JSON = "modules_json";
    private static final String KEY_SELECTED_MODULE_ID = "selected_module_id";
    private static final String KEY_COMPLETED_PREFIX = "completed_occurrence_";
    private static final String KEY_CHECKLIST_OCCURRENCE_PREFIX = "checklist_occurrence_";
    private static final String KEY_CHECKLIST_MASK_PREFIX = "checklist_mask_";

    private SopModuleStore() {
    }

    static List<SopModule> modules(Context context) {
        migrateIfNeeded(context);
        String raw = ReminderConfig.prefs(context).getString(KEY_MODULES_JSON, "");
        List<SopModule> modules = parse(raw);
        return modules;
    }

    static SopModule selectedModule(Context context) {
        List<SopModule> modules = modules(context);
        String selectedId = ReminderConfig.prefs(context).getString(KEY_SELECTED_MODULE_ID, "");
        for (SopModule module : modules) {
            if (module.id.equals(selectedId)) {
                return module;
            }
        }
        return modules.isEmpty() ? null : modules.get(0);
    }

    static void saveSelectedModuleId(Context context, String id) {
        ReminderConfig.prefs(context).edit().putString(KEY_SELECTED_MODULE_ID, id).apply();
    }

    static SopModule find(Context context, String id) {
        for (SopModule module : modules(context)) {
            if (module.id.equals(id)) {
                return module;
            }
        }
        return null;
    }

    static void upsert(Context context, SopModule updated) {
        List<SopModule> modules = modules(context);
        boolean replaced = false;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).id.equals(updated.id)) {
                modules.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            modules.add(updated);
        }
        saveModules(context, modules);
        saveSelectedModuleId(context, updated.id);
    }

    static SopModule createModule(Context context) {
        SopModule module = SopModule.blank("module_" + System.currentTimeMillis());
        List<SopModule> modules = modules(context);
        modules.add(module);
        saveModules(context, modules);
        saveSelectedModuleId(context, module.id);
        return module;
    }

    static void delete(Context context, String id) {
        List<SopModule> modules = modules(context);
        for (int i = modules.size() - 1; i >= 0; i--) {
            if (modules.get(i).id.equals(id)) {
                modules.remove(i);
            }
        }
        saveModules(context, modules);
        String nextSelected = modules.isEmpty() ? "" : modules.get(0).id;
        saveSelectedModuleId(context, nextSelected);
    }

    static void markCompleted(Context context, SopModule module, long nowMillis) {
        String key = ReminderScheduler.occurrenceKey(nowMillis, module);
        if (key == null) {
            ReminderConfig.appendLog(context, "WARN", "[" + module.name + "] 当前还没有可完成的周期");
            return;
        }
        if (module.usesChecklist && !areAllChecklistItemsChecked(context, module, nowMillis)) {
            ReminderConfig.appendLog(context, "WARN", "[" + module.name + "] 动作清单尚未全部完成");
            return;
        }
        ReminderConfig.prefs(context).edit()
                .putString(KEY_COMPLETED_PREFIX + module.id, key)
                .putString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, "[" + module.name + "] 已完成 " + key)
                .apply();
        ReminderConfig.appendLog(context, "INFO", "[" + module.name + "] 已手动标记完成");
    }

    static boolean isCompleted(Context context, SopModule module, long nowMillis) {
        String key = ReminderScheduler.occurrenceKey(nowMillis, module);
        if (key == null) {
            return false;
        }
        String completed = ReminderConfig.prefs(context).getString(KEY_COMPLETED_PREFIX + module.id, "");
        return key.equals(completed);
    }

    static boolean isChecklistItemChecked(Context context, SopModule module, long nowMillis, int index) {
        return (checklistMask(context, module, nowMillis) & (1 << index)) != 0;
    }

    static void setChecklistItemChecked(Context context, SopModule module, long nowMillis, int index, boolean checked) {
        String key = ReminderScheduler.occurrenceKey(nowMillis, module);
        if (key == null) {
            ReminderConfig.appendLog(context, "WARN", "[" + module.name + "] 当前还没有可勾选的周期");
            return;
        }
        int mask = checklistMask(context, module, nowMillis);
        if (checked) {
            mask |= 1 << index;
        } else {
            mask &= ~(1 << index);
        }
        ReminderConfig.prefs(context).edit()
                .putString(KEY_CHECKLIST_OCCURRENCE_PREFIX + module.id, key)
                .putInt(KEY_CHECKLIST_MASK_PREFIX + module.id, mask)
                .apply();
        ReminderConfig.appendLog(context, "INFO", "[" + module.name + "] 清单项已更新：" + completedChecklistCount(context, module, nowMillis) + "/" + checklistSize(module));
        if (areAllChecklistItemsChecked(context, module, nowMillis)) {
            markCompleted(context, module, nowMillis);
        }
    }

    static boolean areAllChecklistItemsChecked(Context context, SopModule module, long nowMillis) {
        int size = checklistSize(module);
        if (!module.usesChecklist || size == 0) {
            return true;
        }
        int mask = checklistMask(context, module, nowMillis);
        int allMask = size >= 31 ? -1 : ((1 << size) - 1);
        return (mask & allMask) == allMask;
    }

    static int completedChecklistCount(Context context, SopModule module, long nowMillis) {
        int count = 0;
        int size = checklistSize(module);
        int mask = checklistMask(context, module, nowMillis);
        for (int i = 0; i < size; i++) {
            if ((mask & (1 << i)) != 0) {
                count++;
            }
        }
        return count;
    }

    private static int checklistMask(Context context, SopModule module, long nowMillis) {
        String key = ReminderScheduler.occurrenceKey(nowMillis, module);
        if (key == null) {
            return 0;
        }
        String storedKey = ReminderConfig.prefs(context).getString(KEY_CHECKLIST_OCCURRENCE_PREFIX + module.id, "");
        if (!key.equals(storedKey)) {
            return 0;
        }
        return ReminderConfig.prefs(context).getInt(KEY_CHECKLIST_MASK_PREFIX + module.id, 0);
    }

    private static int checklistSize(SopModule module) {
        if (module.checklistItems == null) {
            return 0;
        }
        return Math.min(module.checklistItems.size(), 30);
    }

    static void saveModules(Context context, List<SopModule> modules) {
        JSONArray array = new JSONArray();
        try {
            for (SopModule module : modules) {
                array.put(module.toJson());
            }
        } catch (JSONException ex) {
            ReminderConfig.appendLog(context, "ERROR", "模块保存失败：" + ex.getMessage());
            return;
        }
        ReminderConfig.prefs(context).edit().putString(KEY_MODULES_JSON, array.toString()).apply();
    }

    private static void migrateIfNeeded(Context context) {
        SharedPreferences prefs = ReminderConfig.prefs(context);
        if (prefs.contains(KEY_MODULES_JSON)) {
            return;
        }

        saveModules(context, new ArrayList<SopModule>());
        saveSelectedModuleId(context, "");
    }

    private static List<SopModule> parse(String raw) {
        List<SopModule> modules = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return modules;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                modules.add(SopModule.fromJson(array.getJSONObject(i)));
            }
        } catch (JSONException ignored) {
            modules.clear();
        }
        return modules;
    }
}

