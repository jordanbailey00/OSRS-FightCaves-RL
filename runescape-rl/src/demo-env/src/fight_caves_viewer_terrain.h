#ifndef FIGHT_CAVES_VIEWER_TERRAIN_H
#define FIGHT_CAVES_VIEWER_TERRAIN_H

#include "raylib.h"

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define FC_VIEWER_TERR_MAGIC 0x54455252u

typedef struct fc_viewer_terrain_mesh {
    Model model;
    int vertex_count;
    int region_count;
    int min_world_x;
    int min_world_y;
    int loaded;
    float* heightmap;
    int hm_min_x;
    int hm_min_y;
    int hm_width;
    int hm_height;
} fc_viewer_terrain_mesh;

static fc_viewer_terrain_mesh* fc_viewer_terrain_load(const char* path) {
    FILE* file = fopen(path, "rb");
    uint32_t magic = 0;
    uint32_t vertex_count = 0;
    uint32_t region_count = 0;
    int32_t min_world_x = 0;
    int32_t min_world_y = 0;
    float* vertices = NULL;
    unsigned char* colors = NULL;
    Mesh mesh = {0};
    fc_viewer_terrain_mesh* terrain = NULL;

    if (file == NULL) {
        return NULL;
    }
    if (fread(&magic, 4, 1, file) != 1 || magic != FC_VIEWER_TERR_MAGIC) {
        fclose(file);
        return NULL;
    }
    if (
        fread(&vertex_count, 4, 1, file) != 1 ||
        fread(&region_count, 4, 1, file) != 1 ||
        fread(&min_world_x, 4, 1, file) != 1 ||
        fread(&min_world_y, 4, 1, file) != 1
    ) {
        fclose(file);
        return NULL;
    }

    vertices = (float*)malloc(vertex_count * 3u * sizeof(float));
    colors = (unsigned char*)malloc(vertex_count * 4u);
    if (vertices == NULL || colors == NULL) {
        free(vertices);
        free(colors);
        fclose(file);
        return NULL;
    }
    if (
        fread(vertices, sizeof(float), vertex_count * 3u, file) != vertex_count * 3u ||
        fread(colors, 1, vertex_count * 4u, file) != vertex_count * 4u
    ) {
        free(vertices);
        free(colors);
        fclose(file);
        return NULL;
    }

    mesh.vertexCount = (int)vertex_count;
    mesh.triangleCount = (int)(vertex_count / 3u);
    mesh.vertices = vertices;
    mesh.colors = colors;
    mesh.normals = (float*)calloc(vertex_count * 3u, sizeof(float));
    if (mesh.normals == NULL) {
        free(vertices);
        free(colors);
        fclose(file);
        return NULL;
    }
    for (int triangle_index = 0; triangle_index < mesh.triangleCount; ++triangle_index) {
        int base = triangle_index * 9;
        float ax = vertices[base + 0];
        float ay = vertices[base + 1];
        float az = vertices[base + 2];
        float bx = vertices[base + 3];
        float by = vertices[base + 4];
        float bz = vertices[base + 5];
        float cx = vertices[base + 6];
        float cy = vertices[base + 7];
        float cz = vertices[base + 8];
        Vector3 edge_a = {bx - ax, by - ay, bz - az};
        Vector3 edge_b = {cx - ax, cy - ay, cz - az};
        Vector3 normal = Vector3Normalize(Vector3CrossProduct(edge_a, edge_b));
        for (int vertex_offset = 0; vertex_offset < 3; ++vertex_offset) {
            int normal_base = triangle_index * 9 + vertex_offset * 3;
            mesh.normals[normal_base + 0] = normal.x;
            mesh.normals[normal_base + 1] = normal.y;
            mesh.normals[normal_base + 2] = normal.z;
        }
    }

    UploadMesh(&mesh, false);
    terrain = (fc_viewer_terrain_mesh*)calloc(1, sizeof(fc_viewer_terrain_mesh));
    if (terrain == NULL) {
        UnloadMesh(mesh);
        fclose(file);
        return NULL;
    }
    terrain->model = LoadModelFromMesh(mesh);
    terrain->vertex_count = (int)vertex_count;
    terrain->region_count = (int)region_count;
    terrain->min_world_x = (int)min_world_x;
    terrain->min_world_y = (int)min_world_y;
    terrain->loaded = 1;

    {
        int32_t hm_min_x = 0;
        int32_t hm_min_y = 0;
        uint32_t hm_width = 0;
        uint32_t hm_height = 0;
        if (
            fread(&hm_min_x, 4, 1, file) == 1 &&
            fread(&hm_min_y, 4, 1, file) == 1 &&
            fread(&hm_width, 4, 1, file) == 1 &&
            fread(&hm_height, 4, 1, file) == 1 &&
            hm_width > 0 &&
            hm_height > 0
        ) {
            terrain->hm_min_x = (int)hm_min_x;
            terrain->hm_min_y = (int)hm_min_y;
            terrain->hm_width = (int)hm_width;
            terrain->hm_height = (int)hm_height;
            terrain->heightmap = (float*)malloc(hm_width * hm_height * sizeof(float));
            if (terrain->heightmap != NULL) {
                if (
                    fread(
                        terrain->heightmap,
                        sizeof(float),
                        hm_width * hm_height,
                        file
                    ) != hm_width * hm_height
                ) {
                    free(terrain->heightmap);
                    terrain->heightmap = NULL;
                    terrain->hm_width = 0;
                    terrain->hm_height = 0;
                }
            }
        }
    }

    fclose(file);
    return terrain;
}

static float fc_viewer_terrain_height_at(
    const fc_viewer_terrain_mesh* terrain,
    int world_x,
    int world_y
) {
    int local_x;
    int local_y;
    if (terrain == NULL || terrain->heightmap == NULL) {
        return 0.0f;
    }
    local_x = world_x - terrain->hm_min_x;
    local_y = world_y - terrain->hm_min_y;
    if (
        local_x < 0 || local_x >= terrain->hm_width ||
        local_y < 0 || local_y >= terrain->hm_height
    ) {
        return 0.0f;
    }
    return terrain->heightmap[local_x + local_y * terrain->hm_width];
}

static float fc_viewer_terrain_height_avg(
    const fc_viewer_terrain_mesh* terrain,
    int world_x,
    int world_y
) {
    float h00 = fc_viewer_terrain_height_at(terrain, world_x, world_y);
    float h10 = fc_viewer_terrain_height_at(terrain, world_x + 1, world_y);
    float h01 = fc_viewer_terrain_height_at(terrain, world_x, world_y + 1);
    float h11 = fc_viewer_terrain_height_at(terrain, world_x + 1, world_y + 1);
    return (h00 + h10 + h01 + h11) * 0.25f;
}

static void fc_viewer_terrain_free(fc_viewer_terrain_mesh* terrain) {
    if (terrain == NULL) {
        return;
    }
    if (terrain->loaded) {
        UnloadModel(terrain->model);
    }
    free(terrain->heightmap);
    free(terrain);
}

#endif
