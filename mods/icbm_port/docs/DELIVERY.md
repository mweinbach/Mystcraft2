# Delivery forms

The Java behavior follows the MIT-licensed upstream `ItemGrenade`, `EntityGrenade`,
`EntityBombCart`, `ActionCluster`, `CargoProjectileData`, `EntityParachute`,
`EntityBalloon`, and `EntityHeldItemMissile` classes. Models are original geometry
using vanilla texture references.

## Carriers

Craft a parachute or balloon with one item to store that item and its NBT. A loaded
carrier cannot overwrite its existing cargo. Craft the loaded carrier alone to
recover its item and receive one emptied carrier as the recipe remainder.
Sneak-use cycles ITEM, BLOCK, ENTITY, PROJECTILE. Throwing a loaded carrier deploys
its stored cargo; an empty carrier can also take one item from the other hand.
Use an empty carrier on a permitted entity to attach it directly.

ITEM creates a real item passenger; BLOCK creates a persistent flying-block
passenger with block-entity data; ENTITY supports spawn eggs; PROJECTILE supports
the port's bomblets and grenades. Unsupported transformations fall back to an item
passenger. Passengers dismount when the carrier reaches terrain or expires. The
parachute drops its empty carrier; balloons pop. The entity tree saves the actual
passenger, and the carrier clears its stored stack once deployment succeeds.

## Held-item missiles

Craft an empty held-item missile with one permitted item. Craft the loaded missile
alone to recover the item and one empty missile. Sneak-use changes PRIMARY,
SECONDARY, PRIMARY_FIRST, or SECONDARY_FIRST.

Player-owned impacts support ordinary vanilla item attacks, item-on-block use,
and entity interactions using the owner's real permission context. The owner's
main hand is restored in a `finally` block. Tools mine with their proper loot and
durability; the default source block damage multiplier is four. Successful use
updates or consumes the stored item before the casing is recovered. Generic
interactions can be disabled by configuration. Ownerless automation can deliver
and recover the item but cannot impersonate a player for protected block use.

Targeted held missiles use the same staged flight and persistent virtual-flight
ledger as explosive missiles. Direct flight has 200 ticks of fuel, followed by
unpowered falling. Their tier-two health is 80. EMP, defusing, or interception
recovers cargo without applying the payload action.

## Grenades and launchers

Grenades charge throw strength for up to 60 ticks, with speed up to 1.8, drag
0.98 (0.8 in water), and gravity 0.03. Upstream fuse time starts after throwing;
holding the item does not cook its fuse. Payload, remaining fuse, render stack,
and owner survive entity saves. Only upstream tier-one payloads have grenade and
bomblet items.

The rocket launcher consumes one inventory missile per shot. The ballistic
launcher requires a visible block target at least 20 blocks away, with a
500-block targeting ray. Both support paid mixed cluster manifests and held-item
missiles. Cluster loading appends one bomblet per occupied crafting slot and caps
the manifest at 64; empty cluster casings cannot fire. Handheld cluster rockets
fly as `ActionableMissileEntity`: they release within 3 blocks of the aim point
(20 blocks above a ballistic target) and are duds on impact, as upstream.
Sneaking while firing rides the rocket; a lead in the off hand puts one leashed
mob within 7 blocks on it instead. Rockets never hit their own rider.

## Flight state machine

Targeted missiles (`MissileEntity`, `HeldItemMissileEntity` and the cargo/cluster
missiles built on them) go through `StagedFlight` stages: WARMUP on the pad, CLIMB,
LOCK_HEIGHT, then ASCENT (long range, handed to virtual flight at the exit height)
or ARC (short range or a player rider, flown physically to the target).
`FlightLedger` holds captured missiles in virtual flight, restores them over the
target as DESCENT, and COMPLETE detonates. Every mid-flight event has one decision:
**re-plan**, **reject** or **keep going**.

| Stage | Passenger boards | Passenger dismounts / dies | Lead attaches | EMP | Target changes | Chunk unload / save | Blast hit / collision |
|---|---|---|---|---|---|---|---|
| Pre-launch (item, pad, WARMUP) | Allowed. Sneak-fire or an off-hand lead boards the shooter or a mob | Keep going | A boarding mob drops its lead (vanilla) | Temporary: guidance cleared. Engine kill: falls | **Allowed** (`configureTarget`) | Saved with the entity, resumes | Damage until health 0, then destroyed; impact detonates |
| CLIMB | Keep going | Keep going | Rider leads snap past 10 blocks (vanilla) | Same as above | **Reject** | Saved, resumes | Same |
| LOCK_HEIGHT | Keep going; ASCENT vs ARC is decided at the lock height with whoever is riding | Keep going | Same | Same | **Reject** | Saved, resumes | Same |
| ASCENT | Player (or a rider that can't be saved): **re-plan** to ARC from where it is. Capturable mob: keep going and is captured with it | Keep going; captured at the exit height | Same | Same; never captured | **Reject** | Saved, resumes; next step into an unloaded chunk is captured | Same |
| ARC | Keep going (ARC is valid with any rider) | Keep going; once capturable above the exit height it is captured | Same | Same | **Reject** | Saved, resumes; capturable arcs are captured at unloaded chunks, others discarded | Same |
| Capture boundary | Capture checks the riders on that tick (`FlightLedger.capturable`); players and unsaveable riders keep physical flight | n/a | Lead saved with the mob, snaps on restore (holder out of range) | Untargeted and EMP-disabled missiles are never captured | n/a | n/a | n/a |
| Virtual flight | n/a: no live entity | n/a | n/a | n/a | n/a | Ledger NBT in the world save | n/a |
| Restore | Riders come back with the missile | n/a | Same | n/a | n/a | Same tick as reentry | n/a |
| DESCENT | Keep going; never re-captured | Keep going | Same | Same | **Reject** | Saved, resumes | Same |
| Impact (COMPLETE / hit) | n/a (same tick) | Riders dismount when the missile is removed | n/a | Disabled missiles are discarded without detonating | n/a | n/a | Detonates (EMP-disabled: discarded) |

How the code enforces it:

- **Riders.** `FlightLedger.capturable` is the only rider rule. A rider is
  capturable unless it is a player at any depth or an entity type that can't be
  saved. `StagedFlight.nextVelocity` checks it every guided tick, so any way of
  boarding (interaction, seat, nested rider, reload) is covered. An ASCENT that
  can't be captured re-plans to the physical arc from its current position
  (`planArc`), and LOCK_HEIGHT picks ASCENT or ARC with the same rule. The tick
  runs this before the capture check. That ordering keeps missiles out of an
  ASCENT that can never be captured: one that would climb until its 12,000-tick
  lifetime ran out.
- **Targets.** `StagedFlight.configure` rejects any stage after WARMUP
  (`inFlight()`), and `MissileEntity.configureTarget` rejects an untargeted
  missile that has already flown. Retargeting mid-air is not an upstream action.
- **EMP.** `killEngine` (EMP blast) makes the flight DIRECT, ends guidance and
  turns gravity on. `tryCapture` refuses untargeted flights. Temporary
  `disableByEmp` clears guidance while it lasts. A disabled explosive missile is
  discarded on collision; a held-item missile recovers its cargo.
- **Save/load.** Every stage, its tick counter and the target persist in
  `StagedFlight` NBT. Virtual flights persist in the `FlightLedger` saved state.

`FlightStateMachineGameTests.flightStateMachineTable` drives the risky cells as
one table: ASCENT with a player boarding (explosive and held-item), a mob boarding,
a rider dying, an ARC above the exit height losing its player, DESCENT with a player
boarding, an engine kill in ASCENT, a retarget on the pad and in CLIMB, blast damage
in ARC, and a leashed mob boarding. Every tick it also round-trips every live missile
through NBT and checks that WARMUP, CLIMB, LOCK_HEIGHT, ASCENT, ARC and DESCENT
were all saved.

## Configuration

`config/icbm-port-delivery.properties` is created at first launch. Restart after
editing. `cargo.items`, `cargo.entities`, and `held.items` each have `.ban` and
`.list` settings. Lists contain comma-separated exact registry IDs. With
`ban=true`, listed entries are denied; with `ban=false`, only listed entries are
accepted. Entity defaults exclude players, the ender dragon, and the wither.
Other settings are `held.genericInteractions`, `held.blockDamageMultiplier`, and
`balloon.liftTicks` (default 600). Numeric settings are finite and bounded.

Third-party Forge capability adapters and special Dynamic Trees reflection hooks
are not reproduced on Fabric. Generic third-party item interactions depend on
that item's Fabric implementation honoring normal player/item APIs.
