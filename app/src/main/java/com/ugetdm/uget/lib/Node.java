/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class Node {
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

    // JNI wrap functions
    public native static long    create();
    public native static void    free(long nodePointer);

    public native static long    base(long nodePointer);
    public native static long    next(long nodePointer);
    public native static long    prev(long nodePointer);
    public native static long    parent(long nodePointer);
    public native static long    children(long nodePointer);
    public native static int     nChildren(long nodePointer);
    public native static long    info(long nodePointer);

    public native static int     getPosition(long nodePointer, long childPointer);

    public native static long    getNthChild(long nodePointer, int nth);

    public native static long    getFakeByGroup(long nodePointer, int group);
    public native static long    getFakeByParent(long nodePointer, long nodeParent);
}

