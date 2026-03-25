/* Auto-generated Fight Caves NPC model/animation mappings. */
/* Source: OSRS cache + tzhaar_fight_cave.anims.toml */

#ifndef FC_NPC_MODELS_H
#define FC_NPC_MODELS_H

#include <stdint.h>

typedef struct {
    uint16_t npc_id;
    uint32_t model_id;
    uint32_t idle_anim;
    uint32_t attack_anim;
    uint32_t death_anim;
    const char* name;
} FcNpcModelMapping;

static const FcNpcModelMapping FC_NPC_MODEL_MAP[] = {
    {2734, 34252, 9231, 9232, 9230, "Tz-Kih"},
    {2736, 34251, 9235, 9233, 9234, "Tz-Kek"},
    {2738, 34250, 9235, 9233, 9234, "Tz-Kek-Sm"},
    {2739, 34133, 9242, 9245, 9239, "Tok-Xil"},
    {2741, 34135, 9248, 9246, 9247, "Yt-MejKot"},
    {2743, 34137, 9268, 9265, 9269, "Ket-Zek"},
    {2745, 34131, 9278, 9277, 9279, "TzTok-Jad"},
    {2746, 34136, 9253, 9252, 9257, "Yt-HurKot"},
};

#define FC_NPC_MODEL_COUNT 8

#endif /* FC_NPC_MODELS_H */
