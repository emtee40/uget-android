/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

import com.ugetdm.uget.lib.PluginSetting;

public class Setting {
    public class UserInterface {
        public boolean confirmDelete;
        public boolean confirmExit;
        public boolean exitOnBack;
        public boolean startNotification;
        public boolean soundNotification;
        public boolean vibrateNotification;
        public boolean noWifiGoOffline;
        public boolean startInOfflineMode;
        public boolean skipExistingUri;
    }

    public class Clipboard {
        public boolean enable;
        public String  types;
        public int     nthCategory;
        public boolean clearAtExit;
        public boolean clearAfterAccepting;
        public boolean website;
    }

    public UserInterface  ui;
    public Clipboard      clipboard;
    public PluginSetting  plugin;
    public int            autosaveInterval;

    public int         speedUpload;
    public int         speedDownload;

    public int         sortBy;
    public int         pluginOrder;
    public boolean     offlineMode;

    Setting() {
        ui         = new UserInterface();
        clipboard  = new Clipboard();
        plugin     = new PluginSetting();
    }
}

