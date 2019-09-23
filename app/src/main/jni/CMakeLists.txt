cmake_minimum_required(VERSION 3.6.0)

set(PATH_TO_SUPERPOWERED "$ENV{HOME}/private/repos/SuperpoweredSDK/Superpowered/")
set(CACHE STRING "")

message(================= ${ANDROID_ABI})
message(================= "PATH_TO_SUPERPOWERED = ${PATH_TO_SUPERPOWERED}")

file(GLOB CPP_FILES "*.cpp")

add_library(
	SuperpoweredInterface
	SHARED
	${CPP_FILES}
	/Users/claus/private/repos/SuperpoweredSDK/Superpowered/AndroidIO/SuperpoweredAndroidAudioIO.cpp
)

include_directories(src/main/jni)
include_directories(/Users/claus/private/repos/SuperpoweredSDK/Superpowered/)

find_library( 
    # Sets the name of the path variable.
    log-lib

    # Specifies the name of the NDK library that
    # you want CMake to locate.
    log 
)
          
target_link_libraries(
    SuperpoweredInterface
    log
    android
    OpenSLES
	/Users/claus/private/repos/SuperpoweredSDK/Superpowered/libSuperpoweredAndroid${ANDROID_ABI}.a
)