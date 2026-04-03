# training-env: Headless Fight Caves backend for RL training.
# Builds libfc_capi.so — loaded by Python via ctypes for PufferLib integration.
# No Raylib, no rendering, no debug overlays. Pure game simulation.

set(FC_TRAINING_SOURCES
    src/fc_state.c
    src/fc_rng.c
    src/fc_tick.c
    src/fc_combat.c
    src/fc_prayer.c
    src/fc_pathfinding.c
    src/fc_npc.c
    src/fc_wave.c
    src/fc_capi.c
)

# Shared library for Python ctypes loading
add_library(fc_capi SHARED ${FC_TRAINING_SOURCES})
target_include_directories(fc_capi PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_compile_definitions(fc_capi PRIVATE FC_NO_HASH)
target_link_libraries(fc_capi PRIVATE m)

# Set output name to libfc_capi.so (no "lib" prefix duplication)
set_target_properties(fc_capi PROPERTIES
    PREFIX "lib"
    OUTPUT_NAME "fc_capi"
)
