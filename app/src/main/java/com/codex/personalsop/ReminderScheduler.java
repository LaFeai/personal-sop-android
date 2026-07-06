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
import java.time.temporal.ChronoUnit;
import java.util.List;

final class ReminderScheduler {
    static final String EXTRA_MODULE_ID = "module_id";
    private static final int REQUEST_CODE_BASE = 1001;
    private static final int FUTURE_SEARCH_DAYS = 370;
    private static final int MONTHLY_SEARCH_MONTHS = 24;
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
        if (!hasConfiguredTimeWindow(module)) {
            return nowMillis + 24L * 60L * 60L * 1000L;
        }

        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone);
        LocalDateTime activeStart = occurrenceStart(now, module);
        if (context != null && activeStart != null && SopModuleStore.isCompleted(context, module, nowMillis)) {
            return nextStartAfter(activeStart, module).atZone(zone).toInstant().toEpochMilli();
        }


        LocalDate searchDate = now.toLocalDate();
        LocalDate maxDate = searchDate.plusDays(maxSearchDays(module));
        LocalDate candidateDate = nextAllowedDateOnOrAfter(searchDate, module);
        while (candidateDate != null && !candidateDate.isAfter(maxDate)) {
            LocalDateTime start = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
            LocalDateTime end = occurrenceEnd(start, module);
            LocalDateTime next;
            if (now.isBefore(start)) {
                next = start;
            } else if (!now.isBefore(end)) {
                candidateDate = nextAllowedDateAfter(candidateDate, module);
                continue;
            } else {
                long minutesSinceStart = java.time.Duration.between(start, now).toMinutes();
                long slots = (minutesSinceStart / Math.max(1, module.intervalMinutes)) + 1;
                next = start.plusMinutes(slots * Math.max(1, module.intervalMinutes));
                if (!next.isBefore(end)) {
                    candidateDate = nextAllowedDateAfter(candidateDate, module);
                    continue;
                }
            }
            return next.atZone(zone).toInstant().toEpochMilli();
        }
        return nextStartAfter(now, module).atZone(zone).toInstant().toEpochMilli();
    }

    static String occurrenceKey(long nowMillis, SopModule module) {
        if (!hasConfiguredTimeWindow(module)) {
            return null;
        }
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
        } else if (!now.isBefore(end)) {
            next = start.plusDays(1);
        } else {
            long minutesSinceStart = java.time.Duration.between(start, now).toMinutes();
            long slots = (minutesSinceStart / safeInterval) + 1;
            next = start.plusMinutes(slots * safeInterval);
            if (!next.isBefore(end)) {
                next = start.plusDays(1);
            }
        }

        return next.atZone(zone).toInstant().toEpochMilli();
    }

    private static boolean isAllowedDay(LocalDate date, SopModule module) {
        if (SopModule.CYCLE_DAILY.equals(module.cycleType)) {
            return true;
        }
        if (SopModule.CYCLE_WEEKLY.equals(module.cycleType)) {
            int dayIndex = date.getDayOfWeek().getValue() - 1;
            return (module.daysOfWeek & (1 << dayIndex)) != 0;
        }
        if (SopModule.CYCLE_MONTHLY.equals(module.cycleType)) {
            return isMonthlyAllowedDay(date, module);
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            return isIntervalAllowedDay(date, module);
        }
        return false;
    }

    private static boolean isMonthlyAllowedDay(LocalDate date, SopModule module) {
        LocalDate monthlyDate = monthlyDateInMonth(date.withDayOfMonth(1), module);
        return date.equals(monthlyDate);
    }

    private static LocalDate nextAllowedDateOnOrAfter(LocalDate date, SopModule module) {
        if (SopModule.CYCLE_MONTHLY.equals(module.cycleType)) {
            return nextMonthlyDateOnOrAfter(date, module);
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            return nextIntervalDateOnOrAfter(date, module);
        }
        LocalDate maxDate = date.plusDays(FUTURE_SEARCH_DAYS);
        LocalDate candidate = date;
        while (!candidate.isAfter(maxDate)) {
            if (isAllowedDay(candidate, module)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        return null;
    }

    private static LocalDate nextAllowedDateAfter(LocalDate date, SopModule module) {
        if (SopModule.CYCLE_MONTHLY.equals(module.cycleType)) {
            return nextMonthlyDateAfter(date, module);
        }
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            return nextIntervalDateAfter(date, module);
        }
        return nextAllowedDateOnOrAfter(date.plusDays(1), module);
    }

    private static LocalDate nextMonthlyDateAfter(LocalDate after, SopModule module) {
        return nextMonthlyDateOnOrAfter(after.plusDays(1), module);
    }

    private static LocalDate nextMonthlyDateOnOrAfter(LocalDate date, SopModule module) {
        LocalDate monthStart = date.withDayOfMonth(1);
        for (int offset = 0; offset <= MONTHLY_SEARCH_MONTHS; offset++) {
            LocalDate candidate = monthlyDateInMonth(monthStart.plusMonths(offset), module);
            if (candidate != null && !candidate.isBefore(date)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isIntervalAllowedDay(LocalDate date, SopModule module) {
        LocalDate start = safeIntervalStartDate(module);
        if (date.isBefore(start)) {
            return false;
        }
        long daysBetween = ChronoUnit.DAYS.between(start, date);
        return daysBetween % safeIntervalDays(module) == 0;
    }

    private static LocalDate nextIntervalDateAfter(LocalDate after, SopModule module) {
        return nextIntervalDateOnOrAfter(after.plusDays(1), module);
    }

    private static LocalDate nextIntervalDateOnOrAfter(LocalDate date, SopModule module) {
        LocalDate start = safeIntervalStartDate(module);
        if (!date.isAfter(start)) {
            return start;
        }
        int intervalDays = safeIntervalDays(module);
        long daysBetween = ChronoUnit.DAYS.between(start, date);
        long steps = (daysBetween + intervalDays - 1L) / intervalDays;
        return start.plusDays(steps * intervalDays);
    }

    private static LocalDate monthlyDateInMonth(LocalDate monthDate, SopModule module) {
        LocalDate monthStart = monthDate.withDayOfMonth(1);
        int targetDayOfWeek = safeMonthlyDayOfWeek(module) + 1;
        int ordinal = safeMonthlyWeekOrdinal(module);

        int daysUntilTarget = (targetDayOfWeek - monthStart.getDayOfWeek().getValue() + 7) % 7;
        LocalDate candidate = monthStart.plusDays(daysUntilTarget + (long) (ordinal - 1) * 7L);
        return candidate.getMonth() == monthStart.getMonth() ? candidate : null;
    }

    private static int safeMonthlyWeekOrdinal(SopModule module) {
        if (module.monthlyWeekOrdinal < 1 || module.monthlyWeekOrdinal > 4) {
            return 4;
        }
        return module.monthlyWeekOrdinal;
    }

    private static int safeMonthlyDayOfWeek(SopModule module) {
        return Math.max(0, Math.min(module.monthlyDayOfWeek, 6));
    }

    private static int safeIntervalDays(SopModule module) {
        return Math.max(SopModule.MIN_INTERVAL_DAYS, Math.min(module.intervalDays, SopModule.MAX_INTERVAL_DAYS));
    }

    private static LocalDate safeIntervalStartDate(SopModule module) {
        try {
            return LocalDate.ofEpochDay(module.intervalStartEpochDay);
        } catch (RuntimeException ex) {
            return LocalDate.now();
        }
    }

    private static int maxSearchDays(SopModule module) {
        if (SopModule.CYCLE_INTERVAL_DAYS.equals(module.cycleType)) {
            return Math.max(FUTURE_SEARCH_DAYS, safeIntervalDays(module) + 1);
        }
        return FUTURE_SEARCH_DAYS;
    }

    private static boolean hasConfiguredTimeWindow(SopModule module) {
        return module.startHour >= 0
                && module.startHour <= 23
                && module.startMinute >= 0
                && module.startMinute <= 59
                && module.endHour >= 0
                && module.endHour <= 23
                && module.endMinute >= 0
                && module.endMinute <= 59;
    }

    private static LocalDateTime occurrenceStart(LocalDateTime now, SopModule module) {
        LocalDate candidateDate = now.toLocalDate();
        if (!isAllowedDay(candidateDate, module)) {
            return null;
        }
        LocalDateTime start = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
        LocalDateTime end = occurrenceEnd(start, module);
        if ((now.isEqual(start) || now.isAfter(start)) && now.isBefore(end)) {
            return start;
        }
        return null;
    }

    private static LocalDateTime occurrenceEnd(LocalDateTime start, SopModule module) {
        LocalDateTime end = LocalDateTime.of(start.toLocalDate(), LocalTime.of(module.endHour, module.endMinute));
        LocalDateTime dayEnd = start.toLocalDate().plusDays(1).atStartOfDay();
        if (!end.isAfter(start)) {
            end = dayEnd;
        }
        return end.isBefore(dayEnd) ? end : dayEnd;
    }

    private static LocalDateTime nextStartAfter(LocalDateTime after, SopModule module) {
        LocalDate maxDate = after.toLocalDate().plusDays(maxSearchDays(module));
        LocalDate candidateDate = nextAllowedDateOnOrAfter(after.toLocalDate(), module);
        while (candidateDate != null && !candidateDate.isAfter(maxDate)) {
            LocalDateTime candidate = LocalDateTime.of(candidateDate, LocalTime.of(module.startHour, module.startMinute));
            if (candidate.isAfter(after)) {
                return candidate;
            }
            candidateDate = nextAllowedDateAfter(candidateDate, module);
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
