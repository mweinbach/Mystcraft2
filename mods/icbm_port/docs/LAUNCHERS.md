# Launchers and flight

This Fabric subsystem adapts ICBM Classic's MIT-licensed launcher inventory,
energy networks, targeting, modular flight and saved missile tracker. Models use
vanilla textures. It does not claim the original rendering or complete parity.

## Blocks, inventory and energy

The original `launcher` remains compatible with prototype worlds: directional
placement, no frame requirement, 10,000 energy capacity, 1,000 per launch, and
1,000 initial charge. Its unconfigured direct projectile retains the prototype
speed/lifetime for compatibility. Real registered tier 1/2/3 bases face upward
and require respectively 1/2/3 adjacent horizontal frame blocks. Tiered bases and
the cruise launcher now use the upstream `ConfigLauncher` defaults: **1,200
energy capacity and 1,200 per launch**, starting empty. All source bases and cruise use the upstream **10,000-block** range, controlled
by the bounded `maxRange` flight setting (hard limit 30,000). The prototype
retains its earlier 4,000-block safety range. Tier differences are structural
frame requirements, rather than invented tier range limits.

Missile stacks retain payload, cluster manifest, held-item cargo and item NBT.
Each successful launch consumes one actual missile. Slot 0 accepts loaded
missiles and slot 1 accepts a real energy battery or redstone dust. Hoppers can
access these slots. Breaking a block drops its inventory. Redstone is an explicit
compatibility fallback supplying up to 1,000 energy per dust (limited by remaining
capacity). Batteries and external providers use the transactional Team Reborn
Energy API, with the root energy module's numeric units. Charge uses a battery
first; launch also charges if needed. Malformed saved energy is capacity-clamped.

Components form physical six-neighbor networks, recomputed for each operation:
32-block traversal radius, at most 128 loaded component nodes. Removing a
connector immediately revokes screen access and splits energy/ammunition pools.
A launcher can pull a missile from another connected base and transactionally
transfer its missing launch energy. Support blocks distribute hand-fed redstone
energy. Screens open connected bases; sneak-use cycles the player's selection.
A player must remain within eight blocks of the base or a connected screen.
No cached connection can authorize use after the path breaks.

A rising redstone edge schedules firing four ticks later. Sustained power does
not repeat. A powered connected screen supplies a network launch pulse. EMP
prevents launches and disables missile guidance. Failed muzzle/target checks
preserve ammunition. There are no original frame animations, support accuracy
bonus, seat/riding controls, or full original multipart geometry.

## Targeting and radio

GPS air-use records the player position; block-use records that block. The laser
designator records a server raycast hit up to 256 blocks away, falling back to the
player position on a miss. Use either on a launcher to copy coordinates. For
ordinary payloads the dimension must match. Sneak-use records the launcher
position instead. The screen configures flight coordinates, direct/arc mode,
and channel 0–9,999. Direct cruise flight aims toward coordinates without an arc.

A loaded **ender** missile interprets a copied GPS position as the payload's
relocation destination, independent of the missile flight path. That destination
may be in another existing server dimension and is checked against its border
and build height. It survives launcher/missile saves and reaches the payload
implementation as `getTargetDimension()` / `getTargetPosition()`.

A radio pairs by use on a launcher, storing its position, dimension and channel.
Air-use launches that paired loaded launcher within 256 blocks when its channel
matches. It does not load arbitrary chunks. Screen packets use fixed length,
known action, current handler and matching synchronization ID, valid interaction
path, spectator checks, world bounds and tier range. Invalid settings are atomic.

## Staged and saved flight

`FlightProfile` records the source defaults: warmup **40 ticks**, climb **2
blocks**, default lock height **3**, direct powered speed **2**, simulation speed **10**, reentry speed
**1**, short-arc threshold **500**, cruise/handheld fuel **200**, and tier health
**50/80/100/200**. Targeted flight advances through warmup, accelerating climb/lock-height steps and a sampled
parabolic short arc, or vertical ascent for long range, then simulated travel
and descending reentry. The short-arc sampler is a bounded numerical adaptation;
the source climb accelerations .005 and .1 and default lock height are retained,
while the original renderer, seat logic and arbitrary launch angles are absent. Health,
fuel, EMP, cargo/manifests and stage progress persist. Powered direct missiles
fall under gravity when fuel runs out. Damage can destroy missiles without
creating a free detonation. Targeted cluster manifests burst at arrival; direct
cluster missiles retain their forty-tick burst behavior.

Upstream exit/enter heights 500/450 are clamped below the modern dimension build
ceiling. At high altitude or an unloaded next chunk, `FlightLedger` stores the
registered entity NBT and removes the live entity. It never runs a blast while
the missile is virtual. Generic `FlightTracked` supports both payload and
held-item missiles. Travel delay uses horizontal distance / 10; reentry uses
its original UUID and payload/cargo and starts a descending stage.

The ledger persists generation numbers to reject stale pre-transfer entities.
Admission is bounded to 256 pending flights and 8,192 retained identity records;
when full, admission fails inertly. Pending data is validated and clamped on
load. Unavailable destinations expire after at most 72,000 loaded-world ticks.
Generation records remain to prevent old chunks resurrecting a prior flight.
This intentionally favors at-most-once transfer over duplicating payloads if a
server crashes between independent entity/chunk and world-state saves.

`config/icbm-flight.json` controls `temporaryReentryTickets` (default true),
`maxRange` (default 10,000, hard limit 30,000), `maxActiveTickets` (default 4,
hard limit 16), and `pendingLifetimeTicks` (default
72,000). Each temporary ticket lasts 1,200 ticks, with at most three attempts per
flight; tickets are removed early when their missile is gone. They are not
permanent forced chunks. Disabling tickets leaves virtual missiles pending
until their destination naturally loads or their bounded lifetime ends.

## Held-item delivery

Loaded held-item missiles use the same `StagedFlight` and generic ledger as
payload missiles; no fabricated explosive payload type is used. A fixed launcher
has no live player owner. Held operations requiring player ownership (block
placement/tool mining) therefore use the delivery subsystem's intact recovery
fallback. The launcher never impersonates the player who opened its screen.

The launcher layout was checked in a live client at default 854×480 / GUI scale 2; coordinates and channel settings survived close/reopen. Two-client visuals remain unverified.
