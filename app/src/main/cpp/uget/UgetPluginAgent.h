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

#ifndef UGET_PLUGIN_AGENT_H
#define UGET_PLUGIN_AGENT_H

#include <UgetPlugin.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct	UgetPluginAgent          UgetPluginAgent;

typedef enum {
	UGET_PLUGIN_AGENT_GLOBAL = UGET_PLUGIN_GLOBAL_DERIVED,    // begin

	UGET_PLUGIN_AGENT_GLOBAL_PLUGIN,   // set parameter = (UgetPluginInfo*)

	UGET_PLUGIN_AGENT_GLOBAL_DERIVED,
} UgetPluginAgentGlobalCode;

/* ----------------------------------------------------------------------------
   UgetPluginAgent: It derived from UgetPlugin.
                    It use other(curl/aria2) plug-in to download file.

   UgType
   |
   `--- UgetPlugin
        |
        `--- UgetPluginAgent
 */

#define UGET_PLUGIN_AGENT_MEMBERS  \
	UGET_PLUGIN_MEMBERS;           \
	UgInfo*       target_info;     \
	UgetPlugin*   target_plugin;   \
	int           limit[2];        \
	uint8_t       limit_changed:1; \
	uint8_t       paused:1;        \
	uint8_t       stopped:1

struct UgetPluginAgent
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
};


#ifdef __cplusplus
}
#endif

// global functions -------------------
UgetResult  uget_plugin_agent_global_init (void);
void        uget_plugin_agent_global_ref (void);
void        uget_plugin_agent_global_unref (void);

UgetResult  uget_plugin_agent_global_set (int option, void* parameter);
UgetResult  uget_plugin_agent_global_get (int option, void* parameter);

// instance functions -----------------
void  uget_plugin_agent_init  (UgetPluginAgent* plugin);
void  uget_plugin_agent_final (UgetPluginAgent* plugin);

int   uget_plugin_agent_ctrl (UgetPluginAgent* plugin, int code, void* data);
int   uget_plugin_agent_ctrl_speed (UgetPluginAgent* plugin, int* speed);

// sync functions ---------------------
// sync common data (include speed limit) between 'common' and 'target'
// if parameter 'target' is NULL, it get/alloc 'target' from plugin->target_info
void  uget_plugin_agent_sync_common (UgetPluginAgent* plugin,
                                     UgetCommon* common,
                                     UgetCommon* target);

// sync progress data from 'target' to 'progress'
// if parameter 'target' is NULL, it get/alloc 'target' from plugin->target_info
void  uget_plugin_agent_sync_progress (UgetPluginAgent* plugin,
                                       UgetProgress* progress,
                                       UgetProgress* target);

// thread functions -------------------
int   uget_plugin_agent_start (UgetPluginAgent* plugin,
                               UgThreadFunc thread_func);

// ----------------------------------------------------------------------------
// C++11 standard-layout

#ifdef __cplusplus

namespace Uget
{

// This one is for derived use only. No data members here.
// Your derived struct/class must be C++11 standard-layout
struct PluginAgentMethod : Uget::PluginMethod
{
	inline void syncCommon(UgetCommon* common, UgetCommon* target)
		{ uget_plugin_agent_sync_common((UgetPluginAgent*)this, common, target); }
	inline void syncProgress(UgetProgress* progress, UgetProgress* target)
		{ uget_plugin_agent_sync_progress((UgetPluginAgent*)this, progress, target); }
};

// This one is for directly use only. You can NOT derived it.
struct PluginAgent : Uget::PluginAgentMethod, UgetPluginAgent {};

};  // namespace Uget

#endif  // __cplusplus


#endif  // End of UGET_PLUGIN_AGENT_H

