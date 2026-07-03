package com.codex.personalsop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String moduleId = intent == null ? "" : intent.getStringExtra(ReminderScheduler.EXTRA_MODULE_ID);
        SopModule module = moduleId == null ? null : SopModuleStore.find(context, moduleId);
        if (module == null) {
            ReminderConfig.appendLog(context, "WARN", "闹钟已唤醒，但未找到对应模块");
            return;
        }
        if (!module.enabled) {
            ReminderConfig.prefs(context).edit()
                    .putString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, time + " [" + module.name + "] 已唤醒，但模块未启用")
                    .apply();
            ReminderConfig.appendLog(context, "WARN", "[" + module.name + "] 闹钟已唤醒，但模块未启用");
            return;
        }
        if (!module.testMode && ReminderScheduler.occurrenceKey(System.currentTimeMillis(), module) == null) {
            ReminderConfig.prefs(context).edit()
                    .putString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, time + " [" + module.name + "] 已唤醒，但当前不在允许周期内")
                    .apply();
            ReminderConfig.appendLog(context, "WARN", "[" + module.name + "] 闹钟已唤醒，但当前不在允许周期内，跳过发送");
            ReminderScheduler.scheduleNext(context, module);
            return;
        }
        ReminderConfig.appendLog(context, "INFO", "[" + module.name + "] 闹钟已唤醒，开始发送 Bark");

        PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    BarkClient.Result result = BarkClient.send(
                            ReminderConfig.barkEndpoint(context),
                            module.name,
                            module.message
                    );
                    ReminderConfig.prefs(context).edit()
                            .putString(ReminderConfig.KEY_LAST_TRIGGER_STATUS, time + " [" + module.name + "] " + result.message)
                            .apply();
                    if (!result.ok) {
                        ReminderConfig.appendLog(context, "ERROR", "[" + module.name + "] Bark 发送失败：" + result.message);
                        LocalNotifier.show(context, "Bark 发送失败", result.message);
                    } else {
                        ReminderConfig.appendLog(context, "INFO", "[" + module.name + "] " + result.message);
                    }
                    SopModule latest = SopModuleStore.find(context, module.id);
                    if (latest != null && latest.enabled) {
                        boolean scheduled = ReminderScheduler.scheduleNext(context, latest);
                        if (!scheduled) {
                            ReminderConfig.appendLog(context, "ERROR", "[" + module.name + "] 提醒未能继续安排，请检查精确闹钟权限");
                            LocalNotifier.show(context, "提醒未能继续安排", "请检查精确闹钟权限");
                        } else {
                            ReminderConfig.appendLog(context, "INFO", "[" + module.name + "] 已安排下一次提醒");
                        }
                    }
                } finally {
                    pending.finish();
                }
            }
        }, "personal-sop-bark").start();
    }
}


