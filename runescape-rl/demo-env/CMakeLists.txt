# demo-env: Fight Caves debug viewer.
# Links against the shared fc-core backend and keeps viewer/test utilities local.

set(FC_VIEWER_SOURCES
    src/viewer.c
    src/fc_hash.c
    src/fc_debug.c
)

set(RAYLIB_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/raylib" CACHE PATH "Raylib root")

if(EXISTS "${RAYLIB_ROOT}/include/raylib.h")
    add_executable(fc_viewer ${FC_VIEWER_SOURCES})

    target_include_directories(fc_viewer PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/src
        ${PROJECT_SOURCE_DIR}/fc-core/include
        ${RAYLIB_ROOT}/include
    )

    target_link_libraries(fc_viewer PRIVATE
        fc_core
        ${RAYLIB_ROOT}/lib/libraylib.a
        m dl pthread
    )

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

add_executable(test_headless
    tests/test_headless.c
    src/fc_hash.c
)
target_include_directories(test_headless PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${PROJECT_SOURCE_DIR}/fc-core/include
)
target_link_libraries(test_headless PRIVATE fc_core m)
