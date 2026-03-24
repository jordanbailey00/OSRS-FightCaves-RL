cmake_minimum_required(VERSION 3.20)
project(runescape_rl C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)
enable_testing()

# Build type defaults to Release
if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# Shared C backend (training-env owns the mechanics)
add_subdirectory(training-env)

# Raylib viewer shell (thin presentation layer over training-env backend)
add_subdirectory(demo-env)
