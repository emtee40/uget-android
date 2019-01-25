/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

public class Rpc {
    public static final class Method {
        public static final int doNothing   = 0;
        public static final int sendCommand = 1;
        public static final int present     = 2;
    }

    public class Request {
        public  long pointer;    // UgetRpcReq*
        public  int  methodId;

        public Request() { methodId = Method.doNothing; }
        public void finalize() {
            this.cFinal();
        }

        private native void  cFinal();
    }

    public class Command {
        public String        uris[];
        public Download      data;
        public boolean       quiet;
        public int           categoryIndex;    // default is -1

        // control
        public int           offline;          // default is -1

        Command () {
            uris = null;
            data = new Download();
        }
    }

    public  long  pointer;    // UgetRpc*

    public  Rpc(String backupDir) {
        cInit(backupDir);
    }
    public  void finalize() {
        cFinal();
    }

    private native void        cInit(String backupDir);
    private native void        cFinal();

    public  native Rpc.Request getRequest();
    public  native Rpc.Command getCommand(Rpc.Request req);
    public  native boolean     startServer();
    public  native void        stopServer();

}

