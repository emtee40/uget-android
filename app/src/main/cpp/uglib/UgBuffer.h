/*
 *
 *   Copyright (C) 2012-2019 by C.H. Huang
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

#ifndef UG_BUFFER_H
#define UG_BUFFER_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ----------------------------------------------------------------------------
// UgBuffer functions

typedef struct UgBuffer       UgBuffer;
typedef int  (*UgBufferFunc) (UgBuffer* buffer);

void  ug_buffer_init_external(UgBuffer* buffer, char* exbuf, int length);
void  ug_buffer_init(UgBuffer* buffer, int length);

// If you use ug_buffer_init_external() to init UgBuffer,
// you may not free, resize, or allocate buffer.
void  ug_buffer_clear(UgBuffer* buffer, int free_buffer);
void  ug_buffer_set_size(UgBuffer* buffer, int length);
char* ug_buffer_alloc(UgBuffer* buffer, int length);

// UgBuffer.more() default function for external buffer.
int   ug_buffer_restart(UgBuffer* buffer);
// UgBuffer.more() default function for internal buffer.
int   ug_buffer_expand(UgBuffer* buffer);

void  ug_buffer_fill(UgBuffer* buffer, char ch, int count);

// return number of bytes written
int   ug_buffer_write(UgBuffer* buffer, const char* string, int length);
void  ug_buffer_write_data(UgBuffer* buffer, const char* binary, int length);

#define ug_buffer_length(buffer)    (int)((buffer)->cur - (buffer)->beg)
#define ug_buffer_allocated(buffer) (int)((buffer)->end - (buffer)->beg)
#define ug_buffer_remain(buffer)    (int)((buffer)->end - (buffer)->cur)

#ifdef __cplusplus
}
#endif

// This definition is used by UgBuffer::write(char ch)
#ifdef __cplusplus
inline void  ug_buffer_write_char(UgBuffer* buffer, char ch);
#endif

// ----------------------------------------------------------------------------
// UgBuffer structure

struct UgBuffer
{
	char*  beg;
	char*  cur;
	char*  end;

	// for writing: expand or flush buffer
	// for reading: expand or fill buffer
	// return > 0 if ok
	// return < 0 if error.
	// return = 0 if no more data (read)
	UgBufferFunc  more;  // flush/expand (write) or fill/expand (read)
	void*         data;  // extra data for UgBuffer.more()

#ifdef __cplusplus
	// C++11 standard-layout
	inline void  init(int length)
		{ ug_buffer_init(this, length); }
	inline void  init(char* exbuf, int length)
		{ ug_buffer_init_external(this, exbuf, length); }

	// If you use ug_buffer_init_external() to init UgBuffer,
	// you may not free, resize, or allocate buffer.
	inline void  clear(bool free_buffer)
		{ ug_buffer_clear(this, free_buffer); }
	inline void  setSize(int length)
		{ ug_buffer_set_size(this, length); }
	inline char* alloc(int length)
		{ return ug_buffer_alloc(this, length); }

	inline void  fill(char ch, int count)
		{ ug_buffer_fill(this, ch, count); }

	// return number of bytes written
	inline int   write(const char* string, int length = -1)
		{ return ug_buffer_write(this, string, length); }
	inline void  write(char ch)
		{ ug_buffer_write_char(this, ch); }
	inline void  writeData(const char* binary, int length)
		{ ug_buffer_write_data(this, binary, length); }
#endif  // __cplusplus
};

// ----------------------------------------------------------------------------
// C/C++ inline function

#if (defined(__STDC_VERSION__) && (__STDC_VERSION__ >= 199901L)) || defined(__cplusplus)
// C99 or C++ inline function

#ifdef __cplusplus  // C++
inline
#else               // C99
static inline
#endif
void  ug_buffer_write_char(UgBuffer* buffer, char ch)
{
	if ((buffer)->cur >= (buffer)->end)
		(buffer)->more(buffer);
	*(buffer)->cur++ = (char)(ch);
}

#else
// C function
void  ug_buffer_write_char(UgBuffer* buffer, char ch);

#endif  // __STDC_VERSION__ || __cplusplus

// ----------------------------------------------------------------------------
// C++11 standard-layout

#ifdef __cplusplus

namespace Ug
{
// This one is for directly use only. You can NOT derived it.
typedef struct UgBuffer    Buffer;
};  // namespace Ug

#endif  // __cplusplus


#endif  // UG_BUFFER_H

