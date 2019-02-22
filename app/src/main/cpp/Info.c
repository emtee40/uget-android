/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <pthread.h>

#include <UgString.h>
#include <UgInfo.h>
#include <UgetData.h>

#include <android/log.h>

#define LOG_TAG    "com.ugetdm.uget.lib"
#define LOGV(...)  __android_log_print( ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)
#define LOGD(...)  __android_log_print( ANDROID_LOG_DEBUG,   LOG_TAG, __VA_ARGS__)
#define LOGI(...)  __android_log_print( ANDROID_LOG_INFO,    LOG_TAG, __VA_ARGS__)
#define LOGW(...)  __android_log_print( ANDROID_LOG_WARN,    LOG_TAG, __VA_ARGS__)
#define LOGE(...)  __android_log_print( ANDROID_LOG_ERROR,   LOG_TAG, __VA_ARGS__)

static jstring jstring_from_ug_string_array (JNIEnv* env, UgArrayStr* cArray);
static void    jstring_to_ug_string_array (JNIEnv* env, jstring jString, UgArrayStr* cArray);
//static void    jarray_to_ug_string_array (JNIEnv* env, jarray jArray, UgArrayStr* cArray);
//static jarray  jarray_from_ug_string_array (JNIEnv* env, jclass stringClass, UgArrayStr* cArray);

// JNI static functions

JNIEXPORT jlong
Java_com_ugetdm_uget_lib_Info_create (JNIEnv* env, jclass dataClass)
{
	return (jlong)(intptr_t) ug_info_new(8, 2);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_ref (JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*  info;

	info = (UgInfo*)(intptr_t) pointer;
	if (info)
		ug_info_ref(info);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_unref (JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*  info;

	info = (UgInfo*)(intptr_t) pointer;
	if (info)
		ug_info_unref(info);
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Info_refCount(JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*    info;

	info = (UgInfo*)(intptr_t) pointer;

	return info->ref_count;
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Info_getGroup (JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*       info;
    UgetRelation* relation;

	info = (UgInfo*)(intptr_t) pointer;
    relation = ug_info_get(info, UgetRelationInfo);
    if (relation)
        return relation->group;
	return 0;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_setGroup (JNIEnv* env, jclass dataClass, jlong pointer, jint group)
{
	UgInfo*       info;
    UgetRelation* relation;

	info = (UgInfo*)(intptr_t) pointer;
    relation = ug_info_realloc(info, UgetRelationInfo);
    relation->group = group;
}

JNIEXPORT jstring
Java_com_ugetdm_uget_lib_Info_getName(JNIEnv* env, jclass dataClass, jlong pointer)
{
	char*       name;
	UgInfo*     info;
	UgetCommon* common;
	jstring     result;

	info = (UgInfo*)(intptr_t) pointer;
	common = ug_info_get(info, UgetCommonInfo);

	if (common->name)
		result = (*env)->NewStringUTF (env, common->name);
	else if (common->file)
		result = (*env)->NewStringUTF (env, common->file);
	else if (common->uri)
		result = (*env)->NewStringUTF (env, common->uri);
	else
		result = (*env)->NewStringUTF (env, "unnamed");

	return result;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_setName(JNIEnv* env, jclass dataClass, jlong pointer, jstring name)
{
	UgInfo*     info;
	UgetCommon* common;
	const char* cstr;

	info = (UgInfo*)(intptr_t) pointer;

	if (name) {
		cstr = (*env)->GetStringUTFChars(env, name, NULL);
		if (cstr && cstr[0]) {
			common = ug_info_realloc(info, UgetCommonInfo);
			ug_free(common->name);
			common->name = ug_strdup(cstr);
		}
		(*env)->ReleaseStringUTFChars(env, name, cstr);
	}
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_setNameByUri(JNIEnv* env, jclass dataClass, jlong pointer, jstring uri)
{
    UgetCommon* common;
	UgInfo*     info;
	const char* cstr;

	info = (UgInfo*)(intptr_t) pointer;

	if (uri) {
		cstr = (*env)->GetStringUTFChars(env, uri, NULL);
		if (cstr && cstr[0]) {
			common = ug_info_realloc(info, UgetCommonInfo);
			ug_free(common->name);
            common->name = uget_name_from_uri_str(cstr);
        }
		(*env)->ReleaseStringUTFChars(env, uri, cstr);
	}
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Info_get__JLcom_ugetdm_uget_lib_Progress_2 (JNIEnv* env, jclass dataClass, jlong pointer, jobject progressObject)
{
	jclass        progressClass;
	UgInfo*       info;
	UgetCommon*   common;
	UgetProgress* progress;

	info = (UgInfo*)(intptr_t) pointer;

	progress = ug_info_get(info, UgetProgressInfo);
	if (progress) {
		progressClass = (*env)->GetObjectClass(env, progressObject);

		(*env)->SetIntField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "downloadSpeed", "I"),
				progress->download_speed);

		(*env)->SetIntField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "uploadSpeed", "I"),
				progress->upload_speed);

		(*env)->SetLongField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "complete", "J"),
				progress->complete);

		(*env)->SetLongField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "total", "J"),
				progress->total);

		(*env)->SetLongField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "consumeTime", "J"),
				progress->elapsed);

		(*env)->SetLongField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "remainTime", "J"),
				progress->left);

		(*env)->SetIntField (env, progressObject,
				(*env)->GetFieldID (env, progressClass, "percent", "I"),
				progress->percent);

		common = ug_info_get(info, UgetCommonInfo);
		if (common) {
			(*env)->SetIntField (env, progressObject,
					(*env)->GetFieldID (env, progressClass, "retryCount", "I"),
					common->retry_count);
		}

		(*env)->DeleteLocalRef(env, progressClass);
	}

	if (progress == NULL)
		return JNI_FALSE;
	else
		return JNI_TRUE;
}


// Java_com_ugetdm_uget_lib_Info_get__JLcom_ugetdm_uget_lib_DownloadProp_2
jboolean getDownloadProp(JNIEnv* env, jlong pointer, jobject dDataObject)
{
	jclass      dDataClass;
	UgInfo*     info;
	jboolean    has_data = JNI_FALSE;
	union {
		UgetCommon*   common;
		UgetProxy*    proxy;
		UgetHttp*     http;
		UgetRelation* relation;
	} temp;

	info = (UgInfo*)(intptr_t) pointer;

	dDataClass = (*env)->GetObjectClass (env, dDataObject);

	temp.common = ug_info_get(info, UgetCommonInfo);
	if (temp.common) {
		has_data = JNI_TRUE;
		if (temp.common->uri) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "uri", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->uri));
		}
		if (temp.common->mirrors) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "mirrors", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->mirrors));
		}
		if (temp.common->file) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "file", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->file));
		}
		if (temp.common->folder) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "folder", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->folder));
		}
		if (temp.common->user) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "user", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->user));
		}
		if (temp.common->password) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "password", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.common->password));
		}
		// connections
		(*env)->SetIntField (env, dDataObject,
				(*env)->GetFieldID (env, dDataClass, "connections", "I"),
				temp.common->max_connections);
		// retryLimit
//		(*env)->SetIntField (env, dDataObject,
//				(*env)->GetFieldID (env, dDataClass, "retryLimit", "I"),
//				common->retry_limit);
	}

	temp.proxy = ug_info_get(info, UgetProxyInfo);
	if (temp.proxy) {
		has_data = JNI_TRUE;
		if (temp.proxy->host) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "proxyHost", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.proxy->host));
		}
		if (temp.proxy->user) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "proxyUser", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.proxy->user));
		}
		if (temp.proxy->password) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "proxyPassword", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.proxy->password));
		}
		// port
		(*env)->SetIntField (env, dDataObject,
				(*env)->GetFieldID (env, dDataClass, "proxyPort", "I"),
				temp.proxy->port);
		// type
		(*env)->SetIntField (env, dDataObject,
				(*env)->GetFieldID (env, dDataClass, "proxyType", "I"),
				temp.proxy->type);
	}

	temp.http = ug_info_get(info, UgetHttpInfo);
	if (temp.http) {
		has_data = JNI_TRUE;
		if (temp.http->referrer) {
			(*env)->SetObjectField (env, dDataObject,
					(*env)->GetFieldID (env, dDataClass, "referrer", "Ljava/lang/String;"),
					(*env)->NewStringUTF (env, temp.http->referrer));
		}
	}

	temp.relation = ug_info_get(info, UgetRelationInfo);
	if (temp.relation) {
		has_data = JNI_TRUE;
		(*env)->SetIntField (env, dDataObject,
								(*env)->GetFieldID (env, dDataClass, "group", "I"),
								temp.relation->group);
	}
	else {
		// Because old file doesn't save this field, I set a default value for old one. (version 1.x)
		(*env)->SetIntField (env, dDataObject,
							 (*env)->GetFieldID (env, dDataClass, "group", "I"),
							 UGET_GROUP_QUEUING);
	}

	(*env)->DeleteLocalRef(env, dDataClass);
	return has_data;
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Info_get__JLcom_ugetdm_uget_lib_DownloadProp_2 (JNIEnv* env, jclass dataClass, jlong pointer, jobject dDataObject)
{
	jboolean has_data;

	has_data = getDownloadProp (env, pointer, dDataObject);
	return has_data;
}


// Java_com_ugetdm_uget_lib_Info_setData__JLcom_ugetdm_uget_lib_DownloadProp_2
static void setDownloadProp (JNIEnv* env, jlong pointer, jobject dDataObject)
{
	jclass      dDataClass;
	jstring     jstr;
	UgInfo*     info;
	const char* cstr;
	int         value;
	union {
		UgetCommon*   common;
		UgetProxy*    proxy;
		UgetHttp*     http;
		UgetRelation* relation;
	} temp;

	info = (UgInfo*)(intptr_t) pointer;

	dDataClass = (*env)->GetObjectClass(env, dDataObject);

	temp.common = ug_info_realloc(info, UgetCommonInfo);
	// uri
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "uri", "Ljava/lang/String;"));
	free (temp.common->uri);
	temp.common->uri = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->uri = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// mirrors
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "mirrors", "Ljava/lang/String;"));
	free (temp.common->mirrors);
	temp.common->mirrors = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->mirrors = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// file
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "file", "Ljava/lang/String;"));
	free (temp.common->file);
	temp.common->file = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->file = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// folder
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "folder", "Ljava/lang/String;"));
	free (temp.common->folder);
	temp.common->folder = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->folder = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// user
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "user", "Ljava/lang/String;"));
	free (temp.common->user);
	temp.common->user = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->user = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// password
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "password", "Ljava/lang/String;"));
	free (temp.common->password);
	temp.common->password = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.common->password = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// connections
	temp.common->max_connections = (*env)->GetIntField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "connections", "I"));
	// retryLimit
//	temp.common->retry_limit = (*env)->GetIntField (env, dDataObject,
//			(*env)->GetFieldID (env, dDataClass, "retryLimit", "I"));

	temp.proxy = ug_info_realloc(info, UgetProxyInfo);
	// proxyHost
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "proxyHost", "Ljava/lang/String;"));
	free (temp.proxy->host);
	temp.proxy->host = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.proxy->host = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// proxyUser
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "proxyUser", "Ljava/lang/String;"));
	free (temp.proxy->user);
	temp.proxy->user = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.proxy->user = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// proxyPassword
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "proxyPassword", "Ljava/lang/String;"));
	free (temp.proxy->password);
	temp.proxy->password = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.proxy->password = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// proxyPort
	value = (*env)->GetIntField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "proxyPort", "I"));
	if (value)
		temp.proxy->port = value;

	// proxyType
	value = (*env)->GetIntField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "proxyType", "I"));
	temp.proxy->type = value;

	temp.http = ug_info_realloc(info, UgetHttpInfo);
	// referrer
	jstr = (*env)->GetObjectField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "referrer", "Ljava/lang/String;"));
	free (temp.http->referrer);
	temp.http->referrer = NULL;
	if (jstr) {
		cstr = (*env)->GetStringUTFChars (env, jstr, NULL);
		if (cstr && cstr[0])
			temp.http->referrer = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jstr, cstr);
	}
	(*env)->DeleteLocalRef (env, jstr);

	// group
	temp.relation = ug_info_realloc(info, UgetRelationInfo);
	temp.relation->group = (*env)->GetIntField (env, dDataObject,
			(*env)->GetFieldID (env, dDataClass, "group", "I"));

	(*env)->DeleteLocalRef (env, dDataClass);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_set__JLcom_ugetdm_uget_lib_DownloadProp_2 (JNIEnv* env, jclass dataClass, jlong pointer, jobject dDataObject)
{
	setDownloadProp (env, pointer, dDataObject);
}

JNIEXPORT jboolean
Java_com_ugetdm_uget_lib_Info_get__JLcom_ugetdm_uget_lib_CategoryProp_2 (JNIEnv* env, jclass dataClass, jlong pointer, jobject cDataObject)
{
	jclass        cDataClass;
//	jarray        jArray;
	jstring       jString;
	UgInfo*       info;
    UgetCommon*   common;
	UgetCategory* category;
	jboolean      has_data = JNI_FALSE;

	has_data = getDownloadProp (env, pointer, cDataObject);

	info = (UgInfo*)(intptr_t) pointer;

	cDataClass = (*env)->GetObjectClass (env, cDataObject);

	category = ug_info_get(info, UgetCategoryInfo);
	if (category) {
		has_data = JNI_TRUE;

        common = ug_info_get(info, UgetCommonInfo);
		if (common && common->name)
			jString = (*env)->NewStringUTF(env, common->name);
		else
			jString = NULL;
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "name", "Ljava/lang/String;"),
				jString);

		(*env)->SetIntField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "activeLimit", "I"),
				category->active_limit);
		(*env)->SetIntField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "finishedLimit", "I"),
				category->finished_limit);
		(*env)->SetIntField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "recycledLimit", "I"),
				category->recycled_limit);

		jString = jstring_from_ug_string_array (env, &category->hosts);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "hosts", "Ljava/lang/String;"),
				jString);

		jString = jstring_from_ug_string_array (env, &category->schemes);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "schemes", "Ljava/lang/String;"),
				jString);

		jString = jstring_from_ug_string_array (env, &category->file_exts);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "fileTypes", "Ljava/lang/String;"),
				jString);

/*
		jArray = jarray_from_ug_string_array (env,
				stringClass, &category->hosts);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "hosts", "[Ljava/lang/String;"),
				jArray);

		jArray = jarray_from_ug_string_array (env,
				stringClass, &category->schemes);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "schemes", "[Ljava/lang/String;"),
				jArray);

		jArray = jarray_from_ug_string_array (env,
				stringClass, &category->file_exts);
		(*env)->SetObjectField (env, cDataObject,
				(*env)->GetFieldID (env, cDataClass, "fileTypes", "[Ljava/lang/String;"),
				jArray);
 */
	}

	(*env)->DeleteLocalRef (env, cDataClass);

	return has_data;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_set__JLcom_ugetdm_uget_lib_CategoryProp_2 (JNIEnv* env, jclass dataClass, jlong pointer, jobject cDataObject)
{
	jclass        stringClass;
	jclass        cDataClass;
//	jarray        jArray;
	jstring       jString;
	UgInfo*       info;
    UgetCommon*   common;
	UgetCategory* category;

	setDownloadProp(env, pointer, cDataObject);

	info = (UgInfo*)(intptr_t) pointer;

	cDataClass = (*env)->GetObjectClass (env, cDataObject);

	jString = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "name", "Ljava/lang/String;"));
    common = ug_info_realloc(info, UgetCommonInfo);
	free (common->name);
    common->name = NULL;
	if (jString) {
		const char* cstr = (*env)->GetStringUTFChars (env, jString, NULL);
		if (cstr && cstr[0])
            common->name = ug_strdup (cstr);
		(*env)->ReleaseStringUTFChars (env, jString, cstr);
	}
	(*env)->DeleteLocalRef (env, jString);

	category = ug_info_realloc (info, UgetCategoryInfo);
	category->active_limit = (*env)->GetIntField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "activeLimit", "I"));
	category->finished_limit = (*env)->GetIntField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "finishedLimit", "I"));
	category->recycled_limit = (*env)->GetIntField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "recycledLimit", "I"));

	jString = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "hosts", "Ljava/lang/String;"));
	ug_array_foreach_ptr(&category->hosts, (UgForeachFunc) ug_free, NULL);
	category->hosts.length = 0;
	jstring_to_ug_string_array (env, jString, &category->hosts);
	(*env)->DeleteLocalRef (env, jString);

	jString = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "schemes", "Ljava/lang/String;"));
	ug_array_foreach_ptr(&category->schemes, (UgForeachFunc) ug_free, NULL);
	category->schemes.length = 0;
	jstring_to_ug_string_array (env, jString, &category->schemes);
	(*env)->DeleteLocalRef (env, jString);

	jString = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "fileTypes", "Ljava/lang/String;"));
	ug_array_foreach_ptr(&category->file_exts, (UgForeachFunc) ug_free, NULL);
	category->file_exts.length = 0;
	jstring_to_ug_string_array (env, jString, &category->file_exts);
	(*env)->DeleteLocalRef (env, jString);

/*
	jArray = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "hosts", "[Ljava/lang/String;"));
	jarray_to_ug_string_array (env, jArray, &category->hosts);

	jArray = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "schemes", "[Ljava/lang/String;"));
	jarray_to_ug_string_array (env, jArray, &category->schemes);

	jArray = (*env)->GetObjectField (env, cDataObject,
			(*env)->GetFieldID (env, cDataClass, "fileTypes", "[Ljava/lang/String;"));
	jarray_to_ug_string_array (env, jArray, &category->file_exts);
 */

	(*env)->DeleteLocalRef (env, cDataClass);
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Info_getPriority (JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*       info;
	UgetRelation* relation;
	jint          result;

	info = (UgInfo*)(intptr_t) pointer;
	relation = ug_info_get(info, UgetRelationInfo);
	if (relation)
		result = relation->priority;
	else
		result = UGET_PRIORITY_NORMAL;

	return result;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Info_setPriority (JNIEnv* env, jclass dataClass, jlong pointer, jint priority)
{
	UgInfo*       info;
	UgetRelation* relation;

	info = (UgInfo*)(intptr_t) pointer;
	relation = ug_info_realloc(info, UgetRelationInfo);
	relation->priority = priority;
}

JNIEXPORT jstring
Java_com_ugetdm_uget_lib_Info_getMessage (JNIEnv* env, jclass dataClass, jlong pointer)
{
	UgInfo*    info;
	UgetLog*   log;
	UgetEvent* event;
	jstring    result;

	info = (UgInfo*)(intptr_t) pointer;
	log = ug_info_get(info, UgetLogInfo);
	if (log == NULL || log->messages.size == 0)
		result = NULL;
	else {
		event = (UgetEvent*) log->messages.head;
		if (event->string)
			result = (*env)->NewStringUTF (env, event->string);
		else
			result = NULL;
	}

	return result;
}

// ----------------------------------------------------------------------------
// static functions

static jstring jstring_from_ug_string_array (JNIEnv* env, UgArrayStr* cArray)
{
	int   index, stringLen;
	char* buffer;
	jstring jstr;

	for (stringLen = 0, index = 0;  index < cArray->length;  index++)
		stringLen += strlen(cArray->at[index]) + 1;  // + ';'

	buffer = malloc (stringLen + 1);
	buffer[0] = 0;

	for (index = 0;  index < cArray->length;  index++) {
		strcat (buffer, cArray->at[index]);
		strcat (buffer, ";");
	}

	jstr = (*env)->NewStringUTF (env, buffer);
	free (buffer);
	return jstr;
}

static void jstring_to_ug_string_array (JNIEnv* env, jstring jString, UgArrayStr* cArray)
{
	const char*  pCur;
	const char*  pPrev;
	const char*  cString;

	cString = (*env)->GetStringUTFChars (env, jString, NULL);
	for (pPrev = cString, pCur = cString; ;  pCur++) {
		if ((pCur[0] == ';' || pCur[0] == 0) && pCur != pPrev) {
			*(char**)ug_array_alloc (cArray, 1) = strndup (pPrev, pCur - pPrev);
			pPrev = pCur + 1;
		}
		if (pCur[0] == 0)
			break;
	}

	(*env)->ReleaseStringUTFChars (env, jString, cString);
}
/*
static jarray jarray_from_ug_string_array (JNIEnv* env, jclass stringClass, UgArrayStr* cArray)
{
	jarray  jArray;
	int     index;

	jArray = (*env)->NewObjectArray (env, cArray->length, stringClass, NULL);
	for (index = 0;  index < cArray->length;  index++) {
		(*env)->SetObjectArrayElement (env, jArray,
				index, (*env)->NewStringUTF (env, cArray->at[index]));
	}
	return jArray;
}

static void jarray_to_ug_string_array (JNIEnv* env, jarray jArray, UgArrayStr* cArray)
{
	jobject     jString;
	const char* cString;
	int         index;

	for (index = 0;  index < cArray->length;  index++) {
		free (cArray->at[index]);
		cArray->at[index] = NULL;
	}

	cArray->length = (*env)->GetArrayLength (env, jArray);
	for (index = 0;  index < cArray->length;  index++) {
		jString = (*env)->GetObjectArrayElement (env, jArray, index);
		cString = (*env)->GetStringUTFChars (env, jString, NULL);
		cArray->at[index] = ug_strdup (cString);
		(*env)->ReleaseStringUTFChars (env, jString, cString);
		(*env)->DeleteLocalRef (env, jString);
	}
}
*/
