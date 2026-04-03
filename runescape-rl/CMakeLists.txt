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

# Demo environment (viewer + backend)
add_subdirectory(demo-env)

# Training environment (headless backend for RL)
add_subdirectory(training-env)
