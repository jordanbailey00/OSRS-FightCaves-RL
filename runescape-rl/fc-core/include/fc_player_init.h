#ifndef FC_PLAYER_INIT_H
#define FC_PLAYER_INIT_H

/*
 * fc_player_init.h — Player skill levels, equipment, and consumable config.
 *
 * SINGLE FILE for all player configuration. Change skills, gear,
 * consumables here — both training-env and demo-env use this.
 *
 * Training uses FC_ACTIVE_LOADOUT (set below) at compile time.
 * The viewer can switch loadouts at runtime via the loadout dropdown.
 */

/* ======================================================================== */
/* Which loadout to use for training (0=A, 1=B)                              */
/* ======================================================================== */

#define FC_ACTIVE_LOADOUT  1

/* ======================================================================== */
/* Loadout definitions                                                       */
/* ======================================================================== */

typedef struct {
    const char* name;
    int max_hp, max_prayer;
    int attack_lvl, strength_lvl, defence_lvl;
    int ranged_lvl, prayer_lvl, magic_lvl;
    int weapon_kind;
    int weapon_speed;
    int weapon_range;
    int ranged_atk, ranged_str;
    int def_stab, def_slash, def_crush, def_magic, def_ranged;
    int prayer_bonus;
    int ammo;
} FcLoadout;

typedef enum {
    FC_WEAPON_GENERIC_RANGED = 0,
    FC_WEAPON_TWISTED_BOW = 1
} FcWeaponKind;

/*
 * LOADOUT A: Mid-level — Black D'hide + Rune Crossbow
 *
 *   Slot        Item                    Rng Atk  Rng Str  Stab  Slash Crush Magic Ranged Prayer
 *   ----        ----                    -------  -------  ----  ----- ----- ----- ------ ------
 *   Head        Coif                      2        0        4     6     8     4     4      0
 *   Weapon      Rune Crossbow            90        0        0     0     0     0     0      0
 *   Body        Black D'hide Body        30        0       55    47    60    50    55      0
 *   Legs        Black D'hide Chaps       17        0       31    25    33    28    31      0
 *   Hands       Black D'hide Vambraces   11        0        6     5     7     8     0      0
 *   Feet        Snakeskin Boots           3        0        1     1     2     1     0      0
 *   Ammo        Adamant Bolts             0      100        0     0     0     0     0      0
 *                                      ----     ----     ----  ----  ----  ----  ----   ----
 *   TOTAL                               153      100       97    84   110    91    90      0
 */

/*
 * LOADOUT B: End-game — Masori (f) + Twisted Bow
 *
 *   Slot        Item                    Rng Atk  Rng Str  Stab  Slash Crush Magic Ranged Prayer
 *   ----        ----                    -------  -------  ----  ----- ----- ----- ------ ------
 *   Head        Masori mask (f)          12        2        8    10    12    12     9      1
 *   Cape        Ava's assembler           8        2        0     0     0     0     0      0
 *   Neck        Necklace of anguish      15        5        0     0     0     0     0      2
 *   Weapon      Twisted bow              70       20        0     0     0     0     0      0
 *   Body        Masori body (f)          43        4       59    52    64    74    60      1
 *   Legs        Masori chaps (f)         27        2       35    30    39    46    37      1
 *   Hands       Zaryte vambraces         18        2        8     8     8     5     8      1
 *   Feet        Pegasian boots           12        0        5     5     5     5     5      0
 *   Ring        Venator ring             10        2        0     0     0     0     0      0
 *   Ammo        Dragon arrows             0       60        0     0     0     0     0      0
 *   Shield      (none)                    0        0        0     0     0     0     0      0
 *                                      ----     ----     ----  ----  ----  ----  ----   ----
 *   TOTAL                               215       99      116   106   129   150   121      6
 */

#define FC_NUM_LOADOUTS 2

static const FcLoadout FC_LOADOUTS[FC_NUM_LOADOUTS] = {
    /* [0] Loadout A: Mid-level — Black D'hide + Rune Crossbow */
    {
        .name         = "A: Black D'hide + RCB",
        .max_hp       = 700,   /* 70 HP */
        .max_prayer   = 430,   /* 43 prayer */
        .attack_lvl   = 1,
        .strength_lvl = 1,
        .defence_lvl  = 70,
        .ranged_lvl   = 70,
        .prayer_lvl   = 43,
        .magic_lvl    = 1,
        .weapon_kind  = FC_WEAPON_GENERIC_RANGED,
        .weapon_speed = 5,
        .weapon_range = 7,
        .ranged_atk   = 153,   /* 2+90+30+17+11+3 */
        .ranged_str   = 100,   /* adamant bolts */
        .def_stab     = 97,    /* 4+0+55+31+6+1 */
        .def_slash    = 84,    /* 6+0+47+25+5+1 */
        .def_crush    = 110,   /* 8+0+60+33+7+2 */
        .def_magic    = 91,    /* 4+0+50+28+8+1 */
        .def_ranged   = 90,    /* 4+0+55+31+0+0 */
        .prayer_bonus = 0,
        .ammo         = 50000,
    },
    /* [1] Loadout B: End-game — Masori (f) + Twisted Bow */
    {
        .name         = "B: Masori (f) + TBow",
        .max_hp       = 990,   /* 99 HP */
        .max_prayer   = 990,   /* 99 prayer */
        .attack_lvl   = 1,
        .strength_lvl = 1,
        .defence_lvl  = 99,
        .ranged_lvl   = 99,
        .prayer_lvl   = 99,
        .magic_lvl    = 1,
        .weapon_kind  = FC_WEAPON_TWISTED_BOW,
        .weapon_speed = 5,     /* rapid */
        .weapon_range = 10,
        .ranged_atk   = 215,   /* 12+8+15+70+43+27+18+12+10 */
        .ranged_str   = 99,    /* 2+2+5+20+4+2+2+0+2+60(dragon arrows) */
        .def_stab     = 116,   /* 8+1+0+0+59+35+8+5+0+0 */
        .def_slash    = 106,   /* 10+1+0+0+52+30+8+5+0+0 */
        .def_crush    = 129,   /* 12+1+0+0+64+39+8+5+0+0 */
        .def_magic    = 150,   /* 12+8+0+0+74+46+5+5+0+0 */
        .def_ranged   = 121,   /* 9+2+0+0+60+37+8+5+0+0 */
        .prayer_bonus = 6,     /* 1+0+2+0+1+1+1+0+0+0 */
        .ammo         = 50000,
    },
};

/* ======================================================================== */
/* Compile-time constants (used by fc_types.h and training backend)           */
/* These pull from the active loadout index set above.                       */
/* ======================================================================== */

#define FC_PLAYER_MAX_HP        (FC_LOADOUTS[FC_ACTIVE_LOADOUT].max_hp)
#define FC_PLAYER_MAX_PRAYER    (FC_LOADOUTS[FC_ACTIVE_LOADOUT].max_prayer)
#define FC_PLAYER_ATTACK_LVL    (FC_LOADOUTS[FC_ACTIVE_LOADOUT].attack_lvl)
#define FC_PLAYER_STRENGTH_LVL  (FC_LOADOUTS[FC_ACTIVE_LOADOUT].strength_lvl)
#define FC_PLAYER_DEFENCE_LVL   (FC_LOADOUTS[FC_ACTIVE_LOADOUT].defence_lvl)
#define FC_PLAYER_RANGED_LVL    (FC_LOADOUTS[FC_ACTIVE_LOADOUT].ranged_lvl)
#define FC_PLAYER_PRAYER_LVL    (FC_LOADOUTS[FC_ACTIVE_LOADOUT].prayer_lvl)
#define FC_PLAYER_MAGIC_LVL     (FC_LOADOUTS[FC_ACTIVE_LOADOUT].magic_lvl)
#define FC_PLAYER_WEAPON_KIND   (FC_LOADOUTS[FC_ACTIVE_LOADOUT].weapon_kind)
#define FC_PLAYER_WEAPON_SPEED  (FC_LOADOUTS[FC_ACTIVE_LOADOUT].weapon_speed)
#define FC_PLAYER_WEAPON_RANGE  (FC_LOADOUTS[FC_ACTIVE_LOADOUT].weapon_range)
#define FC_EQUIP_RANGED_ATK     (FC_LOADOUTS[FC_ACTIVE_LOADOUT].ranged_atk)
#define FC_EQUIP_RANGED_STR     (FC_LOADOUTS[FC_ACTIVE_LOADOUT].ranged_str)
#define FC_EQUIP_DEF_STAB       (FC_LOADOUTS[FC_ACTIVE_LOADOUT].def_stab)
#define FC_EQUIP_DEF_SLASH      (FC_LOADOUTS[FC_ACTIVE_LOADOUT].def_slash)
#define FC_EQUIP_DEF_CRUSH      (FC_LOADOUTS[FC_ACTIVE_LOADOUT].def_crush)
#define FC_EQUIP_DEF_MAGIC      (FC_LOADOUTS[FC_ACTIVE_LOADOUT].def_magic)
#define FC_EQUIP_DEF_RANGED     (FC_LOADOUTS[FC_ACTIVE_LOADOUT].def_ranged)
#define FC_EQUIP_PRAYER_BONUS   (FC_LOADOUTS[FC_ACTIVE_LOADOUT].prayer_bonus)
#define FC_PLAYER_AMMO          (FC_LOADOUTS[FC_ACTIVE_LOADOUT].ammo)

#endif /* FC_PLAYER_INIT_H */
