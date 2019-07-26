cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wall -Werror -Wno-unused-function")

add_library(yuv2rgb SHARED
        yuv2rgb.c)

# Include libraries needed for plasma lib
target_link_libraries(yuv2rgb
                      android
                      jnigraphics
                      log
                      m)
