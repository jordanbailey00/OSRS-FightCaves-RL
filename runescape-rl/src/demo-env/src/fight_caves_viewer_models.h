#ifndef FIGHT_CAVES_VIEWER_MODELS_H
#define FIGHT_CAVES_VIEWER_MODELS_H

#include "raylib.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define FC_VIEWER_MDL2_MAGIC 0x4D444C32u

typedef struct fc_viewer_model_entry {
    uint32_t model_id;
    Mesh mesh;
    Model model;
} fc_viewer_model_entry;

typedef struct fc_viewer_model_bundle {
    fc_viewer_model_entry* entries;
    int count;
} fc_viewer_model_bundle;

static fc_viewer_model_bundle* fc_viewer_model_bundle_load(const char* path) {
    FILE* file = fopen(path, "rb");
    uint32_t magic = 0;
    uint32_t count = 0;
    uint32_t* offsets = NULL;
    fc_viewer_model_bundle* bundle = NULL;

    if (file == NULL) {
        return NULL;
    }
    if (
        fread(&magic, 4, 1, file) != 1 ||
        fread(&count, 4, 1, file) != 1 ||
        magic != FC_VIEWER_MDL2_MAGIC
    ) {
        fclose(file);
        return NULL;
    }

    offsets = (uint32_t*)malloc(count * sizeof(uint32_t));
    bundle = (fc_viewer_model_bundle*)calloc(1, sizeof(fc_viewer_model_bundle));
    if (offsets == NULL || bundle == NULL) {
        free(offsets);
        free(bundle);
        fclose(file);
        return NULL;
    }
    if (fread(offsets, 4, count, file) != count) {
        free(offsets);
        free(bundle);
        fclose(file);
        return NULL;
    }

    bundle->entries = (fc_viewer_model_entry*)calloc(count, sizeof(fc_viewer_model_entry));
    bundle->count = (int)count;
    if (bundle->entries == NULL) {
        free(offsets);
        free(bundle);
        fclose(file);
        return NULL;
    }

    for (uint32_t index = 0; index < count; ++index) {
        uint32_t model_id = 0;
        uint16_t expanded_vertex_count = 0;
        uint16_t face_count = 0;
        uint16_t base_vertex_count = 0;
        Mesh mesh = {0};
        fc_viewer_model_entry* entry = &bundle->entries[index];
        size_t vertex_float_count;
        size_t color_byte_count;

        fseek(file, (long)offsets[index], SEEK_SET);
        if (
            fread(&model_id, 4, 1, file) != 1 ||
            fread(&expanded_vertex_count, 2, 1, file) != 1 ||
            fread(&face_count, 2, 1, file) != 1 ||
            fread(&base_vertex_count, 2, 1, file) != 1
        ) {
            continue;
        }
        vertex_float_count = (size_t)expanded_vertex_count * 3u;
        color_byte_count = (size_t)expanded_vertex_count * 4u;
        mesh.vertexCount = (int)expanded_vertex_count;
        mesh.triangleCount = (int)face_count;
        mesh.vertices = (float*)RL_MALLOC(vertex_float_count * sizeof(float));
        mesh.colors = (unsigned char*)RL_MALLOC(color_byte_count);
        if (mesh.vertices == NULL || mesh.colors == NULL) {
            if (mesh.vertices != NULL) {
                RL_FREE(mesh.vertices);
            }
            if (mesh.colors != NULL) {
                RL_FREE(mesh.colors);
            }
            continue;
        }
        if (
            fread(mesh.vertices, sizeof(float), vertex_float_count, file) != vertex_float_count ||
            fread(mesh.colors, 1, color_byte_count, file) != color_byte_count
        ) {
            RL_FREE(mesh.vertices);
            RL_FREE(mesh.colors);
            continue;
        }
        if (base_vertex_count > 0) {
            fseek(
                file,
                (long)(base_vertex_count * 3u * sizeof(int16_t) + base_vertex_count + face_count * 3u * sizeof(uint16_t) + face_count),
                SEEK_CUR
            );
        }
        UploadMesh(&mesh, false);
        entry->model_id = model_id;
        entry->mesh = mesh;
        entry->model = LoadModelFromMesh(mesh);
    }

    free(offsets);
    fclose(file);
    return bundle;
}

static fc_viewer_model_entry* fc_viewer_model_bundle_get(
    const fc_viewer_model_bundle* bundle,
    uint32_t model_id
) {
    if (bundle == NULL) {
        return NULL;
    }
    for (int index = 0; index < bundle->count; ++index) {
        if (bundle->entries[index].model_id == model_id) {
            return &bundle->entries[index];
        }
    }
    return NULL;
}

static void fc_viewer_model_bundle_free(fc_viewer_model_bundle* bundle) {
    if (bundle == NULL) {
        return;
    }
    for (int index = 0; index < bundle->count; ++index) {
        if (bundle->entries[index].model.meshCount > 0) {
            UnloadModel(bundle->entries[index].model);
        }
    }
    free(bundle->entries);
    free(bundle);
}

#endif
