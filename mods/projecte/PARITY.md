# ProjectE loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream ProjectE 1.1.0. Forge runs
them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/projecte/lib`), injected
`projecte$` interfaces on vanilla classes and the mixins in `ports.projecte.fabric.mixin` (see
[UPSTREAM.md](UPSTREAM.md)). Every upstream item, block, menu, command, config and data file is registered on both
loaders. This file says which of them have been verified, and how.

"Verified" below means a GameTest on a dedicated GameTest server, on the loaders named. Nothing here has been checked
in a client yet; client-only parts (screens, HUD, key bindings, renderers) compile and register but are unverified.

## Phases

The port lands in phases, one pull request each:

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | EMC core and mapper, player knowledge and EMC sync, Philosopher's Stone, Transmutation Table and Tablet, Alchemical Chest and Bags, Energy Collectors, Antimatter Relays, Energy Condensers, Klein Stars as EMC storage | GameTests below on both loaders |
| 2 | Dark and Red Matter blocks, furnaces, tools and armour; Gem armour | Verified on both loaders (GameTests below) |
| 3 | Rings, amulets, Divining Rods, Watch of Flowing Time, Mercurial Eye, lenses, Destruction Catalyst, Interdiction Torch, Nova Catalyst and Cataclysm, the pedestal, key bindings and modes, Trinkets slots, an EMI plugin | Code present on both loaders (EMI plugin not yet written), unverified |
| 4 | ProjectE EMC audits for every port on Fabric; Tekxit 4 server and client check with and without Item Alchemy | Not started |

## Verified on both loaders

| Feature | GameTest |
| --- | --- |
| The EMC mapper reproduces upstream's values for vanilla and ProjectE items (cobblestone 1, torch 9, transmutation table 260, ...) | `EmcMapperGameTests.vanillaAndProjectEValuesMatchUpstream` |
| Items with no recipe path have no EMC | `EmcMapperGameTests.unmappableItemsHaveNoValue` |
| Burning an item in transmutation learns it and adds its EMC | `TransmutationGameTests.burningAnItemLearnsItAndAddsItsEmc` |
| Withdrawing a learned item costs its EMC | `TransmutationGameTests.withdrawingAnItemCostsItsEmc` |
| Unlearned items cannot be withdrawn | `TransmutationGameTests.unknownItemsCannotBeWithdrawn` |
| Unlearning forgets the item | `TransmutationGameTests.unlearningForgetsTheItem` |
| The Transmutation Tablet opens the transmutation menu | `TransmutationGameTests.tabletOpensTheTransmutationMenu` |
| Philosopher's Stone world transmutation (stone to cobblestone, crimson to warped planks) | `PhilosophersStoneGameTests.transmutesStoneIntoCobblestone`, `transmutesCrimsonPlanksIntoWarped` |
| The stone stays in the crafting grid (Fabric: through Fabric API's recipe remainder) | `PhilosophersStoneGameTests.craftingKeepsTheStone` |
| An Energy Collector generates EMC | `EmcBlockGameTests.collectorGeneratesEmc` |
| An Energy Condenser turns its input into its locked item | `EmcBlockGameTests.condenserTurnsItsInputIntoItsLock` |
| An Antimatter Relay burns items and feeds a neighbour; it charges a Klein Star | `EmcBlockGameTests.relayBurnsItemsAndFeedsANeighbour`, `relayChargesAKleinStar` |
| The Alchemical Chest stores items through its item handler | `EmcBlockGameTests.alchemicalChestHoldsItemsThroughItsItemHandler` |
| Right-clicking an ore with a Dark Matter Pickaxe mines the connected vein and drops its ore | `MatterGameTests.pickaxeVeinMinesAnOre` |
| A charged shovel, hoe and axe flatten, till and strip a square around the clicked block | `MatterGameTests.chargedShovelFlattensAnArea`, `chargedHoeTillsAnArea`, `chargedAxeStripsAnArea` |
| A charged hammer digs a square | `MatterGameTests.chargedHammerDigsAnArea` |
| Dark Matter Shears shear nearby sheep on right-click (Fabric: `IForgeShearable` on sheep through a mixin) | `MatterGameTests.shearsShearNearbySheep` |
| Swords attack through armour, harder with charge | `MatterGameTests.chargedSwordHitsHarder` |
| Charge raises a tool's attack damage (Fabric: Fabric API's stack-aware attribute modifiers) | `MatterGameTests.chargeRaisesAttackDamage` |
| Charge steps up to the tool's maximum through `IItemCharge` | `MatterGameTests.chargeChangesThroughTheCapability` |
| Matter tools mine matter blocks of their tier at full speed and higher tiers slower | `MatterGameTests.matterToolsMineMatterBlocksOfTheirTier` |
| The Dark and Red Matter furnaces smelt with ordinary fuel and double ores | `MatterGameTests.darkMatterFurnaceSmeltsAndDoublesOres`, `redMatterFurnaceSmeltsAndDoublesOres` |
| Dark Matter armour takes 80% off damage that bypasses armour (Fabric: the port's `LivingHurtEvent`) | `MatterGameTests.darkMatterArmourReducesDamage` |
| The Gem Helmet's night vision and the Gem Boots' fall protection act on the armour tick | `MatterGameTests.gemArmourActsOnTheArmourTick` |
| Gem Boots add movement speed through their attribute modifiers | `MatterGameTests.gemBootsAddMovementSpeed` |

Fabric only (allowlisted in [gametest-loader-only.txt](gametest-loader-only.txt)): the Alchemical Chest is a Transfer
API `Storage<ItemVariant>`, and condenser output can be extracted through the Transfer API. Transfer API moves act on
the block entity's own slots at once and are undone if the transaction aborts, so several lookups of one block (or
faces whose handlers share slots) cannot move the same item twice: `twoLookupsCannotExtractTheSameItemTwice`,
`furnaceFacesSharingSlotsCannotDoubleExtract` and `abortedTransactionsRestoreTheChest` check it.

Upstream's JUnit tests (graph mapper, fractions, custom-conversion serialization) pass on both loaders.

The Fabric GameTests also pass with Porting Lib 2.1.1453 (`-PwithPortingLib`) and with the Mekanism port's Fabric jar
(`-PwithMekanism`) loaded beside ProjectE. With Mekanism loaded, `FabricMekanismGameTests` also checks that a player's
ProjectE knowledge and EMC and Mekanism radiation all survive a save and reload, in either save order, and that
Mekanism's charcoal block (16,000 ticks) and ProjectE's fuels burn for their own times in a vanilla furnace.

## Differences from upstream

- Upstream 1.1.0's `registerDefault` world-transmutation entries have no sneaking alternate, so sneaking with the
  Philosopher's Stone does nothing different for them. This is upstream behaviour, kept.
- Fabric: Curios slots are Trinkets slots (Trinkets 3.4.2, which Tekxit 4 ships). Unverified until phase 3.
- Fabric: The One Probe has no Fabric build and is not offered. Jade and WTHIT providers compile on Fabric but are
  not yet registered there.
- Fabric: JEI's plugin compiles but is not registered on Fabric yet; Tekxit 4 ships EMI, and the EMI plugin is
  planned for phase 3.
- Fabric: CraftTweaker's fluid-stack NSS helpers are absent (CraftTweaker for Fabric 1.19.2 has no fluid stacks).
- Fabric: entity and block entity capabilities are saved under Forge's `ForgeCaps` key. The Mekanism port's Fabric
  layer uses the same compound, so each layer adds its own keys to it and reads only its own keys back, rather than
  replacing it; otherwise whichever saved last would wipe the other's data (ProjectE knowledge, EMC and bags, or
  Mekanism radiation).
- Fabric: the alchemical fuels are registered in Fabric's `FuelRegistry` with upstream's burn times instead of
  patching furnace fuel lookups. A patch competed with Mekanism Fabric's identical one: only the first ran, so one
  mod's fuels stopped burning.

## Known gaps

- No client has been launched on either loader. Screens, the transmutation GUI's rendering, HUD overlays, key
  bindings and renderers are unverified.
- Phase 3 above: registered, untested. Phase 2's Red Matter tool modes, the Katar and Morning Star's combined
  actions, and Gem armour's key-bound abilities (explosion, flight, fire protection) have no GameTest of their own;
  they reuse the `ToolHelper` and ability code the tests above exercise.
- Fabric: Forge hooks no ProjectE class uses are not ported (see [UPSTREAM.md](UPSTREAM.md)). Another mod that
  expects ProjectE's Fabric jar to provide Forge's full hook set will not find it; ProjectE's own items get what
  they need.
