/*
 *
 *   Copyright (C) 2015-2019 by C.H. Huang
 *   plushuang.tw@gmail.com
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  ---
 *
 *  In addition, as a special exception, the copyright holders give
 *  permission to link the code of portions of this program with the
 *  OpenSSL library under certain conditions as described in each
 *  individual source file, and distribute linked combinations
 *  including the two.
 *  You must obey the GNU Lesser General Public License in all respects
 *  for all of the code used other than OpenSSL.  If you modify
 *  file(s) with this exception, you may extend this exception to your
 *  version of the file(s), but you are not obligated to do so.  If you
 *  do not wish to do so, delete this exception statement from your
 *  version.  If you delete this exception statement from all source
 *  files in the program, then also delete it here.
 *
 */

#include <UgUri.h>
#include <UgHtml.h>
#include <UgJson.h>
#include <UgJson-custom.h>
#include <UgString.h>

#include <UgetCurl.h>
#include <UgetData.h>
#include <UgetMedia.h>

#ifdef HAVE_GLIB
#include <glib/gi18n.h>
#else
#define _(x)   x
#endif

// --------------------------------------------------------
// UgetMedia for YouTube
// Youtube:
// https://www.youtube.com/watch?v=xxxxxxxxxxx
// https://youtu.be/xxxxxxxxxxx

typedef struct UgetYouTube    UgetYouTube;

struct UgetYouTube
{
	UgUriQuery  query;
	char*       video_id;

	// common
	UgJson      json;

	// Method 1
	UgBuffer    buffer;
	char*       reason;     // error message
	char*       status;     // "OK", "UNPLAYABLE"
	char*       title;
	int         use_cipher; // TRUE of FALSE

	// method 2
	UgHtml      html;
	char*       js;         // JavaScript player URL
};

static UgetYouTube* uget_youtube_new(void)
{
	UgetYouTube* uyoutube;

	uyoutube = ug_malloc0(sizeof(UgetYouTube));

	ug_buffer_init(&uyoutube->buffer, 4096);
	uyoutube->reason = NULL;
	uyoutube->status = NULL;
	uyoutube->title  = NULL;

	ug_html_init(&uyoutube->html);
	ug_json_init(&uyoutube->json);
	uyoutube->js = NULL;
	return uyoutube;
}

static void  uget_youtube_free(UgetYouTube* uyoutube)
{
	ug_buffer_clear(&uyoutube->buffer, TRUE);
	ug_free(uyoutube->reason);
	ug_free(uyoutube->status);
	ug_free(uyoutube->title);

	ug_html_final(&uyoutube->html);
	ug_json_final(&uyoutube->json);
	ug_free(uyoutube->js);

	ug_free(uyoutube);
}

// ----------------------------------------------------------------------------
// "url_encoded_fmt_stream_map" parser

static void  uget_youtube_parse_map(UgetYouTube* uyoutube, UgetMedia* umedia, const char* field)
{
	UgetMediaItem*  umitem = NULL;
	char*  temp;

	while (ug_uri_query_part(&uyoutube->query, field)) {
		// debug
//		printf("    %.*s=%.*s\n",
//				uyoutube->query.field_len, field,
//				uyoutube->query.value_len, uyoutube->query.value);

		if (umitem == NULL)
			umitem = uget_media_item_new(umedia);

		if (strncmp("url", field, uyoutube->query.field_len) == 0) {
			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len,
			              uyoutube->query.value);
			umitem->url = ug_strdup(uyoutube->query.value);
		}
		else if (strncmp("s", field, uyoutube->query.field_len) == 0 ||
		         strncmp("sig", field, uyoutube->query.field_len) == 0 ||
		         strncmp("signature", field, uyoutube->query.field_len) == 0)
		{
			// signature.
			// If it exist, append "&signature=xxxx" to umitem->url
			umitem->data1.integer = uyoutube->query.field_len;
			umitem->data2.string = ug_strndup(uyoutube->query.value,
			                                  uyoutube->query.value_len);
			// TODO: decrypt signature
		}
		else if (strncmp("type", field, uyoutube->query.field_len) == 0) {
			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len,
			              uyoutube->query.value);
			if (strncmp("video/webm", uyoutube->query.value, 10) == 0)
				umitem->type = UGET_MEDIA_TYPE_WEBM;
			else if (strncmp("video/mp4", uyoutube->query.value, 9) == 0)
				umitem->type = UGET_MEDIA_TYPE_MP4;
			else if (strncmp("video/x-flv", uyoutube->query.value, 11) == 0)
				umitem->type = UGET_MEDIA_TYPE_FLV;
			else if (strncmp("video/3gpp", uyoutube->query.value, 10) == 0)
				umitem->type = UGET_MEDIA_TYPE_3GPP;
			else
				umitem->type = UGET_MEDIA_TYPE_UNKNOWN;
		}
		else if (strncmp(field, "quality", uyoutube->query.field_len) == 0) {
//			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len, uyoutube->query.value);
			if (strncmp("tiny", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_144P;
			else if (strncmp("small", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_240P;
			else if (strncmp("medium", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_360P;
			else if (strncmp("large", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_480P;
			else if (strncmp("hd720", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_720P;
			else if (strncmp("hd1080", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_1080P;
			else
				umitem->quality = UGET_MEDIA_QUALITY_UNKNOWN;
		}

		if (uyoutube->query.value_next) {
			// append "&signature=xxxx" to url
			if (umitem->data1.integer) {
				temp = ug_strdup_printf("%s" "&signature=%s",
						umitem->url, umitem->data2.string);
				ug_free(umitem->url);
				ug_free(umitem->data2.string);
				umitem->url = temp;
				umitem->data1.integer = 0;
				umitem->data2.string = NULL;
			}
			field = uyoutube->query.value_next;
			umitem = NULL;
		}
		else
			field = uyoutube->query.field_next;
	}
}

// ----------------------------------------------------------------------------
// "adaptive_fmts" parser

static void  uget_youtube_parse_adaptive_fmts(UgetYouTube* uyoutube, UgetMedia* umedia, const char* field)
{
	UgetMediaItem*  umitem = NULL;
	char*  temp;

	while (ug_uri_query_part(&uyoutube->query, field)) {
		// debug
//		printf("    %.*s=%.*s\n",
//				uyoutube->query.field_len, field,
//				uyoutube->query.value_len, uyoutube->query.value);

		if (umitem == NULL)
			umitem = uget_media_item_new(umedia);

		if (strncmp("url", field, uyoutube->query.field_len) == 0) {
			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len,
			              uyoutube->query.value);
			umitem->url = ug_strdup(uyoutube->query.value);
		}
		else if (strncmp("s", field, uyoutube->query.field_len) == 0 ||
		         strncmp("sig", field, uyoutube->query.field_len) == 0 ||
				 strncmp("signature", field, uyoutube->query.field_len) == 0)
		{
			// signature.
			// If it exist, append "&signature=xxxx" to umitem->url
			umitem->data1.integer = uyoutube->query.field_len;
			umitem->data2.string = ug_strndup(uyoutube->query.value,
			                                  uyoutube->query.value_len);
			// TODO: decrypt signature
		}
		else if (strncmp("type", field, uyoutube->query.field_len) == 0) {
			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len,
			              uyoutube->query.value);
			if (strncmp("video/webm", uyoutube->query.value, 10) == 0)
				umitem->type = UGET_MEDIA_VIDEO_WEBM;
			else if (strncmp("video/mp4", uyoutube->query.value, 9) == 0)
				umitem->type = UGET_MEDIA_VIDEO_MP4;
			else if (strncmp("video/x-flv", uyoutube->query.value, 11) == 0)
				umitem->type = UGET_MEDIA_VIDEO_FLV;
			else if (strncmp("video/3gpp", uyoutube->query.value, 10) == 0)
				umitem->type = UGET_MEDIA_VIDEO_3GPP;
			else if (strncmp("audio/mp4", uyoutube->query.value, 9) == 0)
				umitem->type = UGET_MEDIA_AUDIO_MP4;
			else if (strncmp("audio/webm", uyoutube->query.value, 10) == 0)
				umitem->type = UGET_MEDIA_AUDIO_WEBM;
			else
				umitem->type = UGET_MEDIA_TYPE_UNKNOWN;
		}
		else if (strncmp(field, "quality_label", uyoutube->query.field_len) == 0) {
//			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len, uyoutube->query.value);
			if (strncmp("144p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_144P;
			else if (strncmp("240p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_240P;
			else if (strncmp("360p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_360P;
			else if (strncmp("480p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_480P;
			else if (strncmp("720p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_720P;
			else if (strncmp("1080p", uyoutube->query.value, uyoutube->query.value_len) == 0)
				umitem->quality = UGET_MEDIA_QUALITY_1080P;
			else
				umitem->quality = UGET_MEDIA_QUALITY_UNKNOWN;
		}
		else if (strncmp(field, "audio_sample_rate", uyoutube->query.field_len) == 0) {
//			ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len, uyoutube->query.value);
			umitem->sample_rate = strtol(uyoutube->query.value, NULL, 10);
		}

		if (uyoutube->query.value_next) {
			// append "&signature=xxxx" to url
			if (umitem->data1.integer) {
				temp = ug_strdup_printf("%s" "&signature=%s",
						umitem->url, umitem->data2.string);
				ug_free(umitem->url);
				ug_free(umitem->data2.string);
				umitem->url = temp;
				umitem->data1.integer = 0;
				umitem->data2.string = NULL;
			}
			field = uyoutube->query.value_next;
			umitem = NULL;
		}
		else
			field = uyoutube->query.field_next;
	}
}

// ----------------------------------------------------------------------------
// "player_response" parser

// ----------------------------------------------
// "streamingData" JSON parser
static UgJsonError  ug_json_parse_mime_type(UgJson* json,
                                            const char* name, const char* value,
                                            void* umitem_ptr, void* data)
{
	UgetMediaItem* umitem = umitem_ptr;

	if (strncmp("audio/mp4", value, 9) == 0)
		umitem->type = UGET_MEDIA_AUDIO_MP4;
	else if (strncmp("audio/webm", value, 10) == 0)
		umitem->type = UGET_MEDIA_AUDIO_WEBM;
	else if (strncmp("video/mp4", value, 9) == 0) {
		umitem->type = UGET_MEDIA_TYPE_MP4;
		// if this media doesn't has 2 codec
		if (strpbrk(value+9, ",") == NULL)
			umitem->type |= UGET_MEDIA_TYPE_VIDEO;
	}
	else if (strncmp("video/webm", value, 10) == 0) {
		umitem->type = UGET_MEDIA_TYPE_WEBM;
		// if this media doesn't has 2 codec
		if (strpbrk(value+10, ",") == NULL)
			umitem->type |= UGET_MEDIA_TYPE_VIDEO;
	}
	else if (strncmp("video/x-flv", value, 11) == 0)
		umitem->type = UGET_MEDIA_TYPE_FLV;
	else if (strncmp("video/3gpp", value, 10) == 0)
		umitem->type = UGET_MEDIA_TYPE_3GPP;
	else
		umitem->type = UGET_MEDIA_TYPE_UNKNOWN;

	return UG_JSON_ERROR_NONE;
}

static UgJsonError  ug_json_parse_quality_label(UgJson* json,
                                                const char* name, const char* value,
                                                void* umitem_ptr, void* data)
{
	UgetMediaItem* umitem = umitem_ptr;

	if (strncmp("144p", value, 4) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_144P;
	else if (strncmp("240p", value, 4) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_240P;
	else if (strncmp("360p", value, 4) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_360P;
	else if (strncmp("480p", value, 4) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_480P;
	else if (strncmp("720p", value, 4) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_720P;
	else if (strncmp("1080p", value, 5) == 0)
		umitem->quality = UGET_MEDIA_QUALITY_1080P;

	return UG_JSON_ERROR_NONE;
}

static UgJsonError  ug_json_parse_cipher(UgJson* json,
                                         const char* name, const char* value,
                                         UgetMediaItem* umitem, void* data)
{
	UgUriQuery  uuquery;
	char* field = (char*)value;

	while (ug_uri_query_part(&uuquery, field)) {
		if (strncmp(field, "url", uuquery.field_len) == 0) {
			ug_free(umitem->url);
			umitem->url = ug_strndup(uuquery.value, uuquery.value_len);
		}
		else if (strncmp("s", field, uuquery.field_len) == 0 ||
		         strncmp("sig", field, uuquery.field_len) == 0 ||
				 strncmp("signature", field, uuquery.field_len) == 0)
		{
			// TODO: decrypt signature and append "&signature=xxxx" to umitem->url
		}

//		if (uuquery.value_next)
//			field = uuquery.value_next;
//		else
			field = uuquery.field_next;
	}

	return UG_JSON_ERROR_NONE;
}

static const UgEntry  media_item_entry[] =
{
	{"url",             offsetof(UgetMediaItem, url),
			UG_ENTRY_STRING, NULL, NULL},
	{"cipher",          0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_cipher, NULL},
	{"mimeType",        0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_mime_type, NULL},
	{"qualityLabel",    0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_quality_label, NULL},
	{"audioSampleRate", offsetof(UgetMediaItem, sample_rate),
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_int_string, NULL},
	{NULL}    // null-terminated
};

static UgJsonError  ug_json_parse_media_item(UgJson* json,
                                             const char* name, const char* value,
                                             void* umedia, void* data)
{
	UgetMediaItem* umitem;

	if (json->type != UG_JSON_OBJECT) {
//		if (json->type == UG_JSON_ARRAY)
//			ug_json_push (json, ug_json_parse_unknown, NULL, NULL);
		return UG_JSON_ERROR_TYPE_NOT_MATCH;
	}

	umitem = uget_media_item_new((UgetMedia*)umedia);
	ug_json_push(json, ug_json_parse_entry, umitem, (void*) media_item_entry);
	return UG_JSON_ERROR_NONE;
}

static UgJsonError  ug_json_parse_formats(UgJson* json,
                                          const char* name, const char* value,
                                          void* umedia, void* data)
{
	if (json->type != UG_JSON_ARRAY) {
//		if (json->type == UG_JSON_OBJECT)
//			ug_json_push (json, ug_json_parse_unknown, NULL, NULL);
		return UG_JSON_ERROR_TYPE_NOT_MATCH;
	}

	ug_json_push(json, ug_json_parse_media_item, umedia, NULL);
	return UG_JSON_ERROR_NONE;
}

static const UgEntry  streaming_data_entry[] =
{
	{"formats",          0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_formats, NULL},
	{"adaptiveFormats",  0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_formats, NULL},
	{NULL}    // null-terminated
};

// ----------------------------------------------
// "playabilityStatus" JSON parser
static const UgEntry  playability_status_entry[] =
{
	{"status",  offsetof(UgetYouTube, status),
			UG_ENTRY_STRING, NULL, NULL},
	{"reason",  offsetof(UgetYouTube, reason),
			UG_ENTRY_STRING, NULL, NULL},
	{NULL}    // null-terminated
};

static UgJsonError  ug_json_parse_playability_status(UgJson* json,
                                        const char* name, const char* value,
                                        void* umedia_ptr, void* data)
{
	UgetMedia*     umedia = umedia_ptr;
	UgetYouTube*   uyoutube = umedia->data;

	if (json->type != UG_JSON_OBJECT) {
//		if (json->type == UG_JSON_ARRAY)
//			ug_json_push (json, ug_json_parse_unknown, NULL, NULL);
		return UG_JSON_ERROR_TYPE_NOT_MATCH;
	}

	ug_json_push(json, ug_json_parse_entry, uyoutube, (void*) playability_status_entry);
	return UG_JSON_ERROR_NONE;
}

// ----------------------------------------------
// "videoDetails" parser JSON start
static const UgEntry  video_details_entry[] =
{
	{"title",      offsetof(UgetYouTube, title),
			UG_ENTRY_STRING, NULL, NULL},
	{"useCipher",  offsetof(UgetYouTube, use_cipher),
			UG_ENTRY_BOOL,   NULL, NULL},
	{NULL}    // null-terminated
};

static UgJsonError  ug_json_parse_video_details(UgJson* json,
                                                const char* name, const char* value,
                                                void* umedia_ptr, void* data)
{
	UgetMedia*     umedia = umedia_ptr;
	UgetYouTube*   uyoutube = umedia->data;

	if (json->type != UG_JSON_OBJECT) {
//		if (json->type == UG_JSON_ARRAY)
//			ug_json_push (json, ug_json_parse_unknown, NULL, NULL);
		return UG_JSON_ERROR_TYPE_NOT_MATCH;
	}

	ug_json_push(json, ug_json_parse_entry, uyoutube, (void*) video_details_entry);
	return UG_JSON_ERROR_NONE;
}

// ----------------------------------------------
// "player_response" JSON parser
static const UgEntry  player_response_entry[] =
{
	{"videoDetails",      0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_video_details, NULL},
	{"playabilityStatus", 0,
			UG_ENTRY_CUSTOM, (void*) ug_json_parse_playability_status, NULL},
	{"streamingData",     0,
			UG_ENTRY_OBJECT, (void*) streaming_data_entry, NULL},
	{NULL}    // null-terminated
};

// ----------------------------------------------------------------------------
// method 1
// https://www.youtube.com/get_video_info?video_id=xxxxxxxxxxx
// https://www.youtube.com/get_video_info?video_id=xxxxxxxxxxx&el=vevo&el=embedded&asv=3&sts=15902

static void  uget_youtube_parse_query(UgetYouTube* uyoutube, UgetMedia* umedia)
{
	if (ug_uri_query_part(&uyoutube->query, uyoutube->buffer.beg) == 0)
		return;

	// debug - print field
//	printf("%.*s\n", uyoutube->query.field_len, uyoutube->buffer.beg);
    // debug - print value
//	printf("%.*s\n", uyoutube->query.value_len, uyoutube->query.value);

	if (uyoutube->query.field_len == 26 &&
	    strncmp(uyoutube->buffer.beg, "url_encoded_fmt_stream_map", 26) == 0)
	{
		ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len, uyoutube->query.value);
		uget_youtube_parse_map(uyoutube, umedia, uyoutube->query.value);
	}
	else if (uyoutube->query.field_len == 13 &&
	    strncmp(uyoutube->buffer.beg, "adaptive_fmts", 13) == 0)
	{
		ug_decode_uri(uyoutube->query.value, uyoutube->query.value_len, uyoutube->query.value);
		uget_youtube_parse_adaptive_fmts(uyoutube, umedia, uyoutube->query.value);
	}
	/*
	// deprecated
	else if (uyoutube->query.field_len == 5 &&
	         strncmp(uyoutube->buffer.beg, "title", 5) == 0)
	{
		umedia->title = ug_strndup(uyoutube->query.value, uyoutube->query.value_len);
		ug_decode_uri(umedia->title, uyoutube->query.value_len, umedia->title);
	}
	*/
	else if (uyoutube->query.field_len == 6 &&
	         strncmp(uyoutube->buffer.beg, "reason", 6) == 0)
	{
		uyoutube->reason = ug_strndup(uyoutube->query.value, uyoutube->query.value_len);
		ug_decode_uri(uyoutube->reason, uyoutube->query.value_len, uyoutube->reason);
	}
	else if (uyoutube->query.field_len == 6 &&
	         strncmp(uyoutube->buffer.beg, "status", 6) == 0)
	{
		uyoutube->status = ug_strndup(uyoutube->query.value, uyoutube->query.value_len);
		ug_decode_uri(uyoutube->status, uyoutube->query.value_len, uyoutube->status);
	}
	/*
	// deprecated
	else if (uyoutube->query.field_len == 9 &&
	         strncmp(uyoutube->buffer.beg, "errorcode", 9) == 0)
	{
		uyoutube->error_code = strtol(uyoutube->query.value, NULL, 10);
	}
	*/
	else if (uyoutube->query.field_len == 15 &&
	         strncmp(uyoutube->buffer.beg, "player_response", 9) == 0)
	{
		UgJson* json;
		int     length;

		length = ug_decode_uri(uyoutube->query.value,
		                       uyoutube->query.value_len,
		                       uyoutube->query.value);
		json = &uyoutube->json;
		ug_json_begin_parse(json);
		ug_json_push(json, ug_json_parse_entry, umedia, (void*) player_response_entry);
		ug_json_push(json, ug_json_parse_object, NULL, NULL);
		ug_json_parse(json, uyoutube->query.value, length);
		ug_json_end_parse(json);
		// decide title
		if (umedia->title == NULL && ((UgetYouTube*)umedia->data)->title) {
			umedia->title = ((UgetYouTube*)umedia->data)->title;
			((UgetYouTube*)umedia->data)->title = NULL;
		}
	}
}

static size_t curl_output_youtube(char* beg, size_t size,
                                  size_t nmemb, void* data)
{
	UgetMedia*  umedia = data;
	UgBuffer*   buffer = &((UgetYouTube*)umedia->data)->buffer;
	char*  end;
	char*  cur;

	size *= nmemb;
	end = beg + size;

	for (cur = beg;  cur < end;  cur++) {
		if (cur[0] == '&') {
			ug_buffer_write_data(buffer, beg, cur - beg);
			ug_buffer_write_char(buffer, 0);
			uget_youtube_parse_query(umedia->data, umedia);
			// next field
			buffer->cur = buffer->beg;
			beg = cur + 1;  // + '&'
			continue;
		}
	}
	if (cur == end)
		ug_buffer_write_data(buffer, beg, cur - beg);

	return size;
}

int  uget_media_grab_youtube_method_1(UgetMedia* umedia, UgetProxy* proxy)
{
	CURL*         curl;
	CURLcode      code;
	UgetYouTube*  uyoutube;
	char*         string;
	int           retry = FALSE;

 	uyoutube = umedia->data;
	string = ug_strdup_printf(
			"https://www.youtube.com/get_video_info?video_id=%s&el=detailpage",
			uyoutube->video_id);

	curl = curl_easy_init();
	if (proxy)
		ug_curl_set_proxy(curl, proxy);

	curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, curl_output_youtube);
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, umedia);
	curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
	curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);

	do {
		curl_easy_setopt(curl, CURLOPT_URL, string);
		code = curl_easy_perform(curl);
		ug_free(string);
		string = NULL;

		switch (code) {
		case CURLE_OK:
			if (uyoutube->buffer.beg != uyoutube->buffer.cur) {
				ug_buffer_write_char(&uyoutube->buffer, 0);
				uget_youtube_parse_query(umedia->data, umedia);
			}
			if (uyoutube->reason != NULL) {
				umedia->event = uget_event_new_error(
						UGET_EVENT_ERROR_CUSTOM,
						uyoutube->reason);
				goto break_do_loop;
			}
			break;

		case CURLE_OUT_OF_MEMORY:
			umedia->event = uget_event_new_error(
					UGET_EVENT_ERROR_OUT_OF_RESOURCE, NULL);
			goto break_do_loop;

		default:
			umedia->event = uget_event_new_error(
					UGET_EVENT_ERROR_CUSTOM,
					_("Error occurred during getting video info."));
			goto break_do_loop;
		}

#if 0
		if (retry == TRUE) {
			retry = FALSE;
			// decrypt_signature
		}
		else if (uyoutube->status && strcmp(uyoutube->status, "ok") != 0) {
			if (uyoutube->reason && strstr(uyoutube->reason, "VEVO") == NULL)
				break;
			retry = TRUE;
			// reset data if we need retry
			ug_buffer_restart(&uyoutube->buffer);
			ug_free(uyoutube->reason);
			uyoutube->reason = NULL;
			ug_free(uyoutube->status);
			uyoutube->status = NULL;
			string = ug_strdup_printf(
					"https://www.youtube.com/get_video_info?video_id=%s&el=vevo&el=embedded&asv=3&sts=15902",
					uyoutube->video_id);
		}
#endif
	} while (retry == TRUE);

break_do_loop:
	curl_easy_cleanup(curl);
	return umedia->size;
}

// ----------------------------------------------------------------------------
// method 2
// get HTML and parse it

// ----------------------------------------------
// "ytplayer.config" JSON parser

static UgJsonError  ug_json_parse_assets_js(UgJson* json,
                                            const char* name,
                                            const char* value,
                                            void* umedia, void* data)
{
	UgetYouTube* uyoutube;

	uyoutube = ((UgetMedia*)umedia)->data;
	uyoutube->js = ug_strdup(value);

	return UG_JSON_ERROR_NONE;
}

static UgJsonError  ug_json_parse_args_map(UgJson* json,
                                           const char* name,
                                           const char* value,
                                           void* umedia, void* data)
{
	UgetYouTube* uyoutube;

	uyoutube = ((UgetMedia*)umedia)->data;
	uget_youtube_parse_map(uyoutube, (void*) umedia, value);

	return UG_JSON_ERROR_NONE;
}

static UgJsonError  ug_json_parse_args_adaptive_fmts(UgJson* json,
                                                     const char* name,
                                                     const char* value,
                                                     void* umedia, void* data)
{
	UgetYouTube* uyoutube;

	uyoutube = ((UgetMedia*)umedia)->data;
	uget_youtube_parse_adaptive_fmts(uyoutube, (void*) umedia, value);

	return UG_JSON_ERROR_NONE;
}

static const UgEntry  youtube_assets_entry[] =
{
	{"js",    0,
			UG_ENTRY_CUSTOM, ug_json_parse_assets_js, NULL},
	{NULL}    // null-terminated
};

static const UgEntry  youtube_args_entry[] =
{
	{"title",    offsetof(UgetMedia, title),
			UG_ENTRY_STRING, NULL, NULL},
	{"url_encoded_fmt_stream_map",  0,
			UG_ENTRY_CUSTOM, ug_json_parse_args_map, NULL},
	{"adaptive_fmts",  0,
			UG_ENTRY_CUSTOM, ug_json_parse_args_adaptive_fmts, NULL},
	{NULL}    // null-terminated
};

static const UgEntry  youtube_config_entry[] =
{
	{"assets",    0,
			UG_ENTRY_OBJECT, (void*) youtube_assets_entry, NULL},
	{"args",      0,
			UG_ENTRY_OBJECT, (void*) youtube_args_entry, NULL},
	{NULL}    // null-terminated
};

// ----------------------------------------------
// HTML parser

static const UgHtmlParser  youtube_html_parser;
static const UgHtmlParser  youtube_script_parser;

static void  youtube_start_element(UgHtml*        uhtml,
                                   const char*    element_name,
                                   const char**   attribute_names,
                                   const char**   attribute_values,
                                   void*          dest,
                                   void*          data)
{
	UgetMedia* umedia = dest;

	if (strcmp(element_name, "script") == 0)
		ug_html_push(uhtml, &youtube_script_parser, dest, data);
	else if (strcmp(element_name, "meta") == 0) {
		// <meta name="title" content="media title string">
		if (attribute_names[0]  && strcmp(attribute_names[0],  "name")==0 &&
		    attribute_values[0] && strcmp(attribute_values[0], "title")==0 &&
			attribute_names[1]  && strcmp(attribute_names[1],  "content")==0)
		{
			if (umedia->title == NULL)
				umedia->title = ug_strdup(attribute_values[1]);
		}
	}
}

static void  youtube_end_element(UgHtml*        uhtml,
                                 const char*    element_name,
                                 void*          dest,
                                 void*          data)
{
	if (strcmp(element_name, "script") == 0)
		ug_html_pop(uhtml);
}

static void  youtube_script_text(UgHtml*        uhtml,
                                 const char*    text,
                                 int            text_len,
                                 UgetMedia*     umedia,
                                 UgetYouTube*   uyoutube)
{
	UgJson* json;
	char*   cur;
	int     cur_len;
	int     diff;

//	if (strncmp("var ytplayer", text, 12) != 0)
//		return;

	for (cur = (char*)text, cur_len = text_len;  ;  ) {
		cur = memchr(cur, 'y', cur_len);
		if (cur == NULL)
			return;

		cur_len = text_len - (cur - text);
		if (cur_len < 15)   // strlen("ytplayer.config")
			return;
		diff = memcmp(cur, "ytplayer.config", 15);
		cur++;
		cur_len--;
		if (diff != 0)
			continue;

		cur = memchr(cur, '=', cur_len);
		if (cur == NULL)
			return;
		cur_len = text_len - (cur - text);
		cur = memchr(cur, '{', cur_len);
		if (cur == NULL)
			return;

		break;
	}

	json = &uyoutube->json;
	ug_json_begin_parse(json);
	ug_json_push(json, ug_json_parse_entry, umedia, (void*) youtube_config_entry);
	ug_json_push(json, ug_json_parse_object, NULL, NULL);
	ug_json_parse(json, cur, text_len - (cur - text));
	ug_json_end_parse(json);
}

static const UgHtmlParser  youtube_script_parser =
{
	NULL,
	(UgHtmlParserEndElementFunc) youtube_end_element,
	(UgHtmlParserTextFunc)       youtube_script_text
};

static const UgHtmlParser  youtube_html_parser =
{
	youtube_start_element,
	NULL,
	NULL
};

// ------------------------------------
// curl

static size_t curl_output_youtube_html(char* text, size_t size,
                                       size_t nmemb, void* data)
{
	UgetMedia*  umedia;
	UgHtml*     uhtml;

	umedia = data;
	uhtml = &((UgetYouTube*)umedia->data)->html;
	size *= nmemb;

	ug_html_parse(uhtml, text, size);
	return size;
}

int  uget_media_grab_youtube_method_2(UgetMedia* umedia, UgetProxy* proxy)
{
	CURL*        curl;
	CURLcode     code;
	UgetYouTube* uyoutube;
	char*        string;

	ug_free(umedia->title);
	umedia->title = NULL;
	uyoutube = umedia->data;

	// create URL string
	string = ug_strdup_printf("https://www.youtube.com/watch?v=%s",
	                          uyoutube->video_id);
	// setup option
	curl = curl_easy_init();
	if (proxy)
		ug_curl_set_proxy(curl, proxy);
	curl_easy_setopt(curl, CURLOPT_URL, string);
	curl_easy_setopt(curl, CURLOPT_NOSIGNAL, 1L);
	curl_easy_setopt(curl, CURLOPT_SSL_VERIFYHOST, 0L);
	curl_easy_setopt(curl, CURLOPT_SSL_VERIFYPEER, 0L);
	curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, curl_output_youtube_html);
	curl_easy_setopt(curl, CURLOPT_WRITEDATA, umedia);
	// run & parse
	ug_html_begin_parse(&uyoutube->html);
	ug_html_push(&uyoutube->html, &youtube_html_parser, umedia, uyoutube);
	code = curl_easy_perform(curl);
	ug_html_end_parse(&uyoutube->html);
	// free URL string
	ug_free(string);

	switch (code) {
	case CURLE_OK:
		// player URL
		if (uyoutube->js && strncmp(uyoutube->js, "//", 2) == 0) {
			string = ug_malloc(strlen(uyoutube->js) + 6 + 1); // "https:" + '\0'
			string[0] = 0;
			strcat(string, "https:");
			strcat(string, uyoutube->js);
			ug_free(uyoutube->js);
			uyoutube->js = string;
		}
		break;

	case CURLE_OUT_OF_MEMORY:
		umedia->event = uget_event_new_error(
				UGET_EVENT_ERROR_OUT_OF_RESOURCE, NULL);
		break;

	default:
		umedia->event = uget_event_new_error(
				UGET_EVENT_ERROR_CUSTOM,
				_("Error occurred during getting video web page."));
		break;
	}

	curl_easy_cleanup(curl);
	return umedia->size;
}

// ----------------------------------------------------------------------------
// UgetMedia functions

int  uget_media_is_youtube(UgUri* uuri)
{
	int         length;
	const char* string;

	// youtube.com
	// https://youtube.com/watch?=xxxxxxxxxxx
	// https://youtu.be/xxxxxxxxxxx

	length = ug_uri_host(uuri, &string);
	if (length >= 11 && strncmp(string + length - 11, "youtube.com", 11) == 0)
	{
		if (strncmp(uuri->uri + uuri->file , "watch?", 6) == 0)
			return TRUE;
	}
	else if (length >= 8 && strncmp(string + length - 8, "youtu.be", 8) == 0)
	{
		if (uuri->file != -1)
			return TRUE;
	}

	return FALSE;
}

int  uget_media_grab_youtube(UgetMedia* umedia, UgetProxy* proxy)
{
	int    n;
	char*  string;
	char*  video_id_str;
	int    video_id_len;
	UgUriQuery*   query;
	UgetYouTube*  uyoutube;

	ug_uri_init(&umedia->uuri, umedia->url);
	query = &umedia->uquery;
	video_id_str = NULL;
	video_id_len = 0;

	// get youtube video_id
	if (umedia->uuri.query != -1) {
		// https://www.youtube.com/watch?v=xxxxxxxxxxx
		string = umedia->url + umedia->uuri.query;
		while (ug_uri_query_part(query, string)) {
			if (strncmp("v", string, query->field_len) == 0 && query->value) {
				video_id_str = query->value;
				video_id_len = query->value_len;
				break;
			}
			string = query->field_next;
		}
	}
	else {
		// http://youtu.be/xxxxxxxxxxx
		video_id_len = ug_uri_file(&umedia->uuri, (const char**)&video_id_str);
	}

	if (video_id_str == NULL || video_id_len == 0) {
		umedia->event = uget_event_new_error(UGET_EVENT_ERROR_CUSTOM,
				_("No video_id found in URL of YouTube."));
		return 0;
	}

	uyoutube = uget_youtube_new();
	uyoutube->video_id = ug_strndup(video_id_str, video_id_len);
	umedia->data = uyoutube;

	n = uget_media_grab_youtube_method_1(umedia, proxy);
	if (n == 0 && uyoutube->reason != NULL) {
		// clear data before calling uget_media_grab_youtube_method_2()
		ug_free(umedia->title);
		umedia->title = NULL;
		if (umedia->event) {
			uget_event_free(umedia->event);
			umedia->event = NULL;
		}
		n = uget_media_grab_youtube_method_2(umedia, proxy);
	}

	uget_youtube_free(uyoutube);
	umedia->data = NULL;

	return n;
}
