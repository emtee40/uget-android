/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

 #include <jni.h>
#include <stddef.h>
#include <UgUri.h>
#include <UgUtil.h>
#include <UgString.h>

// ----------------------------------------------------------------------------
// Utility

JNIEXPORT jstring
Java_com_ugetdm_uget_lib_Util_stringFromIntUnit (JNIEnv* env, jclass libClass, jlong value, jint is_speed)
{
	char*   cstr;
	jstring jstr;

	if (is_speed)
		cstr = ug_str_from_int_unit (value, "/s");
	else
		cstr = ug_str_from_int_unit (value, NULL);

	jstr = (*env)->NewStringUTF (env, cstr);
	ug_free (cstr);
	return jstr;
}

JNIEXPORT jstring
Java_com_ugetdm_uget_lib_Util_stringFromSeconds (JNIEnv* env, jclass libClass, jint seconds, jint limit_99_99_99)
{
	char*   cstr;
	jstring jstr;

	cstr = ug_str_from_seconds (seconds, limit_99_99_99);
	jstr = (*env)->NewStringUTF (env, cstr);
	ug_free (cstr);
	return jstr;
}
