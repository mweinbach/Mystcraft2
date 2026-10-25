# Defense gameplay and source comparison

The Fabric slice adapts upstream MIT Java: `ItemRadarGun`, `ItemAntidote`,
`ItemDefuser`, `TileRadarStation`, `TileEMPTower`, `BlockSpikes`, `BlockConcrete`,
and machine/missile configuration defaults. All models and GUI art are original
or vanilla resource references.

## Operating machines

Right-click radar, EMP or SAM stations to open their actual server-backed
container. The battery slot accepts the port's rechargeable energy battery.
Machine energy is exposed through TeamReborn's transactional Fabric energy API
as an insert-only view: other mods can charge a machine but cannot extract from
it (see [ENERGY.md](ENERGY.md)). The battery slot is also reachable through
Fabric's `ItemStorage.SIDED` lookup, so hoppers and pipes can insert or remove
batteries. New machines start empty. Configuration buttons change detection/EMP/SAM range,
radio channel (±1 and ±100), explosive/cargo missile
filters, radio, radar redstone output and enabled state. The SAM radar-only toggle disables autonomous scanning while
retaining incoming radio targeting. Server handlers validate the open container,
player distance, block identity, spectator status, build permissions and button
range. Battery inventory, energy, settings and cooldown persist in block NBT.

Radar consumes the upstream default **1000 energy per tick**, stores 20,000,
and scans loaded missiles every powered tick. Its initial detection range is
100, configurable to upstream maximum 500. Upstream's radar also stores a
trigger range (default 50) that nothing reads; the port has no such control.
Adapted from MIT ICBM-Classic `content/blocks/radarstation/TileRadarStation.java`,
the scan covers a full-height square column of half-width equal to the range,
ignores missiles airborne for one tick or less, and splits the rest into
**incoming** (their next position is closer to the radar) and merely
**detected**, each sorted nearest first. EMP-disabled flights are never
incoming. The radar state is OFF (unpowered or disabled), ON, WARNING
(detections only) or DANGER (incoming). It tracks explosive missiles and, as a
port extension, held-item cargo missiles. When redstone output is enabled the
radar emits strong and weak power equal to the incoming count capped at 15;
the screen's Redstone button or right-clicking the radar with redstone dust
toggles that output, and the choice persists. With a radio channel set, the
radar broadcasts an incoming-missile trigger naming the nearest incoming track,
then waits 40 ticks (upstream SAM delay) before the next alert. An unpowered
radar clears its output; it cannot scan for free.

Load `sam_missile` items into the SAM station by right-clicking, or with
hoppers and item pipes, up to 15. Automated loading goes through an
insert-only `ItemStorage.SIDED` magazine on every side: it accepts only
`sam_missile`, stops at 15, updates the counter only when the transfer
commits, and never gives loaded missiles back. Apply
redstone to enable autonomous defense, or configure a radio channel matching a
radar. Target range defaults to upstream SAM 30 and is adjustable to 128. Family
filters apply to both paths. The station rejects receding, disabled or already
assigned targets and trajectories passing farther than 16 blocks from it.
Each successful spawn consumes one missile and starts a 40-tick firing delay.
Remaining magazine ammunition drops when the station is broken.

SAMs are real guided entities rendered with the custom missile item oriented
to their flight velocity. They predict target movement, steer at up to the
upstream default **4 blocks per tick**, and expire after **200 ticks**. They
neutralize an intercepted missile within 1.5 blocks of their flight segment
without detonating its payload. Block raycasts stop them at walls. Target UUID,
elapsed lifetime and vanilla position/velocity survive saves. Missing targets,
unloaded terrain, world boundaries and EMP end flight safely. Held-item cargo
is recovered without running its tool/block interaction. Interception currently
uses reliable neutralization instead of upstream configurable attack damage.

EMP starts with upstream radius **60**, max base radius **150** and firing cost
`radius * radius * 100`, with **200-tick cooldown**. Adapted from MIT
ICBM-Classic `content/blocks/emptower/TileEMPTower.java`, its buffer is dynamic:
capacity is the firing cost plus 100 ticks of upkeep, upkeep (`radius * 10`
per tick) drains every tick, and the tower refuses energy while cooling down.
Lowering the radius keeps energy above the new capacity (as upstream) and a
reload keeps it too; only insertion stops until it drops below.
Maximum insertion is 10,000 per transfer. Strong redstone, a radio trigger or
the validated Fire EMP button fires it, only when the complete firing cost is
stored. The screen shows capacity, firing cost and upkeep. Place
`emp_tower_extension` blocks in the column above the base: every booster in the
contiguous column of tower/booster blocks adds upstream **20 blocks** of
maximum radius. The column scan stops after 64 blocks or 16 boosters, and EMP
fields are capped at 512. Breaking the base leaves boosters in place but inert;
removing boosters immediately lowers the range cap.

## Radio

Adapted from MIT ICBM-Classic `lib/radio/RadioRegistry.java` and
`api/radio/`, `radio/RadioNetwork` is one registry per dimension. Channels are
integers 1–9999 (0 means unset; upstream uses string frequencies). Block
entities register when loaded or first ticked and unregister when removed or
unloaded; radio never loads chunks and has no distance limit inside a
dimension. Message types are text, target, trigger, trigger-with-target and
incoming-missile, and receivers can reply to the sender (players see replies in
chat). One broadcast reaches at most 256 receivers and nested re-broadcasts
stop at depth 4. Radar only sends. EMP towers fire on a trigger, or on an
incoming-missile trigger whose missile is inside their radius, and reply when
they fire. SAM stations (port machine) launch at the referenced missile when it
passes their checks. Launcher behavior is described in
[LAUNCHERS.md](LAUNCHERS.md). The older relay toggle and 512-block radius no
longer exist.

## EMP fields and protection

EMP disables missile/launcher electronics for 200 ticks. Per-dimension
`PersistentState` retains at most 256 spherical fields, with finite positions,
radius at most 512 and absolute game-time expiry. Reload preserves remaining
duration. EMP neutralizes in-flight SAMs and safely recovers cargo missiles.
EMP payloads and towers also drain other mods' Team Reborn Energy blocks and
items in the blast sphere, bounded and configurable as described in
[ENERGY.md](ENERGY.md).

Radar guns trace blocks up to 200 away and share launcher target NBT;
sneak-use clears their target. Antidote consumes after 32 ticks, clears status
effects and the port's persistent exposure/infection state. Defusers attack
primed explosives, missiles and TNT, recovering ammunition and using one of
128 durability points. Spectators cannot defuse.

Normal/compact/reinforced concrete retains source hardness 10 and resistance
28/280/2800 and needs a pickaxe. Numeric resistance uses 1.19.2 mechanics; exact
cross-version blast shapes are unverified. Reinforced glass has hardness 10 and
resistance 28.8 (the source's 48 converted to 1.19 units), the stone sound, no
mob spawning or suffocation, and drops itself without a tool. Normal/poison/fire
spikes are iron blocks that need a pickaxe, have hardness/resistance 1 and a
full-block selection box, let entities overlap and hit every overlap tick with
the `icbm_port.spikes` damage source (vanilla hurt immunity is the only
cooldown, as upstream). Damage per type comes from `config/icbm_port-blocks.json`
(`normalSpikeDamage`, `poisonSpikeDamage`, `fireSpikeDamage`; default 1, minimum
1); poison lasts 140 ticks and fire seven seconds. The glass button behaves like
a stone button (20-tick pulse, arrows ignored) with hardness 0.5 and glass
sounds; the glass pressure plate detects every entity, including items, with
hardness 0.3, resistance 0.6 and glass sounds.

## Validation and remaining differences

Defense GameTests cover sphere bounds, loaded radar output, guided SAM firing
and exact ammunition use, persisted target/lifetime, receding-target rejection,
wall obstruction, EMP field reload/expiry/storage/radius caps, spike effects,
cargo missile countermeasures, configuration/energy persistence and radio
channel isolation. `DefenseParityGameTests` adds the radar column/age/incoming
split, state, strong redstone and toggle persistence, delayed radar radio
alerts, radio lifecycle registration and recursion bounds, server-side button
validation, and EMP dynamic capacity, upkeep, cooldown refusal, booster count,
inert orphan boosters and radio-triggered firing. Interop GameTests use
the public lookups on placed blocks: insert succeeds and extraction fails on
launchers and all three machines, EMP drains a test energy block and battery
item inside its radius but not outside or when disabled, and a vanilla hopper
and `ItemStorage.SIDED` load SAM ammunition while rejecting other items. Integration builds and dedicated-server tests passed. Radar/EMP screens were inspected in a live client at the default window size before this revision; the new Redstone, ±100 channel and EMP cost lines have not yet been checked in a live client. See VALIDATION.md.

Remaining upstream differences include full machine/radio GUI appearance (no
radar blip map), string radio frequencies, separate base/booster blocks with a
bounded column scan, the held-item radar filter, upstream ownership/faction
behavior and configurable SAM attack damage.
