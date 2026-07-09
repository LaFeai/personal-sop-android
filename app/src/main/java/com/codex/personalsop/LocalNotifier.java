package com.codex.personalsop;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

final class LocalNotifier {
    private static final String CHANNEL_ID = "personal_sop_status";
    private static final int BASE_NOTIFICATION_ID = 2001;

    private LocalNotifier() {
    }

    static void show(Context context, String title, String text) {
        show(context, title, text, BASE_NOTIFICATION_ID);
    }

    static void showReminder(Context context, SopModule module) {
        show(context, module.name, module.message, notificationId(module));
    }

    private static void show(Context context, String title, String text, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "SOP 提醒",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager.createNotificationChannel(channel);
        }
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new android.app.Notification.Builder(context, CHANNEL_ID)
                : new android.app.Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true);
        manager.notify(notificationId, builder.build());
    }

    private static int notificationId(SopModule module) {
        if (module == null || module.id == null) {
            return BASE_NOTIFICATION_ID;
        }
        return BASE_NOTIFICATION_ID + Math.abs(module.id.hashCode() % 100000);
    }
}
