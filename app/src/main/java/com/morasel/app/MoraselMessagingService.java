package com.morasel.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MoraselMessagingService extends FirebaseMessagingService {

    private static final String CHANNEL_ID = "morasel_messages";
    private static final String CHANNEL_NAME = "رسائل مراسل";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        String title = "مراسل";
        String body = "لديك رسالة جديدة";

        if (remoteMessage.getNotification() != null) {
            String notificationTitle =
                    remoteMessage.getNotification().getTitle();
            String notificationBody =
                    remoteMessage.getNotification().getBody();

            if (notificationTitle != null
                    && !notificationTitle.trim().isEmpty()) {
                title = notificationTitle;
            }

            if (notificationBody != null
                    && !notificationBody.trim().isEmpty()) {
                body = notificationBody;
            }
        }

        if (remoteMessage.getData() != null) {
            String dataTitle = remoteMessage.getData().get("title");
            String dataBody = remoteMessage.getData().get("body");

            if (dataTitle != null && !dataTitle.trim().isEmpty()) {
                title = dataTitle;
            }

            if (dataBody != null && !dataBody.trim().isEmpty()) {
                body = dataBody;
            }
        }

        showNotification(title, body);
    }

    private void showNotification(String title, String body) {
        createNotificationChannel();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(
                        android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(
                this, CHANNEL_ID
        )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build();

        NotificationManagerCompat.from(this).notify(
                (int) (System.currentTimeMillis() & 0x7fffffff),
                notification
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );

            channel.setDescription("إشعارات الرسائل الجديدة في مراسل");

            NotificationManager manager =
                    (NotificationManager) getSystemService(
                            Context.NOTIFICATION_SERVICE
                    );

            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);

        // يمكن حفظ رمز الجهاز في Firestore هنا
        // ليتمكن الخادم من إرسال الإشعارات إلى هذا الجهاز.
    }
}
