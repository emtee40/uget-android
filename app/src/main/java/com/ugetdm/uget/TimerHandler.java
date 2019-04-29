/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import android.app.ActivityManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.ugetdm.uget.lib.Core;
import com.ugetdm.uget.lib.Info;
import com.ugetdm.uget.lib.Node;
import com.ugetdm.uget.lib.Rpc;

import java.util.regex.PatternSyntaxException;

public class TimerHandler {
    private MainApp  app;
    private Handler  handler;

    // timer interval
    private static final int connectivityInterval = 1000;
    private static final int queuingInterval   = 1000;
    private static final int clipboardInterval = 1000 * 2;
    private static final int autosaveInterval  = 1000 * 3;
    private static final int rpcInterval       = 1000;

    // handler status
    private boolean  queuingRunning   = false;
    private boolean  clipboardRunning = false;
    private boolean  autosaveRunning  = false;
    protected long   autosaveLastTime;
    private int      autosaveQueuingCounts;

    // used by connectivityRunnable
    private ConnectivityManager connectivityManager;
    private boolean  offlineModeLast = false;

    // used by queuingRunning
    private int      nActiveLast    = 0;
    private int      queuingCounts  = 0;

    // ----------------------------------------------------

    public TimerHandler(MainApp mainApp) {
        app = mainApp;

        // -- run on main thread ---
        // Get a handler that can be used to post to the main thread
        handler = new Handler(Looper.getMainLooper());    // context.getMainLooper()
    }

    public void start() {
        if (connectivityManager == null)
            connectivityManager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
        handler.postDelayed(connectivityRunnable, connectivityInterval);

        startQueuing();
        startClipboard();
        startAutosave();
        autosaveLastTime = System.currentTimeMillis();
        // handler.postDelayed(rpcRunnable, rpcInterval);
    }

    public void stop() {
        handler.removeCallbacks(connectivityRunnable);
        stopQueuing();
        stopClipboard();
        stopAutosave();
        // handler.removeCallbacks(rpcRunnable);
    }

    public void startQueuing() {
        if (queuingRunning == false) {
            queuingRunning = true;
            handler.postDelayed(queuingRunnable, queuingInterval);
        }
    }

    public void stopQueuing() {
        if (queuingRunning) {
            queuingRunning = false;
            handler.removeCallbacks(queuingRunnable);
        }
    }

    public void startClipboard() {
        if (clipboardRunning == false) {
            clipboardRunning = true;
            handler.postDelayed(clipboardRunnable, clipboardInterval);
        }
    }

    public void stopClipboard() {
        if (clipboardRunning) {
            clipboardRunning = false;
            handler.removeCallbacks(clipboardRunnable);
        }
    }

    public void startAutosave() {
        if (autosaveRunning == false && app.setting.autosaveInterval != 0) {
            autosaveRunning = true;
            handler.postDelayed(autosaveRunnable, autosaveInterval);
        }
    }

    public void stopAutosave() {
        if (autosaveRunning) {
            autosaveRunning = false;
            handler.removeCallbacks(autosaveRunnable);
        }
    }

    // ----------------------------------------------------
    // Runnable

    private Runnable connectivityRunnable = new Runnable() {
        @Override
        public void run() {
            // Go offline if no WiFi connection
            if (app.setting.ui.noWifiGoOffline) {
                if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected())
                    app.setting.offlineMode = false;
                else
                    app.setting.offlineMode = true;
            }
            // enable/disable offline mode
            if (offlineModeLast != app.setting.offlineMode) {
                offlineModeLast = app.setting.offlineMode;
                if (app.setting.offlineMode) {
                    // --- go offline
                    app.core.stopCategories();
                    app.userAction = true;
                }
                if (app.mainActivity != null)
                    app.mainActivity.decideTitle();
                // --- start timer handler ---
                startQueuing();
            }

            // call this function after the specified time interval
            handler.postDelayed(this, connectivityInterval);
        }
    };

    private Runnable queuingRunnable = new Runnable() {
        @Override
        public void run() {
            int  nActive;
            long checkedNodes[] = null;
            long deletedNodes[];
            boolean queuingContinuing = true;

            queuingCounts++;
            // stop queuing while loading or saving data
            if (Job.queuedTotal > 0)
                handler.postDelayed(this, queuingInterval);

            // --- reserve selected node --- restore them after app.core.trim()
            if (app.downloadAdapter.getCheckedItemCount() > 0)
                checkedNodes = app.downloadAdapter.getCheckedNodes();

            nActive = app.core.grow(app.setting.offlineMode);

            if (nActive == 0) {
                // --- stop queuing runnable ---
                queuingContinuing = false;
                queuingRunning = false;
            }
            else {
                // --- show speed in notification ---
                if ((queuingCounts & 1) == 1 || nActiveLast == 0)
                    app.notifyActiveSpeed(nActive, nActiveLast == 0);
                // --- adjust speed ---
                if ((queuingCounts & 1) == 1)
                    app.core.adjustSpeed();
            }

            // --- start or stop ---
            if (nActive != nActiveLast) {
                if (nActive > 0 && nActiveLast == 0) {
                    // --- start ---
                    app.acquireWakeLock(true);
                    app.startMainService();
                }
                else if (nActive == 0 && nActiveLast > 0) {
                    // --- stop ---
                    if (app.userAction)
                        app.cancelNotification();
                    else if (app.core.nError > 0)
                        app.notifyError();
                    else
                        app.notifyCompleted();
                    // --- reduce power consumption ---
                    app.releaseWakeLock();
                    app.stopMainService();
                }
                // --- reset  "app.core.nError"
                app.core.nError = 0;
            }

            // --- trim ---
            deletedNodes = app.core.trim();

            // --- restore selections & notify changed
            if (app.core.nMoved > 0 || app.core.nDeleted > 0 || app.setting.offlineMode) {
                // --- remove deleted nodes from checked nodes
                if (deletedNodes != null && checkedNodes != null) {
                    for (int deletedIndex = 0;  deletedIndex < deletedNodes.length;  deletedIndex++) {
                        for (int checkedIndex = 0;  checkedIndex < checkedNodes.length;  checkedIndex++) {
                            if (checkedNodes[checkedIndex] == deletedNodes[deletedIndex])
                                checkedNodes[checkedIndex] = 0;
                        }
                    }
                }
                // --- restore selected node ---
                app.downloadAdapter.setCheckedNodes(checkedNodes);
                // --- notify data changed ---
                app.categoryAdapter.notifyDataSetChanged();
                app.stateAdapter.notifyDataSetChanged();
                // --- main activity
                if (app.mainActivity != null) {
                    // --- selection mode ---
                    if (app.downloadAdapter.singleSelection == false) {
                        app.mainActivity.decideMenuVisible();
                        app.mainActivity.decideToolbarStatus();
                    }
                    // --- show message if no download ---
                    app.mainActivity.decideContent();
                }
            } else {
                long nodeArray[] = app.getActiveDownloadNode();
                if (nodeArray != null) {
                    for (int i = 0;  i < nodeArray.length;  i++) {
                        int position = app.getDownloadPositionByNode(nodeArray[i]);
                        if (position >= 0)
                            app.downloadAdapter.notifyItemChanged(position);
                    }
                }
            }

            app.userAction = false;
            nActiveLast = nActive;

            // call postDelayed() after the specified time interval
            if (queuingContinuing)
                handler.postDelayed(this, queuingInterval);
        }
    };

    private Runnable clipboardRunnable = new Runnable() {
        @Override
        public void run() {
            int  index;
            long cNodePointer;

            // a fake loop for toExit
            toExit:
            for (;;) {
                // stop clipboard monitor while loading or saving data
                if (Job.queuedTotal > 0)
                    break toExit;

                if (app.setting.clipboard.enable == false)
                    break toExit;

                Uri uri = app.getUriFromClipboard(true);
                if (uri == null)
                    break toExit;
                if (app.setting.ui.skipExistingUri && app.core.isUriExist(uri.toString()) == true)
                    break toExit;

                String text = uri.getPath();
                if (text == null || text == "")
                    break toExit;

                // find last '.' in text
                index = text.lastIndexOf('.');
                if (index != -1) {
                    // match
                    text = text.substring(index + 1);
                    try {
                        if (text.matches(app.setting.clipboard.types) == false)
                            index = -1;
                    }
                    catch(PatternSyntaxException e) {
                        // show error message in MainActivity
                        index = -1;
                    }
                    text = null;
                }

                /*
                // find file extension & match it
                for (index = text.length() -1;  index >= 0;  index--) {
                    if (text.charAt(index) != '.')
                        continue;
                    // match
                    text = text.substring(index + 1);
                    try {
                        if (text.matches(app.setting.clipboard.types) == false)
                            index = -1;
                    }
                    catch(PatternSyntaxException e) {
                        // show error message in MainActivity
                        index = -1;
                    }
                    text = null;
                    break;
                }
                */

                // type not found
                if (index < 0) {
                    if (app.setting.clipboard.website == false ||
                            app.core.getSiteId(uri.toString()) == Core.SiteId.unknown)
                    {
                        break toExit;
                    }
                }

                text = uri.toString();
                cNodePointer = app.core.matchCategory(text, null);
                if (cNodePointer == 0)
                    cNodePointer = Node.getNthChild(app.core.nodeReal, app.setting.clipboard.nthCategory);
                if (cNodePointer == 0)
                    cNodePointer = Node.getNthChild(app.core.nodeReal, 0);
                if (cNodePointer == 0)
                    break toExit;

                long checkedNodes[] = app.downloadAdapter.getCheckedNodes();
                app.core.addDownloadByUri(text, cNodePointer, true);
                app.downloadAdapter.setCheckedNodes(checkedNodes);
                if (app.setting.clipboard.clearAfterAccepting)
                    app.clearClipboard();
                // --- notify ---
                app.stateAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                app.downloadAdapter.notifyDataSetChanged();
                // --- start queuing ---
                startQueuing();

                break toExit;
            }

            // toExit:
            // call this function after the specified time interval
            handler.postDelayed(this, clipboardInterval);
        }
    };

    private Runnable autosaveRunnable = new Runnable() {
        @Override
        public void run() {
            // if (app.setting.autosaveInterval == 0)
            //     return;

            long curTime = System.currentTimeMillis();
            if (curTime - autosaveLastTime > app.setting.autosaveInterval * 1000 * 60) {
                if (autosaveQueuingCounts == queuingCounts)
                    autosaveLastTime = curTime;
                else {
                    autosaveQueuingCounts = queuingCounts;
                    app.logAppend("TimerHandler.autosaveRunnable");
                    if (Job.queued[Job.SAVE_ALL] == 0)
                        Job.saveAll();
                }
            }
            // --- call this function after the specified time interval
            handler.postDelayed(this, autosaveInterval);
        }
    };

    private Runnable rpcRunnable = new Runnable() {
        @Override
        public void run() {
            Rpc.Request  request;
            Rpc.Command  command;
            int  index;
            long cNodePointer = 0;

            // a fake loop for toExit
            toExit:
            while (app.rpc != null) {
                // stop RPC while loading or saving data
                if (Job.queuedTotal > 0)
                    break toExit;

                request = app.rpc.getRequest();
                if (request == null)
                    break toExit;

                switch (request.methodId) {
                    case Rpc.Method.sendCommand:
                        command = app.rpc.getCommand (request);
                        if (command == null)
                            break toExit;
                        break;

                    case Rpc.Method.present:
                        if (app.mainActivity != null) {
                            // bring activity to foreground (top of stack)
                            ActivityManager am = (ActivityManager) app.getSystemService(Context.ACTIVITY_SERVICE);
                            am.moveTaskToFront(app.mainActivity.getTaskId(), 0);
                        }
                        /*
                        else {
                            // start activity if it doesn't exist
                            Intent intent = new Intent();
                            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            intent.setClass(mainActivity, MainActivity.class);
                            startActivity(intent);
                        }
                        */
                        break toExit;

                    default:
                        break toExit;
                }
                // end of switch (request.methodId)

                // command
                long dNodePointer;
                for (index = 0;  index < command.uris.length;  index++) {
                    if (command.uris[index] == null)
                        continue;
                    if (app.setting.ui.skipExistingUri && app.core.isUriExist(command.uris[index]) == true)
                        continue;
                    if (command.categoryIndex != -1)
                        cNodePointer = Node.getNthChild(app.core.nodeReal, command.categoryIndex);
                    if (cNodePointer == 0)
                        cNodePointer = app.core.matchCategory(command.uris[index], command.prop.file);
//                  if (cNodePointer == 0)
//                      cNodePointer = Node.getNthChild(nodeReal, setting.clipboard.nthCategory);
                    if (cNodePointer == 0)
                        cNodePointer = Node.getNthChild(app.core.nodeReal, 0);
                    if (cNodePointer == 0)
                        continue;
                    // create node and add it
                    command.prop.uri = command.uris[index];
                    dNodePointer = Node.create();
                    Info.set(Node.info(dNodePointer), command.prop);
                    long checkedNodes[] = app.downloadAdapter.getCheckedNodes();
                    app.core.addDownload(dNodePointer, cNodePointer, false);
                    app.downloadAdapter.setCheckedNodes(checkedNodes);
                }
                // notify
                app.stateAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                app.downloadAdapter.notifyDataSetChanged();

                break toExit;
            }

            // toExit:
            // call this function after the specified time interval
            handler.postDelayed(this, rpcInterval);
        }
    };
}
