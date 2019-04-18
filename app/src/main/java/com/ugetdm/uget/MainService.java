/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import com.ugetdm.uget.lib.*;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

// Life cycle control service

public class MainService extends Service {
    private MainApp  app = null;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    // onTaskRemoved()
    // 1. user remove task from recent app list
    // 2. system remove task if low memory
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);

        if (app != null)    // Error prevention mechanism
            app.onTerminate();
        else {
            // if you return START_NOT_STICKY in startCommand(), below code is unnecessary.
            stopSelf();
        }
    }

    // ----------------------------------------------------
    // onStartCommand() used by startService()

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //app = (MainApp) getApplicationContext();    // throw RuntimeException
        app = (MainApp) getApplication();

        // Error prevention mechanism
        if (app == null) {
            // if you return START_NOT_STICKY in startCommand(), below code is unnecessary.
            stopSelf();
        }

        // return START_NOT_STICKY. Then you service will not be restarted when killed.
        return Service.START_NOT_STICKY;
    }

    // ----------------------------------------------------
    // Binder: user by bindService() or unbindService()

    MainBinder  binder = null;

    class MainBinder extends Binder {
        public MainService getService() {
            return MainService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        //app = (MainApp) getApplicationContext();    // throw RuntimeException
        app = (MainApp) getApplication();

        if (binder == null)
            binder = new MainBinder();
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return super.onUnbind(intent);
    }

}
