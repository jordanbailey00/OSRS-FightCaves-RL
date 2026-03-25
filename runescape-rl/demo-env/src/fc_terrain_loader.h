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
#include <math.h>

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

    /* Generate flat normals for proper lighting */
    mesh.normals = (float*)RL_MALLOC(vert_count * 3 * sizeof(float));
    for (uint32_t i = 0; i < vert_count; i += 3) {
        /* Triangle vertices */
        float *v0 = &mesh.vertices[i*3], *v1 = &mesh.vertices[(i+1)*3], *v2 = &mesh.vertices[(i+2)*3];
        /* Edge vectors */
        float e1x = v1[0]-v0[0], e1y = v1[1]-v0[1], e1z = v1[2]-v0[2];
        float e2x = v2[0]-v0[0], e2y = v2[1]-v0[1], e2z = v2[2]-v0[2];
        /* Cross product */
        float nx = e1y*e2z - e1z*e2y;
        float ny = e1z*e2x - e1x*e2z;
        float nz = e1x*e2y - e1y*e2x;
        /* Normalize */
        float len = sqrtf(nx*nx + ny*ny + nz*nz);
        if (len > 0.0001f) { nx/=len; ny/=len; nz/=len; }
        else { nx=0; ny=1; nz=0; }
        /* Assign to all 3 verts of the triangle */
        for (int j = 0; j < 3; j++) {
            mesh.normals[(i+j)*3+0] = nx;
            mesh.normals[(i+j)*3+1] = ny;
            mesh.normals[(i+j)*3+2] = nz;
        }
    }

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
