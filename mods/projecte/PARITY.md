# ProjectE loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream ProjectE 1.1.0. Forge runs
them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/projecte/lib`), injected
`projecte$` interfaces on vanilla classes and the mixins in `ports.projecte.fabric.mixin` (see
[UPSTREAM.md](UPSTREAM.md)). Every upstream item, block, menu, command, config and data file is registered on both
loaders. This file says which of them have been verified, and how.

"Verified" below means a GameTest on a dedicated GameTest server, on the loaders named. The Fabric jar has also been
launched in the Tekxit 4 client and dedicated server, with and without Item Alchemy
([tekxit4-testing.md](../../docs/tekxit4-testing.md#projecte-fabric-port-in-tekxit-4-2026-10-24)). There, the client
opened the Transmutation Table, Alchemical Chest, Energy Condenser, Energy Collector and Dark Matter Furnace screens,
showed EMC in item tooltips, registered ProjectE's key bindings and showed ProjectE's EMI recipes. The Forge jar has not
been launched in a client.

## Phases

The port lands in phases, one pull request each:

| Phase | Scope | Status |
| --- | --- | --- |
| 1 | EMC core and mapper, player knowledge and EMC sync, Philosopher's Stone, Transmutation Table and Tablet, Alchemical Chest and Bags, Energy Collectors, Antimatter Relays, Energy Condensers, Klein Stars as EMC storage | GameTests below on both loaders |
| 2 | Dark and Red Matter blocks, furnaces, tools and armour; Gem armour | Verified on both loaders (GameTests below) |
| 3 | Rings, amulets, Divining Rods, Watch of Flowing Time, Mercurial Eye, lenses, Destruction Catalyst, Interdiction Torch, Nova Catalyst and Cataclysm, the pedestal, key bindings and modes, Trinkets slots, an EMI plugin | Verified on both loaders (GameTests below); EMI, JEI and Jade plugins and key bindings need a client (phase 4) |
| 4 | ProjectE EMC audits for every port on Fabric; Tekxit 4 server and client check with and without Item Alchemy | Every Fabric port's CI GameTests compare live ProjectE values with `emc/values.json`; Tekxit 4 server and client passed with and without Item Alchemy |

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
| Active Soul, Body and Life Stones heal and feed their holder for EMC | `RingGameTests.soulStoneHealsItsHolder`, `bodyStoneFeedsItsHolder`, `lifeStoneHealsAndFeeds` |
| The Mind Stone stores its holder's experience and gives it back | `RingGameTests.mindStoneStoresAndReturnsExperience` |
| The Zero Ring freezes water | `RingGameTests.zeroRingFreezesWater` |
| The Ring of Ignition lights a campfire (upstream's right-click: campfires and flammable blocks in place) | `RingGameTests.ignitionRingLightsCampfires` |
| The Evertide and Volcanite Amulets place water and lava | `RingGameTests.amuletsPlaceTheirFluids` |
| The Harvest Goddess Band spends bone meal to grow crops around the clicked block | `RingGameTests.harvestGoddessBandGrowsCrops` |
| The Black Hole Band and Void Ring pull items toward their holder | `RingGameTests.blackHoleBandAndVoidRingPullItems` |
| The Swiftwolf's Rending Gale grants flight, which goes when the ring does | `RingGameTests.swiftwolfsRendingGaleGrantsFlight` |
| The Archangel's Smite fires homing arrows for EMC | `RingGameTests.archangelsSmiteFiresHomingArrows` |
| The Arcana Ring acts as the ring its mode selects | `RingGameTests.arcanaRingUsesItsSelectedMode` |
| The Repair Talisman repairs tools in the inventory | `RingGameTests.repairTalismanRepairsTools` |
| The Gem of Eternal Density condenses cheaper items into iron | `RingGameTests.gemOfEternalDensityCondensesItems` |
| The Watch of Flowing Time speeds up nearby block entities | `RingGameTests.watchOfFlowingTimeSpeedsUpBlockEntities` |
| A ring placed on a Dark Matter Pedestal and switched on runs its pedestal effect | `RingGameTests.pedestalRunsItsItem` |
| Klein Stars store EMC to their capacity through the EMC holder capability | `ItemGameTests.kleinStarStoresEmc` |
| The Destruction Catalyst clears blocks, paid for by an off-hand Klein Star | `ItemGameTests.destructionCatalystClearsBlocksUsingKleinStarEmc` |
| The Hyperkinetic and Catalytic Lenses fire explosive projectiles for EMC | `ItemGameTests.lensesFireExplosiveProjectiles` |
| The Divining Rods read the ground | `ItemGameTests.diviningRodsReadTheGround` |
| The Mercurial Eye builds its target block with the Klein Star inside it | `ItemGameTests.mercurialEyeBuildsWithItsKleinStar` |
| The Interdiction Torch pushes mobs away | `ItemGameTests.interdictionTorchRepelsMobs` |
| A primed Nova Catalyst explodes and destroys nearby blocks | `ItemGameTests.novaCatalystExplodes` |
| Alchemical Coal, Mobius Fuel, Aeternalis Fuel and their blocks burn in a vanilla furnace (Fabric: Fabric API's fuel registry) | `ItemGameTests.alchemicalFuelsBurnInAFurnace` |
| A furnace burning Aeternalis Fuel (longer than `Short.MAX_VALUE` ticks) keeps its burn time across a save (Fabric: a mixin stores it as an int, as Forge does) | `ItemGameTests.longBurnTimeSurvivesSaving` |
| The Tome of Knowledge gives full knowledge | `ItemGameTests.tomeOfKnowledgeGivesFullKnowledge` |
| The mode, charge and projectile keys' actions, through the item capabilities the key packet calls | `ItemGameTests.modeAndChargeKeysWorkThroughCapabilities`, `projectileKeyFiresThroughTheShooterCapability` |

Fabric only (allowlisted in [gametest-loader-only.txt](gametest-loader-only.txt)): the Alchemical Chest is a Transfer
API `Storage<ItemVariant>`, and condenser output can be extracted through the Transfer API. Transfer API moves act on
the block entity's own slots at once and are undone if the transaction aborts, so several lookups of one block (or
faces whose handlers share slots) cannot move the same item twice: `twoLookupsCannotExtractTheSameItemTwice`,
`furnaceFacesSharingSlotsCannotDoubleExtract` and `abortedTransactionsRestoreTheChest` check it. With `-PwithTrinkets`
(as CI runs): players have the necklace, ring, belt and Klein Star trinket slots, which accept ProjectE's items
(`FabricTrinketsGameTests.projecteSlotsAcceptProjecteItems`), tick them (`trinketSlotsTickProjecteItems`) and feed EMC
from a Klein Star (`kleinStarSlotFeedsEmcToItems`).

Upstream's JUnit tests (graph mapper, fractions, custom-conversion serialization) pass on both loaders.

The Fabric GameTests also pass with Porting Lib 2.1.1453 (`-PwithPortingLib`), the Mekanism port's Fabric jar
(`-PwithMekanism`) and Trinkets 3.4.2 (`-PwithTrinkets`) loaded beside ProjectE, and without any of them. With Mekanism
loaded, `FabricMekanismGameTests` also checks that a player's ProjectE knowledge and EMC and Mekanism radiation all
survive a save and reload, in either save order, and that Mekanism's charcoal block (16,000 ticks) and ProjectE's
fuels burn for their own times in a vanilla furnace.

## Differences from upstream

- Upstream 1.1.0's `registerDefault` world-transmutation entries have no sneaking alternate, so sneaking with the
  Philosopher's Stone does nothing different for them. This is upstream behaviour, kept.
- Fabric: Curios slots are Trinkets slots (Trinkets 3.4.2, which Tekxit 4 ships). The Fabric jar ships
  `data/trinkets` entries that give players upstream's four Curios slot types: necklace (`chest/necklace`), ring
  (`hand/ring` and `offhand/ring`), belt (`legs/belt`) and Klein Star (a new `chest/klein_star` slot), filled from
  upstream's Curios item tags.
- Both loaders: an EMI plugin (`integration/emi`, not upstream) shows upstream's JEI categories, world transmutation
  and collector fuel upgrades, and adds the Philosopher's Stone and matter furnaces as workstations. Tekxit 4 ships
  EMI rather than JEI.
- Fabric: JEI's and Jade's plugins are registered through the `jei_mod_plugin` and `jade` entrypoints. Tekxit 4 ships
  Jade but not JEI. The One Probe has no Fabric build and is not offered. The WTHIT provider compiles but is not
  registered; Tekxit 4 does not ship WTHIT.
- Fabric: `ItemStack.isCorrectToolForDrops`, `getMaxDamage` and `getHideFlags` answer only for ProjectE's items, so
  another Forge-API layer's (Mekanism Fabric's) identical hooks still answer for its own items.
- Fabric: vanilla saves a furnace's `BurnTime` as a short, which truncates Aeternalis Fuel and the fuel blocks (Forge
  saves an int). `AbstractFurnaceBurnTimeMixin` writes and reads it as an int only when it exceeds `Short.MAX_VALUE`,
  so ordinary saves are unchanged.
- Fabric: CraftTweaker's fluid-stack NSS helpers are absent (CraftTweaker for Fabric 1.19.2 has no fluid stacks).
- Fabric: entity and block entity capabilities are saved under Forge's `ForgeCaps` key. The Mekanism port's Fabric
  layer uses the same compound, so each layer adds its own keys to it and reads only its own keys back, rather than
  replacing it; otherwise whichever saved last would wipe the other's data (ProjectE knowledge, EMC and bags, or
  Mekanism radiation).
- Fabric: the alchemical fuels are registered in Fabric's `FuelRegistry` with upstream's burn times instead of
  patching furnace fuel lookups. A patch competed with Mekanism Fabric's identical one: only the first ran, so one
  mod's fuels stopped burning.

## Known gaps

- The Tekxit 4 client check opened screens and took screenshots, but no one has played through them. Interaction inside
  the screens, HUD overlays, renderers, the JEI plugin (Tekxit has no JEI) and the Forge client are unverified.
  ProjectE's default keys (G, V, C, R, X) collide with other Tekxit keys; rebind them in Controls.
- Phase 2's Red Matter tool modes, the Katar and Morning Star's combined
  actions, and Gem armour's key-bound abilities (explosion, flight, fire protection) have no GameTest of their own;
  they reuse the `ToolHelper` and ability code the tests above exercise.
- Fabric: Forge hooks no ProjectE class uses are not ported (see [UPSTREAM.md](UPSTREAM.md)). Another mod that
  expects ProjectE's Fabric jar to provide Forge's full hook set will not find it; ProjectE's own items get what
  they need.
