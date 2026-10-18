# ICBM Classic parity audit

Target: Minecraft 1.19.2, Fabric, Java 17. Reference: BuiltBrokenModding/ICBM-Classic `prod/1.12` commit `9c1d985a375adc2868ff47707b3a636452d0b63a`. Audit date: 2026-10-18.

This is a source and behavior migration audit of the **current working tree**, not a declaration of complete recreation. **Missing** means no implementation found; **approximation** means usable but materially different behavior; **partial** means a subset of upstream behavior. **Verified** requires evidence of a match to the specific acceptance criteria. No subsystem below has a full-parity verification claim.

The expanded port has dedicated-server gameplay coverage; the integrating agent is running the final frozen-tree test suite. Final counts and results belong in the release validation record. These results do not verify untested upstream mechanics or interactive client presentation. The inventory [upstream-inventory.json](upstream-inventory.json) records 660 production Java paths, 467 resource paths, 130 recipe/factory paths, 27 explosive definitions and field names in 42 config files. Its original implementation-status metadata is historical; the tables here supersede it.

## Current implementation review

The following source review records the expanded implementation on 2026-10-18. These additions remain partial or approximate relative to the acceptance criteria below. Final build/test results must be recorded separately.

| Added port source | Reviewed functionality | Parity qualification / unresolved work |
| --- | --- | --- |
| `tools/upstream_recipe_mapping.json`, `docs/RECIPES.md`, generated recipe resources | 122 explicitly source-mapped entries; 137 fixed recipe outputs and three special recipe serializers, including mapped metadata, vanilla material substitutes and fallback dependency recipes. Static resource validation reports 553 JSON files/856 references; 137 registered items, 44 blocks and eight entities. | Partial migration. Source pattern/count mapping is documented; graph validation assumes sulfur runtime loot and cannot prove live registry matching, arbitrary payload crafting/decrafting, optional mod compatibility or survival balance. Nine extra bomblet recipes and new launcher/SAM/GPS recipes are port extensions. |
| `payload/PayloadType.java`, `payload/Payloads.java`, `payload/PORTING-NOTES.md` | Catalog covers all 27 definitions; 23 executable payloads; typed fuse defaults and distinct effect dispatch. | Approximation. Vanilla explosion/projectiles; persisted spherical gas without toxicity capability or carried contagion; radius/resistance destruction instead of threaded upstream propagation; stationary timed redmatter rather than complete entity; antigravity lacks captured flying-block behavior; ender is random local relocation without GPS/cross-world destinations; rot/color/hot/cold cover only selected vanilla behavior. See porting notes for explicit work/queue/query bounds and lost source attribution. |
| `delivery/DeliveryContent.java`, `GrenadeEntity.java`, `GrenadeItem.java`, `BombCartEntity.java`, `BombCartItem.java`, `RocketLauncherItem.java`, `DeliveryClient.java` | Registration loops select ten tier-one grenades and 23 tiered bomb carts; custom throwable/cart entities and handheld launcher. | Partial delivery implementation. Underlying payloads retain their approximations. Cooking, complete rail/trigger behavior, payload persistence and client rendering need comparison against upstream. Cluster, item cargo, held-item action missiles and ballistic handheld mode are now implemented as partial adaptations (see next rows). |
| `launcher/LauncherBlockEntity.java`, `LauncherContent.java`, `LauncherScreenHandler.java`, `LauncherScreen.java`, `TargetingItem.java`, `RadioItem.java`, `core/GameFlightPath.java` | Shared launcher inventory/target configuration, screen, target tools, local radio item and adapted flight path sources are present. Packet handler checks exact length, current screen/sync ID, can-use and spectator/action constraints. | Partial. Three tiered bases, frame/support/screen/connector and cruise blocks now exist. This does not establish exact upstream component topology, shared energy/inventory networks, energy API, simulated unloaded flight or complete radio parity. Validate integration, launch authorization, persistence, target/frequency bounds and two-client behavior. |
| `delivery/BombletEntity.java`, cluster paths in `DeliveryContent.java` and `RocketLauncherItem.java` | Tier-one bomblet registrations; cluster missiles carry mixed ordered manifests and eject up to 64 droplets in layered discs. Rocket launcher preserves cluster identity. | Approximation. Mixed ordered tier-one manifests now persist up to 64 droplets through item, launcher, handheld launch and entity NBT, with a custom loading recipe. This remains a bounded subset of upstream arbitrary nested projectile/action payloads. Impact physics and client tracking need interactive tests. |
| `delivery/HeldItemMissileItem.java`, `HeldItemMissileEntity.java`, `HeldItemLoadingRecipe.java`, `HeldItemUnloadingRecipe.java`, ballistic branch in `RocketLauncherItem.java` | Persisted single-stack cargo and primary/secondary action modes; tool/sword strikes, tool mining, block-item placement, casing loading/decrafting; direct and targeted ballistic handheld launch. EMP, radar, SAM and defuser recognize held-item missiles and recover cargo safely. | Partial subset. Arbitrary mod item action callbacks, full upstream allow/ban configuration, complete capability/action framework and exact damage/mining scaling are not equivalent. Ballistic handheld raycasts 500 blocks with a 20-block minimum and uses the bounded arc, not upstream staged simulation. |
| `delivery/CargoCarrierItem.java`, `CargoEntity.java` | Parachute/balloon take the opposite-hand item stack, persist cargo/lift age, use drag/lift and release item cargo. | Approximation. This is item transport; no passenger/flying-block attachment, upstream cargo capability, allow/ban lists or crafting/decrafting data transfer. Creative duplication follows creative item-consumption behavior. |
| `launcher/LauncherComponentBlock.java`, `LauncherNetwork.java` | Loaded-component adjacency traversal within 32 blocks/128 nodes; remote screen access; support charges a selected launcher; frame-count gate; screen redstone signals connected bases. | Approximation. Discovery/control does not pool launcher inventories or FE energy, reproduce upstream network topology/tier rules, or simulate unloaded networks. Screen callbacks have no permanent owner authorization beyond current distance/access rules. |
| `defense/DefenseContent.java`, `DefenseMachineBlock.java` | Radar scans loaded missiles within 128 blocks and emits signal 0–15 every 20 ticks; redstone EMP scans radius 32 and disables electronics/missiles for 200 ticks; SAM loads a 0–15 firework magazine and launches a guided interceptor within radius 64 every 20 ticks, consuming a charge. Interceptor follows predicted target motion at 3 blocks/tick, raycasts obstructions, expires at 100 ticks and persists target UUID/lifetime. EMP zones now persist per dimension. | Approximation. No energy, radio screen, EMP multiblock or owner/hostility filters. Guided SAM exists; bounded threat selection/interception and vanilla firework rendering differ from upstream missile mechanics. |
| `defense/RadarGunItem.java`, `AntidoteItem.java`, defuser in `DefenseContent.java` | Radar gun traces 200 blocks and stores shared `IcbmTarget`; antidote clears effects after 32 ticks; defuser interaction implemented. | Partial/approximation. Defuser spends durability (128) in place of energy; verify relevant target and ownership semantics. Antidote source behavior is close but client/use/save behavior still requires tests. |
| `defense/SpikeBlock.java`, utility registrations in `DefenseContent.java` | Concrete/compact/reinforced variants now use hardness 10 and resistance 28/280/2800; normal/poison/fire spikes use damage 1 with poison 140 ticks/fire 7 seconds. Glass controls use vanilla stone button/plate mechanics. | Approximation. Concrete/spike variants are implemented; detailed resistance/drops/configuration and upstream glass activation behavior still need parity verification. Radioactive blocks and protective equipment remain absent. |

## Explosive registry and effects

Authoritative source: `src/main/java/icbm/classic/content/reg/ExplosiveInit.java`, with descriptors in `api/refs/ICBMExplosives.java` and content registries in `lib/explosive/reg/` (all Java prefixes below are `src/main/java/icbm/classic/`). There are **23 tiered payloads**, each enabled for placed explosive, missile and bomb-cart forms; only the **10 tier-one payloads** enable grenade forms. The registry has four additional non-tiered definitions. Do not invent grenade parity for tier-two or higher effects or interpret removed compatibility placeholders as active content.

| Payload (legacy number; tier) | Upstream implementation under `content/` | Current port status | Required behavior to establish parity |
| --- | --- | --- | --- |
| Condensed (0; 1) | `blast/BlastTNT.java` | approximation: `entity/PrimedExplosiveEntity.java`, `entity/MissileEntity.java` use vanilla explosion power 6 | Energy propagation, configurable entity damage, block/item handling and blast shape; all enabled delivery forms; default 100-tick placed fuse and distinct configured form fuses. |
| Shrapnel (1; 1) | `blast/BlastShrapnel.java`, `entity/EntityFragments.java` | approximation: `payload/Payloads.java` | Configured number of critical burning fragments, damage, collision, owner attribution, client tracking. |
| Incendiary (2; 1) | `blast/BlastFire.java` | approximation: `payload/Payloads.java` | Configured affected volume, ignition selection, fuse lava particles and form-specific fuse. |
| Debilitation (3; 1) | `blast/gas/BlastDebilitation.java` | approximation: `payload/Payloads.java` | Persistent area and configured duration/effects, protective armor thresholds, reentry and cleanup. |
| Chemical (4; 1) | `blast/gas/BlastChemical.java` | approximation: `payload/Payloads.java` | Toxicity accumulation, scale, minimum damage, protection, duration and multiplayer gas visibility. |
| Anvil (5; 1) | `blast/BlastShrapnel.java`, `entity/EntityFragments.java` | approximation: `payload/Payloads.java` | Anvil projectile mode, fragment count, collision damage and visuals. |
| Repulsive (6; 1) | `blast/BlastTNT.java` (`PushType.REPEL`) | approximation: `payload/Payloads.java` | Radial repel, configured scale, item destruction and block/entity treatment. |
| Attractive (7; 1) | `blast/BlastTNT.java` (`PushType.ATTRACT`) | approximation: `payload/Payloads.java` | Radial attraction, configured scale and item destruction. |
| Fragmentation (8; 2) | `blast/BlastShrapnel.java` | approximation: `payload/Payloads.java` | Explosive fragments with independently configured damage, count and secondary explosion size. |
| Contagious (9; 2) | `blast/gas/BlastContagious.java` | approximation: `payload/Payloads.java` | Viral transfer, toxicity, duration, armor protection and persistent area. |
| Sonic (10; 2) | `blast/BlastSonic.java` | approximation: `payload/Payloads.java` | Configured blast radius, affected blocks/entities and sonic-specific mechanics. |
| Breaching (11; 2) | `blast/BlastBreach.java` | approximation: `payload/Payloads.java` | Directional width/depth, energy loss with distance/resistance, configured damage and orientation. |
| Thermobaric (13; 2) | `blast/threaded/BlastThermobaric.java` | approximation: `payload/Payloads.java` | Large energy/radius blast propagation, staged work and cancellation; no unsafe off-thread world edits. |
| Nuclear (15; 3) | `blast/threaded/BlastNuclear.java` | approximation: `payload/Payloads.java` | Configured energy/radius, irradiation, post-blast effects, visual synchronization and bounded processing. |
| EMP (16; 3) | `actions/emp/ActionDataEmpArea.java`, `actions/emp/EmpHandler.java` | approximation: `payload/Payloads.java` | Area EMP affects compatible devices, entities, inventories and missiles through documented capability equivalents. |
| Exothermic (17; 3) | `blast/BlastExothermic.java` | approximation: `payload/Payloads.java` | Heat/block conversion and configured volume; fuse lava particles. |
| Endothermic (18; 3) | `blast/BlastEndothermic.java` | approximation: `payload/Payloads.java` | Cold/block conversion and configured volume. Upstream ice fuse animation is itself a TODO. |
| Antigravitational (19; 3) | `blast/BlastAntiGravitational.java`, `entity/flyingblock/` | approximation: `payload/Payloads.java` | Capture actual block states/data, lift and movement, collision and restoration/drop behavior. |
| Ender (20; 3) | `blast/ender/BlastEnder.java`, `EnderBlastCustomization.java` | approximation: `payload/Payloads.java` | GPS customization on placed blocks and missiles, saved target dimension/coordinates and teleport behavior. |
| Antimatter (22; 4) | `blast/threaded/BlastAntimatter.java` | approximation: `payload/Payloads.java` | Configured size, block/entity destruction and scalable queued processing. |
| Red matter (23; 4) | `blast/redmatter/` | approximation: `payload/Payloads.java` | Persistent moving singularity, pull/capture, block collection, damage, lifetime/collisions and saved state. |
| Colors (-1; 1) | `blast/gas/BlastColor.java` | approximation: `payload/Payloads.java` | Configured colorful gas area and duration; original replacement visuals. |
| Smoke (-1; 1) | `actions/entity/ActionSpawnEntity.java`, `entity/EntitySmoke.java` | approximation: `payload/Payloads.java` | Persistent smoke entity and lifetime, area obscuration and synchronization. |
| Hypersonic (21; NONE) | `ExplosiveInit.java`: zero-size `BlastTNT` | compatibility placeholder | Removed upstream in 4.3.0; not a missing live blast. Any port alias must remain explicit. |
| Missile module (24; NONE) | `ExplosiveInit.java`, missile recipe/content code | partial: crafting component | Non-explosive missile component; do not enable placed explosive or grenade by default. |
| Rot (25; NONE) | `blast/BlastRadioactiveBlockSwaps.java` | approximation: queued effect | Radioactive block substitutions; upstream has no item content. |
| Mutation (26; NONE) | `blast/BlastMutation.java` | approximation: queued effect | Entity/block mutation action; upstream has no item content. |

IDs 12 (regeneration) and 14 (S-mine) are comments, not registrations at this baseline. Tiered payload forms require custom data to survive crafting, loading/unloading, placement, entity spawning, save/reload and drops; four separate item names do not prove that contract. Form fuses come from `config/blast/ConfigFuseTimes.java`; unknown types fall back to 100 upstream. Test chain priming, redstone, flint/steel, fire charges, burning projectiles, defusing, explosion ignition, owners and event cancellation for every applicable form.

## Blocks and machines

Authoritative registration: `content/reg/BlockReg.java` (14 registrations, including subtype families); block item wiring: `content/reg/ItemReg.java`.

| Upstream block / source under `content/` | Current status | Acceptance criteria |
| --- | --- | --- |
| Glass pressure plate / `blocks/BlockGlassPressurePlate.java` | partial/approximation: `defense/` variants | Collision/activation filter, redstone strengths, placement and support rules, drops and state transitions. |
| Glass button / `blocks/BlockGlassButton.java` | partial/approximation: `defense/` variants | Triggering entities/projectiles, pulse duration, mounting, support, sound and blockstate. |
| Spikes / `blocks/BlockSpikes.java` | partial/approximation: `defense/` variants | All `EnumSpikes` subtypes, non-solid collision, per-type damage/poison and configured cooldown/rules. |
| Concrete / `blocks/BlockConcrete.java` | partial/approximation: `defense/` variants | All concrete variants, resistance/hardness, recipes and subtype-preserving drops. |
| Reinforced glass / `blocks/BlockReinforcedGlass.java` | partial/approximation: `defense/` variants | Strength/resistance, transparent rendering and drops. |
| Explosives / `blocks/explosive/` | approximation: all 23 content variants | Every enabled variant, block entity payload/customization, fuse behaviors and activation interactions above. |
| Launcher base / `blocks/launcher/base/` | partial/approximation: `launcher/` and `block/LauncherBlock.java` | Upstream tier properties, missile inventory/automation, target validation, energy, linked components, cooldown and screen. |
| Launcher screen / `blocks/launcher/screen/` | partial/approximation: `launcher/` and `block/LauncherBlock.java` | Coordinate/world target editing, bounds, server validation, radio, energy/inventory views and connection rules. |
| Launcher frame / `blocks/launcher/frame/` | partial/approximation: `launcher/` and `block/LauncherBlock.java` | Tier support and launch constraints, joining/leaving network, block persistence. |
| Launcher connector / `blocks/launcher/connector/` | partial/approximation: `launcher/` and `block/LauncherBlock.java` | Network discovery, splitting/merging, energy and inventory sharing; no duplication on reload/break. |
| Cruise launcher / `blocks/launcher/cruise/` | partial/approximation: `launcher/` and `block/LauncherBlock.java` | Oriented direct target launch, valid ammo, cooldown, energy, redstone, inventory and GUI. |
| Radar station / `blocks/radarstation/` | approximation: `defense/` | Detection/filter/range/energy, tracked missiles, warning states, target selection, radio and GUI blips. |
| EMP tower / `blocks/emptower/` | approximation: `defense/` | Real/fake multiblock cells, booster behavior, range/cost, activation, teardown and GUI. |
| Radioactive blocks / `content/radioactive/BlockRadioactive.java` | missing | All variants, exposure, decay/transformation and protection; preserve correct drops. |

Current machine tiers are custom adaptations: base ranges 1,000/2,500/4,000, capacities 10,000/25,000/50,000, launch costs 1,000/2,000/3,000, adjacent frame counts 1/2/3. Shared adjacency discovery supports remote screens and control, not a shared FE/inventory pool. Current item cargo, cluster and utility implementations are described in the implementation review above.

Block entities registered upstream: explosive; EMP tower and fake tower; radar; launcher frame, connector, base and screen; generic multiblock; cruise launcher. Migrating them requires matching Fabric block entity registration, server ticking, saved NBT, client update data, removal handling and chunk unload/reload. The current launcher uses a persistent two-slot block entity inventory; its other machine equivalents remain smaller adaptations of upstream state.

## Item registrations and delivery systems

`content/reg/ItemReg.java` is authoritative. Block items are covered above. Names below are semantic names; legacy camel-case IDs need explicit new-ID mappings rather than accidental capitalization changes.

| Item family / upstream source | Current status | Acceptance criteria |
| --- | --- | --- |
| Explosive missiles / `content/items/ItemMissile.java`, `content/missile/entity/explosive/` | approximation: all 23 payloads, direct/arc paths and mixed clusters | All 23 payloads, tier health/damage, launch source, flight stages, fuel, collision, customization, ownership and persistence. |
| Grenades / `content/items/ItemGrenade.java`, `content/entity/EntityGrenade.java` | partial: `delivery/`, all enabled payload forms | Ten enabled tier-one payloads, throw physics, cooking/fuse, stack consumption, collision and sync. |
| Bomb carts / `content/items/ItemBombCart.java`, `content/entity/EntityBombCart.java` | partial: `delivery/`, all enabled payload forms | 23 payloads; rail placement/motion, activator/redstone/damage triggering, fuse, interaction and saved payload. |
| Rocket launcher and ballistic launcher / `content/items/ItemRocketLauncher.java` | partial: direct rocket and targeted ballistic handheld modes | Both modes, ammo selection, reload/cooldown/consumption, launch vector versus ballistic target, inventory and multiplayer. |
| Surface-to-air missile / `content/missile/entity/anti/` | approximation: guided `defense/SamInterceptorEntity.java` | Radar targeting, target data, homing, attack damage/range/fuel and invalid-target handling. |
| Held-item missile / `content/missile/entity/itemstack/` | partial: persisted cargo, modes, tool impact/mining, block placement, load/decraft | Payload storage, allow/ban lists, held action modes, interaction/impact, crafting/decrafting without loss or duplication. |
| Cluster missile / `content/cluster/missile/` | partial: mixed manifest, loading recipe and bounded burst | Nested payload construction, release targeting, count/limits, persistence and recipes. |
| Empty and explosive bomblets / `content/cluster/bomblet/` | partial: casing, ten tier-one types and entity | Payload, release physics, impact, configured limits and crafting. |
| Parachute and balloon / `content/cargo/` | approximation: persistent item cargo only | Throwable transport, nested cargo storage, physics/lift/descent, payload release, ban lists and crafting/decrafting. |
| Defuser / `content/items/ItemDefuser.java` | partial/approximation: targeting/defense items | Disarm interactions, valid target/fuse semantics, durability and server ownership. |
| Radar gun / `content/items/ItemRadarGun.java` | partial/approximation: targeting/defense items | Acquire/display GPS target, range, saved dimension/position, transfer to supported devices. |
| Remote detonator / `content/items/ItemRemoteDetonator.java` | partial/approximation: targeting/defense items | Radio channel state, range/authorization, target messages, linked launch/detonation. |
| Laser detonator / `content/items/ItemLaserDetonator.java` | partial/approximation: targeting/defense items | Raycast targeting, range, beam sync, target setting and activation. |
| Antidote and poison powder / `content/items/ItemAntidote.java`, `ItemReg.java` | partial/approximation: targeting/defense items | Poison/toxicity curing rules, consumption, crafting and configured effects. |
| Sulfur dust, saltpeter dust/ball / `ItemReg.java`, loot handling | partial: materials, smelting and creeper/blaze sulfur loot | Material tags, saltpeter production, sulfur/loot settings and recipes. |
| Battery / `content/items/ItemBattery.java` | partial: `content/Materials.java` and recipes; battery energy API absent | Optional enable setting, energy capacity/transfer, stored NBT, charging/discharging slots and tooltip. |
| Ingot steel/copper; steel clump; plate steel/iron; circuit basic/advanced/elite; wire copper/gold / `content/items/ItemCrafting.java` | partial: local materials and vanilla copper/iron substitutes | All ten subtype outputs, optional category switches, common material tags, recipes and smelting where registered. |

## Entity registrations

`content/reg/EntityReg.java` registers 17 active entities. `api/refs/ICBMEntities.java` provides their IDs. Holiday mobs inside its commented section are not active feature requirements.

| Upstream class under `content/` | Current mapping/status | Acceptance criteria |
| --- | --- | --- |
| `entity/flyingblock/EntityFlyingBlock` | missing | Block capture with data, motion/collision, restore/drop, persistence/render. |
| `entity/EntityFragments` | approximation: vanilla projectiles | Critical/fire/anvil/explosive modes, damage and collision. |
| `entity/EntityExplosive` | partial analogue `PrimedExplosiveEntity` | Payload identity, all fuses, owner/source, serialization and renderer. |
| `missile/entity/explosive/EntityExplosiveMissile` | approximation `MissileEntity` | All payload data, correct flight, health/damage, persistence and tracker. |
| `missile/entity/explosive/EntityMissileActionable` | missing | Serialized action payload and execution. |
| `missile/entity/anti/EntitySurfaceToAirMissile` | approximation: `SamInterceptorEntity` | Homing/interception contract above. |
| `missile/entity/itemstack/EntityHeldItemMissile` | partial: `delivery/HeldItemMissileEntity` | Item payload action contract above. |
| `entity/EntityExplosion` | approximation: persistent fields/jobs, no equivalent entity | Persistent/staged effect lifecycle and synchronized state. |
| `entity/EntityLightBeam` | missing | Beam endpoints, lifetime and visibility. |
| `entity/EntityGrenade` | partial: `delivery/GrenadeEntity` | Fuse/physics/payload/render contract. |
| `entity/EntityBombCart` | partial: `delivery/BombCartEntity` | Cart/rail/payload/render contract. |
| `entity/EntityPlayerSeat` | missing | Missile rider mounting, position, dismount and cleanup. |
| `blast/redmatter/EntityRedmatter` | approximation: persistent fields/jobs, no equivalent entity | Singularity contract above. |
| `entity/EntitySmoke` | approximation: persistent fields/jobs, no equivalent entity | Smoke contract above. |
| `cluster/bomblet/EntityBombDroplet` | partial: `delivery/BombletEntity` | Bomblet contract above. |
| `cargo/parachute/EntityParachute` | approximation: shared `delivery/CargoEntity` | Cargo/descent/render contract. |
| `cargo/balloon/EntityBalloon` | approximation: shared `delivery/CargoEntity` | Cargo/lift/render contract. |

Every entity requires dedicated-server spawning, client tracking, save/reload, chunk transition, owner loss, death/removal and multiplayer rendering checks. Upstream tracking distances/frequencies differ by entity; prototype 128-block missile tracking is not equivalent to upstream 500-block tracking.

## Flight, tracking, actions and shared infrastructure

| System / upstream Java path | Current status | Acceptance criteria |
| --- | --- | --- |
| Direct flight / `content/missile/logic/flight/DirectFlightLogic.java` | adapted math in `core/DirectFlight.java`; gameplay approximation | Speed/configuration, normalization, collision and lifetime agree with launch source; unit math tests alone do not establish gameplay parity. |
| Ballistic/arc/warmup/follow/dead flight / `content/missile/logic/flight/` | approximation: `core/GameFlightPath` arc; other stages absent | State transitions and persisted stage data, targeting, range limits, acceleration and fuel. Review old ballistic logic separately from active paths. |
| High-altitude simulation / `content/missile/tracker/` | missing | Simulated travel at configured exit/entry heights, loaded chunk pairing, world saved data, reentry, restart and dimension rules. Current missiles expire at unloaded terrain. |
| Action sources/causes / `content/missile/logic/source/` | missing | Entity/block/redstone attribution survives nested effects and cancellation. |
| Action framework / `api/actions/`, `lib/actions/`, `content/actions/` | missing | Registry lookup, serialization, conditionals/status/potential, listeners, ticking and cancellation for supported actions. |
| Explosive capabilities/customizations / `api/caps/`, `lib/explosive/`, `content/blast/caps/` | missing | Loader-neutral/Fabric equivalent for payload, velocity, GPS, radio and EMP providers; extensibility tests. |
| Projectile registry / `api/missiles/projectile/`, `lib/projectile/` | missing | Payload/trajectory providers, registration and data serialization. |
| Blast work scheduling / `lib/explosive/ThreadWorkBlast.java`, `lib/actions/WorkTickingActionHandler.java`, `config/ConfigThread.java` | approximation: queued `payload/Payloads` jobs/fields | Bounded server work, cancellation, chunk lifecycle and no asynchronous world mutation. Avoid claiming parity for a radius cap without documenting it. |
| Gas/protection/radiation / `content/gas/`, `content/radioactive/`, `lib/` supporting effects | approximation: queued `payload/Payloads` jobs/fields | Persistent exposure, spread, armor thresholds/helmet requirement and configured item lists. |
| Loot/material hooks / `CommonProxy.java`, event subscribers | partial: runtime sulfur/material integration | Sulfur and other configured loot drops, material tags, smelting and lifecycle registration. |

## GUI, networking, radio and energy

Five upstream screens/containers exist under `content/blocks/`: launcher base, launcher screen, cruise launcher, radar station and EMP tower (`*/gui/`). The launcher screen/container is implemented; cruise and tiered launchers share that adapted screen. Dedicated upstream radar/EMP screens and separate launcher-base/control-panel screen behavior remain missing. Acceptance requires matching meaningful controls and displayed state, slots/shift-click, battery slot handling, energy bars, target/channel fields, launch/disable/redstone/rotation actions, range/error feedback and multiplayer live updates. Original screen art/layout must be independently recreated.

Network sources: `lib/network/` (packet events/manager, entity and tile lambda/codex packets, player-item packet, entity position, air particles and block explosion) and machine/entity packet handlers. Current status: partial. Validated Fabric launcher configuration packets, projectile tracking and custom guided-SAM spawn tracking exist; the complete upstream packet/effect system is absent. Fabric replacements must validate sender, dimension, loaded target, distance, current screen, slot access, enum/range bounds, payload length and permissions; execute world edits on the server thread and reject invalid actions. Test two clients, forged coordinates, stale blocks, chunk unload and unauthorized remote updates. Registering packet IDs alone is insufficient.

Radio sources: `api/radio/`, `lib/radio/`, `content/blocks/radarstation/RadioRadar.java`. Current status: approximate paired-launcher radio in `launcher/RadioItem.java`; no full channel registry/message bus. Test dimension/channel registry lifecycle, sender/receiver discovery, channel access, text/target/incoming-missile/trigger messages, machine forwarding and reload cleanup. A radius search for blocks without the message contract is an approximation.

Energy sources: `api/energy/`, `lib/energy/`, `content/blocks/launcher/network/NetworkEnergyStorage.java`, `content/items/ItemBattery.java`. Current status: standalone redstone-fed launcher charge, not an external energy bridge. Upstream has FE and null systems and configurable power requirement. Fabric replacement must document chosen units/API, capacities/input/output, simulate versus commit, side restrictions, network distribution, battery transfer and action costs. No built-in energy API replacement can be called Forge Energy compatibility without a tested bridge.

## Recipes, progression and configuration

All **130** reference recipe/factory paths are enumerated individually in the inventory, including conditional alternatives. Source is `src/main/resources/assets/icbmclassic/recipes/`. The asset clarification explicitly applies MIT to team-created **source code and data files**; recipes may be adapted as data with MIT attribution. This does not authorize importing restricted artist textures, models, sounds or other artwork. The current working tree has 137 fixed recipe JSON files plus three special recipes (mixed-cluster loading, held-item loading and unloading). Counts alone do not establish ingredient, conditional, serializer or survival-progression equivalence.

Required recipe groups: explosives (23 active payloads and conditional nuclear/antimatter/redmatter alternatives), missiles (23 payloads, module, SAM, held-item, cluster and vanilla alternatives), bomb carts (23), grenades (10), two bomblet recipes, four launcher components, EMP tower/booster/radar/cruise machines, three spikes recipes, three concrete recipes, reinforced glass, glass controls, six tools, parachute/balloon, poison/antidote and crafting parts. Recipe path count is not obtainable-recipe count: `_factories.json` and mutually exclusive conditional recipes must be accounted for.

Custom serializers/ingredients/conditions: `lib/recipe/ExRecipeFactory.java` and neighbors; `content/cluster/missile/RecipeCluster.java`; `content/cargo/RecipeCargoData*.java`; `content/missile/entity/itemstack/item/RecipeHeldItemMissile*.java`. Current status: mixed-cluster loading and held-item loading/unloading serializers implemented; generic upstream payload ingredients and cargo-carrier assemble/decraft serializers remain missing. Test payload-aware matching, NBT transfer, assemble/decraft roundtrips, leftovers, counts, nested payload restrictions, tag substitutes, feature switches and dependency conditions. Furnace/material recipes registered in Java must also be reviewed. Recipe discovery and JEI substitutes need client checks.

The inventory lists every parsed public initialized field in all 42 config files. This is a source field inventory, not a schema parser (nested classes can repeat field names). Current payload parameters are validated JSON in `config/icbm_port/payloads.json`: enabled flag, radius, damage, fragments, duration, energy and three form fuse durations. Invalid reloads retain active settings, unknown keys/ranges are rejected, and input is capped at 1 MiB. This is a port schema, not upstream Forge config-file compatibility; machine, protection, optional-content and integration configuration coverage remains incomplete. Required groups:

- `ConfigMain`, `ConfigItems`, `ConfigBattery`, `ConfigThread`, `ConfigClient`, `ConfigDebug`, `ConfigJEI`, `ConfigFlyingBlocks`.
- `config/blast/ConfigBlast`, `ConfigFuseTimes`, `ConfigThermobaric`, and every `config/blast/types/Config*.java`.
- `config/missile/ConfigMissile`, `ConfigSAMMissile`, `ConfigClusterMissile`, `ConfigHeldItemMissile`, `ConfigCargoItem`, `ConfigBomblet`.
- `config/machines/ConfigLauncher`, `ConfigRadar`, `ConfigEmpTower`; `config/blocks/ConfigBlocks`, `ConfigSpikes`, `ConfigRadioactive`; supporting block/item resource-list parsers in `config/util/`.

Configuration acceptance: documented defaults/units/ranges, server authority, persisted values, restart/reload semantics, optional registrations/recipes, client-only separation, list parser validation and save/load tests. Reusing a similar key name while changing scale or meaning is an approximation. Preserve balance relationships across fuses, speeds, fuel, range, blast work limits, radiation, immunity/protection and machine energy.

## Commands, integrations and assets

| Surface / upstream source | Current status | Acceptance criteria |
| --- | --- | --- |
| `/icbm blast list`, `trigger`, `spread` / `command/ICBMCommands.java`, `command/sub/blast/` | missing | Brigadier equivalents with matching selector/position/blast options, permissions, errors, completion and server execution. |
| `/icbm remove`, `lag`, help / `command/sub/`, `command/system/` | missing | Correct entity/effect selection and lag diagnostics, permission checks and meaningful feedback; destructive selection scoped precisely. |
| JEI / `mods/jei/` | missing | Payload, cluster, cargo, held-item and decraft recipes displayed in an available Fabric recipe viewer or documented unavailable compatibility. |
| Mekanism / `mods/mekanism/MekProxy.java`, conditional recipe resources | missing | Match EMP draining via `electricityStored` NBT replacement and projectile travel through teleporter frames, including exit position/direction resynchronization; optional dependency checks and conditional materials recipes. Fabric target availability constrains direct compatibility and must be reported. |
| OreDictionary / `ItemReg.java`, recipe code | partial: local/vanilla ingredients and modern tags; no universal bridge | Modern common tags for steel/copper/plates/circuits/wires/sulfur/saltpeter/string and dependency substitutes. |
| Extension APIs / `api/` and registry events | missing | Document Fabric counterpart contracts and provide extension tests; retaining class names is insufficient. |
| Legacy remapping / `ItemReg.java`, `EntityReg.java` | absent by design | Explicitly document own namespace and lack of automatic Forge world migration; any future conversion requires tests, backup workflow and payload remaps. |
| Models/textures / resource inventory | approximation: vanilla references | Independently authored readable item/block/entity art, all state/variant models, particles, GUI and multiplayer renderers; no missing resources. |
| Sounds/animation/languages / resource inventory, `ClientProxy.java` and render classes | approximation: vanilla sound/particle references and independent English strings | Original replacement audio/events, animation timing, client effects and independently translated coverage. Never copy upstream assets. |

Upstream code has an MIT notice; the upstream **asset license expressly excludes Fabric ports**. Do not import restricted textures, models, sounds or other artist assets from that tree. Its clarification expressly licenses team-created source and data under MIT; recipe data adaptation with attribution is permitted. Language/data provenance must be established separately rather than treating the entire resources directory as either uniformly forbidden or uniformly reusable. New artwork must use permitted original/vanilla assets with provenance and attribution. Pixel-perfect upstream asset reproduction is not an authorized acceptance criterion.

## Integration review qualifications

Central registration, missile payloads, cluster manifests and targeting are integrated in the current tree. Missile save data validates finite start/lift and bounded target/time data. Payloads persist through entities and jobs; guided interceptor UUID/lifetime and EMP zones persist separately. Flight remains a loaded-chunk bounded animation: no high-altitude simulation or chunk tickets. Entity rendering and multiplayer UI still need client testing. The integration review identified armed-cart recovery duplication, tier range bypass and spectator defuser checks. Current source contains fixes for all three: armed/triggered carts detonate without recovery, target validation uses the base range, and the defuser rejects spectators. Final regression-test results belong in the integrating agent's validation record.

## Release gates

1. Keep every implementation row tied to actual target sources and evidence; mark approximations and intentionally unavailable integrations explicitly.
2. Compile common/client sources and remap the production artifact; validate port metadata and ensure no restricted upstream resources are included.
3. Run meaningful unit tests for pure simulation/targeting/serialization and dedicated server GameTests for registration, each payload family, delivery form, machine, persistence, configuration and invalid networking.
4. Playtest client rendering/screens/audio, two-player synchronization, chunk/world restart, long-range simulation, automation, defense interoperability and blast performance under representative settings. Record actual results, not intended tests.
5. Verify survival progression: obtain resources, craft machines and payloads, charge/configure, launch, detect/intercept, detonate/defuse and recover persisted world state. Creative-only item availability is not survival parity.

Until these gates and per-row acceptance criteria are demonstrated, this remains an incomplete Fabric reimplementation of ICBM Classic.
