set(FC_CORE_SOURCES
    src/fc_state.c
    src/fc_rng.c
    src/fc_tick.c
    src/fc_combat.c
    src/fc_prayer.c
    src/fc_pathfinding.c
    src/fc_npc.c
    src/fc_wave.c
)

add_library(fc_core STATIC ${FC_CORE_SOURCES})
target_include_directories(fc_core PUBLIC ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_link_libraries(fc_core PUBLIC m)
