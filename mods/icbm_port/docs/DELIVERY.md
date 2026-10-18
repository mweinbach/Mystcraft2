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
the manifest at 64; empty cluster casings cannot fire.

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
