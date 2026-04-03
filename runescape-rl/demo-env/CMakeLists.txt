# demo-env: Self-contained Fight Caves debug viewer.
# Contains both the authoritative backend (fc_*.c) and the Raylib viewer shell.
# No external dependency on training-env/fc_core.

# Backend sources (game logic — shared with headless training-env)
set(FC_BACKEND_SOURCES
    src/fc_state.c
    src/fc_rng.c
    src/fc_hash.c
    src/fc_debug.c
    src/fc_tick.c
    src/fc_combat.c
    src/fc_prayer.c
    src/fc_pathfinding.c
    src/fc_npc.c
    src/fc_wave.c
)

# Find Raylib from the pufferlib pre-built distribution
set(RAYLIB_ROOT "${CMAKE_SOURCE_DIR}/../pufferlib/raylib-5.5_linux_amd64" CACHE PATH "Raylib root")

if(EXISTS "${RAYLIB_ROOT}/include/raylib.h")
    add_executable(fc_viewer
        src/viewer.c
        ${FC_BACKEND_SOURCES}
    )

    target_include_directories(fc_viewer PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${RAYLIB_ROOT}/include
    )

    target_link_libraries(fc_viewer PRIVATE
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

    message(STATUS "Raylib found at ${RAYLIB_ROOT} — fc_viewer will be built (self-contained)")
else()
    message(STATUS "Raylib not found at ${RAYLIB_ROOT} — fc_viewer skipped (set RAYLIB_ROOT)")
endif()
