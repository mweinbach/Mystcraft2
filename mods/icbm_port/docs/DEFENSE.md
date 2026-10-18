# Defense gameplay and source comparison

The Fabric slice adapts upstream MIT Java: `ItemRadarGun`, `ItemAntidote`,
`ItemDefuser`, `TileRadarStation`, `TileEMPTower`, `BlockSpikes`, `BlockConcrete`,
and machine/missile configuration defaults. All models and GUI art are original
or vanilla resource references.

## Operating machines

Right-click radar, EMP or SAM stations to open their actual server-backed
container. The battery slot accepts the port's rechargeable energy battery.
Machine energy is exposed through TeamReborn's transactional Fabric energy API.
New machines start empty. Configuration buttons change detection/EMP/SAM range,
radar trigger range, radio channel, explosive/cargo missile filters, radio/relay
and enabled state. The SAM radar-only toggle disables autonomous scanning while
retaining incoming radio targeting. Server handlers validate the open container,
player distance, block identity, spectator status, build permissions and button
range. Battery inventory, energy, settings and cooldown persist in block NBT.

Radar consumes the upstream default **1000 energy per tick**, stores 20,000,
and scans loaded missiles every powered tick. Its initial detection range is
100 and trigger radius 50; detection is configurable to upstream maximum 500.
It tracks explosive and held-item missile families. Redstone reports incoming
tracks capped at 15. Incoming classification projects velocity toward the
trigger sphere, ignores receding flights and EMP-disabled missiles. Radar
broadcasts an incoming UUID at most every 40 ticks on its configured channel.
An unpowered radar clears its output; it cannot scan for free.

Load `sam_missile` items into the SAM station by right-clicking, up to 15. Apply
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
`radius * radius * 100`, with **200-tick cooldown**. Redstone, radio incoming
messages or the validated Fire EMP button can request firing. It fires only
when the complete action cost is available. Place `emp_tower_extension` blocks
in a connected vertical column above the base: each adds upstream **20 blocks**
of configurable maximum radius. Up to 16 extenders are supported; scans and EMP
fields are globally capped at 512. A broken base/gap tears down orphan pieces
with ordinary drops; removing boosters immediately lowers the range cap.
The source's documented range-scaled upkeep was itself a TODO and is not charged
here. The port uses a bounded 32,000,000 energy buffer for EMP/SAM, rather than
upstream's dynamic EMP buffer. Maximum insertion is 10,000 per transfer.

## Radio

The loaded, per-dimension registry supports channels 1–4095; zero is unset.
Radar sends incoming-target messages to matching receivers within 512 blocks.
SAM receivers launch against validated targets and EMP receivers fire only when
the target is inside their configured sphere. Optional machine relays forward
messages from their own position. A delivery visits each receiver once, allows
four hops and caps total deliveries at 256. Receivers are weakly referenced,
unregister on removal and are pruned when their chunks unload; radio never
loads chunks. These are working incoming-message/channel semantics, not the
full upstream command vocabulary or radio GUI protocol.

## EMP fields and protection

EMP disables missile/launcher electronics for 200 ticks. Per-dimension
`PersistentState` retains at most 256 spherical fields, with finite positions,
radius at most 512 and absolute game-time expiry. Reload preserves remaining
duration. EMP neutralizes in-flight SAMs and safely recovers cargo missiles.

Radar guns trace blocks up to 200 away and share launcher target NBT;
sneak-use clears their target. Antidote consumes after 32 ticks, clears status
effects and the port's persistent exposure/infection state. Defusers attack
primed explosives, missiles and TNT, recovering ammunition and using one of
128 durability points. Spectators cannot defuse.

Normal/compact/reinforced concrete retains source hardness 10 and resistance
28/280/2800. Numeric resistance uses 1.19.2 mechanics; exact cross-version blast
shapes are unverified. Reinforced glass resistance is 60. Normal/poison/fire
spikes permit overlap and deal one damage, with 140 poison ticks or seven fire
seconds. Glass buttons and plates use vanilla behavior.

## Validation and remaining differences

Defense GameTests cover sphere bounds, loaded radar output, guided SAM firing
and exact ammunition use, persisted target/lifetime, receding-target rejection,
wall obstruction, EMP field reload/expiry/storage/radius caps, spike effects,
cargo missile countermeasures, configuration/energy persistence, EMP extension
teardown/action cost and radio channel/relay isolation. Integration builds and dedicated-server tests passed. Radar/EMP screens were inspected in a live client at the default window size, and radar changes were confirmed after close/reopen; see VALIDATION.md.

Remaining upstream differences include full machine/radio GUI appearance,
exact upstream energy buffer sizing, all radio command/message types,
upstream ownership/faction behavior and configurable SAM attack damage.
