/*
 * fc_npc_models.h — Load NPC models from .models MDL2 binary for Raylib rendering.
 *
 * Binary format (from export_models.py write_models_binary):
 *   Header: uint32 magic (0x4D444C32 "MDL2"), uint32 model_count, uint32 offsets[model_count]
 *   Per model:
 *     uint32 model_id
 *     uint16 expanded_vert_count (face_count * 3)
 *     uint16 face_count
 *     uint16 base_vert_count
 *     float  expanded_verts[expanded_vert_count * 3]  (x,y,z)
 *     uint8  colors[expanded_vert_count * 4]           (r,g,b,a)
 *     ... (base verts, skins, face indices, priorities — not needed for rendering)
 */

#ifndef FC_NPC_MODELS_H
#define FC_NPC_MODELS_H

#include "raylib.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

#define FC_NPC_MODEL_MAX 16

/* b237 NPC IDs for FC monsters (from export_fc_npcs.py) */
#define FC_B237_TZ_KIH      3116
#define FC_B237_TZ_KEK       3118
#define FC_B237_TZ_KEK_SM    3120
#define FC_B237_TOK_XIL      3121
#define FC_B237_YT_MEJKOT    3123
#define FC_B237_KET_ZEK      3125
#define FC_B237_TZTOK_JAD    3127
#define FC_B237_YT_HURKOT    3128

typedef struct {
    uint32_t model_id;
    Model model;
    int loaded;
} NpcModelEntry;

typedef struct {
    NpcModelEntry entries[FC_NPC_MODEL_MAX];
    int count;
    int loaded;
} NpcModelSet;

/* Map internal NPC type enum (1-8) to b237 model_id for lookup */
static uint32_t fc_npc_type_to_model_id(int npc_type) {
    switch (npc_type) {
        case 1: return FC_B237_TZ_KIH;
        case 2: return FC_B237_TZ_KEK;
        case 3: return FC_B237_TZ_KEK_SM;
        case 4: return FC_B237_TOK_XIL;
        case 5: return FC_B237_YT_MEJKOT;
        case 6: return FC_B237_KET_ZEK;
        case 7: return FC_B237_TZTOK_JAD;
        case 8: return FC_B237_YT_HURKOT;
        default: return 0;
    }
}

/* Find a loaded model by model_id. Returns NULL if not found. */
static NpcModelEntry* fc_npc_model_find(NpcModelSet* set, uint32_t model_id) {
    for (int i = 0; i < set->count; i++) {
        if (set->entries[i].model_id == model_id && set->entries[i].loaded) {
            return &set->entries[i];
        }
    }
    return NULL;
}

/* Load all NPC models from a .models MDL2 binary file. */
static NpcModelSet* fc_npc_models_load(const char* path) {
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;

    uint32_t magic, model_count;
    fread(&magic, 4, 1, f);
    if (magic != 0x4D444C32) { /* "MDL2" */
        fprintf(stderr, "fc_npc_models: bad magic 0x%08x (expected MDL2)\n", magic);
        fclose(f);
        return NULL;
    }
    fread(&model_count, 4, 1, f);
    if (model_count > FC_NPC_MODEL_MAX) model_count = FC_NPC_MODEL_MAX;

    /* Read offset table (skip — we read sequentially) */
    uint32_t* offsets = (uint32_t*)malloc(model_count * 4);
    fread(offsets, 4, model_count, f);

    NpcModelSet* set = (NpcModelSet*)calloc(1, sizeof(NpcModelSet));
    set->count = (int)model_count;

    for (uint32_t m = 0; m < model_count; m++) {
        fseek(f, offsets[m], SEEK_SET);

        uint32_t model_id;
        uint16_t expanded_vc, face_count, base_vc;
        fread(&model_id, 4, 1, f);
        fread(&expanded_vc, 2, 1, f);
        fread(&face_count, 2, 1, f);
        fread(&base_vc, 2, 1, f);

        int vc = (int)expanded_vc;
        int tc = (int)face_count;

        /* Read expanded vertices (float xyz) */
        float* verts = (float*)malloc(vc * 3 * sizeof(float));
        fread(verts, sizeof(float), vc * 3, f);

        /* Read colors (RGBA per expanded vertex) */
        unsigned char* colors = (unsigned char*)malloc(vc * 4);
        fread(colors, 1, vc * 4, f);

        /* OSRS model coords: X=east, Y=up(negated in cache), Z=south
         * Raylib coords: X=east, Y=up, Z=-north
         * Cache Y is negated: vertex Y in cache = -world_Y.
         * Also scale from OSRS units to tile units: divide by 128.
         */
        /* expand_model outputs: x (east), y (up, 0=ground positive=up), z (south)
         * Raylib wants: X=east, Y=up, Z=-north (south is negative Z)
         * Scale: OSRS units → tile units = divide by 128 */
        for (int i = 0; i < vc; i++) {
            verts[i*3+0] /=  128.0f;  /* X: east */
            verts[i*3+1] /=  128.0f;  /* Y: up (already positive from expand_model) */
            verts[i*3+2] /= -128.0f;  /* Z: south→north flip for Raylib */
        }

        /* Build Raylib mesh */
        Mesh mesh = { 0 };
        mesh.vertexCount = vc;
        mesh.triangleCount = tc;
        mesh.vertices = verts;
        mesh.colors = colors;

        /* Generate normals */
        mesh.normals = (float*)calloc(vc * 3, sizeof(float));
        for (int i = 0; i < tc; i++) {
            int i0 = i*3, i1 = i*3+1, i2 = i*3+2;
            float ax = verts[i1*3+0]-verts[i0*3+0], ay = verts[i1*3+1]-verts[i0*3+1], az = verts[i1*3+2]-verts[i0*3+2];
            float bx = verts[i2*3+0]-verts[i0*3+0], by = verts[i2*3+1]-verts[i0*3+1], bz = verts[i2*3+2]-verts[i0*3+2];
            float nx = ay*bz - az*by, ny = az*bx - ax*bz, nz = ax*by - ay*bx;
            float len = sqrtf(nx*nx + ny*ny + nz*nz);
            if (len > 0.0001f) { nx /= len; ny /= len; nz /= len; }
            for (int j = 0; j < 3; j++) {
                int vi = i*3+j;
                mesh.normals[vi*3+0] = nx;
                mesh.normals[vi*3+1] = ny;
                mesh.normals[vi*3+2] = nz;
            }
        }

        UploadMesh(&mesh, false);

        set->entries[m].model_id = model_id;
        set->entries[m].model = LoadModelFromMesh(mesh);
        set->entries[m].loaded = 1;

        fprintf(stderr, "  NPC model %u: %d tris loaded\n", model_id, tc);
    }

    free(offsets);
    fclose(f);
    set->loaded = 1;
    fprintf(stderr, "fc_npc_models: loaded %d models from %s\n", set->count, path);
    return set;
}

static void fc_npc_models_unload(NpcModelSet* set) {
    if (!set) return;
    for (int i = 0; i < set->count; i++) {
        if (set->entries[i].loaded) {
            UnloadModel(set->entries[i].model);
        }
    }
    free(set);
}

#endif /* FC_NPC_MODELS_H */
