package com.codex.personalsop;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class ReminderConfig {
    private static final String TAG = "PersonalSop";
    private static final int MAX_LOG_LINES = 40;

    static final String PREFS = "personal_sop";
    static final String KEY_ENABLED = "enabled";
    static final String KEY_BARK_ENDPOINT = "bark_endpoint";
    static final String KEY_MESSAGE = "message";
    static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    static final String KEY_NEXT_TRIGGER = "next_trigger";
    static final String KEY_LAST_TRIGGER_STATUS = "last_trigger_status";
    static final String KEY_SEND_LOG = "send_log";
    static final String KEY_TEST_MODE = "test_mode";

    static final int START_HOUR = 17;
    static final int END_HOUR = 21;
    static final int DEFAULT_INTERVAL_MINUTES = 120;
    static final String DEFAULT_MESSAGE = "提醒我完成这个动作";

    private ReminderConfig() {
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isEnabled(Context context) {
        for (SopModule module : SopModuleStore.modules(context)) {
            if (module.enabled) {
                return true;
            }
        }
        return false;
    }

    static String barkEndpoint(Context context) {
        return prefs(context).getString(KEY_BARK_ENDPOINT, "");
    }

    static String message(Context context) {
        SopModule module = SopModuleStore.selectedModule(context);
        return module == null ? "" : module.message;
    }

    static int intervalMinutes(Context context) {
        SopModule module = SopModuleStore.selectedModule(context);
        return module == null ? DEFAULT_INTERVAL_MINUTES : module.intervalMinutes;
    }

    static boolean isTestMode(Context context) {
        SopModule module = SopModuleStore.selectedModule(context);
        return module != null && module.testMode;
    }

    static void appendLog(Context context, String level, String message) {
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        String line = time + " [" + level + "] " + message;
        if ("ERROR".equals(level)) {
            Log.e(TAG, message);
        } else if ("WARN".equals(level)) {
            Log.w(TAG, message);
        } else {
            Log.i(TAG, message);
        }

        String existing = prefs(context).getString(KEY_SEND_LOG, "");
        String next = existing == null || existing.isEmpty() ? line : existing + "\n" + line;
        String[] lines = next.split("\n");
        if (lines.length > MAX_LOG_LINES) {
            StringBuilder trimmed = new StringBuilder();
            for (int i = lines.length - MAX_LOG_LINES; i < lines.length; i++) {
                if (trimmed.length() > 0) {
                    trimmed.append('\n');
                }
                trimmed.append(lines[i]);
            }
            next = trimmed.toString();
        }
        prefs(context).edit().putString(KEY_SEND_LOG, next).apply();
    }

    static String recentLog(Context context) {
        return prefs(context).getString(KEY_SEND_LOG, "");
    }

    static void clearLog(Context context) {
        prefs(context).edit().remove(KEY_SEND_LOG).apply();
    }
}

