/*
      *
      *   Copyright (C) 2019 by C.H. Huang
      *   plushuang.tw@gmail.com
      */

package com.ugetdm.uget;

import android.os.Handler;
import android.os.HandlerThread;

public class Job {
    public  static final int CUSTOM = 0;
    public  static final int LOAD = 1;
    public  static final int SAVE = 2;
    public  static final int LOAD_FD = 3;
    public  static final int SAVE_FD = 4;
    public  static final int LOAD_ALL = 5;
    public  static final int SAVE_ALL = 6;
    public  static final int TOTAL = 7;

    private static MainApp       mainApp;
    private static Handler       handler;
    private static HandlerThread thread;

    public  static int       queuedTotal;
    public  static int       queued[];
    public  static int       result[];

    public  static void init(MainApp app) {
        queued = new int[TOTAL];
        result = new int[TOTAL];

        thread = new HandlerThread("name");
        thread.start();
        handler = new Handler(thread.getLooper());
        mainApp = app;
    }

    public  static void destroy() {
        thread.quit();
    }

    public static void runOnThread(Runnable runnable) {
        queuedTotal++;
        queued[CUSTOM]++;
        result[CUSTOM] = 0;

        class ThreadRunnable implements Runnable {
            Runnable runnable;

            public ThreadRunnable(Runnable runnable) {
                this.runnable = runnable;
            }
            @Override
            public void run() {
                runnable.run();
                queued[CUSTOM]--;
                queuedTotal--;
            }
        };
        handler.post(new ThreadRunnable(runnable));
    }

    public static void load(String filename) {
        queuedTotal++;
        queued[LOAD]++;
        result[LOAD] = 0;

        class loadRunnable implements Runnable {
            public String filename;

            public loadRunnable(String filename) {
                this.filename = filename;
            }
            @Override
            public void run() {
                if (mainApp.core.loadCategory(filename) == 0)
                    result[LOAD] = -1;
                queued[LOAD]--;
                queuedTotal--;
            }
        };
        handler.post(new loadRunnable(filename));
    }

    public static void save(long node, String filename) {
        queuedTotal++;
        queued[SAVE]++;
        result[SAVE] = 0;

        class saveRunnable implements Runnable {
            public String filename;
            public long   node;

            public saveRunnable(long node, String filename) {
                this.node = node;
                this.filename = filename;
            }
            @Override
            public void run() {
                if (mainApp.core.saveCategory(node, filename) == false)
                    result[SAVE] = -1;
                queued[SAVE]--;
                queuedTotal--;
            }
        };
        handler.post(new saveRunnable(node, filename));
    }

    public static void loadFd(int fd) {
        queuedTotal++;
        queued[LOAD_FD]++;
        result[LOAD_FD] = 0;

        class loadFdRunnable implements Runnable {
            public int fd;

            public loadFdRunnable(int fd) {
                this.fd = fd;
            }
            @Override
            public void run() {
                if (mainApp.core.loadCategory(fd) == 0)
                    result[LOAD_FD] = -1;
                queued[LOAD_FD]--;
                queuedTotal--;
            }
        };
        handler.post(new loadFdRunnable(fd));
    }

    public static void saveFd(long node, int fd) {
        queuedTotal++;
        queued[SAVE_FD]++;
        result[SAVE_FD] = 0;

        class saveFdRunnable implements Runnable {
            public long node;
            public int  fd;

            public saveFdRunnable(long node, int fd) {
                this.node = node;
                this.fd = fd;
            }
            @Override
            public void run() {
                if (mainApp.core.saveCategory(node, fd) == false)
                    result[SAVE_FD] = -1;
                queued[SAVE_FD]--;
                queuedTotal--;
            }
        };
        handler.post(new saveFdRunnable(node, fd));
    }

    public static void loadAll() {
        queuedTotal++;
        queued[LOAD_ALL]++;
        result[LOAD_ALL] = 0;

        handler.post(new Runnable() {
            @Override
            public void run() {
                // mainApp.core.loadCategories(null);
                mainApp.loadCategoriesOnStart();
                queued[LOAD_ALL]--;
                queuedTotal--;
            }
        });
    }

    public static void saveAll() {
        queuedTotal++;
        queued[SAVE_ALL]++;
        result[SAVE_ALL] = 0;

        handler.post(new Runnable() {
            @Override
            public void run() {
                mainApp.saveCategories();
                queued[SAVE_ALL]--;
                queuedTotal--;
            }
        });
    }
}