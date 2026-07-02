package com.codex.personalsop;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

final class ReminderScheduler {
    static final String EXTRA_MODULE_ID = "module_id";
    private static final int REQUEST_CODE_BASE = 1001;
    private static final String ACTION_REMIND = "com.codex.personalsop.REMIND";

    private ReminderScheduler() {
    }

    static boolean scheduleNext(Context context) {
        return scheduleAll(context);
    }

    static boolean scheduleAll(Context context) {
        List<SopModule> modules = SopModuleStore.modules(context);
        boolean ok = true;
        boolean anyEnabled = false;
        ReminderConfig.prefs(context).edit().remove(ReminderConfig.KEY_NEXT_TRIGGER).apply();
        for (SopModule module : modules) {
            if (module.enabled) {
                anyEnabled = true;
                ok = scheduleNext(context, module) && ok;
            } else {
                cancel(context, module.id);
            }
        }
        return ok;
    }

    static boolean scheduleNext(Context context, SopModule module) {
        long triggerAt = nextTriggerMillis(context, System.currentTimeMillis(), module);
        return scheduleAt(context, module.id, triggerAt);
    }

    static boolean scheduleTestInMinutes(Context context, String moduleId, int minutes) {
        long triggerAt = System.currentTimeMillis() + Math.max(1, minutes) * 60_000L;
        return scheduleAt(context, moduleId, triggerAt);
    }

    private static boolean scheduleAt(Context context, String moduleId, long triggerAt) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            return false;
        }

        PendingIntent operation = reminderPendingIntent(context, moduleId, PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent showIntent = PendingIntent.getActivity(
                context,
                0,
                new Intent(context, MainActivity.class),
                pendingFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        );
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, showIntent);
        alarmManager.setAlarmClock(info, operation);
        updateStoredNextTrigger(context, triggerAt);
        return true;
    }

    static void cancel(Context context) {
        for (SopModule module : SopModuleStore.modules(context)) {
            cancel(context, module.id);
        }
        ReminderConfig.prefs(context).edit().remove(ReminderConfig.KEY_NEXT_TRIGGER).apply();
    }

    static void cancel(Context context, String moduleId) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            PendingIntent pendingIntent = reminderPendingIntent(context, moduleId, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
        updateEarliestNextTrigger(context);
    }

    static long nextTriggerMillis(long nowMillis, SopModule module) {
        return nextTriggerMillis(null, nowMillis, module);
    }

    static long nextTriggerMillis(Context context, long nowMillis, SopModule module) {
        if (module.testMode) {
            return nowMillis + Math.max(1, module.intervalMinutes) * 60_000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone);
        LocalDateTime activeStart = occurrenceStart(now, module);
        if (context != null && activeStart != null && SopModuleStore.isCompleted(context, module, nowMillis)) {
            return nextStartAfter(activeStart, module).atZone(zone).toInstant().toEpochMilli();
        }

        if (module.requiresCompletion) {
            if (activeStart != null) {
                return now.plusMinutes(Math.max(1, module.intervalMinutes))
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli();
            }
            return nextStartAfter(now.minusMinutes(1), module).atZone(zone).toInstant().toEpochMilli();
        }

        LocalDate date = now.toLocalDate();
        for (int offset = 0; offset <= 14; offset++) {
            LocalDate candidateDate = date.plusDays(offset);
            if (!isAllowedDay(candidateDate, module)) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
            LocalDateTime end = LocalDateTime.of(candidateDate, LocalTime.of(module.endHour, module.endMinute));
            if (!end.isAfter(start)) {
                end = start.plusMinutes(Math.max(1, module.intervalMinutes));
            }
            LocalDateTime next;
            if (now.isBefore(start)) {
                next = start;
            } else if (now.isAfter(end) || now.isEqual(end)) {
                continue;
            } else {
                long minutesSinceStart = java.time.Duration.between(start, now).toMinutes();
                long slots = (minutesSinceStart / Math.max(1, module.intervalMinutes)) + 1;
                next = start.plusMinutes(slots * Math.max(1, module.intervalMinutes));
                if (next.isAfter(end)) {
                    continue;
                }
            }
            return next.atZone(zone).toInstant().toEpochMilli();
        }
        return nowMillis + 24L * 60L * 60L * 1000L;
    }

    static String occurrenceKey(long nowMillis, SopModule module) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone);
        LocalDateTime start = occurrenceStart(now, module);
        if (start == null) {
            return null;
        }
        return start.toLocalDate().toString() + " " + two(start.getHour()) + ":" + two(start.getMinute());
    }

    static long nextTriggerMillis(long nowMillis, int intervalMinutes, boolean testMode) {
        int safeInterval = Math.max(1, intervalMinutes);
        if (testMode) {
            return nowMillis + safeInterval * 60_000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone);
        LocalDate today = now.toLocalDate();
        LocalDateTime start = LocalDateTime.of(today, LocalTime.of(ReminderConfig.START_HOUR, 0));
        LocalDateTime end = LocalDateTime.of(today, LocalTime.of(ReminderConfig.END_HOUR, 0));

        LocalDateTime next;
        if (now.isBefore(start)) {
            next = start;
        } else if (now.isAfter(end) || now.isEqual(end)) {
            next = start.plusDays(1);
        } else {
            long minutesSinceStart = java.time.Duration.between(start, now).toMinutes();
            long slots = (minutesSinceStart / safeInterval) + 1;
            next = start.plusMinutes(slots * safeInterval);
            if (next.isAfter(end)) {
                next = start.plusDays(1);
            }
        }

        return next.atZone(zone).toInstant().toEpochMilli();
    }

    private static boolean isAllowedDay(LocalDate date, SopModule module) {
        if (SopModule.CYCLE_DAILY.equals(module.cycleType)) {
            return true;
        }
        int dayIndex = date.getDayOfWeek().getValue() - 1;
        return (module.daysOfWeek & (1 << dayIndex)) != 0;
    }

    private static LocalDateTime occurrenceStart(LocalDateTime now, SopModule module) {
        for (int offset = 0; offset <= 14; offset++) {
            LocalDate candidateDate = now.toLocalDate().minusDays(offset);
            if (!isAllowedDay(candidateDate, module)) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
            if (!start.isAfter(now)) {
                return start;
            }
        }
        return null;
    }

    private static LocalDateTime nextStartAfter(LocalDateTime after, SopModule module) {
        LocalDate startDate = after.toLocalDate();
        for (int offset = 0; offset <= 14; offset++) {
            LocalDate candidateDate = startDate.plusDays(offset);
            if (!isAllowedDay(candidateDate, module)) {
                continue;
            }
            LocalDateTime candidate = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
            if (candidate.isAfter(after)) {
                return candidate;
            }
        }
        return after.plusDays(1);
    }

    private static String two(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private static void updateEarliestNextTrigger(Context context) {
        long now = System.currentTimeMillis();
        long earliest = 0L;
        for (SopModule module : SopModuleStore.modules(context)) {
            if (!module.enabled) {
                continue;
            }
            long next = nextTriggerMillis(context, now, module);
            if (earliest == 0L || next < earliest) {
                earliest = next;
            }
        }
        if (earliest > 0L) {
            ReminderConfig.prefs(context).edit().putLong(ReminderConfig.KEY_NEXT_TRIGGER, earliest).apply();
        } else {
            ReminderConfig.prefs(context).edit().remove(ReminderConfig.KEY_NEXT_TRIGGER).apply();
        }
    }

    private static void updateStoredNextTrigger(Context context, long triggerAt) {
        long existing = ReminderConfig.prefs(context).getLong(ReminderConfig.KEY_NEXT_TRIGGER, 0L);
        if (existing == 0L || triggerAt < existing) {
            ReminderConfig.prefs(context).edit().putLong(ReminderConfig.KEY_NEXT_TRIGGER, triggerAt).apply();
        }
    }

    private static PendingIntent reminderPendingIntent(Context context, String moduleId, int flag) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMIND);
        intent.putExtra(EXTRA_MODULE_ID, moduleId);
        return PendingIntent.getBroadcast(context, requestCode(moduleId), intent, pendingFlags(flag));
    }

    private static int requestCode(String moduleId) {
        return REQUEST_CODE_BASE + Math.abs(moduleId == null ? 0 : moduleId.hashCode());
    }

    private static int pendingFlags(int flag) {
        int flags = flag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}

