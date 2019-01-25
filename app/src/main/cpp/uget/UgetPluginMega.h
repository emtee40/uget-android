/*
 *
 *   Copyright (C) 2016-2019 by C.H. Huang
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

#ifndef UGET_PLUGIN_MEGA_H
#define UGET_PLUGIN_MEGA_H

#include <UgJson.h>
#include <UgValue.h>
#include <UgetData.h>
#include <UgetPluginAgent.h>


#ifdef __cplusplus
extern "C" {
#endif

typedef struct UgetPluginMega     UgetPluginMega;

extern  const  UgetPluginInfo*    UgetPluginMegaInfo;

/* ----------------------------------------------------------------------------
   UgetPluginMega: It derived from UgetPluginAgent.
                   It use libcurl to get download URL.
                   It use curl/aria2 plug-in to download file.

   UgType
   |
   `--- UgetPlugin
        |
        `--- UgetPluginAgent
             |
             `--- UgetPluginMega
 */

struct UgetPluginMega
{
	UGET_PLUGIN_AGENT_MEMBERS;
/*	// ------ UgType members ------
	const UgetPluginInfo*  info;

	// ------ UgetPlugin members ------
	UgetEvent*    messages;
	UgMutex       mutex;
	int           ref_count;

	// ------ UgetPluginAgent members ------
	// This plug-in use other plug-in to download files,
	// so we need extra UgetPlugin and UgInfo.

	// plugin->target_info is a copy of UgInfo that store in UgetApp
	UgInfo*       target_info;
	// target_plugin use target_info to download
	UgetPlugin*   target_plugin;

	// speed limit control
	// limit[0] = download speed limit
	// limit[1] = upload speed limit
	int           limit[2];
	uint8_t       limit_changed:1;  // speed limit changed by user or program

	// control flags
	uint8_t       paused:1;         // paused by user or program
	uint8_t       stopped:1;        // all downloading thread are stopped
 */

	uint8_t       named:1;          // change UgetCommon::name
	uint8_t       synced:1;         // used by plugin_sync()
	uint8_t       decrypting:1;     // decrypting downloaded file

	// These UgData store in target_info
	UgetFiles*    target_files;
	UgetProxy*    target_proxy;
	UgetCommon*   target_common;
	UgetProgress* target_progress;

	// MEGA URL contains these
	char*  id;     // ID
	char*  key;    // decrypt key
	char*  iv;     // Initialization Vector

	// use MEGA ID to request these information
	char*  url;    // file download URL
	char*  file;   // file name

	// JSON parser
	UgJson   json;
	UgValue  value;
};

#ifdef __cplusplus
}
#endif

// ----------------------------------------------------------------------------
// C++11 standard-layout

#ifdef __cplusplus

namespace Uget
{

const PluginInfo* const PluginMegaInfo = (const PluginInfo*) UgetPluginMegaInfo;

// This one is for derived use only. No data members here.
// Your derived struct/class must be C++11 standard-layout
struct PluginMegaMethod : PluginAgentMethod {};

// This one is for directly use only. You can NOT derived it.
struct PluginMega : PluginMegaMethod, UgetPluginMega
{
	inline void* operator new(size_t size)
		{ return uget_plugin_new(PluginMegaInfo); }
};


};  // namespace Uget

#endif  // __cplusplus


#endif  // End of UGET_PLUGIN_MEGA_H
