/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class Sequence {
    public  long  pointer;    // UgetSequence*

    public  Sequence() {
        cInit();
    }
    public  void finalize() {
        cFinal();
    }

    private native void        cInit();
    private native void        cFinal();

    public  native void        add(int beg, int end, int digits);
    public  native void        clear();
    public  native int         count(String pattern);
    public  native String[]    getPreview(String pattern);

    public  native long        batchStart(String pattern);
    public  native String      batchGetUri(long batchResult);
    public  native void        batchEnd(long batchResult);
}
