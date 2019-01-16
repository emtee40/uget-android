/*
 *
 *   Copyright (C) 2018-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 */

#include <jni.h>

#include <UgetSequence.h>

JNIEXPORT void
Java_com_ugetdm_uget_lib_Sequence_cInit (JNIEnv* env, jobject thiz)
{
	UgetSequence*  seq;
	jclass         seqClass;

	seq = ug_malloc (sizeof (UgetSequence));
	uget_sequence_init (seq);

	seqClass = (*env)->GetObjectClass (env, thiz);
	(*env)->SetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"),
			(jlong)(intptr_t) seq);
	(*env)->DeleteLocalRef (env, seqClass);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Sequence_cFinal (JNIEnv* env, jobject thiz)
{
	UgetSequence*  seq;
	jclass         seqClass;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	uget_sequence_final (seq);
	ug_free (seq);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Sequence_add (JNIEnv* env, jobject thiz, jint beg, jint end, jint digits)
{
	UgetSequence*  seq;
	jclass         seqClass;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	uget_sequence_add (seq, (uint32_t) beg, (uint32_t) end, digits);
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Sequence_clear (JNIEnv* env, jobject thiz)
{
	UgetSequence*  seq;
	jclass         seqClass;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	uget_sequence_clear (seq);
}

JNIEXPORT jint
Java_com_ugetdm_uget_lib_Sequence_count (JNIEnv* env, jobject thiz, jstring jpattern)
{
	UgetSequence*  seq;
	jclass         seqClass;
	const char*    pattern;
	int            count;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	pattern = (*env)->GetStringUTFChars (env, jpattern, NULL);
	count = uget_sequence_count (seq, pattern);
	(*env)->ReleaseStringUTFChars (env, jpattern, pattern);

	return count;
}

JNIEXPORT jobjectArray
Java_com_ugetdm_uget_lib_Sequence_getList (JNIEnv* env, jobject thiz, jstring jpattern)
{
	UgList         list;
	UgLink*        link;
	UgetSequence*  seq;
	jclass         seqClass;
	jobjectArray   uriArray;
	const char*    pattern;
	int            index;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	ug_list_init (&list);

	pattern = (*env)->GetStringUTFChars (env, jpattern, NULL);
	uget_sequence_get_list (seq, pattern, &list);
	(*env)->ReleaseStringUTFChars (env, jpattern, pattern);

	// String  uris[];
	uriArray = (*env)->NewObjectArray (env, list.size,
			(*env)->FindClass(env, "java/lang/String"),
			(*env)->NewStringUTF(env, ""));
	for (index = 0, link = list.head;  link;  link = link->next, index++) {
		(*env)->SetObjectArrayElement (env, uriArray, index,
				(*env)->NewStringUTF (env, link->data));
	}
	ug_list_clear (&list, TRUE);

	return uriArray;
}

JNIEXPORT jobjectArray
Java_com_ugetdm_uget_lib_Sequence_getPreview (JNIEnv* env, jobject thiz, jstring jpattern)
{
	UgList         list;
	UgLink*        link;
	UgetSequence*  seq;
	jclass         seqClass;
	jobjectArray   uriArray;
	const char*    pattern;
	int            index;

	seqClass = (*env)->GetObjectClass (env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID (env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef (env, seqClass);

	ug_list_init (&list);

	pattern = (*env)->GetStringUTFChars (env, jpattern, NULL);
	uget_sequence_get_preview (seq, pattern, &list);
	(*env)->ReleaseStringUTFChars (env, jpattern, pattern);

	// String[];
	uriArray = (*env)->NewObjectArray (env, list.size,
			(*env)->FindClass(env, "java/lang/String"),
			(*env)->NewStringUTF(env, ""));
	for (index = 0, link = list.head;  link;  link = link->next, index++) {
		(*env)->SetObjectArrayElement (env, uriArray, index,
				(*env)->NewStringUTF (env, link->data));
	}
	ug_list_clear (&list, TRUE);

	return uriArray;
}


