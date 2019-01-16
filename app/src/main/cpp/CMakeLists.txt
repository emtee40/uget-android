cmake_minimum_required(VERSION 3.4.1)

set(CURL_INCLUDE_DIR    "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include")
set(OPENSSL_INCLUDE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/include")
set(CMAKE_C_FLAGS       "${CMAKE_C_FLAGS} -DUSE__ANDROID__SAF")

add_subdirectory(uglib)
add_subdirectory(uget)

# -----------------------------------------------------------------------------
project(jni)

include_directories( ${CURL_INCLUDE_DIR}
                     ${OPENSSL_INCLUDE_DIR}
                     ${CMAKE_CURRENT_SOURCE_DIR}
                     ${CMAKE_CURRENT_SOURCE_DIR}/uget
                     ${CMAKE_CURRENT_SOURCE_DIR}/uglib
                   )

add_library( uget-jni
             SHARED

             Ccj.c
             Core.c
             Info.c
             Node.c
             Rpc.c
             Sequence.c
             Util.c
           )

# -----------------------------------------------------------------------------
# log
#find_library( # Sets the name of the path variable.
#              log-lib
#
#              # Specifies the name of the NDK library that
#              # you want CMake to locate.
#              log )

# -----------------------------------------------------------------------------
# zlib
find_library( # Sets the name of the path variable.
              zlib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              z )

# -----------------------------------------------------------------------------
# crypto - prebuilt
add_library( crypto
             STATIC
             IMPORTED )
set_target_properties( # Specifies the target library.
                       crypto

                       # Specifies the parameter you want to define.
                       PROPERTIES IMPORTED_LOCATION

                       # Provides the path to the library you want to import.
                       ${PROJECT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libcrypto.a )
# -----------------------------------------------------------------------------
# ssl - prebuilt
add_library( ssl
             STATIC
             IMPORTED )
set_target_properties( # Specifies the target library.
                       ssl

                       # Specifies the parameter you want to define.
                       PROPERTIES IMPORTED_LOCATION

                       # Provides the path to the library you want to import.
                       ${PROJECT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libssl.a )

# -----------------------------------------------------------------------------
# curl - prebuilt
add_library( libcurl
             STATIC
             IMPORTED )
set_target_properties( # Specifies the target library.
                       libcurl

                       # Specifies the parameter you want to define.
                       PROPERTIES IMPORTED_LOCATION

                       # Provides the path to the library you want to import.
                       ${PROJECT_SOURCE_DIR}/prebuilt/${ANDROID_ABI}/lib/libcurl.a )

# -----------------------------------------------------------------------------
target_link_libraries(uget-jni uget uglib libcurl ssl crypto ${zlib})
