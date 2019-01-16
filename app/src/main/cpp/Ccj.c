/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

#include <jni.h>
#include <fcntl.h>
#include <stdio.h>
#include <sys/stat.h>
#include <UgDefine.h>
#include <UgString.h>
#include <UgStdio.h>

JavaVM* jvm = NULL;  // from jni main thread use (*env)->GetJavaVM(env, &jvm);
int     sdkVer = 0;

// === C Call Java ===
// package com.ugetdm.uget.lib;
// public class Ccj {
//     //  return 0 or -1
//     public static int openFile(String pathUri, int mode);
//     public static int renameFile(String pathUri, String pathUriNew);
//     public static int removeFile(String pathUri);
//
//     public static int createDir(String pathUri);
//     public static int deleteDir(String pathUri);
//
//     // return TRUE or FALSE
//     public static int isFileExist(String pathUri);
//     public static int isDirectory(String pathUri);
// }
//

jclass     jCcj_class;
jmethodID  jCcj_getStoragePathLength;
jmethodID  jCcj_pathToTreeUri;
jmethodID  jCcj_openFile;
jmethodID  jCcj_renameFile;
jmethodID  jCcj_removeFile;

jmethodID  jCcj_isFileExist;
jmethodID  jCcj_isDirectory;

jmethodID  jCcj_createDir;
jmethodID  jCcj_deleteDir;

// ----------------------------------------------------------------------------

static int  get_android_version (JNIEnv* env);

// public native static
JNIEXPORT void
Java_com_ugetdm_uget_lib_Ccj_cInit (JNIEnv* env, jclass this_class)
{
	(*env)->GetJavaVM (env, &jvm);
//	jCcj_class      = (*env)->FindClass (env, "com/ugetdm/uget/lib/Ccj");
    jCcj_class = (*env)->NewGlobalRef (env, this_class);
    jCcj_getStoragePathLength = (*env)->GetStaticMethodID (env, jCcj_class, "getStoragePathLength", "(Ljava/lang/String;)I");
    jCcj_pathToTreeUri = (*env)->GetStaticMethodID (env, jCcj_class, "pathToTreeUri", "(Ljava/lang/String;)Ljava/lang/String;");
	jCcj_openFile   = (*env)->GetStaticMethodID (env, jCcj_class, "openFile", "(Ljava/lang/String;I)I");
	jCcj_renameFile = (*env)->GetStaticMethodID (env, jCcj_class, "renameFile", "(Ljava/lang/String;Ljava/lang/String;)I");
	jCcj_removeFile = (*env)->GetStaticMethodID (env, jCcj_class, "removeFile", "(Ljava/lang/String;)I");

	jCcj_createDir  = (*env)->GetStaticMethodID (env, jCcj_class, "createDir", "(Ljava/lang/String;)I");
	jCcj_deleteDir  = (*env)->GetStaticMethodID (env, jCcj_class, "deleteDir", "(Ljava/lang/String;)I");

	jCcj_isFileExist = (*env)->GetStaticMethodID (env, jCcj_class, "isFileExist", "(Ljava/lang/String;)I");
    jCcj_isDirectory = (*env)->GetStaticMethodID (env, jCcj_class, "isDirectory", "(Ljava/lang/String;)I");

    sdkVer = get_android_version (env);
}

// public native static
JNIEXPORT void
Java_com_ugetdm_uget_lib_Ccj_cFinal (JNIEnv* env, jclass this_class)
{
    (*env)->DeleteGlobalRef (env, jCcj_class);
}

static int  get_android_version (JNIEnv* env)
{
	int     sdkInt = 0;

	// VERSION is a nested class within android.os.Build (hence "$" rather than "/")
	jclass versionClass = (*env)->FindClass (env, "android/os/Build$VERSION" );
	jfieldID sdkIntFieldID = (*env)->GetStaticFieldID (env, versionClass, "SDK_INT", "I" );
	sdkInt = (*env)->GetStaticIntField (env, versionClass, sdkIntFieldID);
    (*env)->DeleteLocalRef (env, versionClass);
    return sdkInt;
}

// ----------------------------------------------------------------------------

static int get_storage_path_length (JNIEnv* env, const char* path)
{
    jstring jPathStr;
    jint result;

    if (sdkVer < 21)
        return 0;

    jPathStr = (*env)->NewStringUTF (env, path);
    result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_getStoragePathLength, jPathStr);
    (*env)->DeleteLocalRef (env, jPathStr);

    return result;
}

static jstring path_to_tree_uri (JNIEnv* env, const char* path)
{
    jstring jPathStr, result;

    if (sdkVer < 21)
        return NULL;

    jPathStr = (*env)->NewStringUTF (env, path);
    result = (*env)->CallStaticObjectMethod (env, jCcj_class, jCcj_pathToTreeUri, jPathStr);
    (*env)->DeleteLocalRef (env, jPathStr);

    return result;
}

// Use SD card access API presented for Android 5.0 (Lollipop)
// Android 5.0 (API Level 21)

// --------------------------------------------------------
// UgStdio.h
//

// ignore parameter "mode"
int  ug_open (const char* filename_utf8, int flags, int mode)
{
	JNIEnv* env;
	jstring jTreeUri;
	jint    jmode = 1;
	int     result;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, filename_utf8);
    if (jTreeUri == NULL) {
        result = open (filename_utf8, flags, mode);
        goto exit;
    }

	// 1:  "r" for read-only
	// 2:  "w" for write-only
	// 4:  "t" truncates existing file
	// 8:  O_CREAT
	// 16: O_EXCL Use with O_CREAT, return error if file exist.
	if (flags & O_RDONLY)
		jmode = 1;
	if (flags & O_WRONLY)
		jmode = 2;
	if (flags & O_RDWR)
		jmode = 1 | 2;
	if (flags & O_TRUNC)
		jmode |= 4;
	if (flags & O_CREAT)
		jmode |= 8;
	if (flags & O_EXCL)
		jmode |= 16;

	result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_openFile, jTreeUri, jmode);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
    	(*jvm)->DetachCurrentThread (jvm);
	return result;
}

// ignore parameter "mode"
int  ug_creat (const char* filename_utf8, int mode)
{
    return ug_open (filename_utf8, O_WRONLY|O_CREAT|O_TRUNC, mode);
}

int  ug_rename (const char *old_filename, const char *new_filename)
{
	JNIEnv* env;
	jstring jTreeUri1, jTreeUri2;
	int     result;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jTreeUri1 = path_to_tree_uri (env, old_filename);
    if (jTreeUri1 == NULL) {
        result = rename (old_filename, new_filename);
        goto exit;
    }

	jTreeUri2 = path_to_tree_uri(env, new_filename);
	result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_renameFile,
			jTreeUri1, jTreeUri2);
    (*env)->DeleteLocalRef (env, jTreeUri1);
    (*env)->DeleteLocalRef (env, jTreeUri2);

exit:
    if (isDoAttachThread)
    	(*jvm)->DetachCurrentThread (jvm);
	return result;
}

int  ug_remove (const char *filename_utf8)
{
	JNIEnv* env;
	jstring jTreeUri;
	int     result;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, filename_utf8);
    if (jTreeUri == NULL) {
        result = remove (filename_utf8);
        goto exit;
    }

	result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_removeFile, jTreeUri);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
    	(*jvm)->DetachCurrentThread (jvm);
	return result;
}

int  ug_unlink (const char *filename)
{
	return ug_remove (filename);
}

FILE* ug_fopen (const char *filename_utf8, const char *mode)
{
    JNIEnv* env;
    jstring jTreeUri;
    FILE*   result;
    int     jmode = 0;
    int     fd;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return NULL;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, filename_utf8);
    if (jTreeUri == NULL) {
        result = fopen (filename_utf8, mode);
        goto exit;
    }

    // 1:  "r" for read
    // 2:  "w" for write
    // 4:  "t" truncates existing file
    // 8:  O_CREAT
    // 16: O_EXCL Use with O_CREAT, return error if file exist.
    if (strchr (mode, 'a'))
        jmode |= 2;    // Does NOT support O_APPEND
    if (strchr (mode, 'r'))
        jmode |= 1;
    if (strchr (mode, 'w'))
        jmode |= 2 | 4 | 8;    // O_CREAT | O_TRUNC
    if (strchr (mode, '+'))
        jmode |= 1 | 2;    // read + write

    fd = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_openFile, jTreeUri, jmode);
    (*env)->DeleteLocalRef (env, jTreeUri);
    if (fd == -1)
        result = NULL;
    else
        result = ug_fdopen (fd, mode);

exit:
    if (isDoAttachThread)
        (*jvm)->DetachCurrentThread (jvm);
    return result;
}

// --------------------------------------------------------
// UgFileUtil.h

int   ug_file_is_exist (const char* filename_utf8)
{
    JNIEnv* env;
    jstring jTreeUri;
    int     result = FALSE;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return FALSE;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, filename_utf8);
    if (jTreeUri == NULL) {
        if (access (filename_utf8, F_OK) != -1)
            result = TRUE;
        goto exit;
    }

    result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_isFileExist, jTreeUri);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
        (*jvm)->DetachCurrentThread (jvm);
    return result;
}

int   ug_file_is_dir (const char* filename_utf8)
{
    JNIEnv* env;
    jstring jTreeUri;
    int     result = FALSE;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return FALSE;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, filename_utf8);
    if (jTreeUri == NULL) {
        struct stat s;

        if (stat (filename_utf8, &s) == 0)
            result = S_ISDIR (s.st_mode);
        goto exit;
    }

    result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_isDirectory, jTreeUri);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
        (*jvm)->DetachCurrentThread (jvm);
    return result;
}

// return 0 or -1
int  ug_create_dir (const char *dir)
{
	JNIEnv* env;
	jstring jTreeUri;
	int     result;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, dir);
    if (jTreeUri == NULL) {
        result =  mkdir (dir, 0755);    // return 0 or -1
        goto exit;
    }

	result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_createDir, jTreeUri);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
    	(*jvm)->DetachCurrentThread (jvm);
	return result;
}

// return 0 or -1
int  ug_delete_dir (const char *dir)
{
	JNIEnv* env;
	jstring jTreeUri;
	int     result;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jTreeUri = path_to_tree_uri (env, dir);
    if (jTreeUri == NULL) {
        result = rmdir (dir);    // return 0 or -1
        goto exit;
    }

	result = (*env)->CallStaticIntMethod (env, jCcj_class, jCcj_deleteDir, jTreeUri);
    (*env)->DeleteLocalRef (env, jTreeUri);

exit:
    if (isDoAttachThread)
    	(*jvm)->DetachCurrentThread (jvm);
	return result;
}

static int  ug_get_saf_path_length (const char *dir)
{
    JNIEnv* env;
    jstring jString;
    int     result = 0;
    int     isDoAttachThread = FALSE;

    if ((*jvm)->GetEnv(jvm, (void**)&env, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*jvm)->AttachCurrentThread (jvm, &env, NULL) != JNI_OK)
            return -1;
        isDoAttachThread = TRUE;
    }

    jString = path_to_tree_uri (env, dir);
    if (jString == NULL)
        goto exit;
    (*env)->DeleteLocalRef (env, jString);

    result = get_storage_path_length (env, dir);

    exit:
    if (isDoAttachThread)
        (*jvm)->DetachCurrentThread (jvm);
    return result;
}

int  ug_create_dir_all (const char* dir, int len)
{
    const char*   dir_end;
    const char*   element_end;	// path element
    char*         element_os;

    if (len == -1)
        len = strlen (dir);
    if (len > 1 && dir[len-1] == UG_DIR_SEPARATOR)
        len--;
    dir_end = dir + len;
    element_end = dir;

    // quick check
    element_os = ug_strndup (dir, len);
    if (ug_file_is_exist (element_os)) {
        ug_free (element_os);
        return 0;
    }
    element_end = dir + ug_get_saf_path_length(element_os);
    ug_free (element_os);

    for (;;) {
        // skip directory separator "\\\\" or "//"
        for (;  element_end < dir_end;  element_end++) {
            if (*element_end != UG_DIR_SEPARATOR)
                break;
        }
        if (element_end == dir_end)
            return 0;
        // get directory name [dir, element_end)
        for (;  element_end < dir_end;  element_end++) {
            if (*element_end == UG_DIR_SEPARATOR)
                break;
        }
        element_os = (char*) ug_malloc (element_end - dir + 1);
        element_os[element_end - dir] = 0;
        strncpy (element_os, dir, element_end - dir);

        if (element_os == NULL)
            break;
        if (ug_file_is_exist (element_os) == FALSE) {
            if (ug_create_dir (element_os) == -1) {
                ug_free (element_os);
                return -1;
            }
        }
        ug_free (element_os);
    }
    return -1;
}
