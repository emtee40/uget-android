/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

// ========================================================================
// UgetApp JNI

public class Core {
    // cNode is UgetNode* categoryNode
    // dNode is UgetNode* downloadNode
    // fNode is UgetNode* fileNode

    private long   pointer;       // UgetApp*

    public  long   nodeReal;      // UgetApp.real
    public  long   nodeSorted;    // UgetApp.sorted
    public  long   nodeSplit;     // UgetApp.split
    public  long   nodeMix;       // UgetApp.mix
    public  long   nodeMixSplit;  // UgetApp.mix_split

    // increase and reset by grow()
    public  int    nMoved = 0;
    public  int    nError = 0;
    public  int    nDeleted = 0;
    public  int    nCompleted = 0;

    // constructor
    public Core() {
        cInit();
    }

    // JNI wrap functions

    public  native void    cInit();
    public  native void    cFinal(boolean shutdown_aria2);

    public  native int     grow(boolean no_queuing);
    public  native long[]  trim();

    public  native void    setConfigDir(String dir);

    public  native void    addCategory(long cNodePointer);
    public  native void    deleteCategory(long cNodePointer);
    public  native boolean moveCategory(long cNodePointer, long cNodePosition);
    public  native long    matchCategory(String uri, String file);    // return cNode
    public  native void    stopCategories();
    public  native void    pauseCategories();    // pause all
    public  native void    resumeCategories();   // resuem all

    public  native void    addDownloadSequence(Sequence sequence, String pattern, long cNodePointer, int startupMode);  // 1 == pause
    public  native void    addDownloadByUri(String uri, long cNodePointer, boolean apply);
    public  native void    addDownload(long dNodePointer, long cNodePointer, boolean apply);
    public  native boolean moveDownload(long dNodePointer, long dNodePosition);
    public  native boolean deleteDownload(long dNodePointer, boolean deleteFiles);
    public  native boolean recycleDownload(long dNodePointer);
    public  native boolean activateDownload(long dNodePointer);
    public  native boolean pauseDownload(long dNodePointer);
    public  native boolean queueDownload(long dNodePointer);
    public  native void    resetDownloadName(long dNodePointer);

    public  native boolean saveCategory(long cNodePointer, String filename);
    public  native long    loadCategory(String filename);
    public  native boolean saveCategory(long cNodePointer, int fileDescriptor);
    public  native long    loadCategory(int fileDescriptor);
    public  native int      saveCategories(String folder);
    public  native int      loadCategories(String folder);

    public  native void    clearAttachment();
    public  native boolean isUriExist(String uri);
    // --------------------------------
    // UgetTask

    // these value assigned by grow()
    public  int    downloadSpeed = 0;
    public  int    uploadSpeed   = 0;

    public  native void    setSpeedLimit(int downloadSpeedKiB, int uploadSpeedKiB);
    public  native void    adjustSpeed();
    public  native void    removeAllTask();

    // --------------------------------
    // other functions and definitions for JNI

    public static final class PluginOrder {
        public static final int curl       = 0;
        public static final int aria2      = 1;
        public static final int curl_aria2 = 2;
        public static final int aria2_curl = 3;
    }

    public static final class Priority {
        public static final int low    = 0;
        public static final int normal = 1;
        public static final int high   = 2;
    }

    public static final class Sorting {
        public static final int unsort    = 0;
        public static final int name      = 1;
        public static final int addedTime = 2;
    }

    public static final class SiteId {
        public static final int unknown = 0;
        public static final int mega = 1;

        public static final int media = 0x10000000;
        public static final int youtube = media;
    }

    // media plug-in
    public static final class Media {
        public static final int matchUnknown  = -1;
        public static final int match0    = 0;
        public static final int match1    = 1;
        public static final int match2    = 2;
        public static final int matchNear = 3;

        public static final int qualityUnknown  = -1;
        public static final int quality240p   = 0;
        public static final int quality360p   = 1;
        public static final int quality480p   = 2;
        public static final int quality720p   = 3;
        public static final int quality1080p  = 4;

        public static final int typeUnknown   = -1;
        public static final int typeMp4    = 0;
        public static final int typeWebm   = 1;
        public static final int type3gpp   = 2;
        public static final int typeFlv    = 3;
    }

    public native void    setSorting(int sortBy);
    public native void    setPluginOrder(int order);
    public native void    setPluginSetting(PluginSetting setting);

    public native int     getSiteId(String url);

    // media plug-in
    public native void    setMediaMatchMode(int mode);
    public native void    setMediaQuality(int quality);
    public native void    setMediaType(int type);

    // --------------------------------
    // load JNI library

    static {
        System.loadLibrary("uget-jni");
    }
}

