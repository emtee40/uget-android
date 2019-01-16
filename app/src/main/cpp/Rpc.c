/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

#include <jni.h>
#include <stdint.h>
#include <pthread.h>

#include <UgString.h>
#include <UgetRpc.h>
#include <UgetNode.h>

#include <android/log.h>

#define LOG_TAG    "com.ugetdm.uget.lib"
#define LOGV(...)  __android_log_print( ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print( ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print( ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print( ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print( ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)

// global lock
static pthread_mutex_t  rpclock;
void  rec_mutex_init (pthread_mutex_t*  mutex);
void  rec_mutex_clear (pthread_mutex_t*  mutex);
void  rec_mutex_lock (pthread_mutex_t*  mutex);
void  rec_mutex_unlock (pthread_mutex_t*  mutex);

JNIEXPORT void
Java_com_ugetdm_uget_lib_Rpc_cInit (JNIEnv* env, jobject thiz, jstring jdir)
{
	UgetRpc*  rpc;
	jclass    rpcClass;
	const char*  dir;

	// global lock
	rec_mutex_init (&rpclock);

	// create UgetRpc with/without backup_dir
	if (jdir == NULL)
		rpc = uget_rpc_new (NULL);
	else {
		dir = (*env)->GetStringUTFChars (env, jdir, NULL);
		rpc = uget_rpc_new (dir);
		(*env)->ReleaseStringUTFChars (env, jdir, dir);
	}

	rpcClass = (*env)->GetObjectClass (env, thiz);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, rpcClass, "pointer", "J"),
			(jlong)(intptr_t) rpc);
	(*env)->DeleteLocalRef (env, rpcClass);

	/*
	// test sendCommand
	{
		UgetRpcCmd* cmd;
		UgLink*     link;

		cmd = uget_rpc_cmd_new ();
		cmd->method_id = UGET_RPC_SEND_COMMAND;
		cmd->value.ctrl.offline = 0;
		cmd->value.common.file = ug_strdup ("file.mp4");
		cmd->value.common.user = ug_strdup ("foo");
		cmd->value.common.password = ug_strdup ("bar");
		link = ug_link_new ();
		link->data = ug_strdup ("http://rpc/test/1234");
		ug_list_append (&cmd->uris, link);

		rec_mutex_lock (&rpc->queue_lock);
		ug_list_append (&rpc->queue, (UgLink*) cmd);
		rec_mutex_unlock (&rpc->queue_lock);
	}
	*/

	/*
	// test present
	{
		UgetRpcReq* req;
		UgLink*     link;

		req = uget_rpc_req_new ();
		req->method_id = UGET_RPC_PRESENT;
		rec_mutex_lock (&rpc->queue_lock);
		ug_list_append (&rpc->queue, (UgLink*) req);
		rec_mutex_unlock (&rpc->queue_lock);
	}
	*/
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Rpc_cFinal (JNIEnv* env, jobject thiz)
{
	UgetRpc*  rpc;
	jclass    rpcClass;

	rpcClass = (*env)->GetObjectClass (env, thiz);
	rpc = (UgetRpc*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, rpcClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, rpcClass);

	uget_rpc_free (rpc);

	// global lock
	rec_mutex_clear (&rpclock);
}

// *** Constructor of inner class ***
// You need parent class in the GetMethodID signature, so in my example:
// jmethodID methodID = (*env)->GetMethodID(env, jClass, "<init>", "(LparentClass;)V");
//
// And I also needed to add calling class object/pointer to the NewObject function:
// jobject obj = (*env)->NewObject(env, jClass, methodID, this);

JNIEXPORT jobject
Java_com_ugetdm_uget_lib_Rpc_getRequest (JNIEnv* env, jobject thiz)
{
	UgetRpc*    rpc;
	UgetRpcReq* req;
	jclass    jClass;
	jobject   jObject;

	jClass = (*env)->GetObjectClass (env, thiz);
	rpc = (UgetRpc*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jClass);

	// global lock
	rec_mutex_lock (&rpclock);

	req = uget_rpc_get_request (rpc);
	if (req == NULL)
		jObject = NULL;
	else {
		// Create New Object with constructor
		jClass = (*env)->FindClass (env, "com/ugetdm/uget/lib/Rpc$Request");
		// jObject = new com.ugetdm.uget.Rpc.Request()
		jObject = (*env)->NewObject (env, jClass,
				(*env)->GetMethodID (env, jClass, "<init>", "(Lcom/ugetdm/uget/lib/Rpc;)V"),
				thiz);
		// long  pointer;
		(*env)->SetLongField (env, jObject,
				(*env)->GetFieldID (env, jClass, "pointer", "J"),
				(jlong)(intptr_t) req);
		// int  methodId;
		(*env)->SetIntField (env, jObject,
				(*env)->GetFieldID (env, jClass, "methodId", "I"),
				(jint) req->method_id);
		(*env)->DeleteLocalRef (env, jClass);
	}

	// global lock
	rec_mutex_unlock (&rpclock);

	return jObject;
}

// define in Node.c
void getDownloadData(JNIEnv* env, jlong pointer, jobject dInfoObject);

// Rpc.Command  com.ugetdm.uget.Rpc.getCommand (Rpc.Request req)
JNIEXPORT jobject
Java_com_ugetdm_uget_lib_Rpc_getCommand (JNIEnv* env, jobject thiz, jobject jreq)
{
	int          index;
	UgLink*      link;
	UgetRpcCmd*  cmd;
	UgetNode*    node;
	jclass       jClass;
	jobject      jObject;
	jobjectArray uriArray;

	// --------------------------------
	// Rpc.Request
	jClass = (*env)->GetObjectClass (env, jreq);
	cmd = (UgetRpcCmd*)(intptr_t) (*env)->GetLongField(env, jreq,
			(*env)->GetFieldID (env, jClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jClass);

	// global lock
	rec_mutex_lock (&rpclock);

	if (cmd->method_id != UGET_RPC_SEND_COMMAND)
		return NULL;
	// --------------------------------
	// Rpc.Command
	jClass = (*env)->FindClass (env, "com/ugetdm/uget/lib/Rpc$Command");
	// Create New Object with constructor
	// jObject = new com.ugetdm.uget.Rpc.Command()
	jObject = (*env)->NewObject (env, jClass,
			(*env)->GetMethodID (env, jClass, "<init>", "(Lcom/ugetdm/uget/lib/Rpc;)V"),
			thiz);

	// String  uris[];
	uriArray = (*env)->NewObjectArray (env, cmd->uris.size,
			(*env)->FindClass(env, "java/lang/String"),
			(*env)->NewStringUTF(env, ""));
	for (index = 0, link = cmd->uris.head;  link;  link = link->next, index++) {
		(*env)->SetObjectArrayElement (env, uriArray, index,
				(*env)->NewStringUTF (env, link->data));
	}
	(*env)->SetObjectField (env, jObject,
			(*env)->GetFieldID (env, jClass, "uris", "[Ljava/lang/String;"),
			uriArray);
	// Download info;
	node = uget_node_new (NULL);
	uget_option_value_to_info (&cmd->value, node->info);
	getDownloadData (env, (jlong)(intptr_t) node,
			(*env)->GetObjectField (env, jObject,
					(*env)->GetFieldID (env, jClass, "info",
							"Lcom/ugetdm/uget/lib/Download;")) );
	uget_node_free (node);
	// boolean  quiet;
	(*env)->SetBooleanField (env, jObject,
			(*env)->GetFieldID (env, jClass, "quiet", "Z"),
			cmd->value.quiet);
	// int categoryIndex;
	(*env)->SetIntField (env, jObject,
			(*env)->GetFieldID (env, jClass, "categoryIndex", "I"),
			cmd->value.category_index);
	// int  offline;
	(*env)->SetIntField (env, jObject,
			(*env)->GetFieldID (env, jClass, "offline", "I"),
			cmd->value.ctrl.offline);

	(*env)->DeleteLocalRef (env, jClass);

	// global lock
	rec_mutex_unlock (&rpclock);

	return jObject;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Rpc_startServer (JNIEnv* env, jobject thiz)
{
	UgetRpc*  rpc;
	jclass    rpcClass;
	jboolean  result;

	rpcClass = (*env)->GetObjectClass (env, thiz);
	rpc = (UgetRpc*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, rpcClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, rpcClass);

	// global lock
	rec_mutex_lock (&rpclock);

	result = uget_rpc_start_server (rpc, FALSE);

	// global lock
	rec_mutex_unlock (&rpclock);

	return result;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Rpc_stopServer (JNIEnv* env, jobject thiz)
{
	UgetRpc*  rpc;
	jclass    rpcClass;

	rpcClass = (*env)->GetObjectClass (env, thiz);
	rpc = (UgetRpc*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, rpcClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, rpcClass);

	// global lock
	rec_mutex_lock (&rpclock);

	uget_rpc_stop_server (rpc);

	// global lock
	rec_mutex_unlock (&rpclock);
}

// ----------------------------------------------------------------------------
// Rpc.Request

// package com.ugetdm.uget
// class Rpc {
//     class Request {
//         private native void  cFinal();
//     }
// }

// void  com.ugetdm.uget.Rpc.Request.cFinal();
//                          ^inner

//JNIEXPORT void
//Java_com_ugetdm_uget_lib_Rpc_00024Request_cFinal (JNIEnv* env, jobject thiz)

JNIEXPORT void JNICALL
Java_com_ugetdm_uget_lib_Rpc_Request_cFinal(JNIEnv *env, jobject thiz) {
	UgetRpcReq*  req;
	jclass       reqClass;

	reqClass = (*env)->GetObjectClass (env, thiz);
	req = (UgetRpcReq*)(intptr_t) (*env)->GetLongField(env, thiz,
					(*env)->GetFieldID (env, reqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, reqClass);

	req->free (req);
}
