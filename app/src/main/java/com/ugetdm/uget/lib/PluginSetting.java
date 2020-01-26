/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class PluginSetting {
    public class Aria2 {
        public String  uri;
        public String  token;
        public String  path;
        public String  arguments;
        public boolean local;
        public boolean launch;
        public boolean shutdown;
        public int     speedUpload;
        public int     speedDownload;
    }

    public class Media {
        public int  matchMode;
        public int  quality;
        public int  type;
    }

    public Aria2    aria2;
    public Media    media;

    public PluginSetting() {
        aria2      = new Aria2();
        media      = new Media();
    }
}
