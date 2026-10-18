# Fabric extension and command contracts

The port exposes a working Fabric-specific API in `dev.icbm.port.api`; it does not
preserve upstream Forge binary compatibility. Upstream references are
`api/reg/`, `api/actions/`, `api/missiles/projectile/`, `content/missile/logic/source/`
and `command/`. The original MIT code notice is retained.

## Initialization and registries

Call `IcbmApi.initialize()` once after built-in content registrations. It is
idempotent and registers all 27 payload descriptors and two executable projectile
factories: `icbm_port:explosive_missile` and `icbm_port:held_item_missile`.
`IcbmCommands.register()` installs the command callback once during mod startup.
The root initializer owns these calls. Addons may register descriptors/factories
from their common initialization; namespaces must be unique.

`registerAction(new Descriptor(id, tier, content, handler))` installs an executable
handler, not merely a catalog entry. Duplicate IDs throw; snapshots returned by
`actions()` and `projectiles()` cannot mutate registries. Registration observers
receive existing descriptors followed by future registrations. Tier is 0–4;
`content` advertises whether a descriptor has built-in physical delivery forms,
not a request to automatically manufacture item/block registrations.

`IcbmApi.execute(ActionContext)` requires the server thread, a registered action,
finite coordinates/size, loaded origin, border/build bounds, and size 0–80.
Size is an **absolute area/radius override**, matching upstream command
`AREA_SIZE`; it is not a damage multiplier. Individual handlers may impose
additional validation and report refusal with `false`. Zero supports nonradial
projectile effects; commands require a positive size. Built-in effect algorithms
and work budgets remain documented in the payload porting notes.

## Cancellation, completion and attribution

Register `BEFORE_ACTION` listeners returning false to cancel before side effects.
Listeners run in registration order; the first denial stops later before-listeners.
`AFTER_ACTION(context, accepted)` records scheduling acceptance/refusal, not the
completion of an asynchronous blast. Built-in payload entrypoints also dispatch
these events, so direct placed/throwable/missile detonations are observable.
Calling a built-in through `execute` does not dispatch twice. Runtime exceptions
from addon handlers/listeners propagate to the caller; the API does not pretend
that a failed callback completed successfully.

`ActionSource` stores direct entity UUID, owner UUID, cause ID and optional immutable
block origin. `of(entity)` recognizes projectile and primed-explosive ownership;
`command(entity, origin)` records command cause. `toNbt/fromNbt` retain identifiers
after unload. `resolve(world)` prefers a loaded owner, then direct entity; null is
valid when neither is loaded. A UUID is attribution, not a permission grant.
Command attribution is preserved through the built-in bridge using a scoped
thread-local context, restored even when a handler throws. Queued payload work
persists source data as implemented in `Payloads`; no chunk is loaded to resolve it.
The port does not yet reproduce the upstream complete conditional/potential/status
and nested action serialization framework.

## Projectile factories

`registerProjectile(id, factory)` installs a handler creating an **unspawned**
entity. `launch(id, ProjectileContext)` validates the server thread, finite vectors,
maximum speed 16 blocks/tick and loaded/bounded origin, applies position/velocity
and resolved projectile owner, and performs exactly one spawn. Payload stacks are
copied on construction/access. Factories must return a new entity in the supplied
world, or null to refuse; never spawn inside the factory. Built-in factories retain
explosive payload, mixed cluster manifest or held-item cargo. The ordinary launcher
retains its targeted-flight setup and may use direct construction; action events
still apply at detonation.

`BEFORE_PROJECTILE` may cancel factory execution; `AFTER_PROJECTILE` reports the
entity and spawn result (entity is null for event cancellation). Unknown IDs,
invalid origins and invalid factory results return false without spawning.
These hooks cover the API factory path, not every vanilla entity spawn. They are
not a global projectile security barrier.

## Commands

All `/icbm` commands require permission level **2**, matching upstream. Suggestions,
help and Brigadier bounds are supplied. Modern dimension IDs replace old integer
Forge dimension IDs, and normal relative coordinates are accepted.

- `/icbm blast list` lists registered built-in and addon descriptors.
- `/icbm blast trigger <id> <size>` runs at the source position; the full form is
  `<id> <dimension> <x> <y> <z> <size>`. Short built-in names and legacy numeric
  explosive IDs resolve to current descriptors; `icbmclassic:` aliases resolve
  to `icbm_port:`. Size is positive and at most 80.
- `/icbm blast spread <count> <distance> <id> <dimension> <x> <y> <z> <size>` uses
  the upstream square grid: `(2 * count + 1)^2` centers. Count is bounded to 1–4
  (up to 81 effects), spacing to 1–128. Every center is checked before the first
  effect; unloaded or out-of-bounds centers reject the entire request.
- `/icbm remove <all|missiles|explosions> [radius]` selects only ICBM entities in
  the source world; a full `<type> <dimension> <x> <y> <z> <radius>` form exists.
  Singular/upstream `ex` aliases are accepted. `missiles` includes explosive,
  held-item and interceptor missiles; `explosions` selects primed explosives.
  Removal deliberately discards, without triggering explosives or refunding ammo.
- `/icbm lag [radius]` performs the upstream emergency cleanup behavior: removes
  ICBM entities and queued/persistent payload work and virtual-flight records near the source (matching saved source or target), releasing associated temporary tickets. It is not a
  performance profiler. `/icbm help` lists syntax and limits.

Removal radius is 1–1,024, default 1,000, and queries loaded entities only. This
intentionally replaces upstream unbounded default remove with a finite bound.
The port fixes upstream `lag` radius argument indexing and finite-size parsing.
Commands do not create chunk tickets. Protection mods should cancel actions through
`BEFORE_ACTION`; operator cleanup intentionally bypasses payload recovery.

## Optional compatibility availability

Read-only Modrinth version queries on 2026-10-18 confirmed Fabric **1.19.2** releases:
JEI `11.68.0.1087`, REI `9.2.784+fabric`, EMI `1.1.24+1.19.2+fabric`. Thus recipe
viewers are available; the optional `IcbmJeiPlugin` now registers actual dynamic crafting displays for mixed
clusters and every allowed registry-item held/cargo loading and unloading case,
including container remainders and normalized NBT subtype identities. Standard
JSON recipes use JEI's vanilla discovery. The plugin compiles against pinned
Fabric JEI 11.8.1.1034 and is loaded only through `jei_mod_plugin`; JEI remains
optional and is not bundled. Use the Gradle `withJei` property for runtime client
checks. Server tests compare representative display outputs and remainders
against real special serializers. A live client displayed normal and dynamic held-item loading recipes; see VALIDATION.md for the tested scope. REI/EMI-specific adapters are not implemented.

The same query found no official Mekanism Fabric 1.19.2 release. The upstream
Mekanism EMP-NBT/teleporter adapter is Forge-specific and remains unsupported;
Fabric energy/material alternatives do not establish Mekanism compatibility.
Queries used `https://api.modrinth.com/v2/project/{jei,rei,emi,mekanism}/version`
with `game_versions=["1.19.2"]&loaders=["fabric"]` filters. Availability alone says
nothing about inter-mod behavior until those combinations are installed and tested.
