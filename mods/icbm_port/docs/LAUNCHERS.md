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
connector immediately revokes screen access and splits the network. Adapted
from MIT ICBM-Classic `content/blocks/launcher/network/`, every frame, support,
screen and connector exposes two Fabric lookups rebuilt from that walk: an
insert-only Team Reborn `EnergyStorage` that offers each connected launcher
`floor(amount / n) + 1` of the inserted energy (extraction always fails), and an
`ItemStorage` combining the launcher inventories. Launchers no longer silently
move energy or ammunition between each other; each base fires from its own
slots and buffer. Support blocks still distribute hand-fed redstone energy.
Screens open connected bases; sneak-use cycles the player's selection. A player
must remain within eight blocks of the base or a connected screen. No cached
connection can authorize use after the path breaks.

### Launch rules

Base launchers follow upstream `TileLauncherBase` / `LauncherBaseCapability`:

- **Minimum range**: refused when both |dx| and |dz| are below 10 (cruise:
  3D distance below 20).
- **Maximum range**: per-axis square (|dx| or |dz| above the range refuses), not
  a circle. Staged flight uses the same square, so every accepted target can be
  flown to.
- **Inaccuracy** (base launchers only, targeted flights): offset radius
  `2 + distance² / range² × 10 + (launchers − 1)`, where *launchers* is the
  number of bases fired together by a screen or radio trigger. The constants are
  fixed rather than configurable, and offsets are rounded to blocks. Unlike
  upstream, an offset that would leave the flight bounds (range square, world
  border, height) is halved until it fits, so a target at the range edge never
  fails at random. As upstream,
  a targeted cluster missile's 3-block release check uses this offset target.
- **Lock height** 3–64 (default 3) and **firing delay** 0–1,200 ticks are set in
  the screen. A delayed launch is checked first, then held as a pending firing
  package (target, countdown, launcher count) that persists in block NBT. While
  it is pending, further launch requests report `DELAYED`.
- Each attempt records a status (`READY`, `LAUNCHED`, `DELAYED`, or an error such
  as power, empty, invalid stack, minimum/maximum range, invalid target, frame,
  blocked muzzle, EMP, spawn failure, queued). The screen shows it top-right.

A powered **screen** fires every launcher in its network, with the launcher
count feeding inaccuracy, while it receives **strong** redstone (for example a
lever or button on the screen itself); it re-checks every three ticks, as
upstream does. Weak power such as a nearby redstone block no longer triggers it.
The base's own rising-edge redstone input (fires four ticks later, no repeat
under sustained power) is kept as a port extension. EMP prevents launches and
disables missile guidance. Failed muzzle/target checks preserve ammunition.

Loaded tiered bases offer a seat (`PlayerSeatEntity`) on the launch face,
refreshed every three ticks and removed when the base is emptied or broken;
whoever sits in it rides the launched missile (sneak to interact with the
launcher instead). Every launch path (redstone, GUI, screen network firing,
radio triggers and a delayed firing package when its countdown ends) spawns
through the same `fireNow` step, so the rider moves to the missile and loaded
cluster ammunition becomes the actionable cluster missile. Creative players
holding a minecart can seat a minecart. Missiles carrying a player stay real
entities instead of entering virtual flight. Prototype and cruise launchers
have no seat, and there is no seat-side rotation control. There are no
original frame animations, support accuracy bonus, cruise turret aiming phase
(cruise fires on the next tick), or full original multipart geometry.

## Targeting and radio

GPS air-use records the player position; block-use records that block. Use it
on a launcher to copy coordinates. For ordinary payloads the dimension must
match. Sneak-use records the launcher position instead. The screen configures
flight coordinates, direct/arc mode, channel 0–9,999 (0 is unset), firing delay
and lock height. Direct cruise flight aims toward coordinates without an arc.

A loaded **ender** missile interprets a copied GPS position as the payload's
relocation destination, independent of the missile flight path. That destination
may be in another existing server dimension and is checked against its border
and build height. It survives launcher/missile saves and reaches the payload
implementation as `getTargetDimension()` / `getTargetPosition()`.

Each launcher base is a radio receiver on its configured channel (upstream puts
the receiver in the screen block entity, which this port does not have). Adapted
from MIT ICBM-Classic `content/blocks/launcher/screen/RadioScreen.java` and
`content/blocks/launcher/cruise/RadioCruise.java`:

- A **target** message is validated (range, height, border); an invalid target
  is answered with an error and *not* stored. A valid one replaces the target
  and is acknowledged unless the message also triggers.
- A **trigger** message fires: base launchers report success with coordinates
  and distance, or the failure status; cruise launchers refuse with a "queued"
  reply while a launch is already pending.

The **remote detonator** (`radio` item) copies the channel from any radio block
when used on it and, on air-use, broadcasts a trigger on that channel with a
20-tick cooldown; replies arrive as chat messages. There is no in-item channel
editor. The **laser designator** shares the channel behavior, raycasts up to 200
blocks and broadcasts a trigger carrying the hit position; it refuses to aim at
launcher blocks and reports a miss. Radio only reaches loaded, registered
receivers in the same dimension and never loads chunks. Screen packets use fixed
length, known action, current handler and matching synchronization ID, valid
interaction path, spectator checks, world bounds, removed/unloaded launcher
checks, delay/lock bounds and tier range; invalid settings change nothing.

## Staged and saved flight

`FlightProfile` records the source defaults: warmup **40 ticks**, climb **2
blocks**, default lock height **3**, direct powered speed **2**, simulation speed **10**, reentry speed
**1**, short-arc threshold **500**, cruise/handheld fuel **200**, and tier health
**50/80/100/200**. Targeted flight advances through warmup, accelerating climb/lock-height steps and a sampled
parabolic short arc, or vertical ascent for long range, then simulated travel
and descending reentry. The short-arc sampler is a bounded numerical adaptation;
the source climb accelerations .005 and .1 and default lock height are retained,
while the original renderer and arbitrary launch angles are absent. Health,
fuel, EMP, cargo/manifests and stage progress persist. Powered direct missiles
fall under gravity when fuel runs out. Damage can destroy missiles without
creating a free detonation, except that cluster missiles (`ActionableMissileEntity`)
release their bomblets when destroyed by explosions or fire, as upstream. Targeted
cluster missiles release within 3 blocks of the target and never on impact; direct
cluster missiles retain the port's forty-tick burst behavior.

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
