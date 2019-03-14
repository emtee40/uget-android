/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class Node {
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

    // parameter positionsToNodes[] must be sorted
    public native static int     getChildrenByPositions(long nodePointer, long positionsToNodes[]);
    public native static int     getPositionsByChildren(long nodePointer, long nodesToPositions[]);

    public native static long    getFakeByGroup(long nodePointer, int group);
    public native static long    getFakeByParent(long nodePointer, long nodeParent);
}

