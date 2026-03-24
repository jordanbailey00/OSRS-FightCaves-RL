# training-env: shared C backend for Fight Caves mechanics
# This is the single owner of all gameplay/simulation logic.
# Both headless training and the demo-env viewer link against this.

add_library(fc_core STATIC
    src/fc_state.c
    src/fc_rng.c
    src/fc_hash.c
    src/fc_debug.c
    src/fc_tick.c
    src/fc_combat.c
    src/fc_prayer.c
    src/fc_pathfinding.c
    src/fc_npc.c
)

target_include_directories(fc_core PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include
)

# Shared library for Python ctypes loading
add_library(fc_capi SHARED src/fc_capi.c)
target_link_libraries(fc_capi PRIVATE fc_core)
set_target_properties(fc_capi PROPERTIES
    POSITION_INDEPENDENT_CODE ON
    OUTPUT_NAME "fc_capi"
)
# fc_core must also be PIC for the shared lib
set_target_properties(fc_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

# Tests
enable_testing()
add_executable(test_pr1 tests/test_pr1.c)
target_link_libraries(test_pr1 PRIVATE fc_core)
add_test(NAME pr1_contracts_and_state COMMAND test_pr1)

add_executable(test_pr2 tests/test_pr2.c)
target_link_libraries(test_pr2 PRIVATE fc_core)
target_link_libraries(test_pr2 PRIVATE m)
add_test(NAME pr2_combat_slice COMMAND test_pr2)
