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
| 2 | Dark and Red Matter blocks, furnaces, tools and armour; Gem armour | Code present on both loaders, unverified |
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

Fabric only (allowlisted in [gametest-loader-only.txt](gametest-loader-only.txt)): the Alchemical Chest is a Transfer
API `Storage<ItemVariant>`, and condenser output can be extracted through the Transfer API.

Upstream's JUnit tests (graph mapper, fractions, custom-conversion serialization) pass on both loaders.

The Fabric GameTests also pass with Porting Lib 2.1.1453 (`-PwithPortingLib`) and with the Mekanism port's Fabric jar
(`-PwithMekanism`) loaded beside ProjectE.

## Differences from upstream

- Upstream 1.1.0's `registerDefault` world-transmutation entries have no sneaking alternate, so sneaking with the
  Philosopher's Stone does nothing different for them. This is upstream behaviour, kept.
- Fabric: Curios slots are Trinkets slots (Trinkets 3.4.2, which Tekxit 4 ships). Unverified until phase 3.
- Fabric: The One Probe has no Fabric build and is not offered. Jade and WTHIT providers compile on Fabric but are
  not yet registered there.
- Fabric: JEI's plugin compiles but is not registered on Fabric yet; Tekxit 4 ships EMI, and the EMI plugin is
  planned for phase 3.
- Fabric: CraftTweaker's fluid-stack NSS helpers are absent (CraftTweaker for Fabric 1.19.2 has no fluid stacks).

## Known gaps

- No client has been launched on either loader. Screens, the transmutation GUI's rendering, HUD overlays, key
  bindings and renderers are unverified.
- Phases 2 and 3 above: registered, untested.
- Fabric: Forge hooks no ProjectE class uses are not ported (see [UPSTREAM.md](UPSTREAM.md)). Another mod that
  expects ProjectE's Fabric jar to provide Forge's full hook set will not find it; ProjectE's own items get what
  they need.
