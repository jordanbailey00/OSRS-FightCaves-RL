/**
 * fc_model_loader.h — Load Fight Caves NPC models from MDL2 binary.
 *
 * Adapted from PufferLib osrs_pvp_models.h. Stripped to the minimal
 * loader needed for FC NPC rendering (no item lookups, no animation).
 *
 * Binary format (MDL2):
 *   header: uint32 magic ("MDL2"), uint32 count, uint32 offsets[count]
 *   per model:
 *     uint32 model_id
 *     uint16 expanded_vert_count
 *     uint16 face_count
 *     uint16 base_vert_count
 *     float  expanded_verts[expanded_vert_count * 3]
 *     uint8  colors[expanded_vert_count * 4]
 *     ... (animation data — skipped for static rendering)
 */

#ifndef FC_MODEL_LOADER_H
#define FC_MODEL_LOADER_H

#include "raylib.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>

#define MDL2_MAGIC 0x4D444C32  /* "MDL2" */

typedef struct {
    uint32_t model_id;
    Mesh mesh;
    Model model;
} FcModel;

typedef struct {
    FcModel* models;
    int count;
} FcModelCache;

static FcModelCache* fc_model_cache_load(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) {
        fprintf(stderr, "fc_model_cache_load: cannot open %s\n", path);
        return NULL;
    }

    uint32_t magic, count;
    fread(&magic, 4, 1, f);
    fread(&count, 4, 1, f);

    if (magic != MDL2_MAGIC) {
        fprintf(stderr, "fc_model_cache_load: bad magic 0x%08X\n", magic);
        fclose(f);
        return NULL;
    }

    uint32_t* offsets = (uint32_t*)malloc(count * sizeof(uint32_t));
    fread(offsets, 4, count, f);

    FcModelCache* cache = (FcModelCache*)calloc(1, sizeof(FcModelCache));
    cache->models = (FcModel*)calloc(count, sizeof(FcModel));
    cache->count = (int)count;

    for (uint32_t i = 0; i < count; i++) {
        fseek(f, (long)offsets[i], SEEK_SET);

        uint32_t model_id;
        uint16_t vert_count, face_count, base_vert_count;
        fread(&model_id, 4, 1, f);
        fread(&vert_count, 2, 1, f);
        fread(&face_count, 2, 1, f);
        fread(&base_vert_count, 2, 1, f);

        cache->models[i].model_id = model_id;

        Mesh mesh = { 0 };
        mesh.vertexCount = vert_count;
        mesh.triangleCount = face_count;

        mesh.vertices = (float*)RL_MALLOC(vert_count * 3 * sizeof(float));
        mesh.colors = (unsigned char*)RL_MALLOC(vert_count * 4);

        fread(mesh.vertices, sizeof(float), vert_count * 3, f);
        fread(mesh.colors, 1, vert_count * 4, f);

        /* Skip animation data (base_verts, skins, face_indices, priorities) */
        /* We just need the expanded geometry for static rendering */

        UploadMesh(&mesh, false);
        cache->models[i].mesh = mesh;
        cache->models[i].model = LoadModelFromMesh(mesh);
    }

    free(offsets);
    fclose(f);

    fprintf(stderr, "fc_model_cache_load: loaded %d models from %s\n", cache->count, path);
    return cache;
}

static FcModel* fc_model_cache_get(FcModelCache* cache, uint32_t model_id) {
    if (!cache) return NULL;
    for (int i = 0; i < cache->count; i++) {
        if (cache->models[i].model_id == model_id) {
            return &cache->models[i];
        }
    }
    return NULL;
}

static void fc_model_cache_free(FcModelCache* cache) {
    if (!cache) return;
    for (int i = 0; i < cache->count; i++) {
        UnloadModel(cache->models[i].model);
    }
    free(cache->models);
    free(cache);
}

#endif /* FC_MODEL_LOADER_H */
