# RSPSwiki

Combined markdown copy of every wiki page currently present in `/home/jordan/code/RSPS/wiki`. The original page files remain unchanged.


---

## Home

_Source: Home.md_

Void is a RuneScape private game server emulating January 2011 (revision 634).

It's designed to be fast, lightweight and easy to write content for, a modern base that anyone can use.

# Players
- Getting Started
  - [Installation](installation-guide)
  - [First launch](installation-guide#step-6-launch-the-server)
  - [Game Settings](properties)
- Player Commands
  - [How to make yourself admin](player-rights#modifying-rights)
  - [Using commands](commands)
  - [Spawning items](commands#item)
- Troubleshooting
  - [Common issues](troubleshooting)

# Content Creation
- [Overview](content-creation)
  - [Content Added](content-progress)
  - [Content Planned](roadmap)
- Setup
  - [IDE Setup](https://github.com/GregHib/void#development)
  - Git Basics
    - [Updating](update)
    - [Rebasing](rebase)
- Core Concepts
  - [Scripts](scripts)
    - [Event Handlers](event-handlers)
    - [Interactions](interaction)
    - [Wildcards](wildcards)
  - [Entities](entities)
    - [Players](players)
    - [Bots](bots)
    - [NPCs](npcs)
    - [Objects](game-objects)
    - [Floor Items](floor-items)
  - [Interfaces](interfaces)
    - [Dialogues](dialogues)
    - [Inventories](inventories)
      - [Transactions](transactions)
      - [Shops](shops)
  - [Definitions](definitions)
  - [Config Files](config-files)
  - [Variables](character-variables)
  - [Clocks](clocks)
  - [Timers](timers)
  - Sounds
  - [Teleports](config-files#teleports)
- Guides
  - [How to read an error](Reading-errors)
  - [Update your fork](rebase)
  - [Creating a Script](https://github.com/GregHib/void/wiki/scripts#creating-a-script)
  - [Adding an npc](npcs#adding-npcs)
  - [Adding an object](game-objects#adding-object)
  - [Adding a item spawn](floor-items#adding-floor-items)
  - [Build your own client](client-building)
  - [Build your own cache](cache-building)
  - [Adding a shop](shops#adding-default-shops)
  - [How to auto-format code](Code-formatting)
  - [Save to PostgreSQL](storage#postgresql)
  - [Save to MySQL](storage#driver-support)

# Developers
- [Setup](https://github.com/GregHib/void#development)
- Architecture
  - [Philosophy](philosophy)
  - [String Ids](string-identifiers)
- Technical Concepts
  - [Wildcard system](wildcard-system)
  - [Queues](queues)
  - [Combat](combat-scripts)
    - [Combat lifecycle](combat-lifecycle)
  - [Monster Drops](drop-tables)
  - [Instances](instances)
  - [Hunt Modes](hunt-modes)
  - [Events](events)
  - Item Charges & Degrading
  - Movement Modes
  - [Bot Architecture](bot-architecture)
  - [Database Storage](storage)
  - Delta's
  - Drops and variables
- Contributing
  - [Guidelines](https://github.com/GregHib/void/blob/main/CONTRIBUTING.md)



---

## Bot Architecture

_Source: Bot-Architecture.md_

Void has Player Bots to keep the game world constantly feeling active and alive.

To support hundreds or thousands of bots each bot runs a simple [Finite State Machine (FSM)](https://en.wikipedia.org/wiki/Finite-state_machine) that attempts a single action per tick. Instead of expensive planning algorithms bots chain resolving requirements over time in order to complete activities.

Core concepts:
- Activities (High level goals)
- Actions (Small FSM units of work)
- Conditions (Requirement checks)
- Resolvers (Requirement handlers)
- Navigation
- Behaviours - Joint term for Activities, Resolvers or Shortcuts


# Architecture

## Behaviour Stack
Each bot maintains a stack of Behaviours, processed from Resolvers at the top to the original Activity at the bottom.

```
-> Withdraw Coins (Resolver)
-> Buy Axe (Resolver)
-> Go to Axe Shop (Resolver)
-> Get Hatchet (Resolver)
Chop Trees (Activity)
```

If a behaviour requirement is unmet a resolver is added to the top.
When a behaviour is complete it is removed.

# Activities
High-level activities around the map assigned to bots based on their available items, skill levels and current world state.

- Capacity
  Max number of bots that can perform the activity at once.
- Requirements
  Conditions have to be met to be assigned the activity.
- Setup/Resolvables
  Conditions that can be satisfied with Resolvers before the activity starts.
- Actions
  Steps required to complete the activity.
- Products
  States or outcomes produced upon completion.

Activities are written in `*.bots.toml` config files.

# Resolvers

Resolvers are specialised activities which can be triggered when a setup requirement is unmet.

Resolvers can be configured in `*.setups.toml` config files or some common resolvers are automatically produced by DynamicResolvers.kt.

Examples:
- Buying an item from a shop
- Equipping an inventory item
- Moving to a location
- Withdrawing items from a bank

# Conditions

Conditions determine whether a bot satisfies a requirement and provide a identifiers for finding resolvers that could satisfy it.

Examples:
- Has at least level 15 Woodcutting
- Owns a specific item
- Has a specific variable set
- Is at a specific location

Conditions are used for requirements, setups and by navigation edges.

# Actions

Small self-contained FSMs that produce instructions, check world state and handle branching logic and failures.

An action runs until it succeeds, fails or times-out due to not producing enough producers recently.

# Instructions
The lowest-level event for player entities used by player clients and bots to control player characters

# Bot Manager
Coordinates all bot behaviour, handling allocation of activities and resolvers and checks and updates the state of the top behaviour for every bot each tick.

# Navigation Graph

Simplified representation of the game world used for efficient path planning between distant locations (e.g. cities).

<img width="915" height="595" alt="image" src="https://github.com/user-attachments/assets/18da6dab-25d3-4e2b-a463-22c8950403bb" />


Supports discovery of nearby locations of interest like banks, shops or other areas.

## Edges
Defines how bots move between nodes in the navigation graph.

Can include:
- Movement cost (weight)
- Requirements (e.g. key, coins)
- Actions (e.g. open door, use portal)

Edges with unmet requirements aren't evaluated.

Edges are written in `*.nav-edges.toml` config files.

# Shortcuts

An activity only evaluated as virtual starting points to navigate from.

E.g. Comparing using runes and jewellery to teleport to a nearby location vs running directly.

Shortcuts are written in `*.shortcuts.toml` config files.


---

## Bots

_Source: Bots.md_

As Void can have a number of bot controlled [players](players) to make the world feel more alive and active even when playing offline and locally.

<img width="767" height="535" alt="Screenshot 2026-02-17 230947" src="https://github.com/user-attachments/assets/0ccbd161-a456-4fa7-99cc-d4b2d5ce0257" />

You can customise the number of bots spawned in your world in [Game Settings](game-settings#bots).

Bot configuration is split into 5 parts total; the most important two being `*.bots.toml` and `*.setups.toml`, with `*.templates.toml` shared across them. The remaining two `*.nav-edges.toml` and `*.shortcuts.toml` are used for bot navigation only.

See the [bot-architecture](bot-architecture) page for more details on how the system works.

# Activities

Activities are things a bot can do, what requirements it needs, and where it needs to be to do it.

```toml
[cut_trees]
# Hard Requirements - What a bot must have to be assigned this activity
requires = [
    { skill = { id = "woodcutting", min = 1, max = 15 } },
]
# Things it might not have right now but will need to be setup before starting
setup = [
    { inventory = [{ id = "steel_hatchet,iron_hatchet" }, { id = "empty", min = 27 }] },
    { area = { id = "lumbridge_trees" } },
]
# The action(s) to carry out to do the activity
actions = [
    { object = { option = "Chop down", id = "tree*", delay = 5, success = { inventory = { id = "empty", max = 0 } } } },
]
# Things the activity produces (used for resolver matching and checking if a bot has timed-out)
produces = [
    { item = "logs" },
    { skill = "woodcutting" }
]
```

# Resolvers
Resolvers are activities bots can complete to get stuff which is needed for another activity.
E.g. if a bot needs air and mind runes to cast spells on a combat dummy it will need to talk with the magic tutor to get them.

```toml
[magic_tutor_runes]
requires = [
    { clock = { id = "claimed_tutor_consumables", max = 0, seconds = true } },
    { bank = [
        { id = "air_rune", max = 0 },
        { id = "mind_rune", max = 0 },
    ] },
    { area = { id = "lumbridge_combat_tutors" } }
]
actions = [
    # There are lots of different types of actions most types of interactions
    { npc = { option = "Talk-to", id = "mikasi", delay = 5, success = { interface_open = { id = "dialogue_npc_chat3" } } } },
    { continue = { id = "dialogue_npc_chat3:continue", success = { interface_open = { id = "dialogue_multi4" } } } },
    { continue = { id = "dialogue_multi4:line3", success = { interface_open = { id = "dialogue_obj_box" } } } },
    { continue = { id = "dialogue_obj_box:continue", success = { inventory = { id = "air_rune", min = 30 } } } },
    { continue = { id = "dialogue_obj_box:continue", success = { inventory = { id = "mind_rune", min = 30 } } } },
]
produces = [
    { item = "air_rune" }, # Produces is how setups figure out what resolvers can solve their requirement.
    { item = "mind_rune" }
]
```


## Templates
Templates are re-usable activities where you can put placeholder `$fields` which can be replaced later.

For example a template:

```toml
[cooking_template]
requires = [
    { skill = { id = "cooking", min = "$level" } },
]
```

Can be used like this:
```toml
[cooking_trout]
template = "cooking_template"
fields = { level = 15 }
```

And is equivalent to:

```toml
[cooking_trout]
requires = [
    { skill = { id = "cooking", min = 15  } },
]
```

You can have multiple fields for any kind of value, anywhere in an activity including in the middle of a string.
Templates can be shared between activities, resolvers and shortcuts, but templates can't reference each other.

```toml
[cooking_template]
requires = [
    { bank = { id = "$raw", amount = 28 } },
    { skill = { id = "cooking", min = "$level" } },
]
setup = [
    { inventory = { id = "$raw", min = 28 } },
    { area = { id = "$location" } },
]
actions = [
    { item_on_object = { id = "$raw", object = "$obj", success = { interface_open = { id = "dialogue_skill_creation" } } } },
    { interface = { option = "All", id = "skill_creation_amount:all" } },
    { continue = { id = "dialogue_skill_creation:choice1" } },
    { restart = { wait_if = { queue = { id = "cooking" } }, success = { inventory = { id = "$raw", max = 0 } } } }
]
produces = [
    { skill = "cooking" },
    { item = "$cooked" },
    { item = "$burnt" }
]
```


---

## Cache Building

_Source: Cache-Building.md_

# How to build cache from scratch

Cache building is mostly automated now by [`CacheBuilder.kt`](https://github.com/GregHib/void/blob/a4af3f90fdc33c890935abc9531f903e06b1a6d0/tools/src/main/kotlin/world/gregs/voidps/tools/cache/CacheBuilder.kt#L14) an easy to use cli tool.
You can download original 634 cache files from either: [My archive](https://gregs.world/archive/rs2/cache/), [Displee's archive](https://www.displee.com/archive/rs2/634/) or [OpenRS2 Caches](https://archive.openrs2.org/caches)

My archive also contains the [`727 cache with most xteas` cache](https://mega.nz/folder/IQkg0aoQ#NMnAVE6tVH7O49bFpfouzA/file/JEly1IxR).

Optionally afterwards you can run `XteaValidator.kt` to verify the cache has 100% map coverage.


---

## Character Variables

_Source: Character-Variables.md_

Both Players and NPCs have a [Variables](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/client/variable/Variables.kt) container for storing variables and information.

By default variables are only stored temporarily and will be lost/reset when the npc dies, player logs out, or the game restarts.

Variable names should use lower camel case. e.g. `"in_combat"` not `"inCombat"`

## Getters

Characters have [extension functions](https://kotlinlang.org/docs/extensions.html) to simplify accessing Variables

The following safe version will return `false` if the value is unset.

```kotlin
val burrow = giantMole["burrow", false]
```

> [!TIP]
> Providing a default is the recommended way of accessing values.

```kotlin
val burrow: Boolean = giantMole["burrow"]
```
> [!WARNING]
> This is the unsafe version and will throw an exception is the value hasn't previously been set and doesn't have a default listed in the [variables](#player-variables) config file.

## Setters
Setting a value is straight-forward, it's important to use descriptive names to make sure values don't conflict with other content.

```kotlin
giantMole["burrow"] = true
```

## Player variables

Variables which a player has often need to be sent to the client to change something or display the value. Client variables are split into one of 4 categories:

* varp - Player Variable
* varbit - Variable Bit (part of a varp)
* varc - Client Variable
* varcstr - Client String Variable

Any variables which need storing but don't have a known id should be stored in `*.vars.toml`

For more details read [Runelites page on the subject](https://github.com/runelite/runelite/wiki/VarPlayers,-VarBits,-and-VarClients)

These variables will need specifying in the relevant `.vars.toml` config file in [./data/](https://github.com/GregHib/void/tree/main/data/). Along with their id and type.

```toml
[cooks_assistant]     # The string name of the variable
id = 29               # The variable id to send to the cleint
persist = true        # Save the value on logout
format = "map"        # The format the value is stored in (might differ from the format sent to the client)
default = "unstarted" # The default value if left unset
values = { unstarted = 0, started = 1, completed = 2 } # Map for converting values before sending to client
```


---

## Charges

_Source: Charges.md_

Sometimes charges can be associated with a players' item. There are three main ways of tracking item charges:

| Name | Description | Item effect |
|----|----|----|
| Item level | Charge is reflected in the items id and name e.g. `black_mask_8`. | Reducing a charge to zero replaces the item. |
| Player level | Charge is stored as a player variable e.g. `ring_of_recoil`. | No effect on the item. |
| Inventory level | Charge is stored per individual item in an inventories slot. | No effect on the item. |

An item charge being reduced to zero can:
  1. Destroy (remove) the item
  2. Replaced the item e.g. `chaotic_rapier_broken`
  3. Do nothing


```toml
[item]
id = 1234
charges = 12345         # maximum number of charges
charge_start = 50       # new item starts with 0 charges
deplete = "combat"      # what causes the item charges to degrade
degrade = "item_broken" # the item to degrade into once reached 0 charges
degrade_message = "Your item broke." # message to send after degraded
```


---

## Client Building

_Source: Client-Building.md_

# Build your own client.jar

1. Checkout https://github.com/GregHib/void-client in IntelliJ
2. `Project Structure | Artifacts | Add | Jar` -> From Modules with Dependencies
3. Set Main Class: Loader
4. Change MANIFEST.MF directory to `./void-client/` (default will be `./void-client/client/src`)
5. OK
6. File | Build | Artifacts | void-client:jar | build

`void-client.jar` will be located in `/out/artifacts/void_client_jar/`

# Modify an existing 634 deob

1. Update the RSA keys to match - [modified-files](https://github.com/GregHib/void-client/commit/a112069f0faa589fe880180fb8fff9fc10c49816)
3. Disable the lobby by setting the initial login stage to 2 - [modified-files](https://github.com/GregHib/void-client/pull/2)
4. Fix the area sound packet from (id = 5, size = 6) to size = 8 - [modified-files](https://github.com/GregHib/void-client/commit/51da151e3563d01c27284f57d88cc15dab4fbcd4)
5. Support java 6+ by skipping `Runtime.load0` call - [modified-files](https://github.com/GregHib/void-client/pull/3)

> [Code changes can be found here](https://gist.github.com/GregHib/900e90082314f949b04ff5c0d3e4d8ab) however naming will likely differ in your deob client.



---

## Clocks

_Source: Clocks.md_

[Clocks](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/client/variable/Clocks.kt) are used for tracking a future point in time either in game ticks or in seconds.
Unlike [Timers](timers) a clock does not count down and is only removed when stopped or a clocks time is checked and finished.
This means clocks measured in seconds that are saved to a player's file can continue tracking even when the game is offline. Clocks measured in game ticks however should not be saved.

## Game ticks

Here is a basic example where we start a clock for 1 tick to prevent spam clicking an inventory item.
By default clocks are measured in game ticks.

```kotlin
inventoryOption("Bury", "inventory") {
    // Check if we have already buried a bone this game tick
    if (player.hasClock("bone_delay")) {
        // If the player has buried a bone this tick return without doing anything
        return@inventoryOption
    }
    player.start("bone_delay", 1)
    // ... code here will only run once per tick
}
```

## Epoch seconds

Clocks can also be used to measure seconds when the `epochSeconds()` is passed as a base value.

> [!NOTE]
> Info: Epoch also known as the Unix Epoch or Unix time, refers to number of seconds since the 1st of Janurary 1970.

In this example we prevent a potentially malicious player from closing doors for 60 seconds if they are caught closing doors too frequently.

```kotlin
objectOperate("Close", "door_*") {
    // Check if the clock has any time remaining
    val secondsRemaining = player.remaining("stuck_door", epochSeconds())
    if (secondsRemaining  > 0) {
        // If they do pretend doors are stuck and can't be closed
        player.message("The door seems to be stuck.")
        return@objectOperate
    }

    // If a player closes doors too many times in a short duration they might be trying to trap another player.
    if (player.keepsSlammingDoors()) {
        // Start a clock to stop them closing anymore doors for 60 seconds.
        player.start("stuck_door", 60, epochSeconds())
    }
    // Close the door like normal
}
```

By adding the `stuck_door` clock as a [persistant variable](character-variables) to the `*.vars.toml` file we make sure that the player can't reset the clock by logging out and back in.
```toml
[stuck_door]
format = "int"
persist = true
```

> [!TIP]
> See [Character Variables](character-variables) for more details on how variables are saved


---

## Code formatting

_Source: Code-formatting.md_

All code submitted must be ran through spotless. To run spotless:

1. Open gradle tab
2. Press "Execute Gradle Task" button
3. Enter spotlessApply
4. Press enter

<img width="845" height="322" alt="image" src="https://github.com/user-attachments/assets/80305239-57c7-4726-8445-2dcc73842d54" />


Or run in project root the command line `./gradlew spotlessApply`


---

## Combat Lifecycle

_Source: Combat-Lifecycle.md_

To help understand when combat modifiers are applied to calculations and how they all work together to calculate a hits accuracy and damage.

## Cheatsheet

| Method | Use | Example |
|---|---|---|
| Hit.effectiveLevel / Damage.effectiveLevel | Override the "effective" level of the attacker or target in the accuracy rating calculation. | Turmoil/leech prayers, magic defence, stance, void set bonus |
| Hit.rating | Modify the offensive or defensive rating of the attacker or target used in the accuracy calculation. | Special attack accuracy increases, Slayer helm task accuracy bonus |
| Hit.chance | Modify the successful hit chance. | Seercull special is guaranteed to hit. |
| Damage.maximum | Calculate the base maximum hit. | Magic dart +100 effective magic level, anti dragon shields and protect magic reducing dragonfire  |
| Damage.modify | Modify the rolled maximum hit. | Special attack damage increases, protection prayers decrease, passive effects like berserker necklace or castle wars bracelet |


## Lifecycle
![flowchart of combat stages and events](https://i.imgur.com/DDfgmxm.png)


---

## Combat scripts

_Source: Combat-scripts.md_

> [!NOTE]
> Combat scripting is somewhat experimental and is subject to change as more bosses and monsters are added.


While fighting in combat two characters will take it in turns to swing at one another dealing a random amount of damage which varies based on levels and equipment used. Each swing is broken up into several stages and determines the number of ticks delay to wait afterwards until swinging again.

# Anatomy of an attack

* Swing
  * Determine attack style
  * Start attack animation
  * Calculate damage
  * Queue hits
* Attack
  * Grant experience
  * Apply effects on target
  * Target block animations
* Hit
  * Hit mark & graphics
  * Retaliation
  * Deflection

![swing-stages-magic](https://github.com/GregHib/void/assets/5911414/f4f280a4-aac0-4531-8f5b-58e7c65deb9e)



## Swing style

Swing is the first and main stage in a combat cycle, it is where all the calculations are made, hits created and delays for the next turn set.

### Player swing

Players attack style is already determined by their weapon and selected spell before the swing so the swing stage acts as the place to start animations, set the next delay based on the weapons attack speed and calculate hits to send.

Melee and ranged attacks are handled by `weaponSwing`:

```kotlin
weaponSwing("granite_maul*", Priority.LOW) { player ->
    player.anim("granite_maul_attack")
    player.hit(target)
    delay = 7
}

weaponSwing("*chinchompa", style = "range", priority = Priority.LOW) { player ->
    player.anim("chinchompa_short_fuse")
    player.shoot(id = player.ammo, target = target)
    player.hit(target, delay = Hit.throwDelay(player.tile.distanceTo(target)))
    delay = player["attack_speed", 4]
}
```

Magic attacks are handled by `spellSwing`:

```kotlin
spellSwing("magic_dart", Priority.LOW) { player ->
    player.anim("magic_dart")
    player.gfx("magic_dart_cast")
    player.shoot(id = player.spell, target = target)
    val distance = player.tile.distanceTo(target)
    player.hit(target, delay = Hit.magicDelay(distance))
    delay = 5
}
```

### NPC swing

Npcs that have multiple styles of attacking also use the swing stage to decide which attack style to use

```kotlin
npcSwing("dark_wizard_water*", Priority.HIGHEST) { npc ->
    npc.spell = if (!random.nextBoolean() && Spell.canDrain(target, "confuse")) "confuse" else "water_strike"
}
```

## Calculations

Hit calculations are separated into two parts [Hit](https://github.com/GregHib/void/blob/a4af3f90fdc33c890935abc9531f903e06b1a6d0/game/src/main/kotlin/world/gregs/voidps/world/interact/entity/combat/hit/Hit.kt#L19) and [Damage](https://github.com/GregHib/void/blob/a4af3f90fdc33c890935abc9531f903e06b1a6d0/game/src/main/kotlin/world/gregs/voidps/world/interact/entity/combat/hit/Damage.kt#L27):

### Hit

Hit calculates a number between 0.0 and 1.0 which represents the chance of successfully dealing damage, 1.0 being 100%, and 0.0 - 0% chance. Hit chance combines the attackers offensive rating against the targets defensive rating, both of which combine the combat levels, equipment and bonuses of the respective characters.

### Damage

Damage calculates the maximum and minimum hit the attacker is able to deal on the target, maximum hits are often fixed for magic spells or combines levels and bonuses in other cases. A number between the min and max hit will then be randomly selected to be used. Damage is only calculated if the Hit roll was successful.

> [!NOTE]
> For further calculation details see the [Combat Lifecycle](combat-lifecycle).

## Attack

Attack stage is applied for each hit before the delay, the damage dealt cannot be modified past this point hense why it is the stage where experience is granted. This stage is often used for block animations and damage effects.

```kotlin
combatAttack { player ->
    if (damage > 40 && player.praying("smite")) {
        target.levels.drain(Skill.Prayer, damage / 40)
    }
}
```

Block animations are handled by `block`:

```kotlin
block("granite_maul*") {
    target.anim("granite_maul_block", delay)
    blocked = true
}
```

```kotlin
npcCombatAttack("king_black_dragon") { npc ->
    when (spell) {
        "toxic" -> npc.poison(target, 80)
        "ice" -> npc.freeze(target, 10)
        "shock" -> {
            target.message("You're shocked and weakened!")
            for (skill in Skill.all) {
                target.levels.drain(skill, 2)
            }
        }
    }
}
```

## Hit

Hit is the final stage when hit marks are shown, hit graphics are displayed and after effects like deflection and vengeance are applied.

```kotlin
combatHit { player ->
    if (player.softTimers.contains("power_of_light")) {
        player.gfx("power_of_light_hit")
    }
}
```

```kotlin
weaponHit("*chinchompa", "range") { character ->
    source as Player
    source.playSound("chinchompa_explode", delay = 40)
    character.gfx("chinchompa_hit")
}
```

```kotlin
specialAttackHit("korasis_sword") { character ->
    character.gfx("disrupt_hit")
}
```



---

## Commands

_Source: Commands.md_

This is a brief introduction to how to use the basic commands.

Commands are entered in the command-console which can be opened and closed by pressing the ` key (typically located top left on a standard keyboard).

<img width="767" height="535" alt="image" src="https://github.com/user-attachments/assets/f6e5f1e7-9e78-44f0-9b1a-ab9b1b4cb9cd" />

When the commands console is open you can type a command name and press enter to run it.

# Commands

The most useful command is `commands` which will show you a list of commands you have permissions to use.

<img width="767" height="535" alt="image" src="https://github.com/user-attachments/assets/3c8feacc-350a-48c5-8c95-8dded0630bd4" />

You can optionally add a search to to the end to filter the list e.g. `commands player`

# Help (command-name)

The help command explains a command and it's format in more detail for example `help commands` will explain the [commands](#commands) command in more detail.

<img width="418" height="178" alt="image" src="https://github.com/user-attachments/assets/807b4465-27b5-4b93-a6b3-a5e52d9ab13a" />


# Item

The item command allows [Admins](player-rights#modifying-rights) to spawn any item into their inventory.

It follows the format `item (item-id) [item-amount]`

- item-id - the items id using underscores e.g. `abyssal_whip`, `bandos_tasset` or `armadyl_godsword` (short-hand often works too e.g. `whip`, `tassy`, `ags` as does numbers e.g. `4151`, `11726`, `11694`)
- amount - the number of that item to spawn (abbreviations work for k for thousand, m for million, b for billion, t for trillion)

For example:
- `item bronze_sword`
- `item coins 100`
- `item feathers 2k`

<img width="767" height="535" alt="image" src="https://github.com/user-attachments/assets/a3292605-9e6c-45f3-8a1d-4eeb0849557d" />

# Unlock

Allows you to unlock different types of content

It follows the format `unlock (content-type)`

- activity-type - `all`, `music`, `songs`, `music_tracks`, `tasks`, `achievements`, `emotes` or `quests`
- player-name - optional to target another player; defaults to self.

Examples:
- `unlock all`
- `unlock quests`
- `unlock music`



---

## Config Files

_Source: Config-Files.md_

Config files are `.toml` files stored in /data/ directory that can store pretty much any data - often related to a specific entity and only used to define [string ids](string-identifiers).

Below is a list of configuration types, a description and example.

## Areas
Bounding boxes for areas that trigger [entered/exited](https://github.com/GregHib/void/blob/9c82412c374ced55dbcce337e34de8815e7bb9d2/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/move/Moved.kt#L26)

[ardougne.areas.toml](https://github.com/GregHib/void/blob/main/data/area/kandarin/ardougne/ardougne.areas.toml)

```toml
[ardougne_teleport]
x = [2660, 2664]
y = [3304, 3308]
tags = ["teleport"]
```
<img width="386" height="329" alt="image" src="https://github.com/user-attachments/assets/a587f606-8f61-4128-9737-fef1bfe2fb3c" />

[Map link](https://greghib.github.io/void-map/?centreX=2662&centreY=3294&centreZ=0&zoom=10)

## Sounds
Sound definition extras

[flesh_crawler.sounds.toml](https://github.com/GregHib/void/blob/main/data/area/misthalin/barbarian_village/stronghold_of_security/flesh_crawler/flesh_crawler.sounds.toml)

```toml
[flesh_crawler_attack]
id = 571

[flesh_crawler_death]
id = 572

[flesh_crawler_defend]
id = 573
```

### MIDIs

Midi tune definitions

[lumbridge_church.midis.toml](https://github.com/GregHib/void/blob/main/data/area/misthalin/lumbridge/church/lumbridge_church.midis.toml)

```toml
[church_organ]
id = 147
```

### Jingles

Jingle definitions

[boat.jingles.toml](https://github.com/GregHib/void/blob/main/data/entity/obj/boat/boat.jingles.toml)

```toml
[swamp_boaty]
id = 137

[ogre_boat_travel]
id = 138

[sailing_theme_short]
id = 171

[sailing_theme]
id = 172
```

## Animations

Animation definitions

[pickaxe.anims.toml](https://github.com/GregHib/void/blob/main/data/skill/mining/pickaxe.anims.toml)

```toml
[bronze_pickaxe_chop]
id = 1511
walk = false
run = false

[rune_pickaxe_stuck]
id = 4760

[bronze_pickaxe_stuck]
id = 4761
```

## Graphics (GFX)

Graphical effect definitions for both gfx and projectiles

[aberrant_spectre.gfx.toml](https://github.com/GregHib/void/blob/main/data/area/morytania/slayer_tower/aberrant_spectre/aberrant_spectre.gfx.toml)

```toml
[aberrant_spectre_goo_cast]
id = 334
height = 190

[aberrant_spectre_goo]
id = 335
delay = 28
curve = 5
flight_time = [ 5, 10, 15, 20, 25, 30, 35, 40, 45, 50 ]

[aberrant_spectre_goo_impact]
id = 336
height = 100
```

## Variables

### Players
Player variables

[gameframe.varps.toml](https://github.com/GregHib/void/blob/main/data/entity/player/modal/toplevel/gameframe.varps.toml)

```toml
[xp_counter]
id = 1801
persist = true
format = "int"

[movement]
id = 173
persist = true
format = "map"
default = "walk"
values = { walk = 0, run = 1, rest = 3, music = 4 }
```

### Player bits

Player variable bits

[makeover.varbits.toml](https://github.com/GregHib/void/blob/main/data/entity/player/modal/makeover/makeover.varbits.toml)

```toml
[makeover_body_part]
id = 6091
format = "list"
values = [ "top", "arms", "wrists", "legs" ]

[makeover_facial_hair]
id = 6084
format = "boolean"

[makeover_colour_skin]
id = 6099
format = "int"
```

### Client
Client variable definitions

[world_map.varcs.toml](https://github.com/GregHib/void/blob/main/data/entity/player/modal/world_map/world_map.varcs.toml)

```toml
[world_map_centre]
id = 622
format = "int"

[world_map_marker_player]
id = 674
format = "int"

[world_map_marker_1]
id = 623
format = "int"

[world_map_marker_type_1]
id = 624
format = "map"
default = "yellow"
values = {
  grave = 0,
  yellow = 1,
  blue = 972,
}
```

### Client-strings

[trade.strings.toml](https://github.com/GregHib/void/blob/main/data/social/trade/trade.strings.toml)

```toml
[item_info_examine]
id = 25

[item_info_requirement]
id = 26

[item_info_requirement_title]
id = 34
```

### Custom

Custom server-side variables

[achievement_task.vars.toml](https://github.com/GregHib/void/blob/main/data/achievement/achievement_task.vars.toml)

```toml
[ardougne_elite_rewards]
persist = true
format = "list"
values = [ "catching_some_rays", "abyssal_valet", "you_could_just_knock", "honestly_its_not_a_purse", "almost_made_in_ardougne" ]

[giant_rat_aggressive]
persist = true
format = "boolean"

[giant_rat_defensive]
persist = true
format = "boolean"
```

## Npcs

### Definitions

NPC Definitions

[cow.npcs.toml](https://github.com/GregHib/void/blob/main/data/entity/npc/animal/cow/cow.npcs.toml)

```toml
[cow_default]
id = 81
hitpoints = 80
attack_bonus = -15
combat_def = "cow"
wander_range = 12
immune_poison = true
slayer_xp = 8.0
respawn_delay = 45
height = 30
drop_table = "cow"
categories = ["cows"]
examine = "Converts grass to beef."

[cow_brown]
clone = "cow_default"
id = 397
```

### Spawns

[entrana.npc-spawns.toml](https://github.com/GregHib/void/blob/main/data/area/asgarnia/entrana/entrana.npc-spawns.toml)
```toml
spawns = [
  { id = "chicken_brown", x = 2850, y = 3371 },
  { id = "chicken_brown", x = 2853, y = 3368 },
  { id = "chicken_brown", x = 2853, y = 3373 },
]
```

### Drops

[Drop tables](drop-tables).

[chicken.drops.toml](https://github.com/GregHib/void/blob/main/data/entity/npc/animal/chicken/chicken.drops.toml)

```toml
[chicken_drop_table]
type = "all"
drops = [
    { table = "chicken_primary" },
    { table = "chicken_secondary" },
    { table = "easy_clue_scroll", roll = 300 }
]

[chicken_primary]
type = "all"
drops = [
    { id = "raw_chicken" },
    { id = "bones" }
]

[chicken_secondary]
roll = 128
drops = [
    { id = "feather", amount = 5, chance = 64 },
    { id = "feather", amount = 15, chance = 32 }
]
```

### Patrols

[port_sarim.patrols.toml](https://github.com/GregHib/void/blob/main/data/area/asgarnia/port_sarim/port_sarim.patrols.toml)

```toml
[port_sarim_guard]
points = [
  { x = 3020, y = 3179, level = 2 },
  { x = 3020, y = 3180, level = 2 },
  { x = 3018, y = 3180, level = 2 },
  { x = 3018, y = 3190, level = 2 },
  { x = 3017, y = 3190, level = 2 },
  { x = 3017, y = 3180, level = 2 },
  { x = 3010, y = 3180, level = 2 },
  { x = 3010, y = 3179, level = 2 },
  { x = 3011, y = 3180, level = 2 },
  { x = 3014, y = 3180, level = 2 },
  { x = 3014, y = 3179, level = 2 },
]
```

## Objects
### Definitions

[tree.objs.toml](https://github.com/GregHib/void/blob/main/data/entity/obj/trees.objs.toml)

```toml
[tree]
id = 1276
examine = "One of the most common trees in Gielinor."

[tree_stump]
id = 1342
examine = "This tree has been cut down."

[tree_3]
clone = "tree"
id = 1277
examine = "A healthy young tree."
```

### Spawns

[shortcut.obj-spawns.toml](https://github.com/GregHib/void/blob/main/data/skill/agility/shortcut/shortcut.obj-spawns.toml)

```toml
spawns = [
    { id = "strong_tree", x = 3260, y = 3179, type = 10, rotation = 2 },
]
```

### Teleports

[runecrafting.teles.toml](https://github.com/GregHib/void/blob/main/data/skill/runecrafting/runecrafting.teles.toml)

```toml
[chaos_altar_portal]
option = "Enter"
tile = { x = 2273, y = 4856, level = 3 }
to = { x = 3060, y = 3585 }

[chaos_altar_ladder_down]
option = "Climb-down"
tile = { x = 2255, y = 4829, level = 3 }
delta = { level = -1 }

[chaos_altar_ladder_up]
option = "Climb-up"
tile = { x = 2255, y = 4829, level = 2 }
delta = { level = 1 }
```


## Items
### Definitions

[ore.items.toml](https://github.com/GregHib/void/blob/main/data/skill/mining/ore.items.toml)

```toml
[clay]
id = 434
price = 34
limit = 25000
weight = 1.0
full = "soft_clay"
kept = "Wilderness"
examine = "Some hard dry clay."

[clay_noted]
id = 435

[copper_ore]
id = 436
price = 18
limit = 25000
weight = 2.267
kept = "Wilderness"
examine = "This needs refining."
```

### Spawns

[draynor.item-spawns.toml](https://github.com/GregHib/void/blob/main/data/area/misthalin/draynor/draynor.item-spawns.toml)

```toml
spawns = [
  { id = "poison", x = 3100, y = 3364, delay = 100 },
  { id = "shears", x = 3126, y = 3358, delay = 6 },
  { id = "spade", x = 3121, y = 3361, delay = 100 },
  { id = "bronze_med_helm", x = 3120, y = 3361, delay = 230 },
]
```
### Item-on-item Recipes

[pie.recipes.toml](https://github.com/GregHib/void/blob/main/data/skill/cooking/pie.recipes.toml)

```toml
[part_mud_pie_compost]
skill = "cooking"
level = 29
remove = ["pie_shell", "compost"]
add = ["part_mud_pie_compost", "bucket"]
ticks = 2
message = "You fill the pie with compost."

[part_mud_pie_bucket]
skill = "cooking"
level = 29
remove = ["part_mud_pie_compost", "bucket_of_water"]
add = ["part_mud_pie_water", "bucket"]
ticks = 2
message = "You fill the pie with water."
```

## Interfaces

### Definitions

[bank.ifaces.toml](https://github.com/GregHib/void/blob/main/data/entity/player/bank/bank.ifaces.toml)

```toml
[bank_pin]
id = 13

[bank_deposit_box]
id = 11
type = "main_screen"

[.close]
id = 15

[.inventory]
id = 17
inventory = "inventory"
width = 6
height = 5
options = { Deposit-1 = 0, Deposit-5 = 1, Deposit-10 = 2, Deposit-X = 4, Deposit-All = 5, Examine = 9 }

[.carried]
id = 18

[.worn]
id = 20
```

## Inventories

[bank.invs.toml](https://github.com/GregHib/void/blob/main/data/entity/player/bank/bank.invs.toml)

```toml
[bank]
id = 95
stack = "Always"

[collection_box_0]
id = 523
stack = "Always"
```

## Shops

[karamja.shops.toml](https://github.com/GregHib/void/blob/main/data/area/karamja/karamja.shops.toml)

```toml
[karamja_wines_spirits_and_beers]
id = 29
defaults = [
    { id = "beer", amount = 10 },
    { id = "karamjan_rum", amount = 10 },
    { id = "jug_of_wine", amount = 10 },
]

[davons_amulet_store]
id = 46
defaults = [
    { id = "holy_symbol", amount = 0 },
    { id = "amulet_of_magic", amount = 0 },
    { id = "amulet_of_defence", amount = 0 },
    { id = "amulet_of_strength", amount = 0 },
    { id = "amulet_of_power", amount = 0 },
]
```

## Enums

[Enums](enums)

[picking.enums.toml](https://github.com/GregHib/void/blob/main/data/entity/obj/picking.enums.toml)

```toml
[pickable_message]
keyType = "object"
valueType = "string"
defaultString = ""
values = {
    cabbage_draynor_manor = "You pick a cabbage.",
    potato = "You pick a potato.",
    wheat = "You pick some wheat.",
    cabbage = "You pick a cabbage.",
    flax = "You pick some flax.",
    onion = "You pick an onion.",
}

[pickable_respawn_delay]
keyType = "object"
valueType = "int"
defaultInt = -1
values = {
    cabbage_draynor_manor = 45,
    potato = 30,
    wheat = 30,
    cabbage = 45,
    flax = 5,
    onion = 30,
}
```


And many more... see the full list in [game.properties](https://github.com/GregHib/void/blob/9c82412c374ced55dbcce337e34de8815e7bb9d2/game/src/main/resources/game.properties#L308).


---

## Content Creation

_Source: Content-Creation.md_

Void’s content system is designed to be approachable at every skill level. Whether you just want to tweak a few values or build full-blown mechanics, you can work entirely in simple text-based `.toml` [config files](config-files), lightweight [scripts](scripts), or a mix of both.

This page gives you a quick sense of what you can do and how to get started without being a programmer.

## Data Files

Almost all game data lives in `.toml` files located inside the `/data/` folder. TOML is just a `key = value` format, much like [Game Settings](game-settings).

These files control names, stats, behaviours, prices, spawn locations, etc.

You can modify existing entries or add your own without touching any code.
If you want to give a sword different stats or add a object new teleport, notepad is all you need.

## Scripts

Scripts unlock logic and behaviour: [conversations](dialogues), [interactions](interactions), [timed events](timers), mini-game rules, [complex NPC behaviour](combat), and anything that goes beyond static data.

A basic script can be just a few lines in a .kt file:

```kotlin
class Greeter : Script {
    init {
        npcOperate("Talk-to", "villager") {
            npc<Happy>("Welcome to the village!")
        }
    }
}
```

Scripts are normal Kotlin files inside the `content` module.
You don’t need much programming experience, find other scripts similar to what you want to add and use them as examples.



---

## Content Progress

_Source: Content-Progress.md_

On-going list of game content added to keep people aware of what exists and what hasn't been added yet.

## Skills

<img width="240" height="333" alt="skill-progress" src="https://github.com/user-attachments/assets/f294683c-1d15-4c8a-af8f-1f943e10e9d2" />

- Agility: A few [uncommon courses not added](https://github.com/GregHib/void/issues/484), [shortcuts only up to level 40](https://github.com/GregHib/void/pull/639)
- Farming: [missing calquat/mushroom patches and some disease chances](https://github.com/GregHib/void/issues/749)
- Slayer: Slayer masters added but [not all monster tasks](https://github.com/GregHib/void/issues/503)
- Thieving: [Missing black-jacking a few other small bits](https://github.com/GregHib/void/issues/485#issuecomment-3323593516)
- Hunter: [coming soon.](roadmap)
- Summoning: [started](https://github.com/GregHib/void/issues/894) - scrolls and combat not added
- Dungeoneering: not started
- Construction: not started
- Magic: A lot of non-combat spells not added yet
- Cooking: No ale brewing
- Fishing: No barbarian fishing


## Quests
<img width="240" height="482" alt="quests-progress" src="https://github.com/user-attachments/assets/61abe44b-22e6-4d2c-84d4-0e122f5d4ad7" />

### Mini-quests

- Into the abyss
- Bar crawl
- Sheep shearer

## Bosses
- King Black Dragon
- Godwars Dungeon (Not Nex)
- Giant Mole
- Dagannoth Kings
- Chaos Elemental
- Jad

## Transport
- Charter Ships
- Spirit Tree
- Fairy Ring
- Wilderness Obelisks
- Shortcuts
- Gnome Gliders
- Ships
- Canoes
- Magic Carpets

## Minigames
- Sorceress' Garden
- TzHaar Fight Caves

## Distractions & Diversions
- Shooting Star
- Penguin Hide & Seek

## Dungeons
- Lumbridge swamp dungeon
- Varrock dungeon
- Edgeville dungeon
- Fremennik Slayer Dungeon
- TzHaar City

## Bots
- Up to ~150 skill based activities for them to do around Lumbridge, Draynor and Varrock

## Achievement Diary's
- Lumbridge Easy


## Players
- Grand Exchange
- Clan Chat 
- Trading
- Assisting
- Combat
  - PvP
  - PvM
  - All weapons
  - All special attacks

## Objects
- Doors - most done
- Teleports - most done

## NPCs
- Shops - nearly all
- Spawns - most done
- Animations - some missing
- Combat - a lot missing


---

## Definitions

_Source: Definitions.md_

Definitions are collections of data stored in the game Cache, they often store data about a specific [Entity](entities), [Interface](interfaces) or other game engine system.

> [!TIP]
> You can find the full list of definitions in [`./engine/data/definition/`](https://github.com/GregHib/void/tree/main/engine/src/main/kotlin/world/gregs/voidps/engine/data/definition)

Definitions can be accessed from anywhere using the format:

```kotlin
ItemDefinitions.get(itemId)
```

Or in functions using the old injection method:

```kotlin
val itemDefinitions: ItemDefinitions = get()
```


## Definition Extras

Void goes beyond standard definitions and links [TOML](https://toml.io/en/) [configuration files](config-files) to each definition making it easy to define [String Identifiers](string-identifiers) and additional data.


Anything added to an npc beyond the integer id is added to the respective [`definitions.extras`](https://github.com/GregHib/void/blob/main/cache/src/main/kotlin/world/gregs/voidps/cache/definition/Extra.kt) map where they can later be accessed in a similar way to [Variables](character-variables).

```toml
[hans]
id = 0
wander_radius = 4
categories = ["human"]
examine = "Servant of the Duke of Lumbridge."
```

```kotlin
val race = npc.def["race", ""] // Get or default return empty string

val examine: String = npc.def["examine"] // Get unsafe with type

val radius: Int? = npc.def.getOrNull("wander_radius") // Get if exists else return null value
```

> [!NOTE]
> This applies to all definitions, `.objs.toml` -> ObjectDefinition, `.ifaces.toml` -. InterfaceDefinition, `fonts.toml` -> FontDefinition, etc...



---

## Dialogues

_Source: Dialogues.md_

## Statements

Statements are the simplist dialogue type and show only text

```kotlin
statement("You can't use this emote yet. Visit the Stronghold of Player Safety to unlock it.")
```

Line of text that are too long will automatically be wrapped around onto a new line.

![image](https://github.com/GregHib/void/assets/5911414/6b8909c1-0e87-4569-90b6-4b3b48bcc10a)

Line breaks can also be specified manually with the `<br>` tag or with [multi-line string literals](https://kotlinlang.org/docs/strings.html#multiline-strings).
```kotlin
statement("You can't use this emote yet. Visit the Stronghold of Player Safety to<br>unlock it.")
```

```kotlin
statement("""
    You can't use this emote yet. Visit the Stronghold of Player Safety to
    unlock it.
""")
```

## Items

Item dialogues show an inventory item next to text.

```kotlin
item(item = "bank_icon", zoom = 1200, text = "You have some runes in your bank. Climb the stairs in Lumbridge Castle until you see this icon on your minimap. There you will find a bank.")
```

![image](https://github.com/GregHib/void/assets/5911414/b02777fd-c70a-4542-8b87-807fb72edf90)

> [!NOTE]
> Some items exist only as icons for this dialogue.

## Characters

### Players

Player dialogues need a facial expression to go with the text displayed.

```kotlin
player<Unsure>("I'd like to trade.")
```

![image](https://github.com/GregHib/void/assets/5911414/c8b424df-0f1c-4889-b46a-490d863c6108)

> [!NOTE]
> [Full list of facial expressions](https://github.com/GregHib/void/blob/main/game/src/main/kotlin/world/gregs/voidps/world/interact/dialogue/Expression.kt)

### NPCs

To start a dialogue with an NPC `player.talkWith(npc)` must first be called.

This is automatically done when interacting with an npc via an NPCOption such as ItemOnNPC.

All future npc dialogues will refer back to the npc being interacted with.

```kotlin
npc<Talk>("Sorry I don't have any quests for you at the moment.")
```
![image](https://github.com/GregHib/void/assets/5911414/05d22201-eeb8-47fa-926c-967731cb8807)

For dialogues with multiple npcs, the npcs ids can be provided
```kotlin
npc<Furious>(npcId = "wally", text = "Die, foul demon!", clickToContinue = false)
```
![image](https://github.com/GregHib/void/assets/5911414/e6b9514c-9ec1-4dd7-bbd9-81c7c3bc33f1)


## Choices

Multi-choice options

```kotlin
choice {
    option("Yes I'm sure!") {
        npc<Cheerful>("There you go. It's a pleasure doing business with you!")
    }
    option("On second thoughts, no thanks.")
}
```

![image](https://github.com/GregHib/void/assets/5911414/424c7cd3-26ce-4eef-9e66-bd3984b06c3b)

Choices can be given custom titles
```kotlin
choice("Start the Cook's Assistant quest?") {
    option("Yes.") {
    }
    option("No.") {
    }
}
```

### Options

Anthing in an action will be executed after an option has been selected.
This can include a mix of delays and character modifications as well as other dialogues.
```kotlin
option("Yes I'm sure!") {
   player.inventory.remove("coins", cost)
   npc<Cheerful>("There you go. It's a pleasure doing business with you!")
}
```

Dialogues will end if an option without an action specified is selected
```kotlin
option("On second thoughts, no thanks.")
```

Options can be given an expression to have the player repeat the dialogue selected
```kotlin
// ❌ Manual
option("I'd like to trade.") {
    player<Unsure>("I'd like to trade.")
}
// ✅ Automatic
option<Unsure>("I'd like to trade.") {
}
```

Options be filtered so they are only displayed when the condition is met.
```kotlin
if (player.questComplete("rune_mysteries")) {
    option("Can you teleport me to the Rune Essence?") {
        player.tele(2910, 4830)
    }
}
```

When only one options condition is met then that option will be automatically selected
```kotlin
choice {
    option<Talk>("Nothing, thanks.")
}
```



---

## Drop Tables

_Source: Drop-Tables.md_

Players are rewarded for killing monsters with Item that are dropped on the floor on their death, the items dropped are selected at random from a table of options which vary depending on the type monster defeated.

# Tables

Drop tables are defined inside [`.drops.toml`](https://github.com/GregHib/void/blob/main/data/entity/npc/animal/chicken/chicken.drops.toml) files

```toml
[chicken_drop_table]                  # Name of the table
type = "all"                          # How to pick from this table
drops = [                             # List of potential drops
    { table = "chicken_primary" },    # Nested sub table for 100% drops
    { table = "chicken_secondary" },  # Nested sub table for main drops          
    { table = "chicken_tertiary" },   # Nested sub table for extra drops
]


[chicken_primary]           # Sub table for 100% drops
type = "all"                # Drop every item in this sub-table
# Default roll is 1
drops = [
    { id = "raw_chicken" }, # Default 100% chance to drop 1
    { id = "bones" }
]

[chicken_secondary] # Sub-table for main drop
roll = 128          # Roll a random number between 1-128
# Default type "first" stops after one drop
drops = [
    { id = "feather", amount = 5, chance = 64 },  # Drop 5 feathers if roll lands between 1-64 (50% chance)
    { id = "feather", amount = 15, chance = 32 }, # Drop between 10 and 15 feathers if roll lands between 64-96 (25% chance)
]                                                 # Drop nothing if roll lands between 96-128 (25% chance)

[chicken_tertiary]
roll = 300 # Roll between 1-300
drops = [
    { id = "clue_scroll_easy", lacks = "clue_scroll_easy*" }, # Drop only if player doesn't already own an easy clue scroll
    { id = "super_large_egg", variable = "cooks_assistant_egg", equals = false, default = false }, # Check variable is equal or return default if variable isn't set on the player
]
```

## Types
There are two table types by default a tables type will be `first`.
* `all` - drop every item listed
* `first` - drop only one item

### Type: All

Tables with type `all` will drop every item or call every sub-table listed, as such no `chance` parameter is required as all items have a 100% chance of being selected.

```toml
[cow_primary]
type = "all"
drops = [
    { id = "cowhide" },
    { id = "raw_beef" },
    { id = "bones" }
]
```

### Type: First

Most tables however roll a random number between 0 and `roll` and select the first drop in the list that the cumulative `chance` is within.

For example take this table:
```toml
[talisman_drop_table]
roll = 70
drops = [
  { id = "air_talisman", chance = 10 },
  { id = "body_talisman", chance = 10 },
  { id = "earth_talisman", chance = 10 },
  { id = "fire_talisman", chance = 10 },
]
```

* First we roll a random number between 0-70. Let's say 22.
* We check down the list of items starting from the top and a chance of 0
* For `air_talisman` 22 is not within 0-10; so we move on adding 10 to our total.
* For `body_talisman` 22 is not within 10-20; so we move on, adding 10 to our total.
* For `earth_talisman` 2 is within 20-30. So `earth_talisman` is our selected drop.

> [!WARNING]
> Drops chances should never exceed the tables `roll` as that could mistakenly give some drops a 0% chance.


## Nesting

Tables can also be drops themselves allowing for nesting and control over "groups" of drops.

The following example will always drop `bones` and `raw_rat_meat` but `giant_rat_bones` will only have a 25% of being dropped
```toml
[giant_rat_drop_table]
type = "all"
drops = [
    { table = "giant_rat_primary" },
    { table = "giant_rat_secondary" }
]

[giant_rat_primary]
type = "all"
drops = [
    { id = "bones" },
    { id = "raw_rat_meat" }
]

[giant_rat_secondary]
roll = 4
drops = [
    { id = "giant_rat_bone" }
]
```
## Conditionals

### Variables

Items can have optional conditions for being dropped by specifying a [variable](character-variables) and the value it should match

```toml
drops = [
  { id = "blood_rune", amount = 2, chance = 2, members = true }, # Spawn only on members worlds
  { id = "clue_scroll_elite", lacks = "clue_scroll_elite*" }, # Dropped only if the player lacks an item in their inventory, equipment, bank or familiar
  { id = "rats_paper", variable = "what_lies_below_stage", within_min = 2, within_max = 4, default = 0 }, # The range the variable must be within (inclusive)
]
```

### Items

Drops can also have optional conditions if a player owns (has on them or in their bank) or does not own a particular item using the `owns` or `lacks` fields. 

```toml
drops = [
  { id = "small_pouch", chance = 6, lacks = "small_pouch" },   # Only drop if the player doesn't have a small pouch already
  { id = "medium_pouch", chance = 6, charges = 45, owns = "small_pouch", lacks = "medium_pouch*" }, # But not if the player owns any type of medium pouch (i.e. medium_pouch or medium_pouch_damaged)
]
```

# Monster drops

You can specify a drop table for a monster simply by naming a table in either a `<npc_id>_drop_table`, or `<npc_race>_drop_table` format.

The more specific table name will be used first, for example the King Black Dragon will use the `king_black_dragon_drop_table` even though it is classified as one of the dragon race in `*.npcs.tom`, where a Green Dragon will use `dragon_drop_table` as a `green_dragon_drop_table` doesn't exist.

```toml
[green_dragon]
id = 941
categories = ["dragon"]
examine = "Must be related to Elvarg."

[king_black_dragon]
id = 50
categories = ["dragon"]
examine = "One of the biggest, meanest dragons around."
```

# Converting from wiki

Drop tables can be converted from the osrs and rs3 wiki pages (with some [exceptions](#exceptions)) using [DropTableConverter.kt](https://github.com/GregHib/void/blob/main/tools/src/main/kotlin/world/gregs/voidps/tools/convert/DropTableConverter.kt).

Find the drops section of an npcs wiki page, in this example we'll use [Dark wizards](https://oldschool.runescape.wiki/w/Dark_wizard#Level_7_and_11_drops).

Clicking on "edit source" will reveal the underlying [Wikitext](https://en.wikipedia.org/wiki/Help:Wikitext):

![image](https://github.com/GregHib/void/assets/5911414/18ec004a-badb-45b1-a4e2-3a5d7d7ecca5)

Copy and paste the wikitext into and run the DropTableConverter

![image](https://github.com/GregHib/void/assets/5911414/0cf38ad8-b0d9-4468-824f-d63c52211727)

The converter will print out the drop table in TOML with the quanities and rarities converted

```toml
[drop_table]
roll = 128
drops = [
  { id = "nature_rune", amount = 4, chance = 7 },
  { id = "chaos_rune", amount = 5, chance = 6 },
  { id = "mind_rune", amount = 10, chance = 3 },
  { id = "body_rune", amount = 10, chance = 3 },
  { id = "mind_rune", amount = 18, chance = 2 },
  { id = "body_rune", amount = 18, chance = 2 },
  { id = "blood_rune", amount = 2, chance = 2, members = true },
  { id = "cosmic_rune", amount = 2 },
  { id = "law_rune", amount = 3 },
]
```

## Exceptions

> [!CAUTION]
> The converter doesn't verify item ids, so an item named "Wizard hat" will be
> converted to `wizard_hat` even though the `.items.toml` id is `black_wizard_hat`.
> Any incorrect ids will be printed out on server startup: `[ItemDrop] - Invalid drop id wizard_hat`.


> [!WARNING]
> The converter won't correctly convert most [variable conditions](#variables) such as drop limits, dynamic drop rates,
> quest requirements or other exceptions. These will need to be added manually.


---

## Entities

_Source: Entities.md_

Entity is a broad category for everything that exists in the game world, with a second sub-category `Character` for Player and NPC entities that can move. The main Entity types are:

* Blue - [Players](players)
* Green - [NPCs](npcs) (Non-Player Characters)
* Orange - [Objects](game-objects) (GameObjects)
* Purple - [Floor items](floor-items)

![Screenshot 2024-02-20 171650](https://github.com/GregHib/void/assets/5911414/268ba82d-be17-4b0e-b3ca-e52e37f8e152)

> [!NOTE]
> Item's are considered a part of [interfaces](interfaces) not entities, for more info see [Inventories](inventories).

# World
Although only an entity due to technicality the World also has a number of operations the same as other entities.

World spawn and despawn are called on server startup and shutdown:

```kotlin
worldSpawn {
}

worldDespawn {
}
```

And world timers work the same as other entities:

```kotlin
worldTimerStart("timer_name") {
}
worldTimerTick("timer_name") {
}
worldTimerStop("timer_name") {
}
```


---

## Event Handlers

_Source: Event-Handlers.md_

Event handlers are blocks of code which fire when [events](events) happening in the game world are emitted by the game engine, every handler is registered inside of a [script](scripts).

This page focuses on using handlers, for the full API see the parents of [Script.kt](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/Script.kt).

## Basic Handler

A basic `playerSpawn` which runs when a player logs in:

```kotlin
class HelloWorld : Script {
    init {
        playerSpawn {
            message("Welcome to Void.")
        }
    }
}
```

For longer handlers, you can extract the logic:

```kotlin
class HelloWorld : Script {
    init {
        playerSpawn(::spawn)
    }

    fun spawn(player: Player) {
        player.message("Welcome to Void.")
    }
}
```

All examples you can assume are inside a scripts `init {}` block.

## Handler parameters

Some handlers take arguments, usually IDs:

```kotlin
npcSpawn("sheep") {
    say("Baa!")
}
```

IDs can use wildcards:

```kotlin
npcSpawn("cow_*") {
    softTimers.start("eat_grass")
}
```

See [Wildcards](wildcards) for details.

## Handler Scope

Handlers run in the context of the entity that triggered them:
```kotlin
npcSpawn("sheep") {
    say("Baa!") // npc.say("Baa!")
}

playerSpawn {
    say("Baa!") // player.say("Baa!")
}
```

Interface and inventory handlers also use player scope:

```kotlin
interfaceOption("Yes", "quest_intro:startyes_layer") {
    // this == player
}
```

## Interactions


[Interactions](interaction) fire when a Character performs an action on an Entity. They fall into two categories *operate* (adjacent) and *approach* (from a distance).

Player -> Target
```kotlin
npcOperate("Talk-to", "doric") { }

objectOperate("Steal-cowbell", "dairy_cow") { }
```

Item/Interface -> Target
```kotlin
itemOnNPCOperate("wool", "fred_the_farmer_lumbridge") { }
```

NPC -> Target
```kotlin
npcOperateFloorItem("Take") {
}
```

## Interfaces

Interface IDs follow: `interface:component`.

```kotlin
interfaceOpened("bank") { }

interfaceClosed("price_checker") { }

interfaceOption("Show rewards", "quest_intro:hidden_button_txt") { }
```

## Inventories

```kotlin
itemAdded("logs", "inventory") { }

itemRemoved("cowhide", "inventory") { }
```

## Timers

[Timers](timers) have a start, stop and tick event.

```kotlin
timerStart("poison") {
   30 // Game Ticks until next call
}

timerTick("poison") {
    Timer.CONTINUE
}

timerStop("poison") { }
```

The best way to learn is by [exploring existing scripts](https://github.com/GregHib/void/tree/main/game/src/main/kotlin/world/gregs/voidps/world) to see how handlers are used in practise.


---

## Events

_Source: Events.md_

All of Void's content is built using Events to transmit information between entities and content in [handlers](event-handlers).

Whilst conceptually it can be considered as a [Publish-Subscriber model](https://en.wikipedia.org/wiki/Publish%E2%80%93subscribe_pattern) since 2.3.0 Void does not have a concept of an Event class due to performance and overhead reasons. Instead on startup [scripts](scripts) store functions which are then called directly when "emitting" an event.

The simplest example would be the `Spawn` "event".

```kotlin
interface Spawn {
    fun playerSpawn(handler: Player.() -> Unit) {
        playerSpawns.add(handler)
    }
    
    companion object : AutoCloseable {
        private val playerSpawns = mutableListOf<(Player) -> Unit>()
        
        fun player(player: Player) {
            for (handler in playerSpawns) {
                handler(player)
            }
        }
    }
}
```

It registers it's subscription inside a script:

```kotlin
class HelloWorld : Spawn {
    init {
        playerSpawn {
            message("Welcome to Void.")
        }
    }
}
```

And can be emitted by calling: `Spawn.player(myPlayer)`.

Script interfaces are often added to [Script.kt](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/Script.kt#L28) to simplify imports and accessing them inside of scripts, although whilst not required all external events must still be `AutoCloseable` and be added to [Script.kt's `interfaces`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/Script.kt#L43) list.


---

## Floor Items

_Source: Floor-Items.md_

Floor items are items that are either spawned with `*.item-spawns.toml`, [dropped](drop-tables) by [Monsters](npcs) or by other [Players](players). They can be picked up to move them into a [Players](players) [Inventory](inventories).

An item that is dropped will only be shown to the person it was dropped by/for and after `revealTicks` it will be shown to all [Players](players), `disappearTicks` after that it will be permanently deleted from the world.

## Configuration

### Definitions

[`*.items.toml`](https://github.com/GregHib/void/blob/main/data/skill/farming/basket.items.toml)

```toml
[basket]
id = 5376
price = 1
limit = 1000
weight = 0.028
examine = "An empty fruit basket."

[basket_noted]
id = 5377

[apples_1]
id = 5378
weight = 0.056
empty = "basket"
examine = "A fruit basket filled with apples."
```


### Spawns

Permanent and re-spawning floor items should be added to [`/data/*.item-spawns.toml`](https://github.com/GregHib/void/blob/main/data/minigame/blast_furnace/blast_furnace.item-spawns.toml) [config files](config-files#items):

```toml
spawns = [
  { id = "spade", x = 1951, y = 4964, delay = 100, members = true },
]
```

## Finding floor items
Much the same as other entities, `FloorItems` contains and can be used to control all the floor items in the world, it can be found searched for by `Tile` or `Zone`:

```kotlin
val itemsUnder = FloorItems.at(Tile(3200, 3200)) // Get all items on a tile

val itemsNearby = FloorItems.at(Zone(402, 403)) // Get all items in 8x8 area
```

## Adding floor items

Items can be spawned onto the floor with:

```kotlin
val floorItem = FloorItems.add(tile, "ashes", revealTicks = 90, disappearTicks = 60)
```

## Removing floor items

Floor items can also be removed with:

```kotlin
npcFloorItemOperate("Take") { (target) ->
    FloorItems.remove(target)
}
```

## Floor item data

| Data | Description |
|---|---|
| Id | The type of item as a [String identifier](string-identifiers). |
| Amount | The amount of the item in this [stack](inventories#item-stack-behaviour). |
| [Definition](definitions) | Definition data about this particular type of item. |
| Tile | The position of the item represented as an x, y, level coordinate. |
| Reveal ticks | Number of ticks before an item is made public for everyone. |
| Disappear ticks | Number of ticks before the item is permanently deleted. |
| Owner | The original owner of the item. |


---

## Game Messages

_Source: Game-Messages.md_

Reference links to evidence of exact wording.
## Useful links
* http://web.archive.org/web/20111005152247/https://www.runehq.com/
* http://web.archive.org/web/20111105021117/https://www.tip.it/runescape
* http://web.archive.org/web/20111104070233/http://www.zybez.net/
* http://tipsforrs.blogspot.com/2011/11/introduction-welcome-to-runescape.html
* https://twitter.com/TheCrazy0neTv/with_replies
* Google - before:2011-01-13
* Combat - [PurpleGod](https://www.youtube.com/channel/UCb2u2gwziN6I3gf7ejHUgKg/videos) https://www.youtube.com/watch?v=DX8lN3r3ALw [LOS](https://www.youtube.com/watch?v=vnyNLXTwjCE&t=1s)
* RS3 OSRS Item, NPC, Object databases https://chisel.weirdgloop.org/gazproj/
* https://mejrs.github.io/osrs?npcid=5945
* https://mejrs.github.io/?npcid=2890

# Game message
| Message | Link |
|---|---|
| `You get some willow logs.`, `You swing your hatchet at the tree`, `Logs cut from a willow tree.`, `You are not a high enough level to use this item.`, `You need to have an attack level of 40.`, `Nothing interesting happens.`, `You have correctly entered your PIN.` | [image](http://i1012.photobucket.com/albums/af249/Leobrocato/IEatsublevels.png) |
| `Sending trade offer...` `X wishes to trade with you.` | [youtube](https://youtu.be/Z7ifeaiB8s4?t=53) |
| `You eat the salmon.`, `It heals some health.`, `You don't have enough inventory space to hold that item.` | [image](http://i2.wp.com/dicerz.co.uk/wp-content/uploads/2015/02/hill-giant-bones.png) |
| `X has joined the party.`, `X has left the party.`, `You leave the party.` | [image](https://images-wixmp-ed30a86b8c4ca887773594c2.wixmp.com/f/2086ea76-f680-4f83-bde9-8049a58656c3/d2njsc3-c8b0a0a8-a839-4b8d-af68-5258848b4484.png?token=eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1cm46YXBwOjdlMGQxODg5ODIyNjQzNzNhNWYwZDQxNWVhMGQyNmUwIiwiaXNzIjoidXJuOmFwcDo3ZTBkMTg4OTgyMjY0MzczYTVmMGQ0MTVlYTBkMjZlMCIsIm9iaiI6W1t7InBhdGgiOiJcL2ZcLzIwODZlYTc2LWY2ODAtNGY4My1iZGU5LTgwNDlhNTg2NTZjM1wvZDJuanNjMy1jOGIwYTBhOC1hODM5LTRiOGQtYWY2OC01MjU4ODQ4YjQ0ODQucG5nIn1dXSwiYXVkIjpbInVybjpzZXJ2aWNlOmZpbGUuZG93bmxvYWQiXX0.hsO1Yg1jdw1D7Kr1MARV4WOegPuDRiCYsOAsXhFQS70) |
| `You don't have enough inventory space.` | [image](http://junglebiscuit.com/images/runescapekqguide1.png) |
| `Nothing interesting happens.`, `You can't reach that.` `<col=ff0000>You have unlocked a new music track: <name>` | [image](https://i.vimeocdn.com/video/277292676_1280x880.jpg) |
| `Please close the interface you have open before setting your audio options.` | [image](https://lh3.googleusercontent.com/proxy/vjGlzD-QyTiPwC9L-v_qK_mmy5hbe0UIpYimCopESuaMZToITioyXuWPUnvddSlCBf-Z7GwngJp1xKvHshASQiRft7yUqvcbKhQz6WZHZ5wLUuxFQYE--aPBPlimDLq-Ntna) |
| `On this particular world you can only chat using quickchat. If you want to chat freely then please change to a different world.` | [image](https://salsrealm-assets.salmoneus.net/images/tips/quick-chat/qchatreply.png) |
|`You need a fire talisman to be able to access the Fire Altar.`|[image](https://external-preview.redd.it/sEMvbn-HECzRMQnG3TeUMK-P6frvseujSIya23fnEso.png?auto=webp&s=f0ff203050789245d03eb865759db07a4db55b36)|
|`You don't have sufficient room in your inventory to take this item.` | [image](https://s1.dmcdn.net/v/4am3g1KDuqn2fJAwo/x1080) |
| `This feature can only be used on the members' servers.` | [video](https://youtu.be/ebYXjzKVB9M?t=26)

## Assisting
| Message| Link |
|---|---|
| `X is requesting your assistance.`, `Sending assistance response.`, `You are assisting X.`, `It has been 24 hours since you first helped someone using the Assist System.`, `You can now use it to gain the full amount of XP.`, `You have stopped assisting X.` | [youtube](https://www.youtube.com/watch?v=xKWxeZrPf_0) |
| `You have earned 0Xp. The assist system is available to you.` | [youtube](https://www.youtube.com/watch?v=ypoa2_CPzfEP) |
| `That assistance request has timed out.`, `The Assist System is available for you to use.`, `You've earned the maximum XP from the Assist System within a 24-hour period.\nYou can assist again in 24 hours.`, `An assist request has been refused. You can assist again in 24 hours.`, `You've earned the maximum XP (30,000 Xp) from the Assist System within a 24-hour\nperiod.\n You can assist again in 24 hours.` | [youtube](https://www.youtube.com/watch?v=NL8lGndvKOM) |
| `You have only just made an assistance request` & `You have to wait X seconds before making a new request.` | [youtube](https://www.youtube.com/watch?v=OHU319BcjGw) |

## Lending
| Message| Link |
|---|---|
| `Your item has been lent to <name>. It will be sent to your Returned Items box if<br> either you or <name> logs out.`, `Speak to a banker to view your Returned Items box.`, `Demanding return of item.`, `Your item has been returned.` | [youtube](https://www.youtube.com/watch?v=5t-Jw3GDxSs) |

# Dialogue
| Dialogue | Link |
|---|---|
| Hans | [youtube](https://www.youtube.com/watch?v=1VdE7naw0Ek) |

# Grand exchange
| Message | Link |
|---|---|
| | [image](https://i.ytimg.com/vi/RRYPXYi2j_A/maxresdefault.jpg) |
| `Abort request acknowledged. Please be aware that your offer may have already been completed.` | [youtube](https://youtu.be/53Z9rXjnkEA?si=8w1RC8ePTKwEIpLc&t=67) |
| `You have items from the Grand Exchange waiting in your collection box.` | [youtube](https://youtu.be/K_bUKpYX5xs?si=CE3ylZfl8jkr0sez&t=71) |
| `One or more of your Grand Excahnge offers have been updated.` |[youtube](https://youtu.be/nlfH9Wpvvtw?si=q6M9cI_F-ZfvLdQg&t=76)

# Soul wars
| Message | Link |
|---|---|
| `The <col=ff0000>red<col=000000> team was victorious! You receive 1 Zeal for your participation in this game.`, `You spend 1 Zeal and receive 4200 Defence experience in return.` | [youtube](https://youtu.be/Us9E_DZT4xw?t=5) |
| `The blue team has lost control of the soul obelisk.`, `The battle was drawn! You receive 2 Zeal for your participation in this game!`, `<col=ff0000>Warning:<col=000000> a game is currently in progress. If you enter to portal,<br> you will be added to the game as soon as possible.`, `Enter portal.<br>Stay outside.`, `Enter waiting area.<br>Stay outside.`, `The red team has taken control of the eastern graveyard.`, `The blue team has taken control of the soul obelisk.`, `The blue team has lost control of the western graveyard.`, `You can't attack players on your own team.`, `Oh dear, you are dead!` | [youtube](https://www.youtube.com/watch?v=Qbp2sNpmcGI) |
| `You now have a higher priority to enter a game of Soul Wars.`, `That familiar's owner is on your side.`, `You have run out of Prayer points; you can recharge at an altar.`, `This is not your familiar.`, `If you die, you must wait 15 seconds before you can rejoin the battle.`, `You can only exit this sanctuary, not enter it.`, `A magical force stops you from moving.` | [youtube](https://youtu.be/JkBglTUArwI?t=28) |
| `Thank you, your abuse report has been received.`, `Your familiar is too far away to use that scroll, or it cannot see you.`, `You have been poisoned!`, `The power of the light fades. Your resistance to melee attacks returns to normal.`, `That's all well and good, but what do I get out of all of<br> this?` | [youtube](https://youtu.be/zTWzmM-eKR4?t=1) |
| `You left a game of Soul Wars early, and must wait 2 minutes<br> before you can join another.`, `The time is now! Crush their souls!` | [youtube](https://youtu.be/-uKPAUvkr7s?t=28) |
| `This is a battleground, not a circus.`, `That player does not currently need bandaging.` | [youtube](https://youtu.be/GvchY9sCiRE?t=47) |
| `Warning: Your activity bar is getting low! Help out in the battle to refill it!`, `You bury the bones and slightly restore your avatar's Slayer level.`, `You bury the bones, but they fail to restore your avatar's Slayer level.` | [youtube](https://youtu.be/orB2hiynjN0?t=41) |
| `Your Slayer level is not high enough to attack this avatar.` | [youtube](https://youtu.be/F9-63V-ltpk?t=142) |
| `You can get 5250 Strength experience for every Zeal you spend.`, `You spend 100 Zeal and receive 577500 Strength experience in return.`, `You've just advanced a Strength level! You have reached level 82.`, `Congratulations! You've just reached Combat level 98!` | [youtube](https://youtu.be/RZ_myxfDmAE?t=26) |

# Cooking

| Message | Link |
|---|---|
| `You fill the pie with meat.`, `You successfully bake a tasty meat pie.` | [youtube](https://youtu.be/BYVh29pUVGw?t=351) |
| `You add the anchovies to the pizza.` | [youtube](https://youtu.be/8VNpKEoWWmg?t=30) |
| `You add the tomato to the pizza.`,
`You add the cheese to the pizza.`,
`I need to cook this pizza before I can add meat to it.`,
`You successfully bake a pizza.`,
`You add the meat to the pizza.`,
`You eat half of the meat pizza.` | [youtube](https://www.youtube.com/watch?v=q0-4XquOpio) |
| `What type of dough would you like to make?<br>Choose a number, then click the dough to begin.`,
`You mix the water and flour to make some bread dough.`,
`You mix the water and flour to make some pastry dough.`,
`You put the pastry dough into the pie dish to make a pie shell.`,
`You need to precook the meat`,
`You fill the pie with redberries.`,
`Choose how many you wish to cook,<br>then click on the item to begin.`,
`You accidentally burn the pie.`,
`You successfully bake a delicious redberry pie.`,
`You remove the burnt pie from the pie dish.`,
`You fill the jug from the fountain.`,
`You cook a piece of beef.`,
`You accidentally burn the meat.` | [youtube](https://www.youtube.com/watch?v=jHE4srX51E8) |
| `You dry a piece of beef and extract the sinew.` | [youtube](https://www.youtube.com/watch?v=5td2no57VPg&t=154s) |
| `You fill the jug from the fountain`, `You squeeze the grapes into the jug. The wine begins to ferment.` | [youtube](https://youtu.be/FJOiEFGI6KA?t=27) |

# Castle wars
| Message | Link |
|---|---|
|`You can't wear hats, capes or helms in the arena.`,
`There isn't enough space on this team!`
`You're wearing objects of my ignorant brothers and you come to<br>me? Such treachery must be rewarded! Enjoy some time in the most<br> mischievous of forms.`
`It's shut, you'll have to break it down.`
`You must spend at least 15 minutes in a game to earn tickets.` | [youtube](https://www.youtube.com/watch?v=zEIcPSe7WN0) |
| `You put out the fire!` | [youtube](https://youtu.be/3EEZ49NasVw?t=98) |


# Fist of guthix
| Message | Link |
|---|---|
|`The first round of the game is about to begin.`
`The effectiveness of your stats has been reduced.`
`You take a magical stone from the dispenser.`
`You cannot attack with the stone!`
`You forfeit and lose the game.`
`You can only attack your opponent!` |[youtube](https://www.youtube.com/watch?v=WoAI_a0Zw40)|


# Canoes
| Message | Link |
|---|---|
| `You arrive at the Champions' Guild.`
`The canoe sinks into the water after the hard journey.` |[youtube](https://youtu.be/OQQMUhTRG74?t=135)|


# Pets
| Message | Link |
|---|---|
|`The kitten gobbles up the fish.` |[youtube](https://youtu.be/FqD5LXcPyoY?t=1)|

# Light sources
| Message | Link |
|---|---|
| `You extinguish the lantern.`
`You extinguish the helmet.`
`You extinguish the candle.`
`You light the helmet's lamp.`
`You light the lantern.`
No message for lighting candles. |[youtube](https://www.youtube.com/watch?v=FqD5LXcPyoY)|

# Crafting
## Glassblowing
| Message | Link |
|---|---|
| `You make a fishbowl.` |[youtube](https://youtu.be/55wh8vQOh_I?t=116)|
| `You make a light orb.` |[youtube](https://youtu.be/wwLYPZg_VmY?t=130)|
| `You make an orb.` |[youtube](https://youtu.be/_tljj6hB93c?t=149)|
| `You make a lantern lens.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=247)|

## Snelm
| Message | Link |
|---|---|
| `You chisel the shell into a helmet.` | [youtube](https://youtu.be/149b40qPzrE?t=38) |

## Crab armour
| Message | Link |
|---|---|
| `You chisel the carapace into a helmet.`, `You chisel the claw into a gauntlet.` | [youtube](https://youtu.be/qecGjcWRTPg?t=217) |
| `Oops! You accidentally break the shell.` | [youtube](https://youtu.be/5hK1vaHG5EM?t=352) |


# Lantern
| Message | Link |
|---|---|
| `You make a bullseye lantern.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=90)|
| `You build the Bullseye lantern.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=269)|
| `You refine some swamp tar into lamp oil.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=379) [youtube](https://youtu.be/o6nntpveh6Y?t=279)|
| `You need to add lamp oil before you can use it.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=393)|
| `You put some oil in the lantern.` |[youtube](https://youtu.be/d0Y0O2Hbcfg?t=398) [youtube](https://youtu.be/o6nntpveh6Y?t=279)|

# King black dragon
| Message | Link |
|---|---|
|`Warning! Pulling the lever will teleport you deep into the Wilderness.`,`Are you sure you wish to pull it?`,`Yes I'm brave`,`Eeep! The Wilderness... No thank you.`,`Yes please, don't show this message again.`|[youtube](https://youtu.be/4QrNJgJPiUY?si=OuRd3EOZl2eNKzX4&t=16)|
| `... and teleport into the Wilderness.`, `... and teleport out of the Wilderness.` |[youtube](https://youtu.be/4QrNJgJPiUY?si=bqtB8KJGp6m4-gJZ&t=24)|
| `... and teleport into the lair of the King Black Dragon!`, `... and teleport out of the dragon's lair.`, `Your shield absorbs most of the dragon's toxic breath.`, `Your shield absorbs most of the dragon's fiery breath.`, `Your shield absorbs most of the dragon's shocking breath.` |[youtube](https://www.youtube.com/watch?v=_t6Q2URu3ho)|


# Item teleports
| Message | Link |
|---|---|
|`Your games necklace has seven uses left.`|[youtube](https://youtu.be/qoidNIWXq2Q?si=4w2ZlVCaolrctGNK&t=57)|
|`Your games necklace crumbles to dust.`|[youtube](https://youtu.be/_ULvKAJO8kM?si=QWYmPJ2nspXJmyY_&t=48)|
|`You rub the necklace...`, `Your skills necklace has three charges left.`|[youtube](https://youtu.be/_hMk0QwhTzs?si=yIkEyMpE-4oFQZfQ&t=114)|
|`Your ring of duelling has one use left.`|[youtube](https://youtu.be/nKZ_Vq78fIQ?si=_AZREc98KoqGPwug&t=85)|
| `You use your skills necklace's last charge.`, `Your ring of duelling crumbles to dust.` |[youtube](https://youtu.be/2wgWB9U5Ju8?si=gq6LT778sbGAzt8b&t=148)|
|`You rub the pendant...`, `Your Dig Site pendant crumbles to dust.`|[youtube](https://youtu.be/EMbozBewf04?si=LGLRvtsSXBNt_R8W&t=122)|
| `Your Dig Site pendant has three uses left.` | [youtube](https://youtu.be/Cpblc8LILg4?si=esGWVbr3Ym8uYBeK&t=29)|
| `Your combat bracelet has three charges left.` | [youtube](https://youtu.be/53Z9rXjnkEA?si=zR4RlJTEL-RCZLoW&t=276) |
| `You dip the amulet in the fountain...`, `You feel a power emanating from the fountain as it<br>recharges your amulet. You can now rub the amulet to<br>teleport and wear it to get more gems whilst mining.`, `You rub the amulet...`, `Your amulet has three charges left.` | [youtube](https://youtu.be/-uG0_53wk0E?si=2iMmgsyvsKIhPcYZ&t=61) |
| `You rub the ring...`, `Your ring of slaying has one use left.` | [youtube](https://youtu.be/K_bUKpYX5xs?si=BF6-xOmDPsg7b-K9&t=172) |
| `You use your amulet's last charge.`, `Your amulet has three charges left.` | [youtube](https://youtu.be/BLfNqdkXgNw?si=VTJdWbi2pDDK1cTx&t=12) |
| `The sceptre has run out of charges. Talk to the Guardian Mummy in the Jalsavrah Pyramid to recharge it.` | [youtube](https://youtu.be/HnQS5NGAp4g?si=NP9Ar5as8K300rXF&t=202) |
| `You rub the bracelet...`, `Your combat bracelet has three charges left.`, `Your ring of wealth has no use left.`, `You dip the ring in the fountain...`, `You feel a power emanating from the fountain as it<br>recharges your ring. You can now rub the ring to<br>activate a teleport, you can wear it to get luckier<br> from certain loots and drops.`, `The guards salute you as you walk past.`, `You feel a power emanating from the totem pole as it<be>recharges your bracelet. You can now rub the bracelet<br>to teleport and wear it to get information while on a<br>Slayer assignment.`, `You touch the jewellery against the totem pole...`, `Your ring of wealth has on use left.` | [youtube](https://youtu.be/2Tmp37z3rUs?si=puNMLFNp-MB6DPO7&t=245) |
| `You empty the ectoplasm onto the ground around your feet...`, `You refill the ectophial from the Ectofuntus.` | [youtube](https://youtu.be/RF2unpk6X_M?si=96i2KFHN9pTC5Yt5&t=85) |
| `Your Camulet has run out of teleport charges. You can renew them<br>by applying camel dung.`, `You rub the amulet...`, `Your Camulet has no charges left.` `You can recharge it by applying camel dung.`, `You recharge the Camulet using camel dung. Yuck!` | [youtube](https://youtu.be/An8-QZnFEhY?si=QgIOkIu9cVwlph7L&t=99) |

# Runecrafting
| Message | Link |
|---|---|
|`Your pouch is full.`, `You feel a powerful force take hold of you...`, `You bind the temple's power into Nature runes.`, `Your pouch has no essence left in it.`, `You need a hatchet to chop down this tree.`, `You do not have a hatchet that you have the Woodcutting level to use.` |[youtube](https://youtu.be/kesE3bv9uRA?si=Wyy_tJlL9WXhOiOu&t=35)|
|`You fail to distract them enough to get past.`, `You are not agile enough to get through the gap.`, `Your pouch has decayed through use.`, `Can you repair my pouches?<br> I think they might be degrading.`, `A simple transfiguration spell should resolve that for<br>you.<br>Now leave me be!` | [youtube](https://youtu.be/BLfNqdkXgNw?si=VTJdWbi2pDDK1cTx&t=12) |
| `Your pouch has decayed through use.` | [youtube](https://youtu.be/lbhjhyaQMZc?si=kRJAA_iiNSKJM-Ci&t=217) |

# Farming
| Message | Link |
|---|---|
|`You need a gardening trowel to do that.`, `This is a herb patch. The soil has not been treated. The patch is empty and weeded.`, `The leprechaun exchanges your items for banknotes.` |[youtube](https://youtu.be/2wgWB9U5Ju8?si=Vxqqw6bD7uIjwk7L&t=56)|
|`You examine the tree for signs of disease and find that it is in perfect health.`, `You examine the bush for signs of disease and find that it is in perfect health.`, `This is a bush patch. The soil has not been treated. The patch is fully grown.`, `Are you sure you want to dig up this patch?`, `Yes, I want to clear it for new crops.`, `No, I want to leave it as it is.` |[youtube](https://youtu.be/53Z9rXjnkEA?si=8w1RC8ePTKwEIpLc&t=67)|
|`You open the compost bin.`, `You begin to harvest the allotment.`, `The allotment is now empty.`, `You treat the allotment with supercompost.`, `You plant 3 watermelon seeds in the allotment.`, `You begin to harvest the herb patch.`, `You plant a snapdragon seed in the herb patch.`, `You don't have that many buckets!`, `You pick some deadly nightshade.`, `You plant a belladonna seed in the belladonna patch.`, `You pick a Bittercap mushroom.`, `You carefully pick a spine from the cactus.`, `You start digging the farming patch...`, `You have successfully cleared this patch for new crops.` | [youtube](https://youtu.be/K_bUKpYX5xs?si=UMR51FNxTBcNQeZs&t=122)|
|`The sceptre has one charge left.`|[youtube](https://youtu.be/nlfH9Wpvvtw?si=q6M9cI_F-ZfvLdQg&t=76)|

## Misc
| Message | Link |
|---|---|
| `You use the wool with the rabbit foot to make a lucky necklace.` | [youtube](https://youtu.be/n5t7C28-2eI?t=555) |
| `You add the feathers to the coif to make a feathered headdress.` | [youtube](https://youtu.be/9f-rKKzABT8?t=29) |
| `You put some string on your amulet.` | [youtube](https://www.youtube.com/watch?v=xRUFAP3taHk) |
| `You have no food equipped for the salamander to use.` | in-game 2023-11-10 |
| `You need to know barbarian hasta skills to equip this weapon.` | in-game 2023-11-10 |
| `This isn't a good time to try to scry.` | in-game scrying orb while in combat (anim 5354) 2023-11-10 |
| `Only a sharp blade can cut through this sticky web.`, `You slash the web apart.`, `You fail to cut through it.` | osrs 2023-11-30 |
| `A root springs from the ground by your feet. What could have caused that?`, `The trip to Karamja will cost you 30 coins.` | [youtube](https://youtu.be/2wgWB9U5Ju8?si=Vxqqw6bD7uIjwk7L&t=56) |
| `You hand your Shantay pass to the guard and pass through the gate.`, `You have started The Temple at Senntisten quest.` | [youtube](https://youtu.be/EMbozBewf04?si=DKViC_YH0v5blpYc&t=108)|
|`You have recharged your Summoning points.` | [youtube](https://youtu.be/TKIKxekhrEk?si=ND2HKYs1PbBnA659&t=2)|
| `You don't have any jewellery that the totem can recharge.` | [youtube](https://youtu.be/T9lD_ui9p9o?si=r1cKBUdzssTHFall&t=26)|
| `You have 2 minutes before your spellbook changes back to the Lunar Spellbook!` | [youtube](https://www.youtube.com/watch?v=trGELTzyzmU) |


## Coal bag
| Message | Link |
|---|---|
| `You add the coal to your bag.`, `You withdraw some coal.` | [youtube](https://youtu.be/g7wkGrezwyo?si=vwiA_xj4Ws7SmAQT&t=35) |
| `Your coal bag is already full.` | [youtube](https://youtu.be/i95enh-MZ4A?si=oy8rxtRoIAsZKYKb&t=97) |
| `Your coal bag is empty.`, `Your coal bag has 3 pieces of coal in it.` | [youtube](https://youtu.be/MS7BbwnsnMQ?si=dGJmyEq6DoWZL6jD&t=304) |
| `There is no coal in your bag to withdraw.` | [youtube](https://youtu.be/0-n-mEkMJJI?si=cjzDRT2478pKs1J8&t=15) |


## Agility

### Barbarian
| Message | Link |
|---|---|
| `The rope swing is being used at the moment.` | [youtube](https://www.youtube.com/watch?v=VDhirmRmsHo) |
| `You walk carefully across the slippery log...`, `... and make it safely to the other side.`, `... but you lose your footing and fall into the water.`, `Something in the water bites you.` | [youtube](https://youtu.be/CQbXl4dh6cY?si=X3H0dSTuudle1k3k&t=166) |
| `You skillfully swing across.`, `You climb the netting...`, `You put your foot on the ledge and try to edge across...`, `You skillfully edge across the gap.`, `You climb the low wall...` | [youtube](https://youtu.be/YKpB7AXnZ-Y?si=Mt0ZuItW4tcARqxL&t=98) |


[Adventurers Log](https://youtu.be/GdkRJLVSqJE?si=7k0bi1mSP07-Lvbb&t=148)


---

## Game Objects

_Source: Game-Objects.md_

Game objects make up the entire game world from scenery and rocks on the floor, to castle walls and trees there are over 3.5 million game objects across the world. The majority of objects are inactive and never change, other objects are active and can be interacted with by [Players](players).

## Configuration

Objects have one type of [config file](config-files#objects) for storing information ending in `.objs.toml`

```toml
[crashed_star_falling_object]
id = 38659
examine = "It's a crashed star."

[crashed_star_tier_9]
id = 38660
collect_for_next_layer = 15
examine = "This is a size-9 star."
```

This information can be obtained using `obj.def["examine", ""]` see [definition extras](definitions#definition-extras) for more info.

### Spawns
Permanent object changes should be added to [`/data/*.obj-spawns.toml`](https://github.com/GregHib/void/blob/main/data/area/misthalin/edgeville/edgeville.obj-spawns.toml) files to be spawned every time the world loads:

```toml
spawns = [
  { id = "crate", x = 3081, y = 3488, type = 10, rotation = 1 },
]
```

## Finding Objects

Objects are all stored in the [`GameObjects`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/obj/GameObjects.kt) map which can be searched by `Tile` and id, layer or shape:

```kotlin
val tile = Tile(3200, 3200)
val objectsUnder = GameObjects.at(tile) // Get all objects on a tile

val gameObject = GameObjects.find(tile, "large_door_opened") // Get specific object by id

val gameObject = GameObjects.getShape(tile, ObjectShape.WALL_CORNER) // Get specific object by shape

val gameObject = GameObjects.getLayer(tile, ObjectLayer.WALL_DECORATION) // Get specific object by layer
```

## Adding objects

New objects can be spawned with `GameObjects` `add()` function allowing scripts to modify the world at any time

```kotlin
// Temporarily add a fire for 60 ticks
val obj = GameObjects.add("fire", tile, shape = ObjectShape.CENTRE_PIECE_STRAIGHT, rotation = 0, ticks = 60)
```
> [!WARNING]
> Adding a object will override existing objects with the same `ObjectLayer`.

## Removing objects

Objects can also be removed temporarily or permanently

```kotlin
objectOperate("Pick", "wheat") {
    target.remove(30) // Remove the object for 30 ticks
    message("You pick some wheat.")
}
```

## Replacing objects

One object can also be replaced with another temporarily or permanently

```kotlin
objectOperate("Slash", "web*") {
    anim("dagger_slash")
    target.replace("web_slashed", ticks = TimeUnit.MINUTES.toTicks(1)) // Replace the object for 100 ticks
}
```

## Object Data

Due to the shear number of objects in the world they are stored with little information compared to other entities, a game object stores:

| Data | Description |
|---|---|
| Id | The type of object as a [String identifier](string-identifiers). |
| Tile | The position of the object as an x, y, level coordinate. |
| [Definition](definitions) | Definition data about this particular type of object. |
| [Shape](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/obj/ObjectShape.kt) | The type of object, used for layering and interaction path finding. |
| Rotation | A number between 0-3 which represents the direction the object is facing. |



---

## Game Settings

_Source: Game-Settings.md_

Game settings are listed in the [`game.properties`](https://github.com/GregHib/void/blob/main/game/src/main/resources/game.properties) file allows you to customise the server to your liking.

The most common and useful ones to players will be:

## Server name
```properties
# The name of the game server
server.name=Void
```

## Networking

```properties
# The port the game server will listen on
network.port=43594
```


## Admin
```properties
# The admin username (always sets administrative privileges on login)
development.admin.name=Greg
```

## Home spawn

```properties
# The default home spawn coordinates (X, Y, Level)
world.home.x=3221
world.home.y=3219
```

## Experience rates

```properties
# Experience multiplier (1.0 = normal XP rate)
world.experienceRate=1.0
```

## Bots

```properties
# The number of AI-controlled bots spawned on startup
bots.count=10

# Frequently between spawning bots on startup
bots.spawnSeconds=60
```

## Running
```properties
# Whether players energy drains while running
players.energy.drain=true
```

## Combat

```properties
# Whether reducing hits by equipment absorption is enabled in combat mechanics
combat.damageSoak=true

# Whether to spawn gravestones on death
combat.gravestones=true
```

## Skills

There's lots of properties for individual skills, quests, events and more.

```properties
# Degrade runecrafting essence pouches when in use
runecrafting.pouch.degrade=true

# Day of the week penguins reset on from 1 (Monday) to 7 (Sunday)
events.penguinHideAndSeek.resetDay=3

# Whether Stronghold of Security doors should give questions or not
strongholdOfSecurity.quiz=true
```




---

## Hunt modes

_Source: Hunt-modes.md_

Hunt modes are used by [NPCs](entities#npcs) to select targets from a collection of nearby [Entities](entities). Properties defined in `hunt_modes.toml` determines under what conditions entities can be targeted, which entities can be targeted, how often a new target is selected.

Hunt modes are use for mechanics such as aggression and hunter npcs getting caught in traps.

> [!NOTE]
> Aggression is usually `hunt_mode = "cowardly"` meaning aggressive so long as within a combat level threshold. `"aggressive"` hunt mode is used exclusively for being aggressive to players at all times

## Hunt modes

Hunt modes are specified in [`hunt_modes.toml`](https://github.com/GregHib/void/blob/main/data/entity/npc/hunt_modes.toml):
```toml
[cowardly]                     # Name of the hunt mode
type = "player"                # Target entity type
check_visual = "line_of_sight" # Only select targets that can be seen directly
check_not_combat = true        # Target can't be in combat
check_not_combat_self = true   # NPC can't be in combat
check_not_too_strong = true    # Targets combat level must be less than double of npc 
check_not_busy = true          # Target can't be doing something
```
And a hunt mode is assigned to an npc in it's `.npcs.toml` file:

```toml
[giant_spider]
id = 59
hunt_mode = "cowardly" # Hunt mode name
hunt_range = 1         # Check for entities up to 1 tile away
```

When a valid target is found a hunt event will be emitted:

```kotlin
huntFloorItem("ash_finder") { target ->
    interactFloorItem(target, "Take")
}
```

> Hunt events are: `HuntFloorItem`, `HuntNPC`, `HuntObject`, `HuntPlayer`

> [!NOTE]
> [Read more about hunt modes](https://github.com/GregHib/void/issues/307)


---

## Installation Guide

_Source: Installation-Guide.md_


Requirements:
- Java installed [Eclipse Java Download Link](https://adoptium.net/en-GB/temurin/releases?package=jre)
- 410MB storage space
- File extraction tool like WinRar, 7Zip or Windows 7/8/10/11 has one built in

# Step 1: Download the latest server .zip

[Server Release Page Link](https://github.com/GregHib/void/releases)

<img width="600" height="600" alt="image" src="https://github.com/user-attachments/assets/6cc9a961-88fb-4a63-bce4-25482e8b91aa" />

# Step 2: Download the latest client .jar

[Client Release Page Link](https://github.com/GregHib/void-client/releases)

<img width="600" height="450" alt="image" src="https://github.com/user-attachments/assets/50ac051a-60a2-4005-8b35-a0f0ccb34100" />

# Step 3: Download the latest cache .7zip

Make sure it's the newest date/latest version

[Cache List Link](https://mega.nz/folder/ZMN2AQaZ#4rJgfzbVW0_mWsr1oPLh1A)

<img width="600" height="350" alt="image" src="https://github.com/user-attachments/assets/3c470fba-0cd2-43f4-bf2e-4a467b767970" />


# Step 4: Extract the void.zip contents

<img width="600" height="425" alt="image" src="https://github.com/user-attachments/assets/5995999c-0e29-404e-9c30-4c3cae1c5aea" />

<img width="450" height="350" alt="image" src="https://github.com/user-attachments/assets/7c11ea26-3915-4dc9-92f7-f08c3c802535" />

# Step 4: Find the cache folder

In the extracted folder navigate to `data/cache/`
<img width="876" height="453" alt="image" src="https://github.com/user-attachments/assets/44d604ea-66bb-4a66-9d36-7f9379390f54" />
<img width="874" height="522" alt="image" src="https://github.com/user-attachments/assets/2ab6ce2f-00b2-4467-9caf-f8afefbd80c1" />

# Step 5: Extract the void-634-cache.7z contents

Extract all of the contents of the cache.7z into the `void/data/cache` folder

<img width="877" height="1030" alt="image" src="https://github.com/user-attachments/assets/34b1590f-aab5-471c-aa4e-16876d05fca5" />

# Step 6: Launch the server

Double click the run-server.bat file

<img width="873" height="361" alt="image" src="https://github.com/user-attachments/assets/a6e8fbbf-b787-46e5-84ae-b3a47773c288" />

You know the server is running if you see the message:
`INFO [Main] Void loaded in ... ms`

<img width="1126" height="627" alt="image" src="https://github.com/user-attachments/assets/b7b0c125-139b-40e9-90d0-0ab743977c00" />

If you run into any errors see the [troubleshooting guide](troubleshooting)

# Step 7: Launch the client

Double click the client.jar and the client should load

<img width="778" height="749" alt="image" src="https://github.com/user-attachments/assets/60e2080f-4c31-4384-aac8-35c7ee2c8605" />


# Step 8: Login and have fun!

Just login with any user-name and password; no account creation required!

<img width="767" height="535" alt="image" src="https://github.com/user-attachments/assets/a04ced29-4391-4b76-ad8f-3a6775330c51" />





---

## Instances

_Source: Instances.md_

An Instance is an empty area of the game world which is temporarily allocated for a quest custscene, group activity, or minigame so that players can play seperated from other players doing the same activity at the same time.

[Instances](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/map/instance/Instances.kt) can reserve small or large areas depending on the activity.
* A small instance is 2x2 Regions (128x128 tiles) designed for a 1x1 region with padding.
* A large instance is 5x5 Regions (320x320 tiles) designed for a 3x3 region with padding.

> [!NOTE]
> You can read more about instancing and padding at [osrs-docs/instancing](https://osrs-docs.com/docs/mechanics/instancing/)

## Instances

Allocating an instance is straight-forward:

```kotlin
val region = Instances.small() // Allocate a small or large instance

// play the activity until finished

Instances.free(region) // Free up the instance for use by something else
```

> [!IMPORTANT]
> Instance regions must be freed up after use otherwise the world could run out of instance spaces.
> There are maximum of 1377 small instances and 700 large instances at any given time.


## Dynamic zones

In most servers Instancing is synonymous with being able to dynamically change maps, in Void these are two separate concepts. Instancing is allocating empty map space, Dynamic Zoning is changing what maps is at a given location and sending those changes to players clients.

Dynamic zones can mix and match Zones or whole Regions from multiple maps into one, optionally rotating them.

```kotlin
val zones: DynamicZones by inject()

enterArea("demon_slayer_stone_circle") {
    val instance = Instances.small() // Allocate a region

    val region = Region(12852)
    zones.copy(region, instance) // Copy the map to the region
    val offset = instance.offset(region) // Calculate the new relative location


    player.tele(Tile(3222, 3367).add(offset) // Teleport the player to the correct relative location
    
    // play the cutscene ...
    
    zones.clear(instance) // Remove the dynamic zones
    Instances.free(instance) // Free up the region
}
```

> Dynamic regions aren't limited to instances either, you can modify the game map and it will update for players within sight in real time, although I'm not sure why you'd want to; there are better ways of modifying the map permenantly.


## Cutscenes

Cutscenes frequently use this combination of [Instance](#instance) and [Dynamic Zones](#dynamic-zones) along with hiding the [GameFrame](interfaces#gameframe-interfaces) tabs and fading the game screen out, so there's a helper function for it.

```kotlin
suspend fun CharacterContext.cutscene() {
    val cutscene = startCutscene("the_cutscene_name", region)

    // ... setup the cutscene here 

    // Optional: set a listener which frees up the region if the player logs out halfway through
    cutscene.onEnd {
        // Action on early exit e.g. logout
    }

    // play the cutscene

    cutscene.end(this) // Make sure the instance is freed
}






---

## Interaction

_Source: Interaction.md_

A lot of [Entities](entities) are interactable, allowing other entities to perform actions on them like Talk-to, Follow, Attack, Trade etc... [Interact](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/mode/interact/Interact.kt) is a [Movement Mode](movement-mode) which moves a character in range of a target entity before performing an interaction.

```kotlin
val interaction = FloorItemOption(player, floorItem, "Light")
player.mode = Interact(player, floorItem, interaction)
```

## Interaction types
Interactions fall into two categories: Approach and Operate.

* Approach interactions trigger when the character is within 10 tiles of the target, this is mainly used for [ranged combat](combat-scripts) and magic spells.
* Operate interactions are triggered when the character is next to the target.

> [!NOTE]
> In RuneScript these are called `ap` and `op` scripts which stand for Approachable and Operable. We have used alternative verbs.
>
> You can read more about interactions at [osrs-docs/entity-interactions](https://osrs-docs.com/docs/mechanics/entity-interactions/).

There are many types of interactions:

| Interaction | Description |
|---|---|
| [`InterfaceOption`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/InterfaceOption.kt) | Clicking [Interfaces](interfaces) or Item . |
| [`Command`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/event/Command.kt) | A command typed into the client console. |
| [`ItemOn...`](https://github.com/GregHib/void/tree/main/engine/src/main/kotlin/world/gregs/voidps/engine/client/ui/interact) | Using an item on a type of [Entity](entities). |
| [`NPCOption`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/npc/NPCOption.kt) | An option on an [NPC](entities#npcs). |
| [`PlayerOption`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/PlayerOption.kt) | An option on a [Player](entities#players). |
| [`FloorItemOption`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/item/floor/FloorItemOption.kt) | An option on a [Floor item](entities#floor-items). |
| [`ObjectOption`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/obj/ObjectOption.kt) | An option on a [Game object](entities#game-objects). |
| [`CombatInteraction`](https://github.com/GregHib/void/blob/main/game/src/main/kotlin/world/gregs/voidps/world/interact/entity/combat/CombatInteraction.kt) | In [combat](combat-scripts) with a target. |
| [`InventoryOption`](https://github.com/GregHib/void/blob/main/game/src/main/kotlin/world/gregs/voidps/world/interact/entity/player/equip/InventoryOption.kt) | An option on an [Inventory item](inventories) on an Interface. |

## Entity interactions

Most entity interactions have at least 4 helper functions: two for player operating or approaching an entity, and two for a character operating or approaching an entity. Their usage and will vary depending on the interaction and the [entity](entities) being interacted with. See [each class](#interaction-types) for the complete list of functions available.

### Npcs

```kotlin
npcOperate("Talk-to", "ellis") {
}
npcApproach("Talk-to", "banker*") {
}
characterOperateNPC("Follow") {
}
characterApproachNPC("Attack") {
}
```

### Commands

```kotlin
command("reset_cam") {
    player.client?.clearCamera()
}
modCommand("clear") {
    player.inventory.clear()
}
adminCommand("bank") {
    player.open("bank")
}
```

### Inventories

```kotlin
inventoryOptions("Eat", "Drink", "Heal") {
}
inventoryOption("Bury", "inventory") {
}
inventoryItem("Rub", "amulet_of_glory_#", "inventory") {
}
```

### Operation Arrival

Most cases you'll want to wait until the player is right next to the target before interaction. 

- todo pic

However when an interaction pops up an [interfaces](interfaces) to begin with interactions should start sooner by specifying `arrive = false` in the interaction parameters:

```kotlin
itemOnObjectOperate("*_mould", "furnace*", arrive = false) {
    player.open("make_mould${if (World.members) "_slayer" else ""}")
}
```

## Wildcards

When interacting with entities often times you'll want to write code to interact with more than one at a time, for example bankers all have the same [dialogues](dialogues) so instead of writing an npc approach script for `banker_al_kharid`, `banker_zanaris`, `banker_shilo_village` etc.. individually you can write one script with a [wildcard character](https://support.microsoft.com/en-gb/office/examples-of-wildcard-characters-939e153f-bd30-47e4-a763-61897c87b3f4) to match a pattern.

Currently Void supports two wildcard characters:

| Symbol | Pattern |
|---|---|
| `*` | Matches any amount of any character |
| `#` | Matches a single digit |

So you can match all bankers using `banker_*` instead of listing them individually.
Note: The real wildcard uses `banker*` to also match `banker` as well.

Matching a single digit is useful mainly for items with [charges](degrading#charges) like jewellery and potions. `amulet_of_glory_#` will match `amulet_of_glory_1`, `amulet_of_glory_2`, `amulet_of_glory_4` etc... (but not `amulet_of_glory_10` which in most cases is fine as charges rarely go above 8).

The same applies for items, interfaces, components and all other [string ids](string-identifiers) used in [script functions](scripts) unless otherwise specified.



---

## Interfaces

_Source: Interfaces.md_

Interfaces are used to display information to a player, whether that's a picture, rectangle, line of text, or a button to click, each interface is made up of a list of different types of components.

## Component types

| Name | Description |
|---|---|
| Container | Parent to a group of child components. |
| Rectangle | Filled or just the outline. |
| Text | Line or paragraph of text. |
| Sprite | Image, picture or icon. |
| Model | Rendered model of a character, object or item. |
| Line | Thin or thick with a colour. |

Due to the nested nature of containers, interfaces can be inserted into one another to create a hierarchy. The majority of interfaces are used inside one another, one that are not we will refer to as full-screen interfaces.

![Screenshot 2024-02-16 175117](https://github.com/GregHib/void/assets/5911414/df6e3a45-1bd3-403e-b716-df28430bde84)

## Fullscreen
Full-screen interfaces as the name suggests, take up the entire client screen, there can only be one full screen interface displayed at a time.

Examples of fullscreen interfaces would be:
* Login interface
* Fixed or resizable Gameframe
* World map

![Screenshot 2024-02-16 174825](https://github.com/GregHib/void/assets/5911414/64a58a52-c891-40e2-9263-ce854568568c)

## Gameframe interfaces

The majority of gameplay resolves around the fixed and resizable "gameframe" interfaces, known by jagex as `toplevel` and `toplevel_full` respectively.

The Gameframe interface is split up into multiple areas to place other interfaces into:

![Screenshot 2024-02-16 174118](https://github.com/GregHib/void/assets/5911414/c647b385-6228-4e3f-b35a-b590b495c602)

### Main screen & Menus (Blue)

The main game screen is used primarily for displaying large interfaces which block the players view.

* Settings
* Bank
* Equipment bonuses

![Screenshot 2024-02-17 172526](https://github.com/GregHib/void/assets/5911414/6dd25bed-91a7-497d-8b89-9691f9b5a09f)


#### Overlays

Overlays are smaller interfaces for displaying contextual information during activities and minigames.

* Godwars kills
* Bounty hunter info
* Wilderness level

![Screenshot 2024-02-17 173211](https://github.com/GregHib/void/assets/5911414/75b8878c-b01a-4324-acba-24b3ef96aaf7)

### Chat screen & Dialogues (Green)

The chat screen is where communication and input interfaces are displayed

* Chat
* Quick chat
* Text input

![Screenshot 2024-02-17 173310](https://github.com/GregHib/void/assets/5911414/64a24cfc-9ef5-4b2d-a404-e9d443fe30af)


### Tabs & Side interfaces (Orange)

Tab interfaces are always avaiable for players to use and interact with their player

* Inventory
* Spellbook
* Logout

![Screenshot 2024-02-17 173446](https://github.com/GregHib/void/assets/5911414/bc564d3e-48c0-45ee-845a-244573ffdd1d)

## Context menus

The pop-up menu displayed when right click on interfaces is known as the context or right-click menu, it is the list of potential actions the player can take.

![Screenshot 2024-02-17 175012](https://github.com/GregHib/void/assets/5911414/d51de257-fbdb-4a9e-b941-70bc82751f95)

# Implementation

Interfaces are defined in `*.ifaces.toml` files and types in `interface_types.toml`.

```toml
[price_checker]                # Name of the interface to be used as an id
id = 206                       # The interface id
type = "main_screen"           # The type of interface - location to attach the interface - interface_types.toml

[.overall]                     # Text component name
id = 20                        # Text component id

[.total]
id = 21

[.limit]
id = 22

[.items]                       # A container component
id = 18                        # Components id
inventory = "trade_offer"      # The id of the item container linked to this component
options = {                    # Item right click options
  Remove-1 = 0,                # Option and the index in the context menu
  Remove-5 = 1,
  Remove-10 = 2,
  Remove-All = 3,
  Remove-X = 4,
  Examine = 9
} 
```

## Clicking

Execute code when a player clicks on a certain components option

```kotlin
//               Option, component, interface
interfaceOption("Remove-5", "price_checker:items") {
  // ...
}
```

## Opening

Interfaces can be opened for a player using the interface id

```kotlin
player.open("price_checker")
```

You can also subscribe using [events](Events) in order to do things when interfaces are opened

```kotlin
interfaceOpen("price_checker") {
  // ...
}
```

## Closing

Closing an specific interface can be done, however it's normally done by type
```kotlin
player.close("price_checker")
```

```kotlin
player.closeMenu() // Close whatever menu is open (if any)
player.closeDialogue() // Close any dialogues open
player.closeInterfaces() // Both menu and dialogues
```

Code can also be executed on closing an interface
```kotlin
interfaceClose("price_checker") {
  // ...
}
```

## Modifying

* Model Animations
* Text
* Visibility (show/hide)
* Sprite
* Colour
* Item

```kotlin
// Interface, component, hidden
player.interfaces.sendVisibility("price_checker", "limit", false)
```

* Send inventory
* Unlock inventory options
* Lock inventory options

```kotlin
// Interface, component, item slot range
player.interfaceOptions.unlockAll("price_checker", "items", 0 until 28)
```


---

## Inventories

_Source: Inventories.md_

# Inventories

Inventories are a fixed size grid of slots capable of holding an amount of items e.g. `Item("bucket_noted", 15)`. The player backpack (aka *the* inventory), worn equipment, and beast of burden are examples of inventories used by players. [Shops](shops) are an example of inventories used by npcs. Inventory definitions are found in [`*.invs.toml`](https://github.com/GregHib/void/blob/main/data/entity/player/bank/bank.invs.toml) files.

## Item stack behaviour

Items come in two categories: stackable and non-stackable. Stackable items can have up to [2,147 million](https://en.wikipedia.org/wiki/2,147,483,647) items within one slot. Non-stackable items can only have 1 item per slot. 

This means having two non-stackable items like a `bronze_dagger` will take up two slots, where two stackable `bronze_dagger_noted` will take up only one slot.

![Screenshot 2024-02-19 174140](https://github.com/GregHib/void/assets/5911414/c844fd42-e06a-4f16-a486-476605eda491)

## Stack modes

Each inventory has a stack mode which can change the stacking behaviour, banks for example will always stack items even if they are non-stackable.

```toml
[bank]
id = 95
stack = "Always"
```

![Screenshot 2024-02-19 175815](https://github.com/GregHib/void/assets/5911414/c93b557f-5b6b-4e47-938f-210ac869bf16)

| Stack mode | Behaviour |
|---|---|
| DependentOnItem | The default; stacks stackable items, doesn't stack non-stackable items |
| Always | Always stacks items regardless if they are stackable or non-stackable |
| Never | Never stacks items even if they are stackable |

> [!NOTE]
> Custom stacking rules can also be created, see [ItemStackingRule.kt](https://github.com/GregHib/void/blob/c4d6e458984c742d2d68a6cd685727ee9bbeda0d/engine/src/main/kotlin/world/gregs/voidps/engine/inv/stack/ItemStackingRule.kt#L8).

An item can be checked if stackable against a certain inventory using the `stackable()` function:
 
```kotlin
val stackable = player.bank.stackable("bronze_dagger") // true
```

## Getting an inventory

Player's have a map of inventories connected to them, you can access one by calling:

```kotlin
val equipment = player.inventories.inventory("worn_equipment")
```

> [!TIP]
> A lot of the commonly used player inventories have helper functions for easy access e.g. `player.equipment`
> ```kotlin
> val Player.equipment: Inventory
>     get() = inventories.inventory("worn_equipment")
> ```

## Finding an item

You can check if an inventory has an item or a number of items using `contains`

```kotlin
if (!inventory.contains("tinderbox")) {
    message("You don't have the required items to light this.")
    return
}
```

And count exactly how many of an item is in an inventory, regardless of stack behaviour.

```kotlin
val goldBars = player.inventory.count("gold_bar")
```

## Free space

There are a number of functions to help manage the free slots in an inventory.

| Function | Description |
|---|---|
| `inventory.isEmpty()` | Check if an inventory has no items inside. |
| `inventory.spaces` | The number of empty slots. |
| `inventory.isFull()` | Check if an inventory has no empty slots. |
| `inventory.count` | The number of item slots in use. |
| `inventory.freeIndex()` | Get the index of an empty slot. |
| `item.isEmpty()` | Check if an item slot is empty. |
| `item.isNotEmpty()` | Check if an item slot is not empty. |

# Inventory Modifications

## Adding items

Adding a single item or a stack of items is simple with the `add` helper function

```kotlin
player.inventory.add("coins", 100)
```

If an inventory is full the item won't be added and `add` will return false allowing you to run different code depending on the outcome.

```kotlin
if (player.inventory.add("silverlight")) {
    npc<Furious>("Yes, I know, someone returned it to me. Take better care of it this time.")
} else {
    npc<Furious>("Yes, I know, someone returned it to me. I'll keep it until you have free inventory space.")
}
```

> [!NOTE]
> Adding two non-stackable items to a normal inventory will add the items to two empty slots

## Removing items

Removing items is much the same as adding, the `remove` item will return true when the required number of items was removed, and false when the inventory doesn't have the required amount.

```kotlin
player.inventory.remove("coins", 123)
```

## Clearing items

Clearing will remove an item from a given slot, regardless of amount

```kotlin
player.inventory.clear(4)
```

Clear without a slot will remove all items from an inventory

```kotlin
player.loan.clear()
```

## Replacing items

Replace is commonly used to switch one non-stackable item for another of a different type

```kotlin
inventoryItem("Empty", "pot_of_flour", "inventory") {
    player.inventory.replace("pot_of_flour", "empty_pot")
}
```

## Moving items

Move will take all of the items on one slot add it to another slot in the same or a different inventory only if the target slot is empty.

```kotlin
inventoryOption("Remove", "worn_equipment") {
    val success = player.equipment.move(slot, player.inventory) // true
}
```

## Other modifications

There are plenty of other helper functions to explore that should cover the majority of use-cases, for a full list see [InventoryOperations.kt](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/inv/InventoryOperations.kt).

| Function name | Description |
|---|---|
| `swap` | Swap the items in two slots, in the same or different inventories. |
| `moveAll` | Move all items to another inventory |
| `moveToLimit` | Move as many items as possible until an amount given. |
| `shift` | Move an item along a row; used in banking |
| `removeToLimit` | Remove as many items as possible until an amount given. |

> [!IMPORTANT]
> For advanced modifications including combining multiple and cross-inventory operations see [Transactions](transactions).


---

## NPC Combat Scripts

_Source: NPC-Combat-Scripts.md_

As of Void 2.5.0 npc combat scripts are primarily done using `.combat.toml` config files which follow the following format:

```toml
# Header
[combat_id]
attack_speed = 4                 # Number of ticks between attacks
retreat_range = 8                # Range from spawn tile npc can retreat within see: https://oldschool.runescape.wiki/w/Retreat
defend_anim = "defend_anim_id"   # Animation when hit by an enemy
defend_sound = "defend_sound_id" # Sound played to enemy when hit
death_anim = "death_anim_id"     # Animation played when killed
death_sound = "death_sound_id"   # Sound played when killed

# Attack types
[combat_id.attack_name]
# Selection
chance = 1                # Weight given to this attack when selecting between multiple attack styles (default 1)
range = 1                 # Tiles from target when this attack can be used
approach = false          # Whether melee attack has to be within range to use or the npc should move closer
condition = "custom_name" # Custom conditions for using this attack (see: npcCondition)

# Execution
say = "Urg!"                     # Force the npc to say something when attacking
anim = "attack_anim_id"          # Attack animation id
gfx = "attack_gfx_id"            # Attack graphics id
sound = "attack_sound_id"        # Attack sound id (typically only for area sounds)
projectile_origin = "tile"       # Where the projectile should start (entity, tile, centre)
projectile = "projectile_gfx_id" # Projectile gfx to shoot at target

## Target
target_anim = "anim_id"                      # Animation for the target to play (typically blank as defend anims handled separately)
target_gfx = "gfx_id"                        # Graphic to play on the target
target_sound = "attack_sound_id"             # Sound to play the target (usually used over "sound")
target_hit = { offense = "slash", max = 10 } # Damage roll applied to target
multi_target_area = "boss_dungeon"           # Area to select multi-targets from (single target if blank)

## On Impact
impact_anim = "anim_id"                     # Animation to play once hit
impact_gfx = "gfx_id"                       # Graphic to play once hit
impact_sound = "sound_id"                   # Sound to play on target once hit
miss_gfx = "gfx_id"                         # Graphic to play on hit if no damage dealt
miss_sound = "sound_id"                     # Sound to play on hit if no damage dealt
impact_regardless = false                   # Whether to switch between impact_/miss_ on hit or always use impact_gfx/sounds
impact_drain = { id = "skill", amount = 1 } # Targets skill(s) to drain on impact
impact_freeze = 10                          # Ticks to freeze target for
impact_poison = 10                          # Damage to poison target with
impact_message = "You have been frozen!"    # Message to send target on impact
```

> See [CombatDefinition](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/data/config/CombatDefinition.kt) for more detailed documentation.

```toml
[npc_id]
combat_def = "combat_id"
max_hit_crush = 100 # Max hit's can be overridden
```

## Multiples
Lots of fields can have more than one value e.g. hits, drains, sounds, gfxs, projectiles. 

```toml
sounds = [
    { id = "sound_id" },
    { id = "sound_id_2" },
] 
```

## Sounds

```toml
sound = { id = "sound_id", radius = 5 }
```

## Graphics

```toml
gfx = { id = "gfx_id", area = true }
```

## Mixed type hits
Hits can also mix their offensive and defensive types. 

For example [Magical-Ranged](https://oldschool.runescape.wiki/w/Magical_ranged) hits roll accuracy against magic but can be blocked via protect from missiles and show as a ranged hitsplat. 

```toml
hit = { offense = "range", defence = "magic", min = 100, max = 150 } 
```


---

## NPCs

_Source: NPCs.md_

Non-Player-Characters exist in the game for [Players](players) to interact with, talk to for quests, information or [shops](shops), and fight in [combat](combat-scripts) although these are typically referred to as Monsters.

While NPCs have a lot of similarities with [Players](players) as both are `Characters`, NPCs have a lot data due to not being connected to a `Client` and thus having no concept of [Interfaces](interfaces) or [Inventories](inventories).

## Configuration

NPCs have two types of [config files](config-files#npcs) which store information which doesn't exist in the cache.

- `.npc.toml` files store additional information such as combat stats, animations and examines.
- `.npc-spawns.toml` files store spawn information

```toml
[cow_default]
id = 81
hitpoints = 80
attack_bonus = -15
wander_radius = 12
immune_poison = true
slayer_xp = 8.0
max_hit_melee = 10
style = "crush"
respawn_delay = 45
drop_table = "cow"
combat_anims = "cow"
combat_sounds = "cow"
categories = ["cows"]
height = 30
examine = "Converts grass to beef."
```

This information can be obtained using `npc.def["examine", ""]` see [definition extras](definitions#definition-extras) for more info.


### Spawns

Permanent and respawning npcs are configured in `/data/*.npc-spawn.toml` files organised by location.
```toml
spawns = [
  { id = "cow_default", x = 2936, y = 3274 },
  { id = "cow_brown", x = 2921, y = 3287 },
  { id = "cow_brown", x = 2933, y = 3274 },
  { id = "cow_light_spots", x = 2924, y = 3288 },
]
```

One-off npc spawns (like for quests or bosses) can be added using `NPCs`:

```kotlin
val cow = NPCs.add("cow_brown", Tile(3200, 3200), Direction.NORTH)
```

## Finding NPCs

Much like [Players](players#finding-players) NPCs can be found using the world [`NPCs`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/npc/NPCs.kt) and can be searched for by id, index, `Tile` or `Zone`:

```kotlin
val npc = NPCs.indexed(42) // Get npc at an index

val npc = NPCs.find(tile, "chicken") // Get a specific npc by id

val npcsUnder = NPCs.at(Tile(3200, 3200)) // Get all npcs under a tile

val npcsNearby = NPCs.at(Zone(402, 403)) // Get all npcs in 8x8 area
```

## Adding NPCs

[Scripts](scripts) can subscribe to npc spawns with:

```kotlin
npcSpawn("cow*") {
    softTimers.start("eat_grass")
}
```

## Removing NPCs

NPCs can be removed easily with:

```kotlin
npcs.remove(cow)
```

And [Scripts](scripts) can subscribe to despawns using:

```kotlin
npcDespawn {
    softTimers.stopAll()
}
```

## NPC options

NPC [interactions](interaction) can be subscribed to as well:

```kotlin
npcApproach("Talk-to", "banker*") { }

npcOperate("Talk-to", "zeke") { }
```

> [!TIP]
> See [interactions](interactions) for more info including the difference between operate and approach interactions.

## NPC data

| Data | Description |
|---|---|
| Id | The type of npc as a [String identifier](string-identifiers). |
| Index | The number between 1-32768 a particular npc is in the current game world. |
| Tile | The current position of the npc represented as an x, y, level coordinate. |
| Levels | Fixed levels used in [Combat calculations](combat-scripts). |
| Mode | The npcs current style of movement. |
| [Definition](definitions) | Definition data about this particular type of npc. |
| [ActionQueue](queues) | List of [Queues](queues) currently in progress for the npc. |
| [Soft Timer](timers) | The one [Timer](timers) that is currently counting down for the npc. |
| [Variables](character-variables) | Progress and tracking data for different types of content. |
| Steps | List of tiles the player plans on walking to in the future. |
| Collision | Strategy for how the npc can move about the game world. |
| Visuals | The npcs general appearance. |


---

## Philosophy

_Source: Philosophy.md_

There's several key themes followed throughout the code base which aren't necessarily standard, this page provides reasoning behind many of the main choices and the benefits they bring.

# Data in files, logic in code
Keeping as much data as possible in files keeps the code clean and allows focus to be kept on the flow of logic. Data can also be easily re-loaded at runtime allowing for faster fixes and prototyping.

# Flexibility with maps
Maps are a naturally efficient way of storing information taking up no more memory than necessary both in RAM and on disk. Both Players and NPC have [Variables](./character-variables) for storing temporary (discarded on logout) and persistent (saved) data instead of hundreds of individual variables which often leads to messy Player class files. It also means account saves only store data of content the player has accessed and player saves will start small and gradually grow in size over time.

> [Definitions](https://github.com/GregHib/void/tree/main/cache/src/main/kotlin/world/gregs/voidps/cache/definition/decoder) which read static cache data all have an [Extras](https://github.com/GregHib/void/blob/main/cache/src/main/kotlin/world/gregs/voidps/cache/definition/Extra.kt) map for storing custom values provided by the files inside the [`/data/`](https://github.com/GregHib/void/tree/main/data/) directory.

# Strings are readable, special numbers are not

All entities have [unique string identifiers](./identifying-entities) as strings are human friendly not only for reading code but also when inputting data:
Want a yellow whip? `::item abyssal_whip_yellow` no id look-up table required.

Requiring unique strings forces contextual and relational information about entities which might otherwise be identical: `banker_lumbridge`, `banker_varrock` which can simplify transformations `"${door.id}_closed".replace("_closed", "_open")` however be cautious with this as name changes could break these implicit relationships.
Even if all else fails you can still use the integer id as a string `"4151"`.

# Events

Important entities each have a publisher for emitting [events](./events), these events can be subscribed to at any time allowing content to interact without creating complex interweaving code.

# Test content as well as systems

Tests are the proof that code written 6 months ago still works today and provides the confidence to make large fundamental changes without introducing new bugs or issues. The more content that is added over time, the more important this becomes.



---

## Player Rights

_Source: Player-Rights.md_

There are 3 types of rights a player can have:
* Player
* Moderator
* Administrator


## Modifying rights

### File
Logout of the account, and in the player account TOML file found in `/data/saves/<player-name>.toml` you can modify or add the "rights" variable to the "variables" section. Valid values are "none", "mod" or "admin". Save the file then log-back into the game.

![image](https://github.com/user-attachments/assets/d72b1f2c-e596-4c86-9be4-49c42d70e2d1)

### Properties

In `game.properties` you can set the `rights` property to your player name and then reload the server, whenever your player will log in they will be granted admin rights, even if they have previously lost them.

### Command

If you already have access to an admin account you can use the `rights` command to grant rights to other logged-in players. E.g. `rights Zezima mod`



---

## Players

_Source: Players.md_

Players are the characters controlled by people, they are used to explore the world, interact with other entities, and hold information about what they have collected and accomplished so far.


## Finding players

Most code will already have access to the player in question, in the situations where one player needs to find or reference another player they can be searched for in the worlds list of [`Players`](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/entity/character/player/Players.kt#L9).

You can search for a player by name, index, `Tile` or `Zone`:

```kotlin
val player = Players.indexed(42) // Get player at an index

val player = Players.find("Cow31337Killer") // Get a specific player by name

val playersUnder = Players.at(Tile(3200, 3200)) // Get all players under a tile

val playersNearby = Players.at(Zone(402, 403)) // Get all players in 8x8 area
```

## Adding players

You can subscribe to new players being spawned with:
```kotlin
playerSpawn {
    message("Welcome to Void.")
}
```

## Removing players

Again for most situations removing players is already handled, `player.logout()` is enough for other scenarios. 

You can subscribe to removed players with:

```kotlin
playerDespawn {
    cancelQuickPrayers()
}
```

## Player options

[Scripts](scripts) can subscribe to player options with:

```kotlin
playerOperate("Req Assist") { }
```

> [!TIP]
> See [interactions](interaction) for more info including the difference between operate and approach interactions.


## Player save data

| Data | Description |
|---|---|
| Account name | The username used to log into the game. |
| Password hash | Encrypted value used to verify a correct password. |
| Index | The number between 1-2048 a particular player is in the current game world. |
| Tile | The current position of the player represented as an x, y, level coordinate. |
| [Inventory](inventories) | Various stores of Items from equipment to shops. |
| [Variables](character-variables) | Progress and tracking data for all types of content. |
| Experience | Progress gained in skilling towards a Level. |
| Levels | Boundaries of skilling experience which unlock new content. |
| Friends | List of befriended players that can be messaged directly. |
| Ignores | List of players who's messages will be hidden. |
| Client | The network connection between the persons Game Client and the game server. |
| Viewport | A store of the entities in the visible surrounding area. |
| Body | The players appearance with/without equipment. |
| Mode | The players current style of movement. |
| Visuals | The players general appearance. |
| Instructions | Instructions send by the Client or Bot controller. |
| Options | Possible interactions other entities can have with this player. |
| [Interfaces](interfaces) | All the [Interfaces](interfaces) the player's Client currently has opened. |
| Collision | Strategy for how the player can move about the game world. |
| [ActionQueue](queues) | List of [Queues](queues) currently in progress for the player. |
| [Soft Timer](timers) | [Timers](timers) that are always counting down for the player. |
| [Timers](timers) | Pause-able [Timers](timers) that are counting down for the player. |
| Steps | List of tiles the player plans on walking to in the future. |




---

## Properties

_Source: Properties.md_

Properties are [Game Settings](game-settings) used to configure and customise a world by a server operator in the [`game.properties`](https://github.com/GregHib/void/blob/main/game/src/main/resources/game.properties) file.

## What is a property

Use properties for:
- Server-wide behaviour (xp rates, movement rules)
- Startup-configuration (network & storage settings)
- Paths and external file locations
- Feature toggles

## What isn't a property

Not everything should be a property however.

Avoid properties for:
- Per Item/Npc/Object values - These should be stored in [config files](config-files)

## Accessing Properties

You can access properties in [scripts](scripts) using the `Settings[...]` operator:

```kotlin
val x = Settings["world.home.x", 0]
```

The second argument is the default value if the property is missing or invalid.

You can access booleans, numbers, and strings the same way:

```kotlin
val membersOnly = Settings["world.members", false]
val xpRate = Settings["world.experienceRate", 1.0]
val serverName = Settings["server.name", "Server"]
```

## Example

```kotlin
worldSpawn {
    if (Settings["events.shootingStars.enabled", false]) {
        eventUpdate()
    }
}
```

## Notes
- Properties are global and apply to the whole server.
- Reload properties with the `reload settings` [command](commands).
- `settingsReload {}` [event handler](event-handlers) to listen for reloads.


---

## Queues

_Source: Queues.md_

Queues are an alternative to [delays](https://osrs-docs.com/docs/mechanics/delays/) for executing code after a set amount of time. In most cases a delay should be used when [interacting](interaction) however in some cases, like during interface interactions, you may need to use a queue to delay to a later point in time. Unlike [Timers](timers) a Queue only runs once.

There are 4 types of [Queues](https://github.com/GregHib/void/blob/a4af3f90fdc33c890935abc9531f903e06b1a6d0/engine/src/main/kotlin/world/gregs/voidps/engine/queue/ActionPriority.kt#L3): 
* [Weak](#weak-queues) - Removed by interuptions and String queues
* [Normal](#normal-queues) - Skipped if interface is open
* [Strong](#strong-queues) - Closes interfaces and cancels Weak actions
* [Soft](#soft-queues) - Closes interfaces and paused by suspensions but not by other queues

> [!NOTE]
> Read more details about queues and how they work on [osrs-docs/queues](https://osrs-docs.com/docs/mechanics/queues/).

Queues are easy enough to start and can be started on any [Character](entities), they only require a unique name and an initial delay in ticks:

## Normal queues

Normal queues are for regular, do something in a few ticks or do something suspendable now
```kotlin
player.queue("welcome") {
    statement("Welcome to Lumbridge! To get more help, simply click on the Lumbridge Guide or one of the Tutors - these can be found by looking for the question mark icon on your minimap. If you find you are lost at any time, look for a signpost or use the Lumbridge Home Teleport spell.")
}
```

## Weak queues

Weak queues are often used for item-on-item interactions and other interruptable skills
```kotlin
player.weakQueue("cast_silver", 3) {
    inventory.replace("silver_bar", data.item)
    exp(Skill.Crafting, data.xp)
    make(item, amount - 1)
}
```

## Strong queues

Strong queues are mainly used for non-interruptable events such as [Hits](combat-scripts#calculations), emotes and dying:

```kotlin
player.strongQueue("teleport") {
    player.playSound("teleport")
    player.gfx("teleport_$type")
    player.animDelay("teleport_$type")
    player.tele(tile)
}
```

> [!NOTE]
> Teleports don't have to be implemented manually, see [Teleport](https://github.com/GregHib/void/blob/main/game/src/main/kotlin/content/skill/magic/spell/Teleport.kt).

## Soft queues

Soft queues are used for things that have to happen and nothing short of death will stop them.
```kotlin
player.softQueue("remove_ammo") {
    player.equipment.remove(ammo, required)
}
```

# World queues

World queues work slightly differently than character queues as they are designed for more for scheduling singular events to occur against real time. For example distractions & diversions, or starting mini-games like castle wars. As such any World action queued will override any previous action queued with the same name.

```kotlin
World.queue("shooting_star_event_timer", TimeUnit.MINUTES.toTicks(minutes)) {
    startCrashedStarEvent()
}
```


---

## Reading errors

_Source: Reading-errors.md_

Errors can be pretty daunting, here's how you break down what an error on void looks like, which parts are useful, and how to figure out what it means.

# Koin errors

Koin is what void uses to startup so if something fails first you'll see a koin error about failing to create an instance
```kotlin
ERROR [[Koin]] * Instance creation error : could not create instance for '[Singleton: 'world.gregs.voidps.engine.entity.item.drop.DropTables']'
```

This error tells us that it was trying to startup and load DropTables but failed.

Koin errors won't always be useful, the real cause is normally found below.


# Log lines

Errors normally include a list of points in the code the server got to before the error occurred. This is called a stack trace.
They are read from the bottom up.

```kotlin
world.gregs.voidps.engine.entity.item.drop.DropTables.load$lambda$0(DropTables.kt:26)
world.gregs.voidps.engine.TimedLoaderKt.timedLoad(TimedLoader.kt:18)
world.gregs.voidps.engine.entity.item.drop.DropTables.load(DropTables.kt:23)
```
This tells us the error occurred in:
- `DropTables` line 23
- `TimedLoader` line 18
- `DropTables` line 26

Unless you're debugging code you don't need to read through this, it might give you some ideas as to what the error is though.

# Exceptions

Now we get to real errors, these generally look like this and provide a the end the real cause of the issue.

```kotlin
Caused by: java.lang.IllegalArgumentException: Unable to find item with id 'fellstalk_seed'.
```

In this case we can see there was an item "fellstalk_seed" which couldn't be found.

# Putting it all together

We now know:
- There was an error on startup
- It failed to load DropTables
- It couldn't find an item with id 'fellstalk_seed'.

If you did a search for "fellstalk_seed" you'd see a usage in a `*.drops.toml` but not in an `*.items.toml`.

Searching on the rs3 wiki you'd find [fellstalk seed](https://runescape.wiki/w/Fellstalk_seed) was added on 6th of September 2011.

Void's 634 revision is only up to January 2011, so the cause of this error is fellstalk seed doesn't exist in this revision.


# Your turn

Now here's a full example, don't worry about how many lines there are, pick out the same pieces of information as shown before and piece together what is going wrong.

```kotlin
ERROR [[Koin]] * Instance creation error : could not create instance for '[Singleton: 'world.gregs.voidps.engine.data.definition.ItemDefinitions']': java.lang.IllegalArgumentException: Duplicate item id found 'dragon_sq_shield_or' at .\data\minigame\treasure_trail\treasure_trail.items.toml.
	world.gregs.voidps.engine.data.definition.ItemDefinitions.load$lambda$0$0(ItemDefinitions.kt:123)
	world.gregs.config.Config.fileReader(Config.kt:11)
	world.gregs.voidps.engine.data.definition.ItemDefinitions.load$lambda$0(ItemDefinitions.kt:56)
	world.gregs.voidps.engine.TimedLoaderKt.timedLoad(TimedLoader.kt:18)
	world.gregs.voidps.engine.data.definition.ItemDefinitions.load(ItemDefinitions.kt:51)
	Main.cache$lambda$0$4(Main.kt:125)
	org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:49)
	org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	org.koin.core.registry.InstanceRegistry.resolveInstance$koin_core(InstanceRegistry.kt:132)
	org.koin.core.resolution.CoreResolver.resolveFromRegistry(CoreResolver.kt:87)
	org.koin.core.resolution.CoreResolver.resolveFromContextOrNull(CoreResolver.kt:74)
	org.koin.core.resolution.CoreResolver.resolveFromContextOrNull$default(CoreResolver.kt:72)
	org.koin.core.resolution.CoreResolver.resolveFromContext(CoreResolver.kt:69)
	org.koin.core.scope.Scope.resolveFromContext(Scope.kt:321)
	org.koin.core.scope.Scope.stackParametersCall(Scope.kt:284)
	org.koin.core.scope.Scope.resolveInstance(Scope.kt:270)
	org.koin.core.scope.Scope.resolve(Scope.kt:243)
	org.koin.core.scope.Scope.get(Scope.kt:225)
	Main.cache$lambda$0$19(Main.kt:926)
	org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:49)
	org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	org.koin.core.registry.InstanceRegistry.createEagerInstances(InstanceRegistry.kt:102)
	org.koin.core.registry.InstanceRegistry.createAllEagerInstances$koin_core(InstanceRegistry.kt:68)
	org.koin.core.Koin.createEagerInstances(Koin.kt:340)
	org.koin.core.KoinApplication.createEagerInstances(KoinApplication.kt:77)
	org.koin.core.context.GlobalContext.startKoin(GlobalContext.kt:65)
	org.koin.core.context.DefaultContextExtKt.startKoin(DefaultContextExt.kt:41)
	Main.preload(Main.kt:95)
	Main.main(Main.kt:57)
ERROR [[Koin]] * Instance creation error : could not create instance for '[Singleton: 'world.gregs.voidps.engine.entity.item.drop.DropTables']': org.koin.core.error.InstanceCreationException: Could not create instance for '[Singleton: 'world.gregs.voidps.engine.data.definition.ItemDefinitions']'
	org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:56)
	org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	org.koin.core.registry.InstanceRegistry.resolveInstance$koin_core(InstanceRegistry.kt:132)
	org.koin.core.resolution.CoreResolver.resolveFromRegistry(CoreResolver.kt:87)
	org.koin.core.resolution.CoreResolver.resolveFromContextOrNull(CoreResolver.kt:74)
	org.koin.core.resolution.CoreResolver.resolveFromContextOrNull$default(CoreResolver.kt:72)
	org.koin.core.resolution.CoreResolver.resolveFromContext(CoreResolver.kt:69)
	org.koin.core.scope.Scope.resolveFromContext(Scope.kt:321)
	org.koin.core.scope.Scope.stackParametersCall(Scope.kt:284)
	org.koin.core.scope.Scope.resolveInstance(Scope.kt:270)
	org.koin.core.scope.Scope.resolve(Scope.kt:243)
	org.koin.core.scope.Scope.get(Scope.kt:225)
	Main.cache$lambda$0$19(Main.kt:926)
	org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:49)
	org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	org.koin.core.registry.InstanceRegistry.createEagerInstances(InstanceRegistry.kt:102)
	org.koin.core.registry.InstanceRegistry.createAllEagerInstances$koin_core(InstanceRegistry.kt:68)
	org.koin.core.Koin.createEagerInstances(Koin.kt:340)
	org.koin.core.KoinApplication.createEagerInstances(KoinApplication.kt:77)
	org.koin.core.context.GlobalContext.startKoin(GlobalContext.kt:65)
	org.koin.core.context.DefaultContextExtKt.startKoin(DefaultContextExt.kt:41)
	Main.preload(Main.kt:95)
	Main.main(Main.kt:57)
ERROR [Main] Error loading files.
org.koin.core.error.InstanceCreationException: Could not create instance for '[Singleton: 'world.gregs.voidps.engine.entity.item.drop.DropTables']'
	at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:56)
	at org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	at org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	at org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	at org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	at org.koin.core.registry.InstanceRegistry.createEagerInstances(InstanceRegistry.kt:102)
	at org.koin.core.registry.InstanceRegistry.createAllEagerInstances$koin_core(InstanceRegistry.kt:68)
	at org.koin.core.Koin.createEagerInstances(Koin.kt:340)
	at org.koin.core.KoinApplication.createEagerInstances(KoinApplication.kt:77)
	at org.koin.core.context.GlobalContext.startKoin(GlobalContext.kt:65)
	at org.koin.core.context.DefaultContextExtKt.startKoin(DefaultContextExt.kt:41)
	at Main.preload(Main.kt:95)
	at Main.main(Main.kt:57)
Caused by: org.koin.core.error.InstanceCreationException: Could not create instance for '[Singleton: 'world.gregs.voidps.engine.data.definition.ItemDefinitions']'
	at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:56)
	at org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
	at org.koin.core.instance.SingleInstanceFactory.get$lambda$0(SingleInstanceFactory.kt:55)
	at org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
	at org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
	at org.koin.core.registry.InstanceRegistry.resolveInstance$koin_core(InstanceRegistry.kt:132)
	at org.koin.core.resolution.CoreResolver.resolveFromRegistry(CoreResolver.kt:87)
	at org.koin.core.resolution.CoreResolver.resolveFromContextOrNull(CoreResolver.kt:74)
	at org.koin.core.resolution.CoreResolver.resolveFromContextOrNull$default(CoreResolver.kt:72)
	at org.koin.core.resolution.CoreResolver.resolveFromContext(CoreResolver.kt:69)
	at org.koin.core.scope.Scope.resolveFromContext(Scope.kt:321)
	at org.koin.core.scope.Scope.stackParametersCall(Scope.kt:284)
	at org.koin.core.scope.Scope.resolveInstance(Scope.kt:270)
	at org.koin.core.scope.Scope.resolve(Scope.kt:243)
	at org.koin.core.scope.Scope.get(Scope.kt:225)
	at Main.cache$lambda$0$19(Main.kt:926)
	at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:49)
	... 12 common frames omitted
Caused by: java.lang.IllegalArgumentException: Duplicate item id found 'dragon_sq_shield_or' at .\data\minigame\treasure_trail\treasure_trail.items.toml.
	at world.gregs.voidps.engine.data.definition.ItemDefinitions.load$lambda$0$0(ItemDefinitions.kt:123)
	at world.gregs.config.Config.fileReader(Config.kt:11)
	at world.gregs.voidps.engine.data.definition.ItemDefinitions.load$lambda$0(ItemDefinitions.kt:56)
	at world.gregs.voidps.engine.TimedLoaderKt.timedLoad(TimedLoader.kt:18)
	at world.gregs.voidps.engine.data.definition.ItemDefinitions.load(ItemDefinitions.kt:51)
	at Main.cache$lambda$0$4(Main.kt:125)
	at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:49)
	... 28 common frames omitted

```

## Quiz:
- When did the error occur?
- What system failed?
- Why was there an error?
- Where is the issue located?
- How can you fix it?

<details>
<summary>Answers</summary>
<ul>
<li>Server Startup</li>
<li>ItemDefinitions</li>
<li>Duplicate item string id</li>
<li>treasure_trail.items.toml</li>
<li>Rename the duplicate</li>
</ul>
</details>





---

## Rebase

_Source: Rebase.md_

Rebasing is a way of taking you existing changes and applying them on-top of the latest version of another branch.

[YouTube - Git Rebase in IntelliJ](https://www.youtube.com/watch?v=wiU3TL3jM8I)

# How to update your main branch

Make sure you're on the branch you want to update.

1. Fetch the latest changes

![image](https://github.com/user-attachments/assets/e6898611-690b-4b40-a799-94f1ac8dd623)

2. Update the main branch

<img width="1141" height="676" alt="image" src="https://github.com/user-attachments/assets/5afcff58-752e-461e-aedd-f054ce56898d" />

3. Rebase onto `Remote | origin/main` branch

![image](https://github.com/user-attachments/assets/e5f82731-da52-42d7-a96a-a0c8d62cc982)

[IntelliJ guide on Merge, rebase and cherry-picking](https://www.jetbrains.com/help/idea/apply-changes-from-one-branch-to-another.html)



---

## Roadmap

_Source: Roadmap.md_

General long term direction, see [project](https://github.com/users/GregHib/projects/3) for the latest active work (poke me if this isn't up-to-date)

## In Progress

- More slayer monsters
- Definition improvements

## Coming Up (In no particular order)

- [Hunter](https://github.com/GregHib/void/issues/750)
- Barrows
- Kalphite Queen
- Ancient Effigies
- Evil Tree
- Corporeal Beast
- [More Slayer Masters](https://github.com/GregHib/void/issues/503)

## Not happening for a while

- Pest Control
- Hunter
- Construction
- Clue Scrolls
- More Quests

## Recently Completed

- Imp Catcher quest [#903](https://github.com/GregHib/void/pull/903)
- Wise Old Man tasks [#901](https://github.com/GregHib/void/pull/901)
- Improved AI Bots [#889](https://github.com/GregHib/void/pull/889)
- Stronghold of Player Safety [#884](https://github.com/GregHib/void/pull/884)
- Fremennik Slayer Dungeon [#851](https://github.com/GregHib/void/pull/851)
- Fight caves [#832](https://github.com/GregHib/void/pull/832)
- Chaos Elemental [#828](https://github.com/GregHib/void/pull/828)
- Accurate combat mechanics [#826](https://github.com/GregHib/void/pull/826)
- Combat config improvements [#825](https://github.com/GregHib/void/pull/825)
- Magic Carpet rides [#820](https://github.com/GregHib/void/pull/820)
- Wiki improvements
- Farming [#814](https://github.com/GregHib/void/issues/814)
- Full content script rewrite [#751](https://github.com/GregHib/void/issues/751)
- Ring of life & Phoenix necklaces [#781](https://github.com/GregHib/void/pull/781)
- Plague City Quest [#778](https://github.com/GregHib/void/pull/778)
- Audit logs [#775](https://github.com/GregHib/void/pull/775)
- Penguin Hide & Seek [#765](https://github.com/GregHib/void/pull/765)
- Dagannoth Kings [#764](https://github.com/GregHib/void/pull/764)
- Sorceress' Garden [#763](https://github.com/GregHib/void/pull/763)


---

## Scripts

_Source: Scripts.md_

Scripts are the backbone of content in Void they allow you to easily attach code to events that occur within the game engine.
A script is any Kotlin class within the `game` module that implements the `Script` interface.

Anything registered in `init` is called when the server starts.

## Example

```kotlin
class Cows : Script {
    init {
        npcSpawn("cow") {
            softTimers.start("eat_grass")
        }
    }
}
```

> [!NOTE]
> See [Event Handlers](event-handlers) to learn about `npcSpawn` and other ways to write content.

## Creating a script

In IntelliJ right click the directory where you'd like to place to create a kotlin file (Shortcut: Alt + Insert)

![Screenshot 2024-02-21 204031](https://github.com/GregHib/void/assets/5911414/a15c451c-0071-4c22-b650-aa886f6ceb45)

Enter the class name and select the `Class` option

<img width="343" height="276" alt="image" src="https://github.com/user-attachments/assets/83dbb4ef-dccc-4353-a16a-23fcc4f7cab6" />

A class will be created:

<img width="750" height="226" alt="image" src="https://github.com/user-attachments/assets/f117eacb-80f9-41c6-8f0f-5c2ca68e2b6f" />

Now you can extend the `Script` interface and add an init function

<img width="436" height="247" alt="image" src="https://github.com/user-attachments/assets/4dd407e1-516c-4cf2-b33f-adc6f9a03214" />


Now you can write your content using event handlers // TODO

That's it! Scripts are discovered automatically next time your run the server.

**Next up**: [adding event handlers](event-handlers)

### Notes
- Keep scripts focused to one [entity](entity) or one behaviour if an entity has a lot of options
- Make sure scripts are located in the `content.` package (unless otherwise specified in [properties](properties))
- Scripts are organised by location first, by type second


## Troubleshooting

It's possible script detection fails and a deleted or moved script can't be found. If this occurs you'll see a startup error like this:

```txt
ERROR [ContentLoader] Failed to load script: content.area.misthalin.lumbridge.farm.Cow
java.lang.ClassNotFoundException: content.area.misthalin.lumbridge.farm.Cow
	at java.base/jdk.internal.loader.BuiltinClassLoader.loadClass(BuiltinClassLoader.java:641)
	at java.base/jdk.internal.loader.ClassLoaders$AppClassLoader.loadClass(ClassLoaders.java:188)
	at java.base/java.lang.ClassLoader.loadClass(ClassLoader.java:525)
	at java.base/java.lang.Class.forName0(Native Method)
	at java.base/java.lang.Class.forName(Class.java:413)
	at java.base/java.lang.Class.forName(Class.java:404)
	at ContentLoader.loadScript(ContentLoader.kt:54)
	at ContentLoader.load(ContentLoader.kt:26)
	at Main.preload(Main.kt:102)
	at Main.main(Main.kt:56)
ERROR [ContentLoader] If the file exists make sure the scripts package is correct.
ERROR [ContentLoader] If the file has been deleted try running 'gradle cleanScriptMetadata'.
ERROR [ContentLoader] Otherwise make sure the return type is written explicitly.
```

You can fix this by running
```gradle
gradle collectSourcePaths
```

```bash
./gradlew collectSourcePaths
```

Or manually deleting the `scripts.txt` file in `game/src/main/resources/`


---

## Setup

_Source: Setup.md_

## Server

Clone the main void project to your local computer, open in IntelliJ as a gradle project.

## Download list

* Pre-modified cache files - https://mega.nz/folder/ZMN2AQaZ#4rJgfzbVW0_mWsr1oPLh1A
* File server jar and properties - https://github.com/GregHib/rs2-file-server/releases
* Client jar - https://mega.nz/folder/lZcHDY7R#X0myX8SlXTI0BYYgh9-zpQ

## Game Cache

Extract the `active-cache` files into the `/data/cache/active/` directory.

[Build your own](cache-building)

## File Server

Download `rs2-file-server.jar` and `file-server.properties` into the same folder.

Extract cache to a folder (e.g. `/data/cache/`)

> Make sure `cachePath` in file-server.properties points to the correct relative directory.

Launch `rs2-file-server.jar`

> The file server has no UI so will look like it's not doing anything when you click it. Run with `java -jar rs2-file-server.jar` to see output.

## Client

Launch `void-client.jar`

Login with any username & password.

[Build your own](client-building)

## Tests

If you try running the unit tests and recieve an error similar to `Execution failed for task ''. No tests found`

Go to `File | Settings | Build, Execution, Deployment | Build Tools | Gradle` and change `Run tests using:` from `Gradle (Default)` to `IntelliJ IDEA`


---

## Shops

_Source: Shops.md_

## Adding default shops

Find the shop in various `*.shops.toml` files, or add it if you know the inventory id
```toml
[bobs_brilliant_axes]
id = 1
defaults = [
    { id = "bronze_pickaxe", amount = 10 },
    { id = "bronze_hatchet", amount = 10 },
    { id = "iron_hatchet", amount = 10 },
    { id = "steel_hatchet", amount = 10 },
    { id = "iron_battleaxe", amount = 10 },
    { id = "steel_battleaxe", amount = 10 },
    { id = "mithril_battleaxe", amount = 10 },
]
```

Add the shopkeeper to a `*.npcs.toml` file
```toml
[bob]
id = 519
categories = ["human"]
shop = "bobs_brilliant_axes"
examine = "An expert on axes."
```
> `shop =` needs to be set to the inventory string id for shops to open automatically when clicking the "Trade" option

Spawn the shopkeeper in the right place in a `*.npc-spawns.toml` file

```toml
spawns = [
  { id = "bob", x = 3228, y = 3203, level = 0, direction = "SOUTH" },
]
```

## Opening a shop

Npcs with the `Trade` option will open shops listed in their npcs.toml automatically however when opened via dialogue options the `OpenShop` event should be used:

```kotlin
player.openShop("zekes_superior_scimitars")
```

## General stores

To add a general store which all players can see and most tradable items can be sold to, simply make sure the inventory name ends with `general_store`, for example:

```toml
[lumbridge_general_store]
id = 24
defaults = [
    { id = "empty_pot", amount = 30 },
    { id = "jug", amount = 10 },
    { id = "shears", amount = 10 },
    # etc...
]
```


---

## Storage

_Source: Storage.md_

By default void saves players to `/data/saves/*.toml` however when running in a production environment you might want to store in a database rather than toml files, void support [PostgreSQL](#postgresql) out of the box, and [other SQL databases](#driver-support) with a little modification.

## Postgresql

Storage using Postgresql is simple, first you must build the server with the database module by passing `-PincludeDb = true` in your gradle build command, or adding `includeDb = true` in the gradle.properties file before building your server or jar file.

Then replace the `storage.type=files` line in `game.properties` with `storage.type=database` and fill out the following details about your database:

```properties
storage.type=database
storage.database.username=postgres # The database username
storage.database.password=password # The database password
storage.database.driver=org.postgresql.Driver # The database driver - see JDBC
storage.database.jdbcUrl=jdbc:postgresql://localhost:5432/game?reWriteBatchedInserts=true
stoage.database.poolSize=4 # Number of connections to use
```

### JDBC
Java database connectivity (JDBC) is a specification for connecting to multiple types of SQL databases.
By default Void only comes bundled with the Postgresql Driver but can support all major database providers.

## Driver Support
> [!CAUTION]
> MySql is no longer supported due to lack of ARRAY type support

Follow these steps to add support for your desired database, for this example we will add support for MySQL:

1. In `/engine/build.gradle.kts` add the driver for the database of your choosing

```gradle.kts
implementation("com.mysql:mysql-connector-j:8.3.0")
```

2. Build your server .jar on the command line by running

```
./gradlew assembleBundleDist
```

Which will create `/game/build/distributions/void-dev.zip` for you to use.


3. In `game.properties` set the driver class name and jdbc url (as well as login credentials)

```properties
storage.database.driver=com.mysql.cj.jdbc.Driver
storage.database.jdbcUrl=jdbc:mysql://localhost:3306/game
```

4. For some non-concurrent databases

> [!TIP]
> This information can be found by googling "mysql jdbc driver maven" and "mysql jdbc driver class name"

Now when you run your game it will connect to the database specified in the JDBC url, load and store player data there.


---

## String identifiers

_Source: String-identifiers.md_

In-game and during client-server communications most [entity](entities) types are represented as an integer identifier e.g [17937 - Naïve bloodrager pouch](https://runescape.wiki/w/Na%C3%AFve_bloodrager_pouch)

At the borders between these boundaries the integer is replaced by a human-readable string equivalent.

Removing [magic numbers](https://en.wikipedia.org/wiki/Magic_number_(programming)) makes code immediately more readable.

## Do ✅ 
```kotlin
if (npc.id == "bob" && option == "Trade") {
    player.openShop("bobs_brilliant_axes")
}
```
## Don't ❌
```kotlin
if (npc.id == 519 && option == 2) {
    player.openShop(1)
}
```

> [!NOTE]
> Integer ids can still be obtained from the relevant definition class e.g. `npc.def.id`

# Formatting rules

A string id should follow the following formatting rules to keep them standardised.

1. Lowercase
2. Underscores not spaces
3. Ascii equivalents of diacritics (accents)
4. "and" instead of "&"
5. All symbols removed

> `Naïve bloodrager pouch` -> `naive_bloodrager_pouch`

> `Magic potion (3)` -> `magic_potion_3`

In cases where there is no string id the integer id can be used as a string.

# Generating ids

The majority of ids are generated from formatting their cache names.

If there is no known information to separate duplicate ids the second id onwards has a number suffix (i.e first one has no suffix, second one has 2, third 3, etc...)



---

## Timers

_Source: Timers.md_

Timers count down every X number of ticks; where X is an `interval` specified on the start of the Timer.
Timers are used in situations where *something* must occur at the start, during or end of the countdown, otherwise a [Clock](clocks) is better suited.

There are two types of [Timers](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/timer/Timers.kt):

| Timer type | Applies to | Stops counting down |
|---|---|---|
| Soft | Player, NPC | Never |
| Regular | Player | When busy (delayed or interface open) |

> [!WARNING]
> NPC's are limited to one soft timer at a time, Player's have no such limits.

## Basic Timer

A basic timer is composed of three parts: start, tick & stop.
Start is the only part required as every timer must have an `interval`.

```kotlin
timerStart("skull") { player: Player ->
    // Display a skull over the players head
    player.appearance.skull = player["skull", 0]
    player.flagAppearance()
    1000 // A skull lasts 1000 ticks / 10 minutes
}

timerStop("skull") { player: Player ->
    // When the timer stops remove the skull sprite from above the players head
    player.appearance.skull = -1
    player.flagAppearance()
}

// A player is marked as skulled when they attack a new player in the wilderness
combatStart { target ->
    if (inWilderness && target is Player && !attackers.contains(target)) {
        // Start the skull timer
        skull()
    }
}
```

## Timer
In this example a player starts an `overload` timer which boosts their levels every 15 seconds for 5 minutes in exchange for 500 hitpoints which are returned after the effect wears off.

```kotlin
// Start
timerStart("overload") { player: Player ->
    // Deal 5 hits of 100
    player.queue(name = "overload_hits") {
        repeat(5) {
            player.directHit(100)
            player.anim("overload")
            player.gfx("overload")
            pause(2)
        }
    }
    TimeUnit.SECONDS.toTicks(15) // Every 25 ticks / 15 seconds
}

// Every 25 ticks
timerTick("overload") { player: Player ->
    // Cancel if 5 minutes are up.
    if (player.dec("overload_refreshes_remaining") <= 0) {
        return@timerTick Timer.CANCEL
    }

    // Restore boosted levels every 25 ticks
    player.levels.boost(Skill.Attack, 5, 0.22)
    player.levels.boost(Skill.Strength, 5, 0.22)
    player.levels.boost(Skill.Defence, 5, 0.22)
    player.levels.boost(Skill.Magic, 7)
    player.levels.boost(Skill.Ranged, 4, 0.1923)
Timer.CONTINUE
}

// Stop
timerStop("overload") { player: Player ->
    // Tell the player the overload has worn off
    player.message("<dark_red>The effects of overload have worn off and you feel normal again.")

    // Restore the players levels to normal and return the hitpoints.
    player.levels.restore(Skill.Constitution, 500)
    player.levels.clear(Skill.Attack)
    player.levels.clear(Skill.Strength)
    player.levels.clear(Skill.Defence)
    player.levels.clear(Skill.Magic)
    player.levels.clear(Skill.Ranged)
}
```

It can be triggered using `timers.start()`
```kotlin
// When an overload potion is consumed
consume("overload_#") { player: Player ->
    player.timers.start("overload") // Start the overload timer
    player["overload_refreshes_remaining"] = 20 // Repeat for 5 minutes
}
```
> [!NOTE]
> As the overload effect is a regular timer it's effects can be prolonged by keeping a menu open when not in combat. This also applies to other effects such as poison.

### Restarting
If a timer should persist after a player logs out, it must be restarted once they log back in

```kotlin
playerSpawn {
    if (get("overload_refreshes_remaining", 0) > 0) {
        timers.restart("overload")
    }
}
```

This will call `timerStart` again, but the `restart` allows code that should only be executed on the initial start to do so.
```kotlin
timerStart("overload") { player: Player ->
    if (restart) {
        return@timerStart TimeUnit.SECONDS.toTicks(15) // If restarted don't deal damage again.
    }
    player.queue(name = "overload_hits") {
        // ...
    }
    TimeUnit.SECONDS.toTicks(15)
}

```
### Checking for a timer

You can check if a timer is currently active using the `contains(name)` method.
```kotlin
if (player.timers.contains("overload")) {
    player.message("You may only use this potion every five minutes.")
    return
}
```


---

## Transactions

_Source: Transactions.md_

Transactions are the underlying mechanism behind [modifying inventories](inventories#inventory-modifications); they allow multiple modifications to be made on multiple inventories without making any changes until everything has been verified to have no errors. In short either all the modifications are applied at once, or none of them are.

## Example

Let's see how this works in a hypothetical example:

```kotlin
val backpack = Inventory(arrayOf(Item("coins", 50))) // Start with 50 coins in our backpack
val bank = Inventory(arrayOf(Item.EMPTY))  // Start with nothing in the bank

// Begin a transaction
backpack.transaction {
    add("coins", 50) // Add 50 coins
    move("coins", 75, bank) // Move 75 coins to another container, in this case a bank for safe keeping
    remove("coins", 50) // Remove 50 coins for a fish
    add("raw_herring") // Add the fish
}

// As the transaction failed neither of the inventories have been changed:
println(backpack[0]) // Item(id = 'coins', amount = 50)
println(bank[0]) // Item(id = '', amount = 0)
```

As 75 of the coins were moved to the bank, there are only 25 coins left in the inventory which is not enough for the 50 coin fish. As one step of the transaction had an error the transaction was not successful, we can check the transactions error with `transaction.error`.

> [!TIP]
> Checkout [TransactionErrors.kt](https://github.com/GregHib/void/blob/3231e13f54c7fb69832bfb7f0a84edbe7b0dc891/engine/src/main/kotlin/world/gregs/voidps/engine/inv/transact/TransactionError.kt#L6) for more info about the different types of errors and what they mean.

```kotlin
when (backpack.transaction.error) {
    TransactionError.None -> {
        npc<Happy>("Enjoy the fish!")
    }
    TransactionError.Deficient -> {
        player<Upset>("Oh dear I don't actually seem to have enough money.")
    }
}
```

As the transaction wasn't successful nothing will have changed, `backpack` will have 50 coins, and `bank` will be empty.

## Operations

Much like [inventory helper functions](inventories#inventory-modifications) there are a lot of different ways of modifying an inventory. Every operation used during a transaction is only applied if all operations are successful, and each individual change made to an inventory is tracked.

> [!NOTE]
> See the full list of operations with descriptions in [`/inv/transact/operation`](https://github.com/GregHib/void/tree/main/engine/src/main/kotlin/world/gregs/voidps/engine/inv/transact/operation).

### Item changes

Item changes are used to track if an item was added to an inventory, optionally at a specific slot.

```kotlin
itemAdded("*_tiara", EquipSlot.Hat, "worn_equipment") { player ->
    player["${item.id.removeSuffix("_tiara")}_altar_ruins"] = true
}

itemRemoved("*_tiara", EquipSlot.Hat, "worn_equipment") { player ->
    player["${item.id.removeSuffix("_tiara")}_altar_ruins"] = false
}
```

All changes, additional and removals from an inventory can also be tracked

```kotlin
itemChange("bank") { player: Player ->
    player["bank_spaces_used_free"] = player.bank.countFreeToPlayItems()
    player["bank_spaces_used_member"] = player.bank.count
}
```

## Linking inventories

While a transaction are started from one inventory it is possible to link other inventories and apply operations to them as part of the same transaction.

```kotlin
player.inventory.transaction {
    val added = removeToLimit(id, amount)
    val transaction = link(player.offer)
    transaction.add(id, added)
}
```

`link()` returns the transaction for the linked inventory on which you can apply operations on like normal. If an operation fails in a linked transaction the parent transaction will also fail.







---

## Troubleshooting

_Source: Troubleshooting.md_

## Port used by another process

```
Exception in thread "main" java.net.BindException: Address already in use: bind
        at java.base/sun.nio.ch.Net.bind0(Native Method)
        at java.base/sun.nio.ch.Net.bind(Net.java:555)
        at java.base/sun.nio.ch.ServerSocketChannelImpl.netBind(ServerSocketChannelImpl.java:344)
        at java.base/sun.nio.ch.ServerSocketChannelImpl.bind(ServerSocketChannelImpl.java:301)
        at io.ktor.network.sockets.ConnectUtilsJvmKt.bind(ConnectUtilsJvm.kt:35)
        at io.ktor.network.sockets.TcpSocketBuilder.bind(TcpSocketBuilder.kt:45)
        at io.ktor.network.sockets.TcpSocketBuilder.bind(TcpSocketBuilder.kt:29)
        at io.ktor.network.sockets.TcpSocketBuilder.bind$default(TcpSocketBuilder.kt:25)
        at world.gregs.voidps.network.GameServer$start$1.invokeSuspend(GameServer.kt:47)
        at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:33)
        at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)
        at kotlinx.coroutines.EventLoopImplBase.processNextEvent(EventLoop.common.kt:280)
        at kotlinx.coroutines.BlockingCoroutine.joinBlocking(Builders.kt:85)
        at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking(Builders.kt:59)
        at kotlinx.coroutines.BuildersKt.runBlocking(Unknown Source)
        at kotlinx.coroutines.BuildersKt__BuildersKt.runBlocking$default(Builders.kt:38)
        at kotlinx.coroutines.BuildersKt.runBlocking$default(Unknown Source)
        at world.gregs.voidps.network.GameServer.start(GameServer.kt:30)
        at world.gregs.voidps.Main.main(Main.kt:65)
```

Another application is currently using that port. Possibly another copy of the server which hasn't closed correctly. Check Task Manager for unexpected Java Applications running in the background.

## Missing cache files

```
Exception in thread "main" java.io.FileNotFoundException: Main file not found at '\void\data\cache\main_file_cache.dat2'.
        at world.gregs.voidps.cache.CacheLoader.load(CacheLoader.kt:23)
        at world.gregs.voidps.cache.CacheLoader.load$default(CacheLoader.kt:20)
        at world.gregs.voidps.cache.CacheLoader.load(CacheLoader.kt:17)
        at world.gregs.voidps.cache.CacheLoader.load$default(CacheLoader.kt:12)
        at world.gregs.voidps.cache.Cache$Companion.load(Cache.kt:47)
        at world.gregs.voidps.Main$main$cache$1.invoke(Main.kt:48)
        at world.gregs.voidps.Main$main$cache$1.invoke(Main.kt:48)
        at world.gregs.voidps.engine.TimedLoaderKt.timed(TimedLoader.kt:10)
        at world.gregs.voidps.Main.main(Main.kt:48)
```

Cache files couldn't be found. Make sure you correctly extracted the cache files into `/data/cache/`

## Missing config files

```
Caused by: java.io.FileNotFoundException: .\data\definitions\animations.toml (The system cannot find the file specified)
        at java.base/java.io.FileInputStream.open0(Native Method)
        at java.base/java.io.FileInputStream.open(FileInputStream.java:219)
        at java.base/java.io.FileInputStream.<init>(FileInputStream.java:158)
        at world.gregs.yaml.Yaml.load(Yaml.kt:31)
        at world.gregs.voidps.engine.data.definition.DefinitionsDecoder.decode(DefinitionsDecoder.kt:53)
        at world.gregs.voidps.engine.data.definition.AnimationDefinitions$load$1.invoke(AnimationDefinitions.kt:19)
        at world.gregs.voidps.engine.data.definition.AnimationDefinitions$load$1.invoke(AnimationDefinitions.kt:18)
        at world.gregs.voidps.engine.TimedLoaderKt.timedLoad(TimedLoader.kt:18)
        at world.gregs.voidps.engine.data.definition.AnimationDefinitions.load(AnimationDefinitions.kt:18)
        at world.gregs.voidps.engine.data.definition.AnimationDefinitions.load$default(AnimationDefinitions.kt:17)
        at world.gregs.voidps.Main$cache$1$6.invoke(Main.kt:101)
        at world.gregs.voidps.Main$cache$1$6.invoke(Main.kt:101)
        at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:50)
        ... 13 more
```

A config file could not be found. Double check that the file name and the relative directory inside `game.properties` is correct.

## Startup error

```
Exception in thread "main" org.koin.core.error.InstanceCreationException: Could not create instance for '[Singleton:'world.gregs.voidps.engine.data.definition.Definition']'
        at org.koin.core.instance.InstanceFactory.create(InstanceFactory.kt:57)
        at org.koin.core.instance.SingleInstanceFactory.create(SingleInstanceFactory.kt:46)
        at org.koin.core.instance.SingleInstanceFactory$get$1.invoke(SingleInstanceFactory.kt:55)
        at org.koin.core.instance.SingleInstanceFactory$get$1.invoke(SingleInstanceFactory.kt:53)
        at org.koin.mp.KoinPlatformTools.synchronized(KoinPlatformTools.kt:36)
        at org.koin.core.instance.SingleInstanceFactory.get(SingleInstanceFactory.kt:53)
        at org.koin.core.registry.InstanceRegistry.createEagerInstances(InstanceRegistry.kt:91)
        at org.koin.core.registry.InstanceRegistry.createAllEagerInstances$koin_core(InstanceRegistry.kt:62)
        at org.koin.core.Koin.createEagerInstances(Koin.kt:330)
        at org.koin.core.KoinApplication.createEagerInstances(KoinApplication.kt:74)
        at org.koin.core.context.GlobalContext.startKoin(GlobalContext.kt:65)
        at org.koin.core.context.DefaultContextExtKt.startKoin(DefaultContextExt.kt:40)
        at world.gregs.voidps.Main.preload(Main.kt:82)
        at world.gregs.voidps.Main.main(Main.kt:50)
```

This is a generic error in the preload step in setup. Scroll down further for the real error.


## Running with Gradle Wrapper

```bash
> Kotlin could not find the required JDK tools in the Java installation. Make sure Kotlin compilation is running on a JDK, not JRE.
```

Open the `gradle.properties` file, remove the hash from the first line and replace the directory with the location of your JDK installation.

```properties
org.gradle.java.home=C:/Users/Greg/.jdks/openjdk-19.0.1/
```

## Opening the project in IntelliJ

### Incorrect JDK

`File | Project Structure | Project Settings | Project`

`SDK:` and `Language level:` are set to a valid jdk (19+)

### Broken Gradle

`View | Tool Windows | Gradle` (also the elephant icon on the right-hand bar)

Press the `Reload All Gradle Project` button.


> [!IMPORTANT]
> If you have tried all of the above steps and still are having problems please create a [New Issue](https://github.com/GregHib/void/issues/new) with the problem described, what you have tried, and any errors or logs you have and I'll try to help you out.


---

## Unlisted music track list

_Source: Unlisted-music-track-list.md_

https://runescape.wiki/w/Music/unlisted_track_list?oldid=7974081
```
320=
375=
523= 
524= pirate, port sarim esq
525= rising storm dramatic
526= marching with piano tick
527= creeping around with wood blocks
528= drums monkey, desert
529= impending doom, heroic
530= rapid drums, fast
531= fun minigame, gnomeball, desert?
532= __- __- __-
534= same as 524
535=
537= dramatic, snare
538= dramatic, trumbone, drums
539= mystery
540= castle, heroic, trumpet
541= shaker
542= flute
543= same as 524
544= drums, slowed barbarian theme
545= harp
546= bells, drums
547= 
548= heroic, trumpet, bells
549= dramatic, eery, guitar, snare
550= dramatic, creepy, drum,
551= shaker, wood blocks
552= dodo dudu
553= high pitch 552 with scratching
554= drums, trumpet
557= dramatic, vampyre, ghost, halloween
560= shaker, piano, trumpet
561= organ
562= organ
563= quiet start, slow, low, deep
564= bell, trumpet
565= sitar, desert
566= ghost
567= light drum, shaker, desert
568= light, funny, shaker
569= steel drum
570= shaker, piano
571= guitar
572= dramatic
573= dark, slow
574= drums,
575= duplicate of a regular song, pirate, docks, flute
576= duplicate of attack?
627= _ __ _ __ scraping
633= creepy, eery
651= drums
663= light, trumpet, xylophone
664= snare
667= ocean, storm, static, noise
668= violin
669= violin
670= violin + piano
671= violin + piano
672= violin + piano + choir
673= piano
674=
675= violin, bells
676=
677=
678=
679=
680=
681=
682=
683=
684=
685=
686=
687=
688=
689=
690= all above are similar atmosphere getting more dramatic each time
697= musicians..
698=
699=
700=
701=
702=
703=
704=
705=
706=
713= drums
714= weird
727= slow drum, tambourine, trombine
731= ^^
732= choir
738= drum, gong
748= drum, marching
754= light piano
758= ^^ 754 cont.
760= ghost, battle
764=-
767= sitar, doodoodoo doodoodoo
768= deep, dark, water?
769= ^^ similar
770= 
771= 
772= 
773= 
774= 
775= 
776= 
777= 
778= 
779= 
780= 
781= 
782= 
783=
800= 
801= 
802= 
803= 
804= 
805= 
806= 
807= 
808= 
809= 
810= 
811= 
812= 
813= 
814= 
815= Above - more atmosphere
832= high pitch keyboard, shaker, eery
833= organ
834= sitar
835= 
836= 
837= drums
838= organ
839= trumpet
840= 
841= 
842= 
843= 
844= 
845= 
846= 
847=
864= choir, ghostly
865= 
866= 
867= 
868= 
869= 
870= 
871= 
872= 
873= 
874= 
875= 
876= 
877= 
878= 
879= 
880=
881=
882=
883=
884=
885=
886=
887=
888=
889=
890=
891=
892=
893=
894=
895= rocket, rumble
934=
937= huh ho huh
938=
944=
952=
959= 
960= 
961=
967= 
968=
971=
991=
993=
1010= 
1011=
1013= 
1014= 
1015=
```


---

## Update

_Source: Update.md_

How to update your main branch:

For updating but keeping your changes see [rebase](rebase).

# Check for updates
<img width="841" height="420" alt="image" src="https://github.com/user-attachments/assets/017d105c-e93f-492d-8bf7-422fd88ab59e" />

# Updates show in blue
<img width="846" height="531" alt="image" src="https://github.com/user-attachments/assets/e6360098-0ea4-402b-8166-fa30fdd3b21c" />

# Download latest update
<img width="1141" height="676" alt="image" src="https://github.com/user-attachments/assets/2bd5ee0e-3128-4657-9927-a6401b45b8b0" />

# Start using latest update
<img width="1138" height="676" alt="image" src="https://github.com/user-attachments/assets/8a977535-1634-4d82-a598-fe74bb566629" />



---

## Utility scoring curves

_Source: Utility-scoring-curves.md_

# [Linear/Constant](https://www.desmos.com/calculator/ucwghwlawq)
![](https://mcguirev10.com/assets/2019/01-03/linear.png)

# [Exponential](https://www.desmos.com/calculator/dvsqwyfjd4)
![](https://mcguirev10.com/assets/2019/01-03/exponential.png)

# [Sine](https://www.desmos.com/calculator/iywenft729)
![](https://mcguirev10.com/assets/2019/01-03/sine.png)

# [Cosine](https://www.desmos.com/calculator/p9kanbukwd)
![](https://mcguirev10.com/assets/2019/01-03/cosine.png)

# [Logistic](https://www.desmos.com/calculator/bspslggkhv)
![](https://mcguirev10.com/assets/2019/01-03/logistic.png)

# [Logit](https://www.desmos.com/calculator/fmz1hgplok)
![](https://mcguirev10.com/assets/2019/01-03/logit.png)

# [Smooth step](https://www.desmos.com/calculator/fmvfxndlff)
![](https://mcguirev10.com/assets/2019/01-03/smoothstep.png)

# [Smoother step](https://www.desmos.com/calculator/wanqbm1ygl)
![](https://mcguirev10.com/assets/2019/01-03/smootherstep.png)

[Credits Jon McGuire](https://mcguirev10.com/2019/01/03/ai-decision-making-with-utility-scores-part-1.html)


---

## Wildcard System

_Source: Wildcard-System.md_

[Wildcards](wildcards) let content scripts supply flexible ID patterns whilst having minimal runtime cost by aggressively caching lookups into a `wildcards.txt` file.


[Wildcards.kt](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/event/Wildcards.kt) stores a map for each [Wildcard.kt](https://github.com/GregHib/void/blob/main/engine/src/main/kotlin/world/gregs/voidps/engine/event/Wildcard.kt) category containing the wildcard string and it's matching ids.

These are used by [event handlers](event-handlers) to resolve ids at call-time.

```kotlin
fun npcOperate(option: String, npc: String = "*", handler: suspend Player.(PlayerOnNPCInteract) -> Unit) {
    Wildcards.find(npc, Wildcard.Npc) { id ->
        playerNpc.getOrPut("$option:$id") { mutableListOf() }.add(handler)
    }
}
```

In order to reduce performance impact they are cached into a .txt file following the format
```txt
# category=hash
wildcard|id1,id2,id3
```

Where comparing the hash of all the ids at the time the file was saved to the current list allows for detecting if any of the ids in [config-files](config-files) have been updated and a full refresh of that category is needed.

## Background
I've made many iterations on this system and trying to get the balance between flexibility and performance has been [a tricky one](#751). The previous system was a trie which stored parameters as-is and resolved wildcards at call-time rather than startup, it was also complex and verbose to add events. This system is simpler and has no call-time costs but likely impacts startup times more (although that is hard to measure due to how spread-out calls within scripts are).

It however is more practical and easier to use than trying to manually obtain lists of ids, it also has the side-effect of encouraging ids to be named in a predictable manor.



---

## Wildcards

_Source: Wildcards.md_

Wildcards let you target multiple IDs at once when writing [scripts](scripts).

One string can match one or many IDs depending on the pattern, multiple patterns can be combined at once.

## No wildcards

Matches exactly one thing
 - `"cow"`
 - `"bank"`
 - `"doric"`

## Match any - `*`

Matches any sequence of characters

- `"cow_*"` // cow_1, cow_big, cow_brown
- `"banker*"` // banker_1, banker_draynor, banker_varrock
- `"attack_style_*"`

## Match all

`"*"` Matches any value of any kind.

## Match digit - `#`

Matches one digit.

- `"cow_#"` // cow_1, cow_2, cow_7
- `"goblin_#"` // cow_0, ... cow_9

## Lists - `,`
Useful when matching a few unrelated IDs:
- `cow,bull,sheep`
- `bronze_arrows,iron_darts,steel_knives`


# Where You'll Use Wildcards

Wildcards are used anywhere an ID is accepted in [event handlers](event-handlers).

```kotlin
npcSpawn("cow_*") { }
objectOperate("Open", "*_chest") { }
itemOnNPCOperate("bucket,bucket_of_milk", "dairy_cow") { }
interfaceOption("*", "worn_equipment:*_slot") { }
```

> [!NOTE]
> For a more technical understand of how wildcards work see [Wildcard System](wildcard-system).


---

## Sidebar

_Source: _Sidebar.md_

### [**Home**](./home)
- [Roadmap](./roadmap)
- [Content Progress](./content-progress)

# Players
- [Installation](./installation-guide)
- [Commands](./commands)
- [Game Settings](./game-settings)
- [Common Issues](./troubleshooting)

# Content Creation
- [Getting Started](https://github.com/GregHib/void#development)
- Core Concepts
  - [Scripts](./scripts)
  - [Entities](./entities)
  - [Interaction](./interaction)
  - [Dialogues](./dialogues)
  - [Interfaces](./interfaces)
  - [Inventories](./inventories)
  - [Variables](./character-variables)
  - [Clocks](./clocks)
  - [Timers](./timers)
  - [Config Files](./config-files)
  - [Definitions](./definitions)
  - [Combat](./combat-scripts)
  - [Drops](./drop-tables)
  - [Queues](./queues)
  - [Events](./events)
  - [Instances](./instances)
  - [Hunt Modes](./hunt-modes)
  - [Storage](./storage)
- [Troubleshooting](./troubleshooting)

# Developers
- [Philosophy](./philosophy)
- [String Ids](./string-identifiers)
