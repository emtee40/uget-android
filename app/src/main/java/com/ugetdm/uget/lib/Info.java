/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class Info {
    public static final class Group {
        public static final int queuing   = 1 << 0;
        public static final int pause     = 1 << 1;
        public static final int active    = 1 << 2;
        public static final int completed = 1 << 3;
        public static final int upload    = 1 << 4;
        public static final int error     = 1 << 5;
        public static final int finished  = 1 << 6;
        public static final int recycled  = 1 << 7;
    }

    public static final class ProxyType {
        public static final int none    = 0;  // Don't use
        public static final int auto    = 1;
        public static final int http    = 2;
        public static final int socks4  = 3;
        public static final int socks5  = 4;
    }

    // JNI wrap functions
    public native static long    create();
    public native static void    ref(long infoPointer);
    public native static void    unref(long infoPointer);

    public native static boolean get(long infoPointer, Progress progressData);
    public native static boolean get(long infoPointer, Download downloadData);
    public native static boolean get(long infoPointer, Category categoryData);
    public native static void    set(long infoPointer, Download downloadData);
    public native static void    set(long infoPointer, Category categoryData);

    public native static int     getGroup(long infoPointer);
    public native static void    setGroup(long infoPointer, int state);
    public native static String  getName(long infoPointer);
    public native static void    setName(long infoPointer, String name);
    public native static void    setNameByUri(long infoPointer, String uri);

    public native static int     getPriority(long infoPointer);
    public native static void    setPriority(long infoPointer, int priority);

    public native static String  getMessage(long infoPointer);
}

