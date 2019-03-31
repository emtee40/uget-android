/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.content.ClipboardManager;
import android.util.*;

import com.ugetdm.uget.lib.*;

import java.io.*;
import java.util.List;

public class MainApp extends Application {
    public Core    core;

    // position used by switchDownloadAdapter()
    public int     nthStatus   = 0;
    public int     nthCategory = 0;
    // scrolled position
//    public int     downloadListScrolledX = 0;
//    public int     downloadListScrolledY = 0;

    //
    public int     nthCategoryCreation = 0;
    public boolean userAction  = false;
    // JSON-RPC
    public Rpc              rpc;
    // adapter
    public DownloadAdapter  downloadAdapter;
    public CategoryAdapter  categoryAdapter;
    public StateAdapter     stateAdapter;
    //
    public MainActivity     mainActivity;
    // NodeActivity use this to save/restore properties
    public CategoryProp     categoryProp = new CategoryProp();

    // Timeout Interval & Handler
    public TimeoutHandler   timeoutHandler = new TimeoutHandler(this);

    // constructor must run before MainActivity.OnCreate()
    public MainApp() {
        core = new Core();
    }

    // ------------------------------------------------------------------------
//    ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    // ------------------------------------------------------------------------
    // WakeLock: keep an Activity running/active when the screen shuts off
    private PowerManager.WakeLock wakeLock = null;
    private int  wakeLockCount = 0;

    public void acquireWakeLock() {
        if (wakeLock == null) {
            logAppend("WakeLock acquire");
            PowerManager pm = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
            if (pm == null)
                return;
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "uGet: Processing...");
            if (wakeLock != null)
                wakeLock.acquire();
        }
        wakeLockCount++;
    }

    public void releaseWakeLock() {
        wakeLockCount--;
        if (wakeLock != null && wakeLockCount == 0) {
            logAppend("WakeLock release");
            wakeLock.release();
            wakeLock = null;
        }
    }

    // ------------------------------------------------------------------------
    // BroadcastReceiver
    BroadcastReceiver  broadcastReceiver;

    void registerBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SHUTDOWN);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
                    onTerminate();
                }
                else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    releaseWakeLock();
                }
                else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                    acquireWakeLock();
                }
            }
        };
        registerReceiver(broadcastReceiver, intentFilter);
    }

    void unregisterBroadcastReceiver() {
        unregisterReceiver(broadcastReceiver);
    }

    // ------------------------------------------------------------------------
    // TimerService: keep application alive

    /*
    TimerService  timerService = null;

    private ServiceConnection timerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            TimerService.TimerBinder binder = (TimerService.TimerBinder) service;
            timerService = binder.getService();
            timerService.startHandler();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            timerService.stopHandler();
            timerService = null;
        }
    };
*/

    public void startMainService () {
        // start service
        Intent intent = new Intent(MainApp.this, MainService.class);
        startService(intent);

//        bindService(intent, timerServiceConnection, Context.BIND_AUTO_CREATE);
    }

    public void stopMainService () {
        // stop service
        Intent intent = new Intent(MainApp.this, MainService.class);
        stopService(intent);

//        if (timerService != null)
//            timerService.stopHandler();
//        unbindService(timerServiceConnection);
    }

    // ------------------------------------------------------------------------
    @Override
    public void onCreate() {
        super.onCreate();
        // log
        logClear();
        logAppend("App.onCreate()");

        Ccj.init(this);    // initial C Call Java in main thread
        // call unregisterBroadcastReceiver() and releaseWakeLock() in onTerminate()
        registerBroadcastReceiver();
        acquireWakeLock();
    }

    @Override
    public void onTerminate () {
        super.onTerminate();
        logAppend("App.onTerminate()");
        //    Log.v("uGet", "App.onTerminate()");

        // offline status
        saveStatus();

        // call startTimerService() and rpc.startServer() in startRunning()
        logAppend("App.stopMainService()");
        stopMainService();
        logAppend("App.stopHandler()");
        timeoutHandler.stopHandler();
        logAppend("App.rpc.stopServer()");
        rpc.stopServer();

        // clear data in system
        logAppend("App.clearClipboard()");
        clearClipboard();
        cancelNotification();

        // JNI finalize functions
        logAppend("core.removeAllTask()");
        core.removeAllTask();
        saveAllData();
        logAppend("core.clearAttachment()");
        core.clearAttachment();

        // this will release all JNI data.
        // Don't call any JNI function after App.cFinal()
        core.cFinal(setting.plugin.aria2.shutdown && setting.plugin.aria2.local);
        Ccj.cFinal();

        //    Lib.sync();
        //    SystemClock.sleep(3000);

        // call registerBroadcastReceiver() and acquireWakeLock() in onCreate()
        unregisterBroadcastReceiver();
        releaseWakeLock();

        logAppend("App.onTerminate() ok");
        android.os.Process.killProcess(android.os.Process.myPid());
        //    System.exit(0);
    }

    // this function was called by MainActivity.onCreate()
    public void onCreateRunning() {
        if (rpc != null)
            return;
        logAppend ("App.onCreateRunning()");

        logAppend ("App.onCreateRunning() create Adapter and RPC server");
        // Adapter
        downloadAdapter = new DownloadAdapter(Node.getNthChild(core.nodeMix, 0));
        categoryAdapter = new CategoryAdapter(core.nodeReal, core.nodeMix);
        stateAdapter = new StateAdapter(this, Node.getNthChild(core.nodeMix, 0));

        File filesDir = getFilesDir();
        if (filesDir != null) {
            // RPC server
            rpc = new Rpc(filesDir.getAbsolutePath() + "/attachment");
            // load/create category
            core.setConfigDir(filesDir.getAbsolutePath());
            logAppend ("App.onCreateRunning() before loading category");
            //   getExternalStorageDirectory().toString()
            core.loadCategories(null);
            logAppend("App.onCreateRunning() after loading category");
        }

        // if there is no category
        if (Node.nChildren(core.nodeReal) == 0) {
            logAppend ("App.onCreateRunning() no category loaded, create one.");
            createDefaultCategory();
        }

        loadFolderHistory();
        logAppend("App.onCreateRunning() after loading folder history");
        initFolderWritable();

        initSharedPreferences();
        logAppend("App.onCreateRunning() after initSharedPreferences");
        initClipboard();
        logAppend("App.onCreateRunning() after initClipboard");
        initNotification();

        // call stopTimerService() and rpc.stopServer() in onTerminate()
        rpc.startServer();
        logAppend("App.onCreateRunning() after rpc.startServer");
        timeoutHandler.startHandler();
        logAppend("App.onCreateRunning() after timeoutHandler.startHandler");
        startMainService();

        // offline status
        loadStatus();

        refreshUriPermission();  // update permission after device reboot

        // loading category proterties from uGet for Android 1.x
        if (preferences.getInt("pref_about_version", 0) == 0) {
            preferences.edit().putInt("pref_about_version", 1).apply();
            long cnode;
            for (cnode = Node.children(core.nodeReal);  cnode != 0;  cnode = Node.next(cnode) ) {
                long infoPointer = Node.info(cnode);
                Info.setGroup(infoPointer, Info.Group.queuing);
            }
        }

        logAppend ("App.onCreateRunning() return");
    }

    public void refreshUriPermission() {
        // update permissions
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<UriPermission> list = getContentResolver().getPersistedUriPermissions();

            for (UriPermission uriPermission : list) {
                Uri uri = uriPermission.getUri();
                logAppend(uri.toString());
                if (uriPermission.isWritePermission()) {
                    logAppend(uri.toString() + " takePersistableUriPermission");
                    grantUriPermission(getPackageName(), uri,
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    getContentResolver().takePersistableUriPermission(uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION |
                                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
            }
        }
    }

    public void saveStatus() {
        File filesDir = getFilesDir();
        if (filesDir == null)    // avoid NullPointerException
            return;
        File statusFile = new File(filesDir, "offline");
        if (statusFile == null)
            return;

        if (setting.offlineMode) {
            try {
                statusFile.createNewFile();
            } catch (Exception e) {}
        }
        else {
            statusFile.delete();
        }
    }

    public void loadStatus() {
        File filesDir = getFilesDir();
        if (filesDir == null)    // avoid NullPointerException
            return;
        File statusFile = new File (filesDir, "offline");
        if (statusFile == null)
            return;

        if (statusFile.exists()) {
            statusFile.delete();
            setting.offlineMode = true;
        }
    }

    public void saveAllData() {
        File filesDir = getFilesDir();
        if (filesDir == null)
            return;

        File path = new File(filesDir,"category");
        path.mkdirs();
        path = null;
        saveFolderHistory();
        int nCategorySaved = core.saveCategories(filesDir.getAbsolutePath());
        if (nCategorySaved == 0) {
            Log.v("uGet", "App.saveAllData(): Nothing save");
            // TODO: show message dialog
        }
        Log.v ("uGet", "App.nCategorySaved = " + nCategorySaved);
    }

    public void createDefaultCategory() {
        long  nodePointer;
        long  infoPointer;
        CategoryProp  categoryProp = new CategoryProp();

        categoryProp.name = getString(R.string.cnode_name_new, nthCategoryCreation++);
        categoryProp.activeLimit = 2;
        categoryProp.finishedLimit = 100;
        categoryProp.recycledLimit = 100;
        categoryProp.hosts = ".edu;.idv";
        categoryProp.schemes = "ftps";
        categoryProp.fileTypes = "bmp;jpg";
        categoryProp.folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
        categoryProp.connections = 1;
        categoryProp.proxyPort = 80;
        categoryProp.group = Info.Group.queuing;

        nodePointer = Node.create();
        infoPointer = Node.info(nodePointer);
        Info.set(infoPointer, categoryProp);

        addCategoryNode(nodePointer);
    }

    public void switchDownloadAdapter() {
        long  cnode;

        if (nthCategory == 0)
            cnode = Node.getNthChild(core.nodeMix, 0);
        else
            cnode = Node.getNthChild (core.nodeSorted, nthCategory - 1);

        if (stateAdapter.pointer != cnode) {
            stateAdapter.pointer = cnode;
            stateAdapter.notifyDataSetChanged();
        }

        switch (nthStatus) {
        default:
//      case 0:
            break;

        case 1:
            cnode = Node.getFakeByGroup(cnode, Info.Group.active);
            break;

        case 2:
            cnode = Node.getFakeByGroup(cnode, Info.Group.queuing);
            break;

        case 3:
            cnode = Node.getFakeByGroup(cnode, Info.Group.finished);
            break;

        case 4:
            cnode = Node.getFakeByGroup(cnode, Info.Group.recycled);
            break;
        }

	    if (downloadAdapter.pointer != cnode) {
	        downloadAdapter.pointer = cnode;
            downloadAdapter.clearChoices(false);
	        downloadAdapter.notifyDataSetChanged();
	    }
    }

    // ------------------------------------------------------------------------
    // category functions

    public void    addCategoryNode(long cNodePointer)
    {
        int  position;

        core.addCategory(cNodePointer);
        position = Node.getPosition(core.nodeReal, cNodePointer);
        if (categoryAdapter != null) {
            categoryAdapter.notifyItemInserted(position);
            categoryAdapter.notifyItemChanged(0);    // "All Category" nChildren() was changed
        }
        // --- If user selected "All Category", status nChildren() was changed too.
        if (stateAdapter != null)
            stateAdapter.notifyDataSetChanged();
    }

    public void    deleteNthCategory(int nth)
    {
        long cNodePointer;

        if (nth > 0) {
            cNodePointer = Node.getNthChild(core.nodeReal, nth -1);
            if (cNodePointer != 0) {
                core.deleteCategory(cNodePointer);
                categoryAdapter.notifyItemRemoved(nth);
                categoryAdapter.notifyItemChanged(0);    // "All Category" getItemCount() changed
            }
        }

        if (Node.nChildren(core.nodeReal) == 0) {
            // --- create new category and append it.
            createDefaultCategory();
        }
        else {
            // move position to previous category.
            cNodePointer = Node.getNthChild(core.nodeReal, nth -1);
            if (cNodePointer == 0) {
                nthCategory = nth - 1;
            }
        }
        // switch status and download adapter
        switchDownloadAdapter();
        userAction = true;
    }

    public boolean moveNthCategory(int from_nth, int to_nth)
    {
        boolean result = false;
        long    cNodePosition;
        long    cNodePointer;

        if (from_nth > 0 && to_nth > 0) {
            // if user move category down, position + 1.
            cNodePointer  = Node.getNthChild(core.nodeReal, from_nth -1);
            cNodePosition = Node.getNthChild(core.nodeReal, to_nth - 1);
            if (cNodePointer != 0 || cNodePosition != 0) {
                if (to_nth > from_nth)
                    cNodePosition = Node.next(cNodePosition);
                result = core.moveCategory(cNodePointer, cNodePosition);
                categoryAdapter.notifyItemMoved(from_nth, to_nth);
            }
        }

        return result;
    }

    public boolean saveNthCategory(int nthCategory, String filename)
    {
        int nthCategoryReal = nthCategory - 1;
        if (nthCategoryReal < 0)
            nthCategoryReal = 0;
        long nodePointer = Node.getNthChild(core.nodeReal, nthCategoryReal);
        return core.saveCategory(nodePointer, filename);
    }

    public boolean saveNthCategory(int nthCategory, int fd)
    {
        int nthCategoryReal = nthCategory - 1;
        if (nthCategoryReal < 0)
            nthCategoryReal = 0;
        long nodePointer = Node.getNthChild(core.nodeReal, nthCategoryReal);
        return core.saveCategory(nodePointer, fd);
    }

    public long getNthCategory(int nthCategory) {
        int nthCategoryReal = nthCategory - 1;
        if (nthCategoryReal < 0)
            nthCategoryReal = 0;
        return Node.getNthChild(core.nodeReal, nthCategoryReal);
    }

    // ------------------------------------------------------------------------
    // download functions

    public File getDownloadedFile(int nth)
    {
        long    pointer;
        String  path;
        DownloadProp   downloadProp;

        // get download from category
        pointer = Node.getNthChild(downloadAdapter.pointer, nth);
        if (pointer == 0)
            return null;
        pointer = Node.info(pointer);

        downloadProp = new DownloadProp();
        Info.get(pointer, downloadProp);
        path = new String();
        if (downloadProp.folder != null)
            path += downloadProp.folder + File.separator;
        // filename
        if (downloadProp.file != null)
            path += downloadProp.file;
        else if (Info.getName(pointer) != null)
            path += Info.getName(pointer);
        else
            return null;

        File  file = new File(path);
        if (file.exists() == false)
            return null;
        return file;
    }

    public int addDownloadNode(long dNodePointer, int toNthCategory) {
        int toNthCategoryReal = toNthCategory - 1;
        if (toNthCategoryReal < 0)
            toNthCategoryReal = 0;

        long cNodePointer = Node.getNthChild(core.nodeReal, toNthCategoryReal);
        core.addDownload(dNodePointer, cNodePointer, false);
        int  position = Node.getPosition(cNodePointer, dNodePointer);

        if (toNthCategory == nthCategory) {
            stateAdapter.notifyDataSetChanged();
            downloadAdapter.notifyItemInserted(position);
        }
        categoryAdapter.notifyDataSetChanged();
        return position;
    }

    public void deleteNthDownload(int nth, boolean deleteFiles)
    {
        long  cNodePointer;
        long  dNodePointer;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer != 0) {
            core.deleteDownload(dNodePointer, deleteFiles);
            stateAdapter.notifyDataSetChanged();
            categoryAdapter.notifyDataSetChanged();
            downloadAdapter.notifyItemRemoved(nth);
        }

        userAction = true;
    }

    public int recycleNthDownload(int nth)
    {
        long  cNodePointer;
        long  dNodePointer;
        int   nthLast = nth;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer != 0) {
            // core.recycleDownload() return false if it removed.
            if (core.recycleDownload(dNodePointer))
                nth = getDownloadPositionByNode(dNodePointer);
            else
                nth = -1;

            if (nth < 0) {
                // item was removed
                downloadAdapter.notifyItemRemoved(nthLast);
                categoryAdapter.notifyDataSetChanged();
            }
            else {
                // item was moved to other position
                downloadAdapter.notifyItemMoved(nthLast, nth);
            }
            stateAdapter.notifyDataSetChanged();
        }

        userAction = true;
        return nth;
    }

    public int moveNthDownload(int from_nth, int to_nth)
    {
        long    cNodePointer;
        long    dNodePointer1;
        long    dNodePointer2;

        cNodePointer  = downloadAdapter.pointer;
        dNodePointer1 = Node.getNthChild(cNodePointer, from_nth);
        dNodePointer2 = Node.getNthChild(cNodePointer, to_nth);
        if (dNodePointer1 == 0)
            return -1;

        if (core.moveDownload(dNodePointer1, dNodePointer2)) {
            to_nth = getDownloadPositionByNode(dNodePointer1);
            downloadAdapter.notifyItemMoved(from_nth, to_nth);
            return to_nth;
        }

        return -1;
    }

    public int activateNthDownload(int nth)
    {
        long    cNodePointer;
        long    dNodePointer;
        int      nthLast = nth;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer == 0)
            return -1;
        else {
            if (core.activateDownload(dNodePointer)) {
                nth = getDownloadPositionByNode(dNodePointer);
                if (nth < 0) {
                    // item was moved to other status
                    downloadAdapter.notifyItemRemoved(nthLast);
                }
                else {
                    // item was moved to other position
                    downloadAdapter.notifyItemMoved(nthLast, nth);
                }
                stateAdapter.notifyDataSetChanged();
            }
        }

        userAction = true;
        return nth;
    }

    public int pauseNthDownload(int nth)
    {
        long    cNodePointer;
        long    dNodePointer;
        int      nthLast = nth;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer == 0)
            return -1;
        else {
            if (core.pauseDownload(dNodePointer)) {
                nth = getDownloadPositionByNode(dNodePointer);
                if (nth < 0) {
                    // item was moved to other status
                    downloadAdapter.notifyItemRemoved(nthLast);
                }
                else {
                    // item was moved to other position
                    downloadAdapter.notifyItemMoved(nthLast, nth);
                }
                stateAdapter.notifyDataSetChanged();
            }
        }

        userAction = true;
        return nth;
    }

    public int queueNthDownload(int nth)
    {
        long    cNodePointer;
        long    dNodePointer;
        int      nthLast = nth;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer == 0)
            return -1;
        else {
            if (core.queueDownload(dNodePointer)) {
                nth = getDownloadPositionByNode(dNodePointer);
                if (nth < 0) {
                    // item was moved to other status
                    downloadAdapter.notifyItemRemoved(nthLast);
                }
                else {
                    // item was moved to other position
                    downloadAdapter.notifyItemMoved(nthLast, nth);
                }
                stateAdapter.notifyDataSetChanged();
            }
        }

//      userAction = true;
        return nth;
    }

    public int tryQueueNthDownload(int nth)
    {
        long    cNodePointer;
        long    dNodePointer;
        long    infoPointer;

        cNodePointer = downloadAdapter.pointer;
        dNodePointer = Node.getNthChild(cNodePointer, nth);
        if (dNodePointer == 0)
            return -1;
        infoPointer = Node.info(dNodePointer);
        if ((Info.getGroup(infoPointer) & Info.Group.active) > 0)
            return nth;

        return queueNthDownload(nth);
    }

    public int  getNthDownloadPriority(int nth)
    {
        long    nodePointer;
        int     result;

        // get download node from category node
        nodePointer = Node.getNthChild(downloadAdapter.pointer, nth);
        result = Info.getPriority(Node.info(nodePointer));
        return result;
    }

    public void setNthDownloadPriority(int nth, int priority)
    {
       long    nodePointer;

        // get download from category
        nodePointer = Node.getNthChild(downloadAdapter.pointer, nth);
        if (nodePointer != 0)
            Info.setPriority(Node.info(nodePointer), priority);
    }

    public long getDownloadNodeByPosition(int position) {
        if (position == -1)
            return 0;

        long dNode = Node.getNthChild(downloadAdapter.pointer, position);
        if (dNode != 0)
            return Node.base(dNode);
        else
            return 0;
    }

    public int  getDownloadPositionByNode(long dNode) {
        if (dNode != 0) {
            dNode = Node.getFakeByParent(dNode, downloadAdapter.pointer);
            if (dNode != 0)
                return Node.getPosition(downloadAdapter.pointer, dNode);
        }
        return -1;
    }

    public long[]  getActiveDownloadNode() {
        long nodeArray[];
        long curPointer;
        int  length;

        curPointer = Node.base(downloadAdapter.pointer);
        curPointer = Node.getFakeByGroup(curPointer, Info.Group.active);
        length = Node.nChildren(curPointer);
        if (length == 0)
            return null;
        nodeArray = new long[length];
        curPointer = Node.children(curPointer);

        for (int i = 0;  i < length;  i++) {
            nodeArray[i] = Node.base(curPointer);
            curPointer = Node.next(curPointer);
        }

        return nodeArray;
    }

    // ------------------------------------------------------------------------
    // Preferences

    Setting           setting;
    SharedPreferences preferences;
    // Make a global variable which keeps a hard reference to the listener.
    // It may be freed by garbage collector if you don't use global variable.
    SharedPreferences.OnSharedPreferenceChangeListener  changeListener;

    public void initSharedPreferences() {
        setting = new Setting();
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
//      preferences = getSharedPreferences("preferences", MODE_PRIVATE);
        getSettingFromPreferences();
        applySetting(true, false);
    }

    public void getSettingFromPreferences() {
        SharedPreferences.Editor  preferencesEditor;
        String  string;
//      if (preferences == null)
//          return;
        preferencesEditor = preferences.edit();

        setting.ui.confirmDelete = preferences.getBoolean("pref_ui_confirm_delete", true);
        setting.ui.confirmExit = preferences.getBoolean("pref_ui_confirm_exit", true);
        setting.ui.exitOnBack = preferences.getBoolean("pref_ui_exit_on_back", false);
        setting.ui.startNotification = preferences.getBoolean("pref_ui_notification_starting", true);
        setting.ui.soundNotification = preferences.getBoolean("pref_ui_notification_sound", true);
        setting.ui.vibrateNotification = preferences.getBoolean("pref_ui_notification_vibrate", true);
        setting.ui.noWifiGoOffline = preferences.getBoolean("pref_ui_no_wifi_go_offline", false);
        setting.ui.skipExistingUri = preferences.getBoolean("pref_ui_skip_existing_uri", false);

        setting.clipboard.enable = preferences.getBoolean("pref_clipboard_monitor", true);

        // pref_clipboard_type
        setting.clipboard.types = preferences.getString("pref_clipboard_type",
                "BIN|ZIP|GZ|7Z|XZ|Z|TAR|TGZ|BZ2|" +
                "LZH|A[0-9]?|RAR|R[0-9][0-9]|ISO|" +
                "RPM|DEB|EXE|MSI|APK|" +
                "3GP|AAC|FLAC|M4A|M4P|MP3|OGG|WAV|WMA|" +
                "MP4|MKV|WEBM|OGV|AVI|MOV|WMV|FLV|F4V|MPG|MPEG|RMVB").replaceAll("(\\r|\\n)", "");
        // remove '\r' and '\n' and save it
        preferencesEditor.putString("pref_clipboard_type", setting.clipboard.types);
        // for Java's Regular Express format - case-insensitive "(?i:XXXXXXXX)"
        setting.clipboard.types = "(?i:" + setting.clipboard.types + ")";
        // for Java's Regular Express function - String.matches() can't accept '*' directly.
        setting.clipboard.types = setting.clipboard.types.replace("*", "\\*");

        // pref_clipboard_index
        string = preferences.getString("pref_clipboard_index", "0");
        try {
            if (string.length() > 0)
                setting.clipboard.nthCategory = Integer.parseInt(string);
            else
                setting.clipboard.nthCategory = 0;
        } catch (NumberFormatException e) {
            setting.clipboard.nthCategory = 0;
            preferencesEditor.putString("pref_clipboard_index",
                    Integer.toString(setting.clipboard.nthCategory));
        }

        // pref_clipboard_website
        setting.clipboard.website = preferences.getBoolean("pref_clipboard_website", true);
        // pref_clipboard_clear_at_exit
        setting.clipboard.clearAtExit = preferences.getBoolean("pref_clipboard_clear_at_exit", true);
        // pref_clipboard_clear_after_accepting
        setting.clipboard.clearAfterAccepting = preferences.getBoolean("pref_clipboard_clear_after_accepting", true);

        // pref_sort
        string = preferences.getString("pref_sort", "0");
        try {
            if (string.length() > 0)
                setting.sortBy = Integer.parseInt(string);
            else
                setting.sortBy = 0;
        } catch (NumberFormatException e) {
            setting.sortBy = 0;
            preferencesEditor.putString("pref_sort",
                    Integer.toString(setting.sortBy));
        }

        // pref_speed_download
        string = preferences.getString("pref_speed_download", "0");
        try {
            if (string.length() > 0)
                setting.speedDownload = Integer.parseInt(string);
            else
                setting.speedDownload = 0;
        } catch (NumberFormatException e) {
            setting.speedDownload = Integer.MAX_VALUE;
            preferencesEditor.putString("pref_speed_download",
                    Integer.toString(setting.speedDownload));
        }

        // pref_speed_upload
        string = preferences.getString("pref_speed_upload", "0");
        try {
            if (string.length() > 0)
                setting.speedUpload = Integer.parseInt(string);
            else
                setting.speedUpload = 0;
        } catch (NumberFormatException e) {
            setting.speedUpload = Integer.MAX_VALUE;
            preferencesEditor.putString("pref_speed_upload",
                    Integer.toString(setting.speedUpload));
        }

        // pref_plugin_order
        string = preferences.getString("pref_plugin_order", "0");
        try {
            if (string.length() > 0)
                setting.pluginOrder = Integer.parseInt(string);
            else
                setting.pluginOrder = 0;
        } catch (NumberFormatException e) {
            setting.pluginOrder = 0;
            preferencesEditor.putString("pref_plugin_order",
                    Integer.toString(setting.pluginOrder));
        }

        // pref_aria2_xxx
        setting.plugin.aria2.uri = preferences.getString("pref_aria2_uri", "http://localhost:6800/jsonrpc");
        setting.plugin.aria2.token = preferences.getString("pref_aria2_token", "aria2");

        // pref_aria2_speed_download
        string = preferences.getString("pref_aria2_speed_download", "0");
        try {
            if (string.length() > 0)
                setting.plugin.aria2.speedDownload = Integer.parseInt(string);
            else
                setting.plugin.aria2.speedDownload = 0;
        } catch (NumberFormatException e) {
            setting.plugin.aria2.speedDownload = Integer.MAX_VALUE;
            preferencesEditor.putString("pref_aria2_speed_download",
                    Integer.toString(setting.plugin.aria2.speedDownload));
        }

        // pref_aria2_speed_upload
        string = preferences.getString("pref_aria2_speed_upload", "0");
        try {
            if (string.length() > 0)
                setting.plugin.aria2.speedUpload = Integer.parseInt(string);
            else
                setting.plugin.aria2.speedUpload = 0;
        } catch (NumberFormatException e) {
            setting.plugin.aria2.speedUpload = Integer.MAX_VALUE;
            preferencesEditor.putString("pref_aria2_speed_upload",
                    Integer.toString(setting.plugin.aria2.speedUpload));
        }

        setting.plugin.aria2.local = preferences.getBoolean("pref_aria2_local", false);
        setting.plugin.aria2.path = preferences.getString("pref_aria2_path", "aria2c");
        setting.plugin.aria2.arguments = preferences.getString("pref_aria2_args", "--enable-rpc=true -D --check-certificate=false").replaceAll("(\\r|\\n)", "");
        setting.plugin.aria2.launch = preferences.getBoolean("pref_aria2_launch", true);
        setting.plugin.aria2.shutdown = preferences.getBoolean("pref_aria2_shutdown", true);

        // pref_media_match_mode
        string = preferences.getString("pref_media_match_mode", "3");
        try {
            if (string.length() > 0)
                setting.plugin.media.matchMode = Integer.parseInt(string);
            else
                setting.plugin.media.matchMode = 3;
        } catch (NumberFormatException e) {
            setting.plugin.media.matchMode = 3;
            preferencesEditor.putString("pref_media_match_mode",
                    Integer.toString(setting.plugin.media.matchMode));
        }

        // pref_media_quality
        string = preferences.getString("pref_media_quality", "1");
        try {
            if (string.length() > 0)
                setting.plugin.media.quality = Integer.parseInt(string);
            else
                setting.plugin.media.quality = 1;
        } catch (NumberFormatException e) {
            setting.plugin.media.quality = 1;
            preferencesEditor.putString("pref_media_quality",
                    Integer.toString(setting.plugin.media.quality));
        }

        // pref_media_type
        string = preferences.getString("pref_media_type", "0");
        try {
            if (string.length() > 0)
                setting.plugin.media.type = Integer.parseInt(string);
            else
                setting.plugin.media.type = 0;
        } catch (NumberFormatException e) {
            setting.plugin.media.type = 0;
            preferencesEditor.putString("pref_media_type",
                    Integer.toString(setting.plugin.media.type));
        }

        // pref_autosave_interval
        string = preferences.getString("pref_autosave_interval", "1");
        try {
            if (string.length() > 0)
                setting.autosaveInterval = Integer.parseInt(string);
            else
                setting.autosaveInterval = 1;
        } catch (NumberFormatException e) {
            setting.autosaveInterval = Integer.MAX_VALUE;
            preferencesEditor.putString("pref_autosave_interval",
                    Integer.toString(setting.autosaveInterval));
        }

        // write changes to file
        preferencesEditor.apply();
    }

    public void applySetting(boolean aria2Changed, boolean sortChanged) {
//      Log.v("uGet", "App.applySetting() call setSorting(" + setting.sort_by + ")");
        if (sortChanged) {
            core.setSorting(setting.sortBy);
            downloadAdapter.notifyDataSetChanged();
        }
        core.setPluginOrder(setting.pluginOrder);
        core.setMediaMatchMode(setting.plugin.media.matchMode);
        core.setMediaQuality(setting.plugin.media.quality);
        core.setMediaType(setting.plugin.media.type);
        core.setSpeedLimit(setting.speedDownload, setting.speedUpload);
        if (aria2Changed) {
            core.setPluginSetting(setting.plugin);

            if (setting.plugin.aria2.launch && setting.plugin.aria2.local &&
                    setting.plugin.aria2.path != null &&
                    setting.plugin.aria2.arguments != null)
            {
                Runtime runtime = Runtime.getRuntime();
                boolean launched = false;

                try {
                    Process process = runtime.exec("/system/bin/ps aria2c");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            process.getInputStream()));

                    int read;
                    char[] buffer = new char[4096];
                    StringBuffer output = new StringBuffer();
                    while ((read = reader.read(buffer)) > 0) {
                        output.append(buffer, 0, read);
                    }
                    reader.close();
                    process.waitFor();
                    if (output.toString().contains("aria2c"))
                        launched = true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }

                if (launched == false) {
                    new Thread() {
                        @Override
                        public void run() {
                            Runtime runtime = Runtime.getRuntime();
                            try {
                                Process proc = runtime.exec(setting.plugin.aria2.path + " " +
                                        setting.plugin.aria2.arguments);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }.start();
                }
            }

	/*
            try {
                if (launched == false) {
                    Process proc = runtime.exec("su");
                    DataOutputStream dostream = new DataOutputStream(proc.getOutputStream());
                    dostream.writeBytes(setting.aria2.path + " " + setting.aria2.arguments + "\n");
                    dostream.flush();
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
	*/
        }

    }

    // ------------------------------------------------------------------------
    // Clipboard

    public ClipboardManager  clipboard;
    public Uri               clipboardUri;

    protected void initClipboard() {
        clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipboardUri = null;
    }

    protected void clearClipboard() {
        if (clipboard.hasPrimaryClip() == false)
            return;
        if (setting.clipboard.clearAtExit == true) {
//          clipboard.setText("");
            clipboard.setPrimaryClip(ClipData.newPlainText("Styled Text", ""));
//          clipboard.setPrimaryClip(ClipData.newRawUri("URI", Uri.parse("")));
        }
    }

    public Uri getUriFromClipboard(boolean changed) {
        if (clipboard.hasPrimaryClip() == false)
            return null;

        Uri uri = null;
        ClipData clipData = clipboard.getPrimaryClip();
        if (clipData == null)
            return null;
        ClipData.Item item = clipData.getItemAt(0);

        try {
            String tempString = item.getText().toString();
            uri = Uri.parse(tempString);
            if (uri == null)
                return null;
            tempString = uri.getScheme();
            if (tempString.compareTo("file") == 0 || tempString.contains(" "))
                return null;
        }
        catch (Exception e) {
            return null;
        }

        if (changed == false)
            return uri;
        if (clipboardUri == null || clipboardUri.compareTo(uri) != 0)
            clipboardUri = uri;
        else
            return null;

        return clipboardUri;
    }

    // ------------------------------------------------------------------------
    // folder history

    public String folderHistory[];
    public String folderWritable[];

    public void initFolderWritable() {
        folderWritable = new String[2];
        File filesDir = getFilesDir();
        if (filesDir == null)
            filesDir = new File(File.separator);

        folderWritable[0] = filesDir.getAbsolutePath();
        folderWritable[1] = Environment.getExternalStorageDirectory().getAbsolutePath();
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            File[] externalFilesDirs  = getExternalFilesDirs(null);
            if (externalFilesDirs == null)
                return;
            for (int index = 0;  index < externalFilesDirs.length;  index++) {
                if (externalFilesDirs[index] == null)
                    continue;
                String absolutePath = externalFilesDirs[index].getAbsolutePath();
                if (absolutePath.startsWith(folderWritable[1]) == false) {
                    String[] newFolderWritable = new String[3];
                    System.arraycopy(folderWritable, 0, newFolderWritable, 0, folderWritable.length);
                    newFolderWritable[2] = absolutePath;
                    folderWritable = newFolderWritable;
                    break;
                }
            }
        }
    }

    public void addFolderHistory(String folder) {
        if (folder.equals(""))
            return;

        int  index = folderHistory.length -1;
        for (int count = 0;  count < folderHistory.length;  count++) {
            if (folderHistory[count] != null && folderHistory[count].equals(folder)) {
                index = count;
                break;
            }
        }

        for (int count = index;  count > 0;  count--) {
            if (folderHistory[count-1] == null)
                continue;
            folderHistory[count] = folderHistory[count-1];
        }
        folderHistory[0] = folder;
    }

    public void initFolderHistory() {
        if (folderHistory[0] != null)
            return;

        try {
            folderHistory[0] = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
            folderHistory[1] = Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                File[] externalFilesDirs  = getExternalFilesDirs(null);
                if (externalFilesDirs == null)
                    return;
                for (int index = 0;  index < externalFilesDirs.length;  index++) {
                    if (externalFilesDirs[index] == null)
                        continue;
                    String absolutePath = externalFilesDirs[index].getAbsolutePath();
                    if (absolutePath.startsWith(folderHistory[1]) == false) {
                        folderHistory[2] = absolutePath;
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            // e.printStackTrace();
        }
    }

    public void loadFolderHistory() {
        File filesDir = getFilesDir();
        if (filesDir == null)
            return;
        if (folderHistory == null)
            folderHistory = new String[6];

        try {
            /*
            FileInputStream  fis;
            fis = new FileInputStream(filesDir.getAbsolutePath() +
                    File.separator + "folder-history.txt");
            bufReader = new BufferedReader(
                    new InputStreamReader(fis, Charset.forName("UTF-8")));
            */
            BufferedReader  bufReader;
            FileReader  freader;
            freader = new FileReader(filesDir.getAbsolutePath() +
                    File.separator + "folder-history.txt");
            bufReader = new BufferedReader(freader);

            String  line;
            for (int count = 0;  count < folderHistory.length;  count++) {
                line = bufReader.readLine();
                if (line == null)
                    break;
                line.replaceAll("(\\r|\\n)", "");
                folderHistory[count] = line;
            }

            bufReader.close();
        }
        catch (IOException e) {
            initFolderHistory();
        }
    }

    public void saveFolderHistory() {
        File filesDir = getFilesDir();
        if (filesDir == null)
            return;

        try {
            /*
            FileOutputStream  fos;
            fos = new FileOutputStream(filesDir.getAbsolutePath() +
                    File.separator + "folder-history.txt");
            bufWriter = new BufferedWriter(
                    new OutputStreamWriter(fos, Charset.forName("UTF-8")));
            */
            BufferedWriter  bufWriter;
            FileWriter  fwriter;
            fwriter = new FileWriter(filesDir.getAbsolutePath() +
                    File.separator + "folder-history.txt");
            bufWriter = new BufferedWriter(fwriter);

            for (int count = 0;  count < folderHistory.length;  count++) {
                if (folderHistory[count] == null)
                    continue;
                bufWriter.write(folderHistory[count]);
                bufWriter.newLine();
            }
            bufWriter.close();
            bufWriter = null;
            fwriter = null;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // Notification
    NotificationManager  notificationManager = null;
    private final int    notificationId = 0;
    // --- Notification.Builder ---
    Notification.Builder builderNormal = null;
    Notification.Builder builderCompleted = null;
    Notification.Builder builderError = null;
    // --- ID of the NotificationChannel ---
    final String          CHANNEL_NORMAL    = "0.Normal";
    final String          CHANNEL_COMPLETED = "1.Completed";
    final String          CHANNEL_ERROR     = "2.Error";

    public void initNotification() {
        if (notificationManager == null)
            notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channelNormal;
            NotificationChannel channelCompleted;
            NotificationChannel channelError;

            channelNormal = new NotificationChannel(CHANNEL_NORMAL,
                    getString(R.string.notification_channel_normal),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channelCompleted = new NotificationChannel(CHANNEL_COMPLETED,
                    getString(R.string.notification_channel_completed),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channelError = new NotificationChannel(CHANNEL_ERROR,
                    getString(R.string.notification_channel_error),
                    NotificationManager.IMPORTANCE_DEFAULT);

            channelNormal.enableVibration(false);
            channelNormal.setSound(null, null);
            channelCompleted.enableVibration(setting.ui.vibrateNotification);
            if (setting.ui.soundNotification == false)
                channelCompleted.setSound(null, null);
            //channelComplete.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, new AudioAttributes.Builder().build());
            channelError.enableVibration(setting.ui.vibrateNotification);
            if (setting.ui.soundNotification == false)
                channelError.setSound(null, null);

            try {
                notificationManager.createNotificationChannel(channelNormal);
                notificationManager.createNotificationChannel(channelCompleted);
                notificationManager.createNotificationChannel(channelError);
            } catch(Exception e) {}

            builderNormal = new Notification.Builder(getApplicationContext(), CHANNEL_NORMAL);
            builderCompleted = new Notification.Builder(getApplicationContext(), CHANNEL_COMPLETED);
            builderError = new Notification.Builder(getApplicationContext(), CHANNEL_ERROR);
        }
        else {
            builderNormal = new Notification.Builder(getApplicationContext());
            builderCompleted = builderNormal;
            builderError = builderNormal;
        }
    }

    public void cancelNotification() {
        notificationManager.cancel(notificationId);
        //notificationManager.cancelAll();
    }

    public void notifyMessage(Notification.Builder builder, String title, String content) {
        Intent notifyIntent = new Intent(MainApp.this, MainActivity.class);
        Context  context;

        // Intent.FLAG_ACTIVITY_SINGLE_TOP
        // Intent.FLAG_ACTIVITY_NEW_TASK
        notifyIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        context = getApplicationContext();
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, notifyIntent, 0);

        builder.setTicker(content)
               .setContentIntent(pendingIntent)
               .setContentTitle(title)
               .setContentText(content)
               .setAutoCancel(true)
               .setSmallIcon(R.mipmap.ic_notification);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            builder.setShowWhen(true);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            builder.setSmallIcon(R.mipmap.ic_launcher);
        else {
            builder.setSmallIcon(R.mipmap.ic_notification)
                   .setColor(getResources().getColor(R.color.colorPrimary));
        }

        Notification notification;
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN)
            notification = builder.getNotification();
        else
            notification = builder.build();

        notificationManager.notify(notificationId, notification);
    }

    public void notifyActiveSpeed(int nActive, boolean firstTime) {
        if (setting.ui.startNotification == false)
            return;

        String title = Util.stringFromIntUnit(core.downloadSpeed, 1) + " ↓ ";
        if (core.uploadSpeed > 0)
            title +=   Util.stringFromIntUnit(core.uploadSpeed, 1)   + " ↑";
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            title = getString(R.string.app_name) + " · " + title;
        String subText = getString(R.string.notification_active_counts, nActive);

        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            builderNormal.setDefaults(0);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builderNormal.setSubText(subText);
        if (firstTime)
            builderNormal.setWhen(System.currentTimeMillis());

        builderNormal.setOngoing(true);
        builderNormal.setOnlyAlertOnce(true);
        notifyMessage(builderNormal, title, subText);
    }

    public void notifyCompleted() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builderCompleted.setSubText(null);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int  flags = 0;
            if (setting.ui.soundNotification)
                flags |= Notification.DEFAULT_SOUND;
            if (setting.ui.vibrateNotification)
                flags |= Notification.DEFAULT_VIBRATE;
            builderCompleted.setDefaults(flags);
        }

        builderCompleted.setWhen(System.currentTimeMillis());
        builderCompleted.setOngoing(false);
        builderCompleted.setOnlyAlertOnce(false);

        String title = getString(R.string.notification_completed_title);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            title = getString(R.string.app_name) + " · " + title;

        notifyMessage(builderCompleted, title,
                getString(R.string.notification_completed_content));
    }

    public void notifyError() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
            builderError.setSubText(null);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            int  flags = 0;
            if (setting.ui.soundNotification)
                flags |= Notification.DEFAULT_SOUND;
            if (setting.ui.vibrateNotification)
                flags |= Notification.DEFAULT_VIBRATE;
            builderError.setDefaults(flags);
        }

        builderError.setWhen(System.currentTimeMillis());
        builderError.setOngoing(false);
        builderError.setOnlyAlertOnce(false);

		String title = getString(R.string.notification_error_title);
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
            title = getString(R.string.app_name) + " · " + title;
		
        notifyMessage(builderError, title,
                getString(R.string.notification_error_content));
    }

    // ------------------------------------------------------------------------
    // Log

    public File logGetFile()
    {
        String  path;
        File  filesDir = getFilesDir();
        if (filesDir == null)
            return null;

        if (Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED))
        {
            path = getExternalFilesDir(null).getAbsolutePath() +
                    File.separator + "log.txt";
//            path = Environment.getExternalStorageDirectory().getAbsolutePath() +
//                    File.separator + "uGet-log.txt";
        }
        else
            path = filesDir.getAbsolutePath() + File.separator + "log.txt";

        return new File(path);
    }

    public void logClear()
    {
        File logFile = logGetFile();
        if (logFile == null)
            return;
        logFile.delete();
    }

    public void logAppend(String text)
    {
        File logFile = logGetFile();
        if (logFile == null)
            return;

        if (!logFile.exists())
        {
            try {
                logFile.createNewFile();
            }
            catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(text);
            buf.newLine();
            buf.close();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}

