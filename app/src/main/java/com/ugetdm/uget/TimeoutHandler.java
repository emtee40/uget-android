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

public class TimeoutHandler {
    // Timeout Interval & Handler

    private MainApp  app;
    private Handler  handler;

    private boolean  handlerStarted = false;
    private boolean  offlineModeLast = false;
    private int      nActiveLast    = 0;
    private int      queuingCounts  = 0;
    private int      autoSaveCounts = 0;
    private static final int queuingInterval   = 1000 * 1;
    private static final int clipboardInterval = 1000 * 2;
    private static final int autoSaveInterval  = 1000 * 60;
    private static final int rpcInterval       = 500;

    public TimeoutHandler(MainApp mainApp) {
        app = mainApp;

        // Get a handler that can be used to post to the main thread
        // handler = new Handler();    // call this in main thread
        handler = new Handler(Looper.getMainLooper());    // context.getMainLooper()
//        handler = new Handler();
    }

    public void startHandler() {
        if (handlerStarted == true)
            return;

        handlerStarted = true;
        handler.postDelayed(queuingRunnable, queuingInterval);
        handler.postDelayed(clipboardRunnable, clipboardInterval);
        handler.postDelayed(autoSaveRunnable, autoSaveInterval);
        handler.postDelayed(rpcRunnable, rpcInterval);
    }

    public void stopHandler() {
        if (handlerStarted == false)
            return;

        handlerStarted = false;
        handler.removeCallbacks(queuingRunnable);
        handler.removeCallbacks(clipboardRunnable);
        handler.removeCallbacks(autoSaveRunnable);
        handler.removeCallbacks(rpcRunnable);
    }

    private Runnable queuingRunnable = new Runnable() {
        @Override
        public void run() {
            queuingCounts++;
            int  nActive;
            long checkedNodes[] = null;
            boolean intoOfflineMode = false;

            // Go offline if no WiFi connection
            if (app.setting.ui.noWifiGoOffline) {
                ConnectivityManager connectivityManager;
                connectivityManager = (ConnectivityManager) app.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected())
                    app.setting.offlineMode = false;
                else
                    app.setting.offlineMode = true;
            }
            // enable/disable offline mode
            if (offlineModeLast != app.setting.offlineMode) {
                offlineModeLast = app.setting.offlineMode;
                // if go offline
                if (app.setting.offlineMode) {
                    app.core.stopCategories();
                    app.userAction = true;
                    intoOfflineMode = true;
                }
            }

            // --- reserve selected node ---
            if (app.downloadAdapter.getCheckedItemCount() > 0)
                checkedNodes = app.downloadAdapter.getCheckedNodes();

            nActive = app.core.grow(app.setting.offlineMode);
            if (nActiveLast != nActive) {
//                  Log.v ("uGet", "Core.grow() nActive = " + nActive);
                app.stateAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                if (nActive > 0 && nActiveLast == 0) {
                    app.acquireWakeLock();
                    app.notifyStarting();
                }
                else if (nActive == 0 && nActiveLast > 0) {
                    if (app.userAction)
                        app.cancelNotification();
                    else if (app.core.nError > 0)
                        app.notifyError();
                    else
                        app.notifyCompleted();

                    // save all data before program release WakeLock
                    app.saveAllData();
                    app.releaseWakeLock();
                }
            }

            // speed
            if (nActive > 0 && (queuingCounts & 1) == 1)
                app.core.adjustSpeed();
            // trim
            long[] deletedNodes = app.core.trim();
            // remove deleted node from checked node
            if (deletedNodes != null && checkedNodes != null) {
                for (int deletedIndex = 0;  deletedIndex < deletedNodes.length;  deletedIndex++) {
                    for (int checkedIndex = 0;  checkedIndex < checkedNodes.length;  checkedIndex++) {
                        if (checkedNodes[checkedIndex] == deletedNodes[deletedIndex])
                            checkedNodes[checkedIndex] = 0;
                    }
                }
            }

            if (app.core.nMoved > 0 || app.core.nDeleted > 0 || intoOfflineMode) {
                // --- restore selected node ---
                app.downloadAdapter.setCheckedNodes(checkedNodes);
                // --- main activity
                if (app.mainActivity != null) {
                    // --- selection mode ---
                    if (app.downloadAdapter.singleSelection == false) {
                        app.mainActivity.decideMenuVisible();
                        app.mainActivity.updateToolbar();
                    }
                    // --- show message if no download ---
                    app.mainActivity.decideContent();
                }
                // --- notify data changed ---
                app.downloadAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                app.stateAdapter.notifyDataSetChanged();
            } else {
                long nodeArray[] = app.getActiveDownloadNode();
                if (nodeArray != null) {
                    for (int i = 0;  i < nodeArray.length;  i++) {
                        int  position = app.getDownloadNodePosition(nodeArray[i]);
                        if (position >= 0)
                            app.downloadAdapter.notifyItemChanged(position);
                    }
                }
            }

            app.userAction = false;
            nActiveLast = nActive;

            // call this function after the specified time interval
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

                app.core.addDownloadByUri(text, cNodePointer, true);
                if (app.setting.clipboard.clearAfterAccepting)
                    app.clearClipboard();
                // notify
                app.stateAdapter.notifyDataSetChanged();
                app.categoryAdapter.notifyDataSetChanged();
                app.downloadAdapter.notifyDataSetChanged();

                break toExit;
            }

            // toExit:
            // call this function after the specified time interval
            handler.postDelayed(this, clipboardInterval);
        }
    };

    private Runnable autoSaveRunnable = new Runnable() {
        @Override
        public void run() {
            autoSaveCounts++;
            if (autoSaveCounts >= app.setting.autosaveInterval) {
                autoSaveCounts = 0;
                app.saveAllData();
            }

            // call this function after the specified time interval
            handler.postDelayed(this, autoSaveInterval);
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
            for (;;) {
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
                    app.core.addDownload(dNodePointer, cNodePointer, false);
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
