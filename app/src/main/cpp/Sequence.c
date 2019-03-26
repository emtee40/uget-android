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
Java_com_ugetdm_uget_lib_Sequence_getPreview (JNIEnv* env, jobject thiz, jstring jpattern)
{
	UgList         list;
	UgLink*        link;
	UgetSequence*  seq;
	jobjectArray   uriArray;
	const char*    pattern;
	int            index;
	union {
		jclass  seq;
		jclass  str;
	} jClass;

	jClass.seq = (*env)->GetObjectClass(env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID(env, jClass.seq, "pointer", "J"));
	(*env)->DeleteLocalRef(env, jClass.seq);

	ug_list_init (&list);

	pattern = (*env)->GetStringUTFChars(env, jpattern, NULL);
	uget_sequence_get_preview(seq, pattern, &list);
	(*env)->ReleaseStringUTFChars(env, jpattern, pattern);

	// String[];
	jClass.str = (*env)->FindClass(env, "java/lang/String");
	uriArray = (*env)->NewObjectArray(env, list.size, jClass.str, NULL);
	(*env)->DeleteLocalRef(env, jClass.str);
	for (index = 0, link = list.head;  link;  link = link->next, index++) {
		(*env)->SetObjectArrayElement(env, uriArray, index,
				(*env)->NewStringUTF(env, link->data));
	}
	uget_sequence_clear_result(&list);

	return uriArray;
}

// --------------------------------------------------------

struct BatchResult
{
	UgList  list;
	UgLink* cur;
};

JNIEXPORT jlong
Java_com_ugetdm_uget_lib_Sequence_startBatch(JNIEnv* env, jobject thiz, jstring jpattern)
{
	struct BatchResult* batchResult;
	UgetSequence*  seq;
	jclass         seqClass;
	const char*    pattern;

	seqClass = (*env)->GetObjectClass(env, thiz);
	seq = (UgetSequence*)(intptr_t) (*env)->GetLongField(env, thiz,
			(*env)->GetFieldID(env, seqClass, "pointer", "J"));
	(*env)->DeleteLocalRef(env, seqClass);

	batchResult = ug_malloc(sizeof(struct BatchResult));

	ug_list_init(&batchResult->list);

	pattern = (*env)->GetStringUTFChars(env, jpattern, NULL);
	uget_sequence_get_list(seq, pattern, &batchResult->list);
	(*env)->ReleaseStringUTFChars(env, jpattern, pattern);

	batchResult->cur = batchResult->list.head;
	return (intptr_t) batchResult;
}

JNIEXPORT jstring
Java_com_ugetdm_uget_lib_Sequence_getBatchUri(JNIEnv* env, jobject thiz, jlong result)
{
	struct BatchResult* batchResult;
	UgLink* next;
	jstring uri;

	batchResult = (struct BatchResult*) result;
	if (batchResult->cur == NULL)
		return NULL;
	uri = (*env)->NewStringUTF(env, batchResult->cur->data);

	next = batchResult->cur->next;
	ug_free(batchResult->cur);
	batchResult->cur = next;

	return uri;
}

JNIEXPORT void
Java_com_ugetdm_uget_lib_Sequence_endBatch(JNIEnv* env, jobject thiz, jlong result)
{
	struct BatchResult* batchResult;
	UgLink* next;

	batchResult = (struct BatchResult*) result;
	for (;  batchResult->cur;  batchResult->cur = next) {
		next = batchResult->cur->next;
		ug_free(batchResult->cur);
	}

	ug_free(batchResult);
}