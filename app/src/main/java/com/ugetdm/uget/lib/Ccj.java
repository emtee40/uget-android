/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

package com.ugetdm.uget.lib;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.provider.DocumentsContract;
import android.support.v4.provider.DocumentFile;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

// C Call Java
public class Ccj {
    public  static Context  context;
    public  static ContentResolver  contentResolver;
    public  static String  externalFilesDir;    // external app files dir

    public native static void cInit();
    public native static void cFinal();

    // call this function in main thread
    public static void init(Context ct) {
        context = ct;
        contentResolver = context.getContentResolver();

        initStorageMethod();
        cInit();
        initUriCache();

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // first entry is emulated storage. Second if it exists is secondary (real) SD.
            File[] externalFilesDirs = context.getExternalFilesDirs(null);
            if (externalFilesDirs == null)
                return;

            // externalFilesDirs[0] = Internal Storage
            // externalFilesDirs[1] = External Storage
            if (externalFilesDirs.length > 1 && externalFilesDirs[1] != null) {
                String absolutePath = externalFilesDirs[1].getAbsolutePath();
                externalFilesDir = absolutePath;
            }
        }
    }

    static StorageManager mStorageManager;
    static Method getVolumeList;
    static Method getUuid;
    static Method getPath;
    static Method isPrimary;
    public static void initStorageMethod() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return;

        try {
            mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            Class<?> storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");

            getVolumeList = mStorageManager.getClass().getMethod("getVolumeList");
            getUuid = storageVolumeClazz.getMethod("getUuid");
            getPath = storageVolumeClazz.getMethod("getPath");
            isPrimary = storageVolumeClazz.getMethod("isPrimary");
        }
        catch (Exception e) {
        }
    }

    public static int getStoragePathLength (String path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return 0;

        try {
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                if ((Boolean) isPrimary.invoke(storageVolumeElement))
                    continue;
                String storagePath = (String) getPath.invoke(storageVolumeElement);
                if (path.startsWith(storagePath))
                    return storagePath.length();
            }
        }
        catch (Exception ex) { }

        return 0;
    }

    public static String pathToTreeUri (String path) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
            return null;

        try {
            Object result = getVolumeList.invoke(mStorageManager);

            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                if ((Boolean) isPrimary.invoke(storageVolumeElement))
                    continue;
                String storagePath = (String) getPath.invoke(storageVolumeElement);
                if (!path.startsWith(storagePath))
                    continue;
                if (externalFilesDir != null && path.startsWith(externalFilesDir))
                    continue;

                if (path.length() > storagePath.length() && path.charAt(storagePath.length()) == '/')
                    path = path.substring(storagePath.length() + 1);
                else
                    path = path.substring(storagePath.length());

                path = (String) getUuid.invoke(storageVolumeElement) + ':' + path;
                return "content://com.android.externalstorage.documents/tree/" + URLEncoder.encode(path, "UTF-8");
            }
        }
        catch (Exception ex) { }

        return null;
    }

    // external file URI sample: file in external Download folder
    // content://com.android.externalstorage.documents/document/1DE6-1A0A%3ADownload%2Ftest5.json
    // external folder URI sample: external Download folder
    // content://com.android.externalstorage.documents/tree/07FB-2B14%3ADownload
    public static String[] splitTreeAndName (String treeUri) {
        String[] result = new String[2];

        int  index = treeUri.lastIndexOf("%2F");    // find '/'

        if (index == -1) {
            index = treeUri.indexOf("%3A");    // find ':'
            result[0] = treeUri.substring(0, index+3);
            result[1] = treeUri.substring(index+3);
            return result;
        }

        if (index == -1) {
            result[0] = treeUri;
            result[1] = "";
        }
        else {
            result[0] = treeUri.substring(0, index);
            result[1] = treeUri.substring(index+3);
            try {
                result[1] = URLDecoder.decode(result[1], "UTF-8");
            }
            catch (Exception e) {}
        }
        return result;
    }

    // ------------------------------------------------------------------------
    // Uri & DocumentFile cache

    public static List<String> treeUriList = new ArrayList<>();
    public static Hashtable<String, DocumentFile> treeUriHash = new Hashtable<>();

    private static void initUriCache() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            List<UriPermission> list = context.getContentResolver().getPersistedUriPermissions();
            for (UriPermission uriPermission : list)
                treeUriList.add(uriPermission.getUri().toString());
        }
    }

    public static String getPersistedUriString(String uriString) {
        String base = null;

        for (String prefix:treeUriList) {
            if (uriString.startsWith(prefix)) {
                if (base == null || base.length() < prefix.length())
                    base = prefix;
            }
        }
        return base;
    }

    public static DocumentFile getDocumentTreeByUri(String uriString, String uriPersisted) {
        int indexBeg, indexEnd;
        DocumentFile docBase, docFile = null;

        docBase = treeUriHash.get(uriString);
        if (docBase != null)
            return docBase;

        if (uriPersisted == null)
            uriPersisted = getPersistedUriString(uriString);
        if (uriPersisted == null)
            return null;
        docBase = treeUriHash.get(uriPersisted);
        if (docBase == null) {
            docBase = DocumentFile.fromTreeUri(context, Uri.parse(uriPersisted));
            if (docBase == null)
                return null;
            else
                treeUriHash.put(uriPersisted, docBase);
        }
        if (uriString.equals(uriPersisted))
            return docBase;

        indexBeg = uriPersisted.length();
        while (indexBeg < uriString.length()) {
            indexEnd = uriString.indexOf("%2F", indexBeg);
            if (indexEnd == -1)
                indexEnd = uriString.length();

            docFile = treeUriHash.get(uriString.substring(0, indexEnd));
            if (docFile == null)
                docFile = docBase.findFile(uriString.substring(indexBeg, indexEnd));
            if (docFile == null)
                docFile = docBase.createDirectory(uriString.substring(indexBeg, indexEnd));
            if (docFile == null)
                return null;

            treeUriHash.put(uriString.substring(0, indexEnd), docFile);
            indexBeg = indexEnd + 3;    // + "%2F"
            docBase = docFile;
        }
        return docFile;
    }

    // ------------------------------------------------------------------------
    // UgStdio.h

    // parameter mode:
    // 1:  "r" for read-only
    // 2:  "w" for write-only
    // 4:  "t" truncates existing file
    // 8:  O_CREAT
    // 16: O_EXCL Use with O_CREAT, return error if file exist.
    //
    public static int openFile(String docUriStr, int mode) {
        // get mode string
        StringBuffer stringBuffer = new StringBuffer();
        if ((mode & 1) == 1)
            stringBuffer.append('r');
        if ((mode & 2) == 2)
            stringBuffer.append('w');
        if ((mode & 4) == 4)
            stringBuffer.append('t');
        String modeStr = stringBuffer.toString();
        stringBuffer = null;

        String[] treeAndName = null;
        DocumentFile docTree = null;
        DocumentFile docFile;

        treeAndName = splitTreeAndName(docUriStr);
        if (treeAndName[1].length() == 0)
            return -1;

        try {
            // docTree = DocumentFile.fromTreeUri(context, Uri.parse(treeAndName[0]));
            docTree = getDocumentTreeByUri(treeAndName[0], null);
            docFile = docTree.findFile(treeAndName[1]);
            if (docFile != null) {
                // if mode has O_CREAT & O_EXCL
                if ((mode & 8) == 8 && (mode & 16) == 16)
                    return -1;
            }
            else {
                // if file not exist and mode has O_CREAT
                if ((mode & 8) == 8) {
                    if (docTree.exists())
                        docFile = docTree.createFile("application/octet-stream", treeAndName[1]);
                    if (docFile == null)
                        return -1;
                }
                else
                    return -1;
            }
            return openFileDescriptor(docFile, modeStr);
        } catch(Exception e) {
            return -1;
        }
    }

    public static int openFileDescriptor(DocumentFile docFile, String mode) {
        ParcelFileDescriptor parcelFileDescriptor;

        try {
            parcelFileDescriptor = contentResolver.openFileDescriptor(docFile.getUri(), mode);
            return parcelFileDescriptor.detachFd();
        } catch (Exception e) {
            return -1;
        }
    }

    public static int renameFile(String docUriStr, String docUriStrNew) {
        String[] treeAndName;
        DocumentFile docTree;
        DocumentFile docFile;

        treeAndName = splitTreeAndName(docUriStr);
        if(treeAndName[1].length() == 0)
            return -1;
        try {
            // docTree = DocumentFile.fromTreeUri(context, Uri.parse(treeAndName[0]));
            docTree = getDocumentTreeByUri(treeAndName[0], null);
            docFile = docTree.findFile(treeAndName[1]);

            if (docFile != null && docFile.renameTo(splitTreeAndName(docUriStrNew)[1]))
                return 0;
        } catch(Exception e) {}

        return -1;
    }

    public static int removeFile(String docUriStr) {
        String[] treeAndName;
        DocumentFile docTree;
        DocumentFile docFile;

        treeAndName = splitTreeAndName(docUriStr);
        if(treeAndName[1].length() == 0)
            return -1;
        try {
            // docTree = DocumentFile.fromTreeUri(context, Uri.parse(treeAndName[0]));
            docTree = getDocumentTreeByUri(treeAndName[0], null);
            docFile = docTree.findFile(treeAndName[1]);

            if (docFile != null && docFile.delete())
                return 0;
        } catch(Exception e) {}
        return -1;
    }

    // ------------------------------------------------------------------------
    // UgFileUtil.h

    public static int isFileExist(String docUriStr) {
        DocumentFile docFile;

        docFile = DocumentFile.fromTreeUri(context, Uri.parse(docUriStr));
        if (docFile.exists())
            return 1;    // return TRUE

        String[] treeAndName = splitTreeAndName(docUriStr);
        try {
            // docFile = DocumentFile.fromTreeUri(context, Uri.parse(treeAndName[0]));
            docFile = getDocumentTreeByUri(treeAndName[0], null);
            if (docFile.findFile(treeAndName[1]) != null)
                return 1;  // return TRUE;
        } catch(Exception e) {}
        return 0;  // return FALSE;
    }

    public static int isDirectory(String docUriStr) {
        DocumentFile docTree;

        try {
            // docTree = DocumentFile.fromTreeUri(context, Uri.parse(docUriStr));
            docTree = getDocumentTreeByUri(docUriStr, null);
            if(docTree.isDirectory())
                return 1;    // return TRUE;
        } catch (Exception e) {}

        return 0;    // return FALSE
    }

    public static int createDir(String docUriStr) {
        String[] treeAndName;
        DocumentFile docTree;
        DocumentFile docFile;

        try {
            treeAndName = splitTreeAndName(docUriStr);
            if(treeAndName[1].length() == 0)
                return -1;
            // --- check existed directory
            // docTree = DocumentFile.fromTreeUri(context, Uri.parse(treeAndName[0]));
            docTree = getDocumentTreeByUri(treeAndName[0], null);
            docFile = docTree.findFile(treeAndName[1]);
            if (docFile != null)
                return -1;
            // create directory
            if (docTree.createDirectory(treeAndName[1]) != null)
                return 0;
        }
        catch (Exception e) {}
        return -1;
    }

    public static int deleteDir(String docUriStr) {
        DocumentFile docTree;

        // docTree = DocumentFile.fromTreeUri(context, Uri.parse(docUriStr));
        docTree = getDocumentTreeByUri(docUriStr, null);
        if (docTree != null && docTree.delete())
            return 0;
        else
            return -1;
    }

}
