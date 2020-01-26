/*
 *
 *   Copyright (C) 2018-2020 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

 #include <jni.h>
#include <stddef.h>
#include <pthread.h>

#include <UgUri.h>
#include <UgUtil.h>
#include <UgString.h>
#include <UgetApp.h>
#include <UgetSequence.h>
#include <UgetPluginCurl.h>
#include <UgetPluginAria2.h>
#include <UgetPluginMedia.h>
#include <UgetPluginMega.h>

#include <android/log.h>

#define LOG_TAG    "com.ugetdm.uget.lib"
#define LOGV(...)  __android_log_print( ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print( ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print( ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print( ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print( ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)


/*
jint JNI_OnLoad (JavaVM* vm, void* reserved)
{
	JNIEnv* env = NULL;
	jint    result = -1;

	if (vm->GetEnv((void**) &env, JNI_VERSION_1_4) != JNI_OK) {
		LOGE("ERROR: GetEnv failed\n");
	}
	return result;
}

jint JNI_OnUnload(JavaVM* vm, void* reserved)
{
}
 */

// ----------------------------------------------------------------------------
// OpenSSL

#include <openssl/crypto.h>

static pthread_mutex_t *lockarray;

static void lock_callback(int mode, int type, char *file, int line)
{
	(void)file;
	(void)line;

	if (mode & CRYPTO_LOCK) {
		pthread_mutex_lock (&(lockarray[type]));
	}
	else {
		pthread_mutex_unlock (&(lockarray[type]));
	}
}

static unsigned long thread_id (void)
{
	unsigned long ret;

	ret = (unsigned long) pthread_self();
	return(ret);
}

static void init_locks (void)
{
	int i;

	lockarray = (pthread_mutex_t*) OPENSSL_malloc(CRYPTO_num_locks() *
	                                        sizeof(pthread_mutex_t));
	for (i=0; i < CRYPTO_num_locks(); i++) {
		pthread_mutex_init(&(lockarray[i]), NULL);
	}

	CRYPTO_set_id_callback((unsigned long (*)())thread_id);
	CRYPTO_set_locking_callback((void (*)())lock_callback);
}

static void kill_locks(void)
{
	int i;

	CRYPTO_set_locking_callback(NULL);
	for (i=0; i<CRYPTO_num_locks(); i++)
		pthread_mutex_destroy(&(lockarray[i]));

	OPENSSL_free(lockarray);
}


// ----------------------------------------------------------------------------
// global lock

void  rec_mutex_init (pthread_mutex_t*  mutex)
{
	pthread_mutexattr_t  attr;

	pthread_mutexattr_init (&attr);
	pthread_mutexattr_settype (&attr, PTHREAD_MUTEX_RECURSIVE);
	pthread_mutex_init (mutex, &attr);
	pthread_mutexattr_destroy (&attr);
}

void  rec_mutex_clear (pthread_mutex_t*  mutex)
{
	pthread_mutex_destroy (mutex);
}

void  rec_mutex_lock (pthread_mutex_t*  mutex)
{
	pthread_mutex_lock (mutex);
}

void  rec_mutex_unlock (pthread_mutex_t*  mutex)
{
	pthread_mutex_unlock (mutex);
}

// ----------------------------------------------------------------------------

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_cInit (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	jclass    jCore_class;

	uget_plugin_global_set(UgetPluginCurlInfo,  UGET_PLUGIN_GLOBAL_INIT, (void*) TRUE);
	uget_plugin_global_set(UgetPluginAria2Info, UGET_PLUGIN_GLOBAL_INIT, (void*) TRUE);
	uget_plugin_global_set(UgetPluginMediaInfo, UGET_PLUGIN_GLOBAL_INIT, (void*) TRUE);
	uget_plugin_global_set(UgetPluginMegaInfo,  UGET_PLUGIN_GLOBAL_INIT, (void*) TRUE);

	// OpenSSL
	init_locks();

	// size of curl_off_t
//	LOGV ("sizeof (curl_off_t) = %d", sizeof (curl_off_t));

	app = calloc (1, sizeof (UgetApp));
	uget_app_init (app);
	uget_app_add_plugin (app, UgetPluginCurlInfo);
	uget_app_add_plugin (app, UgetPluginAria2Info);
	uget_app_add_plugin (app, UgetPluginMediaInfo);
	uget_app_add_plugin (app, UgetPluginMegaInfo);

	// Enable URI hash table
	uget_app_use_uri_hash (app);

	jCore_class = (*env)->GetObjectClass (env, thiz);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"),
			(jlong)(intptr_t) app);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nodeReal", "J"),
			(jlong)(intptr_t) &app->real);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nodeSorted", "J"),
			(jlong)(intptr_t) &app->sorted);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nodeSplit", "J"),
			(jlong)(intptr_t) &app->split);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nodeMix", "J"),
			(jlong)(intptr_t) &app->mix);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nodeMixSplit", "J"),
			(jlong)(intptr_t) &app->mix_split);
	(*env)->DeleteLocalRef (env, jCore_class);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_cFinal (JNIEnv* env, jobject thiz, jboolean shutdown_aria2)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_app_final (app);
	free (app);

	if (shutdown_aria2)
		uget_plugin_global_set(UgetPluginAria2Info, UGET_PLUGIN_ARIA2_GLOBAL_SHUTDOWN_NOW, (void*) TRUE);
	uget_plugin_global_set(UgetPluginMegaInfo,  UGET_PLUGIN_GLOBAL_INIT, (void*) FALSE);
	uget_plugin_global_set(UgetPluginMediaInfo, UGET_PLUGIN_GLOBAL_INIT, (void*) FALSE);
	uget_plugin_global_set(UgetPluginAria2Info, UGET_PLUGIN_GLOBAL_INIT, (void*) FALSE);
	uget_plugin_global_set(UgetPluginCurlInfo,  UGET_PLUGIN_GLOBAL_INIT, (void*) FALSE);

	// OpenSSL
	kill_locks();
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Core_grow (JNIEnv* env, jobject thiz, jboolean no_queuing)
{
	UgetApp*  app;
	jclass    jCore_class;
	jint      nActive;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	// reset counter
	app->n_moved = 0;
	app->n_deleted = 0;
	app->n_completed = 0;
//	app->n_error = 0;
	app->n_error = (*env)->GetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nError", "I"));
	// grow
	nActive = uget_app_grow (app, no_queuing);

	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "downloadSpeed", "I"),
			app->task.speed.download);
	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "uploadSpeed", "I"),
			app->task.speed.upload);
	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nMoved", "I"),
			app->n_moved);
	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nDeleted", "I"),
			app->n_deleted);
	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nCompleted", "I"),
			app->n_completed);
	(*env)->SetIntField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "nError", "I"),
			app->n_error);
	(*env)->DeleteLocalRef (env, jCore_class);

	return nActive;
}

JNIEXPORT jlongArray
Java_com_ugetdm_uget_lib_Core_trim (JNIEnv* env, jobject thiz)
{
	UgetApp*    app;
	UgArrayPtr* deletedNodes;
	jclass      jCore_class;
	jlongArray  jArray;
	jlong       jLong;
	int         index;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	// C array
	deletedNodes = ug_malloc(sizeof(UgArrayPtr));
	ug_array_init(deletedNodes, sizeof(void*), 16);
	// trim
	app->n_deleted = uget_app_trim(app, deletedNodes);
	// copy C array to Java array
	if (app->n_deleted == 0)
		jArray = NULL;
	else {
		// Java long : 64-bit
		// C pointer : 32-bit or 64-bit
		jArray = (*env)->NewLongArray(env, app->n_deleted);
		for (index = 0;  index < deletedNodes->length;  index++) {
			jLong = (jlong)(intptr_t) deletedNodes->at[index];
			(*env)->SetLongArrayRegion(env, jArray, index, 1, &jLong);
		}
	}
	// C array
	ug_array_clear(deletedNodes);
	ug_free(deletedNodes);

	(*env)->SetIntField(env, thiz,
						(*env)->GetFieldID(env, jCore_class, "nDeleted", "I"),
						app->n_deleted);
	(*env)->DeleteLocalRef(env, jCore_class);

	return jArray;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setConfigDir (JNIEnv* env, jobject thiz, jstring folder)
{
	UgetApp*  app;
	jclass    jCore_class;
	const char*  cstr;

	if (folder == NULL)
		return;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	cstr = (*env)->GetStringUTFChars (env, folder, NULL);

	uget_app_set_config_dir (app, cstr);

	(*env)->ReleaseStringUTFChars (env, folder, cstr);
	(*env)->DeleteLocalRef (env, jCore_class);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_addCategory (JNIEnv* env, jobject thiz, jlong cNodePointer)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_app_add_category (app, (UgetNode*)(intptr_t) cNodePointer, TRUE);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_deleteCategory (JNIEnv* env, jobject thiz, jlong cNodePointer)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t)(*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_app_delete_category (app, (UgetNode*)(intptr_t) cNodePointer);
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_moveCategory (JNIEnv* env, jobject thiz, jlong cNodePointer, jlong cNodePosition)
{
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	result = uget_app_move_category (app,
	                                 (UgetNode*)(intptr_t) cNodePointer,
	                                 (UgetNode*)(intptr_t) cNodePosition);

	return result;
}

JNIEXPORT jlong
Java_com_ugetdm_uget_lib_Core_matchCategory (JNIEnv* env, jobject thiz, jstring juri, jstring jfile)
{
	UgetNode* cnode;
	UgetApp*  app;
	jclass    jCore_class;
	UgUri     uuri;
	const char* uri;
	const char* file;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	cnode = NULL;
	uri = (*env)->GetStringUTFChars (env, juri, NULL);
	if (jfile)
		file = (*env)->GetStringUTFChars (env, jfile, NULL);
	else
		file = NULL;

	ug_uri_init (&uuri, uri);
	cnode = uget_app_match_category (app, &uuri, file);

	if (jfile)
		(*env)->ReleaseStringUTFChars (env, jfile, file);
	(*env)->ReleaseStringUTFChars (env, juri, uri);

	return (jlong)(intptr_t) cnode;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_stopCategories (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	UgetNode* cnode;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	for (cnode = app->real.children;  cnode;  cnode = cnode->next)
		uget_app_stop_category ((UgetApp*)app, cnode);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_pauseCategories (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	UgetNode* cnode;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	for (cnode = app->real.children;  cnode;  cnode = cnode->next)
		uget_app_pause_category ((UgetApp*)app, cnode);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_resumeCategories (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	UgetNode* cnode;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	for (cnode = app->real.children;  cnode;  cnode = cnode->next)
		uget_app_resume_category ((UgetApp*)app, cnode);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_addDownloadByUri (JNIEnv* env, jobject thiz, jstring juri, jlong cNodePointer, jboolean apply)
{
	UgetApp*  app;
	jclass    jCore_class;
	const char* uri;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uri = (*env)->GetStringUTFChars (env, juri, NULL);
	uget_app_add_download_uri (app, uri, (UgetNode*)(intptr_t) cNodePointer, apply);
	(*env)->ReleaseStringUTFChars (env, juri, uri);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_addDownload (JNIEnv* env, jobject thiz, jlong dNodePointer, jlong cNodePointer, jboolean apply)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_app_add_download (app, (UgetNode*)(intptr_t) dNodePointer, (UgetNode*)(intptr_t) cNodePointer, apply);
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_deleteDownload (JNIEnv* env, jobject thiz, jlong dNodePointer, jboolean deleteFiles)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	result = uget_app_delete_download (app, dnode, deleteFiles);
	return result;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_recycleDownload (JNIEnv* env, jobject thiz, jlong dNodePointer)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	result = uget_app_recycle_download (app, dnode);
	return result;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_moveDownload (JNIEnv* env, jobject thiz, jlong dNodePointer, jlong dNodePosition)
{
	UgetApp*  app;
	jclass    jCore_class;
	UgetNode* dnode1;
	UgetNode* dnode2;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode1 = (UgetNode*)(intptr_t) dNodePointer;
	dnode2 = (UgetNode*)(intptr_t) dNodePosition;

	dnode1 = dnode1->base;
	if (dnode2) {
		dnode2 = dnode2->base;
		if (dnode2 == dnode1->next)
			dnode2 = dnode2->next;
	}
	result = uget_app_move_download (app, dnode1, dnode2);
	return result;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_activateDownload (JNIEnv* env, jobject thiz, jlong dNodePointer)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	result = uget_app_activate_download (app, dnode);
	return result;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_pauseDownload (JNIEnv* env, jobject thiz, jlong dNodePointer)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	result = uget_app_pause_download (app, dnode);
	return result;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_queueDownload (JNIEnv* env, jobject thiz, jlong dNodePointer)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;
	jboolean  result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	result = uget_app_queue_download (app, dnode);
	return result;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_resetDownloadName (JNIEnv* env, jobject thiz, jlong dNodePointer)
{
	UgetNode* dnode;
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	dnode = (UgetNode*)(intptr_t) dNodePointer;
	dnode = dnode->base;
	uget_app_reset_download_name (app, dnode);
}

JNIEXPORT jlong
Java_com_ugetdm_uget_lib_Core_loadCategory__Ljava_lang_String_2 (JNIEnv* env, jobject thiz, jstring filename)
{
	jclass    jCore_class;
	jlong     cNodePointer;
	UgetApp*     app;
	const char*  cstr;

	if (filename == NULL)
		return 0;
	
	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	cstr = (*env)->GetStringUTFChars (env, filename, NULL);

	cNodePointer = (intptr_t) uget_app_load_category (app, cstr, NULL);

	(*env)->ReleaseStringUTFChars (env, filename, cstr);
	(*env)->DeleteLocalRef (env, jCore_class);

	return cNodePointer;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_saveCategory__JLjava_lang_String_2 (JNIEnv* env, jobject thiz, jlong cNodePointer, jstring filename)
{
	jclass    jCore_class;
	jboolean  result;
	UgetApp*     app;
	const char*  cstr;

	if (filename == NULL)
		return FALSE;
	
	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	cstr = (*env)->GetStringUTFChars (env, filename, NULL);

	result = uget_app_save_category (app, (UgetNode*)(intptr_t) cNodePointer, cstr, NULL);

	(*env)->ReleaseStringUTFChars (env, filename, cstr);
	(*env)->DeleteLocalRef (env, jCore_class);

	return result;
}

JNIEXPORT jlong
Java_com_ugetdm_uget_lib_Core_loadCategory__I (JNIEnv* env, jobject thiz, jint fd)
{
	jclass    jCore_class;
	jlong     cNodePointer;
	UgetApp*     app;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	cNodePointer = (intptr_t) uget_app_load_category_fd (app, fd, NULL);

	(*env)->DeleteLocalRef (env, jCore_class);

	return cNodePointer;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_saveCategory__JI (JNIEnv* env, jobject thiz, jlong cNodePointer, jint fd)
{
	jclass    jCore_class;
	jboolean  result;
	UgetApp*     app;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));

	result = uget_app_save_category_fd (app, (UgetNode*)(intptr_t) cNodePointer, fd, NULL);

	(*env)->DeleteLocalRef (env, jCore_class);

	return result;
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Core_loadCategories (JNIEnv* env, jobject thiz, jstring folder)
{
	UgetApp*  app;
	jclass    jCore_class;
	const char*  cstr;
	jint         result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	if (folder)
		cstr = (*env)->GetStringUTFChars (env, folder, NULL);
	else
		cstr = NULL;

	result = uget_app_load_categories (app, cstr);

	if (folder)
		(*env)->ReleaseStringUTFChars (env, folder, cstr);
	(*env)->DeleteLocalRef (env, jCore_class);

	return result;
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Core_saveCategories (JNIEnv* env, jobject thiz, jstring folder)
{
	UgetApp*  app;
	jclass    jCore_class;
	const char*  cstr;
	jint         result;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	if (folder)
		cstr = (*env)->GetStringUTFChars (env, folder, NULL);
	else
		cstr = NULL;

	result = uget_app_save_categories (app, cstr);

	if (folder)
		(*env)->ReleaseStringUTFChars (env, folder, cstr);
	(*env)->DeleteLocalRef (env, jCore_class);

	return result;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_clearAttachment (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_app_clear_attachment (app);
}

// ----------------------------------------------------------------------------

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Core_isUriExist (JNIEnv* env, jobject thiz, jstring uri)
{
	UgetApp*  app;
	jclass    jCore_class;
	const char* cstr;
	int         existing;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);
	cstr = (*env)->GetStringUTFChars (env, uri, NULL);

	existing = uget_uri_hash_find (app->uri_hash, cstr);

	(*env)->ReleaseStringUTFChars (env, uri, cstr);
	return existing;
}

// ----------------------------------------------------------------------------
// UgetTask

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setSpeedLimit (JNIEnv* env, jobject thiz, jint download_KiB, jint upload_KiB)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_task_set_speed (&app->task, download_KiB * 1024, upload_KiB * 1024);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_adjustSpeed (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_task_adjust_speed (&app->task);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_removeAllTask (JNIEnv* env, jobject thiz)
{
	UgetApp*  app;
	jclass    jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	uget_task_remove_all (&app->task);
}

// ----------------------------------------------------------------------------

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setSorting (JNIEnv* env, jobject thiz, jint sort_by)
{
	UgetApp*   app;
	jclass     jCore_class;

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	switch (sort_by) {
	case 0:    // Don't sort
		uget_app_set_sorting (app,
				(UgCompareFunc) NULL, FALSE);
		break;

	case 1:    // sort by name
		uget_app_set_sorting (app,
				(UgCompareFunc) uget_node_compare_name, FALSE);
		break;

	case 2:    // sort by added time
		uget_app_set_sorting (app,
				(UgCompareFunc) uget_node_compare_added_time, TRUE);
		break;
	}
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setPluginOrder(JNIEnv* env, jobject thiz, jint plugin_order)
{
	const UgetPluginInfo*  default_plugin;
	UgetApp*    app;
	jclass      jCore_class;

	jCore_class = (*env)->GetObjectClass(env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID(env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef(env, jCore_class);

	switch (plugin_order) {
	default:
	case 0:    // curl
		default_plugin = UgetPluginCurlInfo;
		uget_app_remove_plugin(app, UgetPluginAria2Info);
		break;

	case 1:    // aria2
		default_plugin = UgetPluginAria2Info;
		uget_app_remove_plugin(app, UgetPluginCurlInfo);
		break;

	case 2:    // curl + aria2
		default_plugin = UgetPluginCurlInfo;
		uget_app_add_plugin(app, UgetPluginAria2Info);
		break;

	case 3:    // aria2 + curl
		default_plugin = UgetPluginAria2Info;
		uget_app_add_plugin(app, UgetPluginCurlInfo);
		break;
	}

	uget_app_set_default_plugin(app, default_plugin);
	uget_plugin_agent_global_set(UGET_PLUGIN_AGENT_GLOBAL_PLUGIN,
	                             (void*)default_plugin);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setPluginSetting (JNIEnv* env, jobject thiz, jobject setting)
{
	const UgetPluginInfo* pinfo;
	UgetApp*    app;
	jclass      jCore_class;
	jclass      jSetting_class;
	jclass      jAria2_class;
	jobject     jAria2_object;
	jboolean    jbool;
	const char* cstr;
	jstring     jstr;
	int         speed[2];

	jCore_class = (*env)->GetObjectClass (env, thiz);
//	jCore_class = (*env)->FindClass (env, "com/ugetdm/uget/lib/Core");
	app = (UgetApp*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, jCore_class, "pointer", "J"));
	(*env)->DeleteLocalRef (env, jCore_class);

	pinfo = UgetPluginAria2Info;
	// setting
	jSetting_class = (*env)->GetObjectClass (env, setting);
	// setting.aria2
	jAria2_object = (*env)->GetObjectField (env, setting,
			(*env)->GetFieldID (env, jSetting_class, "aria2", "Lcom/ugetdm/uget/lib/PluginSetting$Aria2;"));
	(*env)->DeleteLocalRef (env, jSetting_class);

	jAria2_class = (*env)->GetObjectClass (env, jAria2_object);

	// setting.aria2.uri
	jstr = (*env)->GetObjectField (env, jAria2_object,
			(*env)->GetFieldID (env, jAria2_class, "uri", "Ljava/lang/String;"));
	cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
	if (cstr)
		uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_URI, (char*) cstr);
	(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	(*env)->DeleteLocalRef (env, jstr);

	// setting.aria2.token
	jstr = (*env)->GetObjectField (env, jAria2_object,
			(*env)->GetFieldID (env, jAria2_class, "token", "Ljava/lang/String;"));
	if (jstr == NULL)
		uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_TOKEN, (char*) NULL);
	else {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		// token can be NULL
		if (cstr == NULL || cstr[0] == 0)
			uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_TOKEN, (char*) NULL);
		else
			uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_TOKEN, (char*) cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// setting.aria2.speedDownload
	// setting.aria2.speedUpload
	speed[0] = (*env)->GetIntField (env, jAria2_object,
			(*env)->GetFieldID (env, jAria2_class, "speedDownload", "I"));
	speed[1] = (*env)->GetIntField (env, jAria2_object,
			(*env)->GetFieldID (env, jAria2_class, "speedUpload", "I"));
	speed[0] *= 1024;
	speed[1] *= 1024;
	uget_plugin_global_set(pinfo, UGET_PLUGIN_GLOBAL_SPEED_LIMIT, speed);

	// setting.aria2.local
	jbool = (*env)->GetBooleanField (env, jAria2_object,
			(*env)->GetFieldID (env, jAria2_class, "local", "Z"));
	if (jbool) {
		// setting.aria2.path
		jstr = (*env)->GetObjectField (env, jAria2_object,
				(*env)->GetFieldID (env, jAria2_class, "path", "Ljava/lang/String;"));
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr)
			uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_PATH, (char*) cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
		(*env)->DeleteLocalRef (env, jstr);

		// setting.aria2.arguments
		jstr = (*env)->GetObjectField (env, jAria2_object,
				(*env)->GetFieldID (env, jAria2_class, "arguments", "Ljava/lang/String;"));
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr)
			uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_ARGUMENT, (char*) cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
		(*env)->DeleteLocalRef (env, jstr);

		// setting.aria2.launch
		jbool = (*env)->GetBooleanField (env, jAria2_object,
				(*env)->GetFieldID (env, jAria2_class, "launch", "Z"));
		uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_LAUNCH, (void*)(uintptr_t) jbool);

		// setting.aria2.shutdown
		jbool = (*env)->GetBooleanField (env, jAria2_object,
				(*env)->GetFieldID (env, jAria2_class, "shutdown", "Z"));
		uget_plugin_global_set(pinfo, UGET_PLUGIN_ARIA2_GLOBAL_SHUTDOWN, (void*)(uintptr_t) jbool);
	}

	(*env)->DeleteLocalRef (env, jAria2_class);
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Core_getSiteId (JNIEnv* env, jobject thiz, jstring url)
{
    const char* cstr;
    jint  id;

    cstr = (*env)->GetStringUTFChars (env, url, NULL);
    id = uget_site_get_id (cstr);
    (*env)->ReleaseStringUTFChars (env, url, cstr);

    return id;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setMediaMatchMode (JNIEnv* env, jobject thiz, jint mode)
{
	uget_plugin_global_set(UgetPluginMediaInfo,
	                 UGET_PLUGIN_MEDIA_GLOBAL_MATCH_MODE, (void*)(intptr_t) mode);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setMediaQuality (JNIEnv* env, jobject thiz, jint quality)
{
	uget_plugin_global_set(UgetPluginMediaInfo,
	                 UGET_PLUGIN_MEDIA_GLOBAL_QUALITY, (void*)(intptr_t) quality);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Core_setMediaType (JNIEnv* env, jobject thiz, jint type)
{
	uget_plugin_global_set(UgetPluginMediaInfo,
	                 UGET_PLUGIN_MEDIA_GLOBAL_TYPE, (void*)(intptr_t) type);
}

