# Gridwright loader parity

Gridwright is a clean-room mod: there is no upstream code to match, only the public descriptions of
Extra Utilities 2's behaviour listed in [`NOTICE.md`](../NOTICE.md). This file records, feature by
feature, how the shared behaviour reaches each loader, where the loaders differ, and which GameTest
covers it. Gameplay, registries, recipes, assets and EMC data live in `src/main`; `src/forge` and
`src/fabric` add only adapters behind
[`Platform`](../src/main/java/ports/gridwright/platform/Platform.java). Every GameTest is shared
(`src/gametestCommon`) and runs on both loaders; [`gametest-loader-only.txt`](../gametest-loader-only.txt)
is empty.

## Phase 1: Grid Power

| Feature | Forge | Fabric | GameTests |
| --- | --- | --- | --- |
| Grid Power (per player or team) | Shared | Shared | `GridGameTests` |
| GP producers | Shared | Shared | `ProducerGameTests` |
| Generators (FE / TR Energy out) | Forge Energy capability | Team Reborn Energy | `GeneratorGameTests` |
| Resonator | Shared recipe type and menu | Shared recipe type and menu | `ResonatorGameTests` |
| EMC | ProjectE data | Item Alchemy data | `EmcGameTests` |

## Phase 2a: transfer

Shared logic lives in `ports.gridwright.transfer`; each loader supplies only
[`TransferPlatform`](../src/main/java/ports/gridwright/transfer/TransferPlatform.java) (inventory and tank
access, plus the block-break permission check). All 23 `TransferGameTests` run on both loaders.

| Feature | Forge | Fabric | GameTests |
| --- | --- | --- | --- |
| Item transfer/retrieval nodes | `IItemHandler` capability, simulate then commit | `Storage<ItemVariant>` in transactions; each stored kind is tried until one leaves the source | `itemNodeMovesOneItemPerSecond`, `roundRobinAcrossTargets`, `retrievalNodePullsFromNetwork`, `rejectedItemsDoNotStallTheNode`, `lockedSlotsDoNotHideOutput`, `sidedInventoriesOfferEveryConnectedFace` |
| Fluid transfer/retrieval nodes | `IFluidHandler` capability (mB) | `Storage<FluidVariant>`; droplets converted to whole mB | `fluidNodesMoveFluid` |
| Pipes and networks | Shared | Shared | `pipesShowConnections` |
| Speed / stack upgrades (GP) | Shared | Shared | `upgradesNeedOwnerAndPower`, `poweredSpeedUpgradesMoveFaster`, `stackUpgradeMovesStacks` |
| World interaction upgrade | Owner checked with a `FakePlayer` through `BlockEvent.BreakEvent` | A stand-in `ServerPlayer` through `PlayerBlockBreakEvents.BEFORE` | `worldInteractionMinesIntoNetwork`, `worldInteractionRespectsProtection`, `worldInteractionPlacesBlocks`, `worldInteractionPlacesDirectionalBlocks`, `worldInteractionPumpsSources`, `worldInteractionPoursSources`, `worldInteractionStaysInsideBuildHeight` |
| Item filter and menus | Shared | Shared | `filtersAllowAndDeny`, `filterMatchesNbtAndFluids`, `filterMenuEditsHeldFilter`, `nodeMenuSortsShiftClicks`, `nodeStatePersists` |

## Phase 2b: utility items and blocks

| Feature | Public behaviour (see NOTICE) | Forge | Fabric | GameTests |
| --- | --- | --- | --- | --- |
| Angel Ring | Survival flight powered by Grid Power | Server tick end event runs the shared `AngelRingFlight` | `END_SERVER_TICK` runs the same code; flight state is cleared on world unload | `angelRingFliesOnGridPower`, `angelRingRevokedWhenRemoved` |
| Golden Lasso | Carry a passive mob in an item; no hostiles, bosses or players | Shared; `#forge:bosses` joins `#gridwright:lasso_immune` when present | Shared; `#c:bosses` joins it when present | `lassoCatchesAndReleasesPassiveCreatures`, `lassoRefusesHostilesBossesPlayersAndPets` |
| Builder's Wand | Extend a face of matching blocks from the inventory | Each block goes through `ItemStack.useOn`, which fires `EntityPlaceEvent` | No place event exists: `Platform.mayUseBlock` asks `UseBlockCallback` first (any answer but PASS denies), then `useOn` | `buildersWandExtendsAFace`, `buildersWandStopsAtItsLimitAndSupply`, `buildersWandRespectsClaims` |
| Destruction Wand | Break a face of matching blocks | `ServerPlayerGameMode.destroyBlock` fires `BreakEvent` | The same call fires `PlayerBlockBreakEvents.BEFORE` | `destructionWandBreaksAFace`, `destructionWandRespectsClaims` |
| Watering Can | Speed up plants, wet farmland | Shared | Shared | `wateringCanWetsFarmlandAndPutsOutFire`, `wateringCanGrowsPlants` |
| Bag of Holding | Portable inventory; no bags inside bags | `NetworkHooks.openScreen` with the bag's slot | `ExtendedScreenHandlerFactory` with the bag's slot | `bagOfHoldingStoresAndRefusesNesting` |
| Pocket Workbench | Crafting grid anywhere | Shared vanilla crafting menu | Shared vanilla crafting menu | `pocketWorkbenchCrafts` |
| Drums | Single-fluid tanks in four sizes | `IFluidHandler` capability on every side; amounts in mB (1 bucket = 1,000) | Transactional `Storage<FluidVariant>` on every side; amounts in droplets (1 bucket = 81,000) | `drumsHoldOneFluidThroughTheLoaderApi`, `drumsKeepTheirFluidWhenBroken`, `drumsFillFromBuckets` |
| Trash cans | Void items, fluids or energy | Item handler, fluid handler or Forge Energy capability that accepts everything | Insertion-only item / fluid storage and Team Reborn Energy storage that accept everything | `trashCanDestroysHopperInput`, `trashCansDestroyThroughLoaderApis` |
| Compressed blocks | 9:1 tiers of common blocks | Shared recipes; EMC 9^tier | Shared recipes; EMC 9^tier | `compressedBlocksCraftNineToOneAndBack` |
| Spikes | Hurt mobs; better spikes kill as a player | Forge `FakePlayerFactory` player takes the kill credit | `FabricFakePlayer` (a `ServerPlayer` with a packet-dropping connection) takes it | `woodenSpikesNeverKill`, `goldenSpikesDropExperienceIronSpikesDoNot`, `spikesHurtCreaturesStandingOnThem` |
| Sound Muffler | Quieter sounds nearby | `PlayLevelSoundEvent` (position and entity variants), applied on the client only so a broadcast sound is muffled once | No sound event: `ServerLevelMixin` replays both `playSeededSound` overloads at the muffled volume; sounds a client plays only for itself are not muffled | `soundMufflerQuietsNearbySounds` |
| Wards | Stop hostile natural spawns or enderman teleports nearby | `LivingSpawnEvent.CheckSpawn` set to `DENY` for `MobSpawnType.NATURAL`; `EntityTeleportEvent.EnderEntity` cancelled | No spawn or teleport event: `NaturalSpawnerMixin` makes `NaturalSpawner.isValidPositionForMob` return false; `EnderManMixin` makes `EnderMan.teleport(double, double, double)` return false | `spawnWardStopsHostileNaturalSpawns`, `greaterSpawnWardRunsOnGridPower`, `enderWardStopsEndermanTeleports` |

## Phase 2c: quarries and mechanical blocks

| Feature | Public behaviour (see NOTICE) | Forge | Fabric | GameTests |
| --- | --- | --- | --- | --- |
| Quantum quarry + actuators | Mines a generated world, not real terrain; needs actuators and lots of power | Shared; modded ores from `#forge:ores` | Shared; modded ores from `#c:ores` | `quantumQuarryNeedsActuators`, `quantumQuarryStopsWhenOverloaded`, `quantumQuarryMinesWithPowerAndUpgrades`, `quantumQuarryIsDeterministic`, `quarryTablesFollowTheirRegion`, `taggedOresJoinTheirRegion`, `quarryUpgradesChangeLoot`, `quantumQuarryWaitsWhenFull`, `brokenQuarryDropsWaitingLoot` |
| Biome marker | Picks the quantum quarry's biome | Shared | Shared | `biomeMarkerPicksTheTable` |
| Ender quarry | Mines inside a fence loop with upgrades | Forge Energy capability in; each break fires `BreakEvent` as the owner's `FakePlayerFactory` player | Team Reborn Energy in; each break fires `PlayerBlockBreakEvents.BEFORE` (and `AFTER`) as the owner's `FabricFakePlayer` | `enderQuarryFindsItsBoundary`, `enderQuarryMinesInsideOnlyAndRespectsProtection`, `enderQuarryNeedsEnergyOwnerAndLoop`, `enderQuarryReportsBadBoundaries` |
| Mechanical miner | Fake player breaks the block in front | `BreakEvent` as the owner | `PlayerBlockBreakEvents.BEFORE` / `AFTER` as the owner | `minerBreaksWithItsTool`, `minerWithoutToolHarvestsLikeAHand`, `minerRespectsProtection`, `minerNeedsOwnerPowerAndNoSignal` |
| Mechanical user | Fake player right-clicks with its items | Vanilla `useItemOn` fires `RightClickBlock`; use in the air also needs `BreakEvent` to pass for the block in front and every block the item could aim at | Vanilla `useItemOn` fires `UseBlockCallback`; use in the air also needs `PlayerBlockBreakEvents.BEFORE` to pass for the same blocks | `userPlacesBlocks`, `userFillsBuckets`, `userRespectsProtection`, `userStopsWhenTheClickIsRefused`, `userCooldownsRunOut`, `userCannotReachPastIntoProtection` |

### Differences from the public descriptions

| Block | Public behaviour | Gridwright | Deliberate differences |
| --- | --- | --- | --- |
| Quantum quarry | Endgame machine that mines a dimension generated for it, so it yields worldgen blocks and ores (modded ones too) without touching the world; needs actuators on all sides and huge power; a biome marker picks the biome. | Mines a weighted worldgen-style table per region and terrain ([`QuarryTable`](../src/main/java/ports/gridwright/worker/QuarryTable.java)); modded ores from `c:ores` / `forge:ores`; four horizontal actuators; 32 GP + 16 GP per upgrade level; deterministic per world seed, position and index. | No dimension or chunk generation (cheaper, deterministic, nothing to load); Grid Power instead of RF; four actuators, not six; upgrades and the marker go in the quarry's own slots, not in actuators; modded ores enter at one flat weight per depth band rather than their real generation frequency. |
| Biome marker | Records a biome for the quantum quarry. | Records the biome the user stands in; nether/end biomes switch the table's region; badlands, mountain, ocean and desert terrains adjust it. | Only biome *tags* matter, so modded biomes behave like the vanilla category they are tagged as. |
| Ender quarry | Mines everything inside a closed fence (or Ender-Marker) loop down to bedrock, replacing blocks with dirt; chunk-loads its area; adjacent upgrade blocks (silk touch, fortune I–III, speed I–III, world hole, pump) multiply its RF cost. | Fence or wall loop (any shape, one height, ≤1,024 boundary blocks, ≤128 across); mines from its height to the bottom of the world; FE / TR energy at 400 per block plus per-upgrade costs; skips protected blocks, block entities, unbreakables and `#gridwright:unminable`. | Leaves the hole open (no dirt, so no world-hole upgrade); no Ender-Markers; no pump (fluids are left in place); no chunk loading (waits for loaded chunks); upgrades are items in its slots, additive rather than multiplicative costs; skips block entities instead of breaking them. |
| Mechanical miner | Fake-player block that breaks the block in front of it; enchantments and speed upgrades. | Breaks the block in front with the tool in its slot as the owner would: player break time, tool-dependent drops, tool wear; 4 GP. | The tool itself carries enchantments and efficiency (no separate upgrade slots); skips block entities. |
| Mechanical user | Fake-player block that right-clicks with its items (bone meal, buckets, placing blocks, seeds…). | Right-clicks the block in front every 20 ticks with each input stack in turn, then uses the item in the air if the click did nothing; 2 GP. | No entity interaction (shears on sheep, buckets on cows) and no speed upgrades yet. |

The quarries take the transfer nodes' `speed_upgrade` item (phase 2a) rather than a separate quarry
speed upgrade; a quarry counts up to 8 of them, a node up to 64.

Both mechanical blocks and both quarries act only as their owner, through a fake player with the
owner's profile, and fire the loaders' own break and right-click events before acting, so claim and
protection mods can refuse them exactly as they would refuse the player. Unowned machines do nothing.

## Phase 2c: Deep Dark

Entirely shared: the dimension, biome and ore features are datapack JSON in `src/main/resources`, and
travel uses vanilla `ServerPlayer#teleportTo`, which fires Forge's `PlayerChangedDimensionEvent` and
Fabric's `AFTER_PLAYER_CHANGE_WORLD`. GameTests: `DeepDarkGameTests` (both loaders). Forge's GameTest
server loads datapack dimensions, so `arrivalInGeneratedDeepDark` also arrives in real generated
terrain there; Fabric's GameTest server is built from built-in registries and has no Deep Dark, so that
test only checks the missing-dimension path on Fabric, and the datapack itself is decoded and checked
on both loaders by the other tests.

| XU2 public behaviour | Gridwright implementation | Deliberate differences |
| --- | --- | --- |
| A portal block is right-clicked to travel to the Deep Dark. | `gridwright:deep_dark_portal`, a crafted block (no GUI). Right-click on the server schedules the trip for straight after the current tick. 1:1 x/z. | A 3-second per-player cooldown stops double-click ping-pong. Machines and fake players never travel. |
| Using a portal in the Deep Dark returns you to the Overworld. | Returns you to the exact dimension and position you entered from (saved per player in overworld `SavedData`, so it survives restarts on both loaders). | If that spot is now blocked, unsafe or no longer has a floor within a block (water and ladders count), a clear spot nearby is used; if the dimension is gone or there is no record, the overworld surface at your x/z (MOTION_BLOCKING heightmap), else world spawn. Surface fallbacks use the nearest column inside the world border and never put you on or in something harmful (magma, cactus, berry bushes, fire, lava, powder snow). If no safe destination exists at all, the trip is cancelled with a message. |
| A portal appears where you arrive so you can get back. | Arrival searches a 7×7×25 box for two blocks of air over a sturdy floor with no lava or fire within a block; failing that it digs a 3×3×3 room, gives it a floor and seals fluids and falling blocks around it. A glowing (light 10) return portal is placed beside you, or reused if one already touches your standing space. | Arrivals stay inside the world border. Bedrock, unbreakable blocks and block entities are never removed. Return portals the mod places drop nothing when broken, so trips cannot mint portals. |
| An underground, cave-like dimension with no natural light. | Datapack dimension `gridwright:deep_dark` (type `gridwright:deep_dark`): no skylight, a ceiling, ambient light 0, y 0–127 between a bedrock floor (y 0–4) and roof (y 123–127); stone above y≈48, deepslate below; large caverns plus noodle tunnels; a fixed night clock. | Beds and respawn anchors do not work there (no respawning in the Deep Dark); compasses and clocks spin. |
| More ores than normal. | Biome `gridwright:deep_caverns` places vanilla ore features about 2–2.5× vanilla's per-chunk counts (diamonds ≈1.7× and only below y 48). | Other mods' ores are not copied in automatically; packs add them via biome modifiers / `BiomeModifications` targeting `#gridwright:is_deep_dark`. |
| Hostile mobs spawn more often. | Monster-only spawn list with weights at or above the overworld's and larger groups; monsters spawn at block light 0, which is everywhere you have not lit. No passive, ambient or water mobs. | Mobs are vanilla mobs with vanilla stats (no strength boost). |
| Players are hurt by staying in the dark ("the Darkness"). | Not implemented. | Left out on purpose: darkness already means constant spawns; a damage-over-time effect is a later, optional idea. |

## Phase 2d: power extras

| Feature | Public behaviour (see NOTICE) | Forge | Fabric | GameTests |
| --- | --- | --- | --- | --- |
| Power converter | Gridwright's own: lossy GP ↔ energy bridge | `IEnergyStorage` capability (`ForgePowerEvents`): extract-only when making energy, receive-only when making GP | Team Reborn `EnergyStorage` (`FabricPower`) with the same one-way rule | `roundTripLosesThreeQuarters`, `closedLoopRunsDown`, `generatorEnergyBecomesGpAtSixteenToOne`, `convertersAndGeneratorsOnlyMoveEnergyOneWay` |
| Fire, lava, manual, dragon egg mills | GP producers | Shared | Shared | `fireMillNeedsFireBelow`, `lavaMillCountsLavaSources`, `manualMillRunsOnlyWhileWound`, `dragonEggMillNeedsTheEgg` |
| Rainbow generator | GP while every generator kind runs nearby | Shared | Shared | `rainbowNeedsEveryGeneratorRunning`, `idleGeneratorsDoNotCount`, `neighbouringRainbowsBothStop` |
| Lava generator input | Lava piped into the lava generator | Insert-only `IFluidHandler` (mB) | `InsertionOnlyStorage<FluidVariant>` (droplets) | `lavaGeneratorBurnsPipedLava` |
| Overclock upgrade | Faster, less efficient generators | Shared | Shared | `overclockTradesEfficiencyForSpeed`, `upgradesInstallRemoveAndDrop` |
| JEI / REI / EMI | Resonating category | `@JeiPlugin`, `@EmiEntrypoint`, `@REIPluginClient` (`GridwrightReiForgePlugin`) | `jei_mod_plugin`, `emi`, `rei_client` entrypoints | `resonatingDisplaysCoverEveryRecipe`, `viewerPluginsFollowInstalledViewers` (also run with `-PwithJei`, `-PwithRei`, `-PwithEmi`) |

## Deliberate loader differences

- **Sound muffling coverage.** Forge muffles on the client, where the level sound event fires for
  every sound the server sends and for the client's own predicted sounds (the server-side firing is
  skipped so nothing is muffled twice). Fabric muffles every sound the server broadcasts; it has no
  client hook, so a player's own predicted sounds stay at full volume for that player.
  This and the two ward mixins below are Gridwright's only mixins, and `scripts/check_loader_parity.py`
  fails if any other appears.
- **Ward hooks.** Forge wards answer the loader's spawn-check and ender-teleport events; Fabric has
  neither, so `NaturalSpawnerMixin` and `EnderManMixin` hook the same two vanilla checks. Both reach
  the shared `Wards` registry, so coverage and rules are identical. On Forge another mod may also
  answer `CheckSpawn`; a ward's `DENY` is just one listener's result.
- **Claim checks.** Gridwright has no protection API of its own. Both wands fire each loader's
  standard break / place hooks so claim mods can cancel per block; the Fabric builder's wand uses
  `UseBlockCallback` because Fabric has no place event.
- **Fluid units.** Drum capacities are whole buckets on both loaders; only the API units differ.
- **Fake players.** Spikes use a fixed-profile player named `[Gridwright]` on both loaders; it never
  joins the world, so mobs hit by it are told not to retaliate. Quarries and mechanical blocks use a
  fake player with their owner's profile instead (Forge `FakePlayerFactory`, Fabric `FabricFakePlayer`),
  so claim mods judge them as that player; item cooldowns are advanced by elapsed server ticks because
  neither loader ticks these players.

## Not implemented

See the README's plan and known gaps: there is no cursed lasso, creative wand or drum, Angel Ring
wings or Curios/Trinkets slot, or spike enchantment, and no damage-over-time darkness in the Deep Dark.
