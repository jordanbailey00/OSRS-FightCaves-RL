/**
 * fc_terrain_loader.h — Load Fight Caves terrain from TERR binary.
 *
 * Loads vertex-colored terrain mesh exported by export_fc_terrain.py.
 * Format: TERR magic, vertex count, region count, min coords,
 *         float vertices[count*3], uint8 colors[count*4].
 */

#ifndef FC_TERRAIN_LOADER_H
#define FC_TERRAIN_LOADER_H

#include "raylib.h"
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>

#define TERR_MAGIC 0x54455252  /* "TERR" */

typedef struct {
    Mesh mesh;
    Model model;
    int loaded;
} FcTerrain;

static FcTerrain fc_terrain_load(const char* path) {
    FcTerrain t = { 0 };

    FILE* f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "fc_terrain_load: cannot open %s\n", path);
        return t;
    }

    uint32_t magic, vert_count, region_count;
    int32_t min_x, min_y;
    fread(&magic, 4, 1, f);
    fread(&vert_count, 4, 1, f);
    fread(&region_count, 4, 1, f);
    fread(&min_x, 4, 1, f);
    fread(&min_y, 4, 1, f);

    if (magic != TERR_MAGIC) {
        fprintf(stderr, "fc_terrain_load: bad magic 0x%08X\n", magic);
        fclose(f);
        return t;
    }

    Mesh mesh = { 0 };
    mesh.vertexCount = (int)vert_count;
    mesh.triangleCount = (int)vert_count / 3;

    mesh.vertices = (float*)RL_MALLOC(vert_count * 3 * sizeof(float));
    mesh.colors = (unsigned char*)RL_MALLOC(vert_count * 4);

    fread(mesh.vertices, sizeof(float), vert_count * 3, f);
    fread(mesh.colors, 1, vert_count * 4, f);
    fclose(f);

    UploadMesh(&mesh, false);
    t.mesh = mesh;
    t.model = LoadModelFromMesh(mesh);
    t.loaded = 1;

    fprintf(stderr, "fc_terrain_load: %d verts, %d tris from %s\n",
            mesh.vertexCount, mesh.triangleCount, path);
    return t;
}

static void fc_terrain_free(FcTerrain* t) {
    if (t->loaded) {
        UnloadModel(t->model);
        t->loaded = 0;
    }
}

#endif /* FC_TERRAIN_LOADER_H */
