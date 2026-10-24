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

## Deliberate loader differences

- **Sound muffling coverage.** Forge muffles on the client, where the level sound event fires for
  every sound the server sends and for the client's own predicted sounds (the server-side firing is
  skipped so nothing is muffled twice). Fabric muffles every sound the server broadcasts; it has no
  client hook, so a player's own predicted sounds stay at full volume for that player.
  This is Gridwright's only mixin, and `scripts/check_loader_parity.py` fails if any other appears.
- **Claim checks.** Gridwright has no protection API of its own. Both wands fire each loader's
  standard break / place hooks so claim mods can cancel per block; the Fabric builder's wand uses
  `UseBlockCallback` because Fabric has no place event.
- **Fluid units.** Drum capacities are whole buckets on both loaders; only the API units differ.
- **Fake players.** Spikes use a fixed-profile player named `[Gridwright]` on both loaders; it never
  joins the world, so mobs hit by it are told not to retaliate.

## Not implemented

See the README's plan and known gaps: transfer nodes and quarries are other phases; there is no
cursed lasso, creative wand or drum, Angel Ring wings or Curios/Trinkets slot, or spike enchantment.
