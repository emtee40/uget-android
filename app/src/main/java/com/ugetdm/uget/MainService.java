/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import com.ugetdm.uget.lib.*;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;

// Life cycle control service

public class MainService extends Service {
    // startService(intent)
    // onCreate() -> onStartCommand() -> onDestroy()

    public static final String ACTION_START_FOREGROUND = "ACTION_START_FOREGROUND";

    public static boolean   isForeground;
    public static int       count;

    @Override
    public void onCreate() {
        super.onCreate();

        isForeground = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // MainApp = (MainApp) getApplicationContext();    // throw RuntimeException
        MainApp app = (MainApp) getApplication();
        app.logAppend("MainService.onDestroy()");

        // --- stop foreground service
        if (isForeground)
            stopForeground(true);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // MainApp = (MainApp) getApplicationContext();    // throw RuntimeException
        MainApp app = (MainApp) getApplication();
        app.logAppend("MainService.onStartCommand()");

        // --- start foreground service
        if (intent.getAction().equals(ACTION_START_FOREGROUND)) {
            Notification notification = createNotification(app);
            startForeground(14777, notification);
            isForeground = true;
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

        // MainApp = (MainApp) getApplicationContext();    // throw RuntimeException
        MainApp app = (MainApp) getApplication();

        // Error prevention mechanism
        if (app != null) {
            // app.logAppend("MainService.onTaskRemoved()");
            // if (Job.queued[Job.SAVE_ALL] == 0)
            //     Job.saveAll();
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

    private Notification createNotification(MainApp app) {
        Notification.Builder builder = app.builderService;
        Notification         notification;
        Intent notifyIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifyIntent, 0);
        builder.setContentIntent(pendingIntent)
               .setWhen(System.currentTimeMillis());

        String title = getString(R.string.notification_service_running);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            title = getString(R.string.app_name) + " · " + title;
        builder.setContentTitle(title);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            builder.setSmallIcon(R.mipmap.ic_launcher);
        else {
            builder.setSmallIcon(R.mipmap.ic_notification)
                    .setColor(getResources().getColor(R.color.colorPrimary));
        }
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            notification = builder.getNotification();
        else
            notification = builder.build();

        return notification;
    }
}
