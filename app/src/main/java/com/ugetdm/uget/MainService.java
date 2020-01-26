/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import com.ugetdm.uget.lib.*;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

// Life cycle control service

public class MainService extends Service {
    // startService(intent)
    // onCreate() -> onStartCommand() -> onDestroy()

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";
    public static final String ACTION_STOP_FOREGROUND  = "ACTION_STOP_FOREGROUND";

    // --- startup counts that used by MainApp.startMainService() and MainApp.stopMainService() ---
    public static int   count;
    public static MainApp app;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (app == null) {
            // MainApp = (MainApp) getApplicationContext();    // throw RuntimeException
            app = (MainApp) getApplication();
        }

        // --- The Intent supplied to onStartCommand may be null if the service is being restarted after its process has gone away
        if (intent == null)
            return Service.START_NOT_STICKY;

        if (intent.getAction().equals(ACTION_START_FOREGROUND)) {
            app.logAppend("MainService - START");
            // --- start foreground service
            initNotificationBuilder();
            Notification notification = buildNotification(this);
            startForeground(MainApp.NOTIFICATION_ID, notification);
        }
        else if (intent.getAction().equals(ACTION_STOP_FOREGROUND)) {
            app.logAppend("MainService - STOP");
            // --- stop foreground service
            stopForeground(true);
            stopSelf();
        }

        // return super.onStartCommand(intent, flags, startId);
        // return START_NOT_STICKY. Then you service will not be restarted when killed.
        return Service.START_NOT_STICKY;
    }

    // onTaskRemoved()
    // 1. user remove task from recent app list
    // 2. system remove task if low memory
    // 3. this doesn't work in Android 8+
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        // Error prevention mechanism
        if (app != null) {
            app.logAppend("MainService.onTaskRemoved()");
            app.destroy(false);
        }
        else {
            // if you return START_NOT_STICKY in startCommand(), below code is unnecessary.
            stopSelf();
        }
    }

    // ----------------------------------------------------
    // Binder: user by bindService() or unbindService()

    // bindService(intent, mServiceConnection, int flags)
    // onCreate() -> onBind() -> onUnbind() -> onDestroy()

    private LocalBinder  binder = null;

    class LocalBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (binder == null)
            binder = new LocalBinder();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

    // ----------------------------------------------------
    // Notification

    public  static Notification.Builder builder;
    private final String        CHANNEL_ID_SERVICE    = "-.Service";

    private void initNotificationBuilder() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager;
            NotificationChannel notificationChannel;

            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationChannel = notificationManager.getNotificationChannel(CHANNEL_ID_SERVICE);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(CHANNEL_ID_SERVICE,
                        getString(R.string.notification_channel_service),
                        NotificationManager.IMPORTANCE_MIN);
                notificationChannel.enableVibration(false);
                notificationChannel.setSound(null, null);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            builder = new Notification.Builder(getApplicationContext(), CHANNEL_ID_SERVICE);
        }
        else {
            builder = new Notification.Builder(getApplicationContext());
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                builder.setPriority(Notification.PRIORITY_MIN);
        }
    }

    public static Notification buildNotification(Context context) {
        Intent notifyIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, 0);
        builder.setContentIntent(pendingIntent)
                .setWhen(System.currentTimeMillis());

        String title = context.getString(R.string.notification_service_running);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            title = context.getString(R.string.app_name) + " · " + title;
        builder.setContentTitle(title);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            builder.setSmallIcon(R.mipmap.ic_launcher);
        else {
            builder.setSmallIcon(R.mipmap.ic_notification)
                    .setColor(context.getResources().getColor(R.color.colorPrimary));
        }

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            return builder.getNotification();
        else
            return builder.build();
    }
}
