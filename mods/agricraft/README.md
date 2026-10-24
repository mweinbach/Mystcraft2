# AgriCraft

AgriCraft 4.0.6 for Minecraft 1.19.2 on **Fabric (Loader 0.16.14, Fabric API 0.77.0+1.19.2)** and
**Forge 43.5.0**, backported from upstream's `1.20.1` branch and built from one shared core. Fabric is the
main target (Tekxit 4); the Forge build follows the repository's dual-loader convention.

Read [`NOTICE.md`](NOTICE.md) for provenance: the code, assets and data are upstream AgriCraft (MIT,
[`LICENSE`](LICENSE)), with the 1.19.2 changes listed in [`PARITY.md`](PARITY.md).

- Mod ID `agricraft`, package `com.agricraft.agricraft` (upstream's), licence MIT.
- Status: **in progress**. Phase 1 ports AgriCraft 4.0.6's gameplay, phase 2 adds irrigation, ported
  from AgriCraft 3 because AgriCraft 4 has none, and phase 3 adds upstream's integrations (EMI, JEI, an
  REI plugin written for the port, Jade, WTHIT, Mod Menu/Forge config screen, Botania, the Fabric Seasons
  stub). Server GameTests pass on both loaders; the screens, overlay, journal, models, irrigation
  renderers, recipe-viewer pages and tooltips have not been looked at in a client yet.

## Features

| Feature | Items/blocks | Behaviour |
| --- | --- | --- |
| Crop sticks | `wooden_crop_sticks`, `iron_crop_sticks`, `obsidian_crop_sticks` | Place on a soil, plant a seed on them, or add a second set to make cross crop sticks. Iron and obsidian sticks resist more. Water- and lava-loggable. |
| Plants and seeds | `seed` (one item, the plant in NBT) | Every plant is JSON data (`data/<ns>/agricraft/plants`): growth stages, products, clip products, seeds, and requirements on soil humidity, acidity and nutrients, light, biomes, dimensions, nearby blocks, fluids and seasons. Right-clicking a soil with a vanilla seed plants the AgriCraft plant. |
| Genetics | — | Each seed carries a species gene and six stats with a dominant and a recessive allele: **gain** (yield), **growth** (speed), **strength** (tolerance of bad conditions), **resistance** (to weeds), **fertility** (chance to take part in a cross) and **mutativity** (chance and direction of stat mutations). |
| Cross-breeding | cross crop sticks | Mature neighbours of cross sticks either spread (clone) or combine into a child. Mutations (`data/<ns>/agricraft/mutations`) turn two parents into a new plant; stats mutate by at most one step. |
| Weeds | — | Spawn on empty sticks and on weak plants, grow, spread and can kill plants. Remove them with a rake (which may drop seeds). |
| Seed analyzer | `seed_analyzer` | Shows a seed's genes; with a journal inside, every analyzed plant is recorded in it. |
| Journal | `journal` | A book of researched plants: growth requirements, products and mutations. |
| Tools | `trowel`, `magnifying_glass`, `clipper`, `wooden_rake`, `iron_rake`, `seed_bag`, `debugger` | Move plants between sticks, inspect crops and soils, clip products, remove weeds, carry and sort seeds; a magnifying glass crafted onto any helmet gives the same overlay. |
| Resource crops | `coal_pebble`, `copper_nugget`, `diamond_shard`, `emerald_shard`, `quartz_shard`, `netherite_sliver`, `amathyllis_petal` | Products of AgriCraft's resource plants; nine craft the vanilla item. |
| Irrigation (from AgriCraft 3) | `irrigation_tank`, `irrigation_channel`, `irrigation_channel_hollow`, `agri_channel_valve`, `sprinkler` | Tanks fill from buckets and rain and merge into multi-blocks; channels carry the water, valves (by hand, or redstone on hollow channels) stop it; sprinklers under a channel moisten farmland and speed up plants below. Tanks expose their water to pipes (Forge fluid capability, Fabric Transfer). |
| Recipe viewers | EMI, JEI, REI (optional) | Mutation, produce, clipping and growth-requirement pages for every loaded plant, with the seed and crop sticks, clipper or farmland as workstations; seeds of different plants are told apart. Rebuilt after joining a server or `/reload`. EMI and JEI pages follow upstream; the REI plugin is written for this port. |
| Tooltips | Jade (both loaders), WTHIT (Fabric) (optional) | Crop species, stats and growth; soil humidity, acidity and nutrients; irrigation tank and channel water level. |
| Config screen | Mod Menu (Fabric), Forge mod list | Opens ResourcefulConfig's screen for AgriCraft's options. |
| Botania | Botania (optional) | Plants with the `agricraft:botania_mana` modifier (Botania's flowers in the bundled Botania datapack) grow only within 16 blocks of a mana pool holding enough mana, and each growth step drains it; the Horn of the Wild harvests mature crops. Disable with `compat.botania`. |
| Mod compatibility data | `datapacks/<modid>`, `resourcepacks/<modid>` | Plant data and models for other mods' crops, registered only when that mod is loaded. |

Configuration uses [ResourcefulConfig](https://modrinth.com/mod/resourceful-config) 1.0.20 (required on
both loaders; Tekxit 4 ships it). Commands: `/agricraft seed …` and `/agricraft dump …`.

## Build and test

```sh
./scripts/gradle.sh :agricraft:build
./scripts/gradle.sh -p mods/agricraft/fabric build
python3 mods/agricraft/scripts/check_loader_parity.py
./scripts/gradle.sh :agricraft:runGameTestServer -PwithProjectE
./scripts/gradle.sh -p mods/agricraft/fabric runGametest -PwithItemAlchemy
./scripts/gradle.sh :agricraft:runGameTestServer -PwithEmi -PwithJei -PwithRei -PwithJade
./scripts/gradle.sh -p mods/agricraft/fabric runGametest -PwithEmi -PwithJei -PwithRei -PwithJade -PwithWthit
python3 scripts/emc.py check
```

JARs: `build/libs/agricraft-forge-1.19.2-<version>.jar` and
`fabric/build/libs/agricraft-fabric-1.19.2-<version>.jar`. Install one, never both, with Fabric API on Fabric
and ResourcefulConfig on either loader.

## Layout

```text
src/main            upstream common/: gameplay, API, client code, assets and data
src/forge           upstream forge/ adapters, ported to Forge 43
src/fabric          upstream fabric/ adapters, ported to Fabric API 0.77
src/gametest*       shared GameTests and the per-loader runners (never in the JARs)
fabric/             the Loom build for the Fabric JAR
emc/                EMC policy and generated values (see emc/README.md)
```

## EMC

Values for ProjectE and Item Alchemy come from [`emc/policy.json`](emc/policy.json); see
[`emc/README.md`](emc/README.md). AgriCraft seeds and journals carry their data in NBT and get no EMC;
resource items are worth a ninth of the vanilla item their recipe makes, rounded up.
