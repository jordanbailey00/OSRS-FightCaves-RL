claude

agent_handoff.md
debug_viewer_plan.md

● All docs updated, stale files cleaned. Here's where we stand:

  What's done:
  - Cave walls/cliffs rendering from b237 OSRS cache (3,867 objects, 424K
  triangles)
  - All 8 NPC types visible as colored placeholder cubes
  - Player centered in arena
  - Full UI panel working
  - Headless sim backend running

  What's next (tomorrow):
  1. Playable game — keyboard input → fc_step() actions (movement, prayer, food,
   attack targeting)
  2. NPC models — replace cubes with actual cache meshes (Jad especially)
  3. Floor texture — lava crack pattern


PROMPT IN NEW CHAT

ensure the agent handoff and debug viewer plan both have this phased plan approach. we will be using the backend logic from current fightcvaes demo and the RSPS as our reference material. they both spell out all of the backend logic we need to turn this into a playable game in the raylib debug viewer


1. Core shared backend systems you need implemented

These are the systems that must exist in both the headless env and the debug viewer backend.

A. Simulation kernel / authoritative game loop

This is the foundation for everything else.

Fixed tick loop
Tick ordering / update phases
Deterministic state updates
Global simulation clock
Per-entity timers/cooldowns
Scheduled events that land on future ticks
Action queue / intent queue
Validation of whether an action is legal on the current tick
Interrupt / cancel rules
Reset / episode restart logic
Seeded RNG and deterministic random usage
State snapshot / restore support

You need a clearly defined order like:

process queued inputs
update movement
update target acquisition / AI
update combat timers
spawn projectiles / gfx events
apply damage landing this tick
process deaths / drops / splits / despawns
update prayers / stat drain / regen
emit observation / debug state

If this ordering is not frozen, parity will drift.


B. World/map/tile systems

This is the gameplay geometry layer, not just the rendered mesh.

Tile grid / coordinate system
World-to-tile and tile-to-world conversion
Plane/height level support
Collision map / clipping map
Movement-blocked tiles
Projectile-blocked tiles
Line-of-sight rules
Reachability rules
Occupancy rules
Multi-tile entity footprint support
Edge/corner clipping behavior
Arena boundaries
Spawnable tiles
Attackable tiles
Safe tile checks
Pathfinding / route generation
Path following
Repathing when blocked
Fallback behavior when path is invalid
Adjacent tile logic
Distance metrics used by combat/AI
Terrain height sampling for rendering position only

Important distinction:
the rendered terrain mesh is visual, but the tile collision and pathing map is gameplay-authoritative.


C. Entity/state model

You need a clean state schema for all gameplay actors.

Player state
Position
Facing / orientation
Movement path / current step
Current target
Current action
Animation state
Combat timers
Hitpoints
Prayer points
Stats and boosted/drained values
Equipment
Inventory
Prayer activation state
Attack style / weapon mode
Auto-retaliate state if supported
Death / terminal state
Pending hit splats
Pending graphics/projectiles targeting player

NPC state
NPC id / definition id
Position
Size / footprint
Facing
Spawn origin
Movement state
Aggro state
Current target
Attack style
Attack cooldown
Animation state
Hitpoints
Death state
Special behavior state
Split/transformation state where applicable
Per-NPC timers
Wave membership

Transient world entities
Projectiles
Spot animations / graphic effects
Ground items if needed
Temporary markers/debug events
Damage splats
Death animations pending removal


D. Input/action system

This is one of the most important systems for parity.

The backend should not think in “mouse clicks” or “UI buttons.”
It should think in actions/intents.

Examples:

move to tile
attack npc
toggle prayer on/off
eat inventory slot
drink potion inventory slot
equip item inventory slot
change combat style
use item if relevant
no-op

You need:

Action schema
Action validation
Action masks for illegal actions
Per-action preconditions
Input buffering rules
Whether actions are instant or delayed until next tick
Whether a new action cancels movement or attack
Whether consumables can be used while moving/attacking
Whether multiple actions can occur same tick
Priority ordering between actions

For RL, this is critical: the viewer and headless env must call the exact same backend action API.


E. Player movement system

This is more than just sliding a model around.

Click-to-move intent resolution
Pathfinding to destination
One-step-per-tick movement progression
Movement speed rules
Walking vs running if supported
Facing updates
Stop conditions
Repath conditions
Blocked tile handling
Multi-tile collision handling
Move cancellation rules
Movement while under attack
Movement while consuming items
Movement while switching prayers
Movement bounds / arena boundaries
Prevent illegal movement through blocked walls/objects
Reachability to targets
Player spawn/reset position

You specifically called this out, and yes:
only allow movement on tiles that are actually legal according to the authoritative collision/clipping model, not just the visual mesh.

F. NPC movement and AI locomotion

You need NPC movement to be authoritative too.

Spawn behavior
Idle behavior
Aggro acquisition
Chase logic
Leashing / chase limits if any
Pathfinding toward player
Repathing
Size-aware pathing
Movement stop distance for attack range
Collision with player / other NPCs
Occupancy conflict resolution
Target loss / reacquisition
Retreat or reset behavior if applicable
Special scripted movement behavior
Post-hit movement rules
Death cleanup / corpse timing

For Fight Caves specifically:

spawn-at-wave positions
chase behavior
safespot interaction
healer behavior
Tz-Kek split behavior if used in your backend
Jad/healer interaction rules


G. Stats system

You need the numerical combat/stat state system.

Base stats
Current stats
Boosted/drained stats
HP
Prayer points
Stat caps/floors
Regen rules
Stat drain over time if any
Potion boost formulas
Prayer restore formulas
Damage reduction effects if any
Death when HP reaches zero
Respawn/reset stat initialization

Even if only HP and prayer matter immediately, define the full structure cleanly.

H. Inventory system

Inventory is backend-authoritative, UI just shows it.

Inventory container
Slot indexing
Stackable vs non-stackable items
Add/remove item operations
Slot swapping if needed
Empty slot tracking
Item definitions
Consumable definitions
Equipable definitions
Item count handling
Inventory action legality
Inventory state serialization
Ground item/drop handling if needed

For your current scope, this likely minimally includes:

food
prayer potions / restore potions
possibly ranged ammo
possibly runes if magic is used
gear swaps if you plan to support them
I. Equipment system

This is another gameplay-authoritative layer.

Equipment slots
Equip/unequip rules
Item requirements if any
Weapon type classification
Weapon speed
Animation set mapping by equipped weapon
Equipment bonuses
Attack style changes from equipment
Ammo compatibility
Shield/two-hand rules
Equipment-driven range / attack type
Equipment-driven accuracy/max-hit/defence
Serialization of full equipment state

If the agent’s combat outcome depends on it, it belongs in backend.

J. Prayer system

This needs to be a real backend system, not a UI toggle only.

Prayer definitions
Prayer unlock availability for your scope
Activation/deactivation
Exclusive-prayer rules
Prayer drain timing
Prayer drain rates
Prayer restore via potions
Protect prayer effects
Offensive prayer boosts
Defensive prayer boosts
Timing of prayer checks relative to hits/projectiles
One-tick/tick-exact behavior
Prayer disabled when points reach zero
Visual/UI state synced from backend

For Fight Caves this is especially important:

protect from magic
protect from missiles
protect from melee
Jad prayer timing windows
interaction between projectile style and prayer at hit-resolution time
K. Combat target/engagement system

Before damage formulas, you need combat state flow.

Target selection
Target validity checks
Attack initiation rules
Distance checks
LOS checks
Range checks
Attack-chase-stop logic
Single-way vs multi-way combat rules if relevant
Retargeting
Target loss
Death retargeting cleanup
Attack queue / windup
Attack cooldown
Attack speed by weapon/NPC
Out-of-range behavior
Attack interruption rules
L. Player combat system

This is the actual player attack resolution pipeline.

Attack type selection
Melee/ranged/magic handling as needed
Attack animation trigger
Attack timer
Accuracy roll
Damage roll
Max hit rules
Equipment/stat/prayer modifiers
Style modifiers
Projectile spawn for ranged/magic
Hit delay by distance/style
Ammo use if relevant
Magic/ranged resource consumption if relevant
Damage application timing
Splats/impact events
XP tracking only if needed for stats; probably not needed now

For RL/debug purposes, exact timing matters more than pretty visuals.

M. NPC combat system

NPC combat must be defined with the same fidelity.

NPC attack style(s)
Attack range
Attack speed
Attack target selection
Attack windup
Attack animation
Projectile launch if needed
Hit delay
Accuracy/damage logic
Prayer interaction
Melee pathing before attack
Magic/ranged LOS/range checks
Special attacks / style switching if any
Retargeting after movement

For Fight Caves you will need:

melee NPC logic
ranged NPC logic
magic NPC logic
Jad attack selection and timing
healer attack/heal/support behavior if applicable
N. Projectile system

This is often handled poorly in ports and causes big parity bugs.

Projectile spawn
Source tile/entity
Destination tile/entity
Launch tick
Travel duration
Arc/path metadata if needed visually
Impact tick
Association with attack style
Whether projectile is visual-only or authoritative
Prayer check timing relative to projectile/impact
Target movement during flight
Whether projectile homes or lands on original destination
Despawn on impact
Debug tracing of projectile lifetime

The important rule:
backend decides when the hit lands.
The viewer only visualizes it.

O. Damage / hit resolution system

This is separate from attack initiation.

Pending hit queue
Hit landing tick
Hit type / style
Damage amount
Prayer mitigation / nullification
Multiple hits landing same tick
Simultaneous deaths
Overkill handling
HP floor and death trigger
Damage splat generation
On-hit reactions
Death trigger ordering
Reward/terminal consequences
P. Death / despawn / transformation system

Needed for both player and NPCs.

Death detection
Death animation state
Death delay before removal
NPC removal
Player terminal state / reset
Spawn of split NPCs where applicable
Cleanup of pending targets
Cleanup of projectiles referencing dead entities
Reward/terminal flags
Wave-clear detection

Fight Caves-specific:

wave progression after all relevant NPCs dead
special NPC split behavior
Jad/healer phase transitions if applicable
Q. Animation state system

This should be backend-authored enough to preserve parity, even if the actual animation playback is viewer-side.

You need animation state tags for:

Player
idle
walk
run if supported
turn
attack
block/hit-react
death
consume item
prayer toggle if represented
equip change if represented
NPC
idle
move
attack
hit-react
death
special animations

What matters for backend:

animation id / sequence id
animation start tick
expected duration
lockout rules if any
events tied to animation timing

The viewer can interpolate frames, but the simulation needs the authoritative animation event timing.

R. Consumable system

Food and potions need explicit timing/legality.

Use item by inventory slot
Item definition lookup
Consume legality
Eat animation/state
Drink animation/state
HP restoration formula
Prayer restoration formula
Overheal rules if any
Consumption delay
Combo-eat/drink rules if applicable
Item removal / replacement with vial if needed
Consumable action priority
Can consume while moving?
Can consume while attacking?
Can consume same tick as prayer toggle?
Can consume on tick of incoming damage?

This is extremely important for RL behavior and debugging.

S. Wave / encounter scripting system

For Fight Caves you need an encounter controller.

Wave index
Wave definition data
Spawn list per wave
Spawn timing
Spawn positions
Wave complete detection
Transition to next wave
Final boss spawn
Healer spawns / triggers
Terminal victory state
Reset to initial wave on episode reset

This can be data-driven and separate from generic combat systems.

T. Special-case Fight Caves mechanics

These are not generic MMO systems, but you need them if they exist in the backend.

Jad style selection logic
Jad attack telegraph state
Jad projectile style/timing
Protect prayer correctness window
Healer spawn conditions
Healer aggro / heal behavior
Healer-target switching behavior
Tz-Kek split behavior
NPC size-specific pathing around obstacles
Safespot behavior emergent from clipping/LOS
End-of-run success/failure states
U. Observation/reward/terminal integration layer

Because this backend also powers RL, this is part of the real system surface.

Observation extraction from authoritative state
Action mask generation
Reward feature extraction
Terminal code generation
Episode truncation rules
Reset contract
State-to-observation ordering stability
NPC sorting rules in observations
Hidden/internal debug fields separated from policy observation
Replay trace support

The viewer should be able to show the same internal values the RL side sees.

2. Viewer-only systems

These do not belong in the headless backend, but the viewer needs them.

A. Rendering/presentation
Terrain rendering
Object rendering
NPC model rendering
Player model rendering
Equipment model overrides if applicable
Animation playback from backend animation state
Projectile rendering
Spot animation rendering
Health bars
Hit splats
Nameplates/debug labels
Selection highlights
Target highlights
Prayer icon overlays if useful
Tile overlays / path overlays / LOS overlays / collision overlays
B. Camera system
Orbit camera
Pan
Zoom
Reset camera
Follow player mode
Freecam mode
Lock-to-entity debug mode
Camera clipping and sensible limits
World picking / mouse raycasting
C. UI panels

You mentioned these explicitly, and yes, they should exist in the viewer:

Inventory UI
Prayer UI
Combat UI
Stats UI
Equipment UI
Buff/debuff state display
HP/prayer readouts
Current target panel
Current tick display
Current wave display
Terminal state display
Action log
Debug event log
Hover info panel
Agent observation/reward overlay if useful

These panels should reflect backend state only.
No duplicated UI-side state.

D. Human control binding layer
Click-to-move
Click NPC to attack
Click prayer icon to toggle
Click item to eat/drink/equip
Keyboard shortcuts if desired
Pause/unpause
Tick-step one frame
Fast-forward / slowdown
Reset run
Switch between human control and agent control
Replay mode
E. Debugging tools in viewer

This is one of the main reasons the viewer exists.

Show collision map
Show blocked tiles
Show pathfinding result
Show current target
Show attack range
Show LOS rays
Show projectile timelines
Show pending hits
Show per-entity cooldowns
Show active prayers
Show stat values
Show action masks
Show chosen RL action
Show reward breakdown
Show NPC AI state
Show animation id/state
Show tick-ordered event stream

This is where the viewer becomes far more valuable than just “playable graphics.”

3. Data/content systems you need to account for

Because you are using an OSRS 237 cache while still using the Void backend logic as oracle/reference, you need a data normalization layer.

A. Definition loading / normalization
NPC definitions
Item definitions
Object definitions
animation/sequence definitions
projectile/spotanim definitions
underlay/overlay/floor data if needed
map/clipping data
equipment bonus tables
prayer definitions
consumable definitions
combat style definitions
B. ID translation / compatibility layer

This is a major one.

You already called it out: IDs may differ between Void/634 and OSRS 237.

So you need:

npc id mapping
item id mapping
object id mapping
animation id mapping
projectile id mapping
prayer id mapping if represented numerically
encounter-specific id mapping

The safest approach is to normalize to internal canonical ids/enums inside your backend, then have loaders/adapters from each source.

Do not let raw cache ids leak everywhere.

C. Content authoring/config

You also need:

wave definitions
spawn tables
NPC combat stats
item stats
food values
potion restore values
prayer drain rates
attack timing tables
animation bindings
projectile travel timing
loot/drop data only if needed

This content should be data-driven, not scattered in code.

4. Everything else you need to account for

This is the second half of the problem. Even if all gameplay systems exist, these are the things that usually break the project.

A. Determinism

Since the viewer and headless env share a backend, you need:

deterministic tick order
deterministic RNG
deterministic action application
deterministic pathfinding results
deterministic target selection
deterministic observation extraction
deterministic replay from seed + action trace
ability to compare viewer vs headless traces

If this is weak, the viewer becomes misleading.

B. Strict backend/frontend separation

Do not let viewer code invent gameplay behavior.

The viewer must not decide:

damage
timing
collision
prayer effects
movement legality
animation event timing
projectile impact timing
target validity

It only:

sends actions
reads state
draws state
C. State serialization / replay

For debugging and demoing agents, you need:

save/load snapshots
full action trace capture
deterministic replay
event log export
per-tick state dump
diff tooling between two runs
minimal replay file format
optional rewind support

This is one of the highest-value systems for RL debugging.

D. Debug instrumentation

You want the backend to expose enough data that the viewer can explain behavior.

Examples:

“why move failed”
“why attack failed”
“why target lost”
“why prayer didn’t protect”
“why path changed”
“why NPC didn’t move”
“why item could/couldn’t be consumed”
“what cooldown blocked action”

Without this, debugging becomes guesswork.

E. Parity contracts

You should freeze contracts for:

tick ordering
action schema
observation schema
reward schema
terminal codes
combat timing rules
prayer timing rules
pathing/collision semantics
NPC target selection rules
wave progression rules

These need written docs and tests.

F. Acceptance tests / verification

You need test coverage at several levels.

Unit tests
collision legality
LOS
stat restore formulas
prayer drain
damage formulas
item consumption
equip logic
attack timing
projectile timing
Scenario tests
move around blocked tiles
safespot a melee NPC
pray against ranged/magic hit correctly
eat at low HP
drink prayer potion at zero prayer
wave clear transitions
Jad style + prayer timing
healer behavior
player death / reset
Replay parity tests
same seed + same actions = same state trace
headless and viewer backend identical state traces
G. Performance constraints

Because the backend is shared with RL training, every system has to be built with training in mind.

Need to account for:

no viewer dependencies in backend
no per-frame rendering data inside core step path
efficient entity storage
efficient collision queries
efficient pathing
no excessive heap allocation per tick
low-cost observation extraction
clear fast path for headless batch stepping
optional debug instrumentation toggles so training path stays lean
H. Content scope control

A common failure mode is accidentally trying to rebuild all of RuneScape.

For your actual purpose, you likely do not need:

banking
shops
trading
chat
quests
skilling systems
world persistence
networking
login/account systems
general object interaction outside what Fight Caves needs

You need enough systems to make Fight Caves mechanically complete and debuggable.

I. Canonical internal model

You need one internal representation for:

tiles
entities
stats
combat styles
prayers
items
actions
animations
projectiles
terminal states

Do not let:

cache ids,
viewer ids,
legacy Void ids,
OSRS 237 ids

all compete directly inside the sim core.

J. Failure handling / edge cases

You should explicitly account for:

dead target mid-attack
projectile in flight when source/target dies
simultaneous lethal hits
prayer toggle on exact hit tick
consume item on exact damage tick
blocked destination tile
target becomes unreachable
NPC stuck on pathing edge case
empty inventory slot click
attempting invalid equipment swap
zero prayer activation attempt
multiple actions issued same tick
stale target handle/entity id
wave transition with pending projectiles/hits
reset during active animations/projectiles

These edge cases matter a lot in RL.

5. Recommended system grouping for implementation

If you are turning this into a build plan, I would group it like this:

Group 1: authoritative simulation foundation
tick loop
entity state
map/collision/LOS
action system
reset/seed/replay
Group 2: movement and navigation
player movement
NPC movement
pathfinding
occupancy
arena boundaries
Group 3: combat foundation
targeting
attack timers
damage resolution
death handling
projectile timing
Group 4: player support systems
stats
inventory
equipment
prayers
consumables
Group 5: encounter-specific Fight Caves logic
wave scripting
spawn tables
Jad logic
healer logic
split NPC logic
Group 6: animation/effect state
animation state machine
spotanims/projectiles
hit splats/health bars as derived events
Group 7: viewer frontend
camera
UI tabs
click mapping
debug overlays
playback controls
Group 8: parity/debug/RL instrumentation
observation extraction
action masks
reward features
replay traces
per-tick diff tooling
6. Practical rule for deciding what belongs where

Use this rule:

Shared backend

Anything that answers:

Can this action happen?
What happens if it does?
When does it happen?
Who takes damage?
Can this tile be entered?
Does prayer block this hit?
Does this NPC move or attack?
Is the episode won/lost?
Viewer-only

Anything that answers:

How do we show it?
How do we inspect it?
How does a human send an action?
How do we visualize internal state?
7. The most important “extra” things people forget

These are the ones I would watch closely.

tick ordering
prayer timing semantics
projectile impact timing
tile collision vs visual mesh mismatch
NPC size/footprint pathing
LOS vs movement clipping distinction
action cancellation/priority rules
consumable timing and lockouts
animation state as gameplay timing source where needed
ID normalization between OSRS 237 cache and Void backend
deterministic replay
viewer never becoming authoritative
8. Bottom line

To make the debug viewer truly useful for your project, it needs to cover:

all authoritative Fight Caves gameplay systems,
all state needed for RL observations/rewards/terminals, and
all viewer tooling needed to inspect and explain that state.

So in practice, the full coverage target is:

simulation kernel
tile/collision/LOS/pathing
player state
NPC state
inventory
equipment
prayers
stats
consumables
combat
projectiles
damage/death
animations/effects state
wave scripting
Fight Caves specials
observation/reward/terminal extraction
deterministic replay/debug instrumentation
viewer rendering/UI/camera/input overlays

That is the complete system surface.
