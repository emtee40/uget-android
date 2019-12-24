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

// YouTube support
#ifndef UGET_MEDIA_H
#define UGET_MEDIA_H

#include <UgList.h>
#include <UgetData.h>
#include <UgetSite.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct UgetMedia         UgetMedia;
typedef struct UgetMediaItem     UgetMediaItem;

typedef enum UgetMediaMatchMode
{
	UGET_MEDIA_MATCH_0 = 0,
	UGET_MEDIA_MATCH_1 = 1,
	UGET_MEDIA_MATCH_2,
	UGET_MEDIA_MATCH_NEAR,   // near quality

	UGET_MEDIA_N_MATCH_MODE,
} UgetMediaMatchMode;

// Video Quality
typedef enum UgetMediaQuality
{
	UGET_MEDIA_QUALITY_UNKNOWN = 0,

	UGET_MEDIA_QUALITY_144P,    // YouTube tiny
	UGET_MEDIA_QUALITY_240P,    // YouTube small
	UGET_MEDIA_QUALITY_360P,    // YouTube medium
	UGET_MEDIA_QUALITY_480P,    // YouTube large
	UGET_MEDIA_QUALITY_720P,    // YouTube hd720
	UGET_MEDIA_QUALITY_1080P,   // YouTube hd1080

	UGET_MEDIA_N_QUALITY,
} UgetMediaQuality;

typedef enum UgetMediaType
{
	UGET_MEDIA_TYPE_UNKNOWN = 0,

	UGET_MEDIA_TYPE_VIDEO = 0x1000,
	UGET_MEDIA_TYPE_AUDIO = 0x2000,

	// used by uget_media_match()
	UGET_MEDIA_TYPE_DEMUX = 0xF000,  // include demuxed audio or video
	UGET_MEDIA_TYPE_MUX   = 0x0FFF,  // include files that mux audio and video

	// video/mp4; codecs="avc1.42E01E, mp4a.40.2"
	// video/webm; codecs="vp8.0, vorbis"
	UGET_MEDIA_TYPE_MP4   = 0x0001,
	UGET_MEDIA_TYPE_WEBM  = 0x0002,
	UGET_MEDIA_TYPE_3GPP  = 0x0003,
	UGET_MEDIA_TYPE_FLV   = 0x0004,

	UGET_MEDIA_N_TYPE,

	// --- Video only ---
	// video/mp4; codecs="avc1.42E01E"
	UGET_MEDIA_VIDEO_MP4  = UGET_MEDIA_TYPE_MP4  | UGET_MEDIA_TYPE_VIDEO,
	// video/webm; codecs="vp8.0"
	UGET_MEDIA_VIDEO_WEBM = UGET_MEDIA_TYPE_WEBM | UGET_MEDIA_TYPE_VIDEO,
	UGET_MEDIA_VIDEO_3GPP = UGET_MEDIA_TYPE_3GPP | UGET_MEDIA_TYPE_VIDEO,
	UGET_MEDIA_VIDEO_FLV  = UGET_MEDIA_TYPE_FLV  | UGET_MEDIA_TYPE_VIDEO,

	// --- Audio only ---
	// audio/mp4; codecs="mp4a.40.2"
	UGET_MEDIA_AUDIO_MP4  = UGET_MEDIA_TYPE_MP4  | UGET_MEDIA_TYPE_AUDIO,
	// audio/webm; codecs="opus"
	UGET_MEDIA_AUDIO_WEBM = UGET_MEDIA_TYPE_WEBM | UGET_MEDIA_TYPE_AUDIO,
} UgetMediaType;


struct UgetMedia
{
	UG_LIST_MEMBERS(UgetMediaItem);
//	uintptr_t        size;
//	UgetMediaItem*   head;
//	UgetMediaItem*   tail;

	UgUri       uuri;
	UgUriQuery  uquery;

	int    site_id;
	char*  url;
	char*  title;

	// error message
	UgetEvent*  event;

	// for internal use only
	void*  data;
	void*  data1;
	void*  data2;
	void*  data3;
	void*  data4;
};

UgetMedia*  uget_media_new(const char* url, UgetSiteId site_id);
void        uget_media_free(UgetMedia* umedia);
void        uget_media_clear(UgetMedia* umedia, int free_items);

int         uget_media_grab_items(UgetMedia* umedia, UgetProxy* proxy);

// return begin of matched items. Don't free it
UgetMediaItem*  uget_media_match(UgetMedia*          umedia,
                                 UgetMediaMatchMode  mode,
                                 UgetMediaQuality    quality,
                                 UgetMediaType       type);

// ----------------------------------------------------------------------------
// UgetMediaItem

struct UgetMediaItem
{
	UG_LINK_MEMBERS(UgetMediaItem, UgetMediaItem, self);
//	UgetMediaItem* self;
//	UgetMediaItem* next;
//	UgetMediaItem* prev;

	char* url;
	int   quality;     // video - 480p, 720p
	int   sample_rate; // audio
	int   type;        // UgetMediaType

	// for internal use only
	union {
		int   integer;
		char* string;
		void* pointer;
	} data;

	union {
		int   integer;
		char* string;
		void* pointer;
	} data1;

	union {
		int   integer;
		char* string;
		void* pointer;
	} data2;
};

UgetMediaItem*  uget_media_item_new(UgetMedia* umedia);
void            uget_media_item_free(UgetMediaItem* umitem);

#ifdef __cplusplus
}
#endif  // __cplusplus

#endif  // End of UGET_MEDIA_H

