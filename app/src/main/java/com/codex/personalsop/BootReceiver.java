package com.codex.personalsop;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            boolean scheduled = ReminderScheduler.scheduleAll(context);
            if (!scheduled) {
                LocalNotifier.show(context, "提醒未恢复", "请打开应用并检查精确闹钟权限");
            }
        }
    }
}

