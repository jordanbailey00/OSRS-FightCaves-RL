# demo-env: Raylib presentation shell over the shared fc_core backend.
# This does NOT own gameplay logic. It is a viewer/input/replay shell only.
# All mechanics calls go through fc_core from training-env.

# Find Raylib from the pufferlib pre-built distribution
set(RAYLIB_ROOT "${CMAKE_SOURCE_DIR}/../pufferlib/raylib-5.5_linux_amd64" CACHE PATH "Raylib root")

if(EXISTS "${RAYLIB_ROOT}/include/raylib.h")
    add_executable(fc_viewer src/viewer.c)

    target_include_directories(fc_viewer PRIVATE
        ${RAYLIB_ROOT}/include
    )

    target_link_libraries(fc_viewer PRIVATE
        fc_core
        ${RAYLIB_ROOT}/lib/libraylib.a
        m dl pthread
    )

    # Raylib on Linux needs X11/GL
    find_package(X11 QUIET)
    if(X11_FOUND)
        target_link_libraries(fc_viewer PRIVATE ${X11_LIBRARIES})
    endif()
    find_package(OpenGL QUIET)
    if(OpenGL_FOUND)
        target_link_libraries(fc_viewer PRIVATE OpenGL::GL)
    endif()

    message(STATUS "Raylib found at ${RAYLIB_ROOT} — fc_viewer will be built")
else()
    message(STATUS "Raylib not found at ${RAYLIB_ROOT} — fc_viewer skipped (set RAYLIB_ROOT)")
endif()
