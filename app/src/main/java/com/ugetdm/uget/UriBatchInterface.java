/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget;

public interface UriBatchInterface {
    // return 0 if failed
    public long   batchStart();

    public String batchGet1(long resultOfBatchStart);

    public void   batchEnd(long resultOfBatchStart);

    public int    batchCount();
}
