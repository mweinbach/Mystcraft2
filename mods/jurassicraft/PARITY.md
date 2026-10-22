# Loader parity and upstream gaps

JurassiCraft 1.19.2 ships as one gameplay implementation with two thin loader
adapters. This file records how the former separate Forge and Fabric ports were
reconciled, which loader differences remain, and the known gaps against the
imported 1.12.2 baseline (revision `f7b194e`, see [UPSTREAM.md](UPSTREAM.md)).

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | All gameplay, registry definitions, recipes, loot, worldgen, assets, lang and NBT formats. |
| `src/forge` | `ForgeMod`, `ForgePlatform`, `ForgeClient`, `ForgeConfigBackend`, the biome modifier, JEI, `mods.toml` and the access transformer. |
| `src/fabric` | `FabricMod`, `FabricClientMod`, `FabricPlatform`, `FabricConfigBackend`, mixins, the OBJ model loader, EMI, `fabric.mod.json` and the access widener. |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | Development-only GameTest mods and fixtures; never packaged. |
| `fabric/` | The isolated Loom build; the root Gradle build produces the Forge JAR. |

Shared code reaches loader services only through `ports.jurassicraft.platform.Platform`
and `ClientPlatform`: registration, menus, networking, persistent entity data,
spawn eggs, record items, food properties, flammability, render layers, key
mappings, renderers and colors. Loader events call the shared handlers in
`JurassicEvents` and `JurassicClient`. Biome additions come from the single
tables in `WorldgenPlacements` and `NaturalSpawns`, which Forge applies through
one `jurassicraft:biome_additions` biome modifier and Fabric through
`BiomeModifications`.

`scripts/check_loader_parity.py` checks both built JARs: descriptor isolation, mod
ID and version, no references to the other loader, byte-identical shared
resources, no GameTest classes or structures, the Fabric mixin refmap, and
matching access-transformer and access-widener entries.

## Reconciliations

The rule: where the two ports differed, keep the variant that matches upstream
1.12.2; where upstream does not settle it, keep the union of features.

### World generation

| Topic | Kept | Reason |
| --- | --- | --- |
| Dinosaur fossil ore density | Fabric: `jurassicraft:binomial_count` with 32 trials and trapezoid period heights | Upstream makes 32 attempts per chunk; Forge used one placement with a flat height. |
| Amber, ice shard, gypsum, nest, trackway, peat and plant decorations | Both (already identical) | Counts, vein sizes, heights and rarities match upstream. |
| Biome attachment | New shared `WorldgenPlacements` table | Replaces Forge's per-feature biome-modifier JSONs and Fabric's separate registration; the generation config is applied once in shared code. |
| Fossil-nest favored biomes | Union in `#jurassicraft:nest_favored` | Upstream favors MOUNTAIN, HILLS, MESA and SANDY. The tag lists mountains, hills, badlands, desert and beach, plus optional `#forge:is_sandy` and `#c:desert`. Forge's hard-coded `forge:is_sandy` check never matched on Fabric; listing desert and beach directly keeps upstream's SANDY scope on both loaders. |
| Plant fossils in coal | Both (equivalent) | 1 in 10 coal ores, as upstream's coal generator wrapper; the coal tag unions vanilla, `forge` and `c` coal ores. |
| Peat biomes | New `#jurassicraft:swamps` | Swamp and mangrove swamp plus optional `#forge:is_swamp` and `#c:swamp`, matching upstream SWAMP on both loaders. |
| Moss and lilac biomes | Fabric tags | Built from vanilla biome tags and `#jurassicraft:swamps`, so they no longer depend on Forge-only tags. |

### Spawning

| Topic | Kept | Reason |
| --- | --- | --- |
| Natural dinosaur spawns | New shared `NaturalSpawns` with `#jurassicraft:spawns/<species>` tags | Replaces Forge's hard-coded biome modifier; same weights and groups on both loaders; disabled by default as upstream. |
| Natural goat spawns | Shared rule following the pig spawn list | Upstream copies pig spawns, so modded and datapack pig biomes work. |
| Attributes and spawn placements | Shared tables, loader sinks | No behavior change. |

### Vehicles

| Topic | Kept | Reason |
| --- | --- | --- |
| Step height | Fabric, 1.5 blocks | Upstream `stepHeight = 1.5F`; Forge used 1.0. |
| Rider heading follows turns | Fabric | Upstream adds the vehicle's rotation delta to passengers. |
| Run-over damage names, record drop when destroyed | Both (equivalent) | Match upstream. |
| Foliage destruction setting | Both | `destroyBlocks` in `jurassicraft-vehicles.toml` on both loaders. |

### Dinosaurs

| Topic | Kept | Reason |
| --- | --- | --- |
| Melee attack pose | Fabric's synced attack timer, alongside the shared leap and spit animations | Closest to upstream's `ATTACKING` pose animation; Forge only used the arm swing. |
| Juveniles follow food | Fabric's `TemptJuvenileDinosaurGoal` | Upstream `TemptNonAdultEntityAI` (under 50% grown); Forge lacked it. |
| Carcass on lethal damage | Both (equivalent) | Matches upstream when `allowCarcass` is on. |
| Birth near a forced pose or tranquilizer | Fabric | Same pause rules; the mother's eggs are found around her. |

### Blocks and items

| Topic | Kept | Reason |
| --- | --- | --- |
| Nest explosion drops | Fabric: only encased nests drop | Upstream `canDropFromExplosion` returns `encased`; Forge always dropped. |
| Hatched egg gender tooltip | Fabric | Upstream shows the gender line. |
| Releasing a gestated coelacanth onto water | Fabric (union) | Not in upstream; kept so the fish isn't released on land. |
| Skull display | Forge placement, sound and saved state, plus Fabric's tooltip | Forge matched upstream's clicked-face placement. Fabric's hidden `skull_display` item model was dropped because no such item is registered. |
| Fence climbing | Shared `FenceWireBlock.isLadder` | Overrides Forge's ladder hook by signature; Fabric's climb mixin calls the same method. |
| Flammability | Shared values | Applied through `FireBlock.setFlammable` on Forge and `FlammableBlockRegistry` on Fabric. |
| Spawn eggs | Shared `DinosaurSpawnEgg` behavior | Forge uses `ForgeSpawnEggItem` (registry-safe supplier); Fabric uses `SpawnEggItem`. Sneak-use cycles gender on both. |
| Records | Both track lengths (4749, 1765, 2044 ticks) | Measured from the upstream audio. Forge takes a tick length and lazy sound; Fabric takes seconds, so its length is overridden in ticks. |
| Harvest levels | Forge tags | Amber and ice shards need iron, fossils and nests need stone, peat uses a shovel, as upstream. Fabric was missing the iron and shovel tags. |
| Axe-mineable blocks | Union | Both ports' wooden blocks. |

### Machines and menus

| Topic | Kept | Reason |
| --- | --- | --- |
| Machine inventories (DNA machines, combinator, cultivator, grinder, hatchery, bug crate, feeder, cleaning station) | Fabric's `WorldlyContainer` with per-face `canPlace`/`canTake` | One stable slot set and the same NBT on both loaders. The combinator keeps its slots across mode changes and filters by mode, instead of Forge's per-mode capability invalidation. Forge exposes the same container through `SidedInvWrapper`. |
| Block-entity visual sync | Fabric's `load()` path | Works on both loaders; Forge's extra `handleUpdateTag` was redundant. |
| Menu opening | Shared `Platform.openMenu` | The block position travels in the menu buffer on both loaders. |

### Loot, recipes and tags

| Topic | Kept | Reason |
| --- | --- | --- |
| Structure bonus loot (visitor centre, dining figures, digsites) | Fabric: named `jurassicraft_bonus` pools in the structure loot JSON | Forge's runtime append duplicated the JSON pools. Pools do not scale with `lootSpawnrate`, as upstream. |
| Village and structure chest injection | Shared `addTo(...)` with loader sinks | Same pools on both loaders. |
| Iron nuggets, iron ingots, redstone, blue dyes | New `#jurassicraft:` tags | Successors of upstream ore-dictionary inputs: the vanilla item plus optional `#forge:` and `#c:` tags. Literal `minecraft:iron_ingot` stays where upstream used the item. |
| Fossil-nest replaceable blocks | Union | Both ports' stone, dirt and sand groups plus optional `forge` and `c` ore tags. |
| Water-bottle nucleotide recipe | Per loader | The NBT ingredient type is loader-specific (`forge:partial_nbt` and `fabric:nbt`). |
| DNA machine block models | Per loader | Forge's `forge:obj` loader and Fabric's `jurassicraft_obj` loader read the same OBJ and MTL files. |

### Structures and villages

| Topic | Kept | Reason |
| --- | --- | --- |
| Park murals and paintings | Fabric's `ParkHangingEntities` | Places hanging and other template entities in the anchor chunk after the walls. Forge's `HangingProcessor` relied on a Forge-only hook. |
| Village pools and trades | Shared `VillagePools` and `VillageTrades` | Same digsites, geneticist and paleontologist trades on both loaders. |
| Workstation POIs | Shared definitions | Fabric registers them through `PointOfInterestHelper`, which also maps the block states villagers look for. |
| Visitor-centre maps | Forge's `maps` package | Cartographers sell the map on both loaders. |
| Villager digsite memory | Shared logic, loader storage | Forge persistent data; Fabric stores the same tag under `JurassiCraftData` via a mixin. |

### Client and integrations

| Topic | Kept | Reason |
| --- | --- | --- |
| Seat keys, helicopter autopilot and rotation lock | Forge: Ctrl+1–9, Ctrl+A, Ctrl+D | Upstream defaults. Fabric's key API cannot require a modifier, so it defaults to keypad 1–9, Home and End. Seat chords are active only while riding and are not shown as conflicts; they were red before, because the vehicle context overlapped in-game play. Autopilot and rotation lock stay red: Forge still treats Ctrl+A and Ctrl+D as the strafe keys when the contexts do not overlap. |
| Record player key | Union: R | Upstream used only interaction; both ports added a key, now the same one. |
| Zoom and rotation keys | Both | Page Down/Page Up and Left/Right, as upstream. |
| Helicopter ascend and descend | Both: Space and Left Ctrl | Upstream's dedicated bindings. While the pilot holds one, that press does not also jump or sprint. Forge's controls list still paints them red: `KeyMapping.same` compares the physical key when the contexts do not overlap. Left Ctrl is itself a modifier, so that binding still counts as held while riding; Forge would otherwise ignore an unmodified key whose context does not overlap in-game play. Fabric hides the warning for vehicle-only bindings. |
| Journal | Forge: opens on the client | As upstream; Fabric used a server round trip. |
| Field guide | Union | Using it on a dinosaur sends the upstream inspection packet; empty-hand use opens the manual (port addition). |
| Configuration | Shared options, native files | Forge: `jurassicraft-common.toml`, `jurassicraft-vehicles.toml`, `jurassicraft-vehicle-client.toml`. Fabric keeps its first release's `jurassicraft-fabric.json` and key/value vehicle files with the same keys. |
| Recipe viewers and tooltips | JEI on Forge, EMI on Fabric, Jade on both | Each viewer's plugin is loader-specific; Jade providers are shared. |
| Entity spawn data | Vanilla spawn packets | Forge's `IEntityAdditionalSpawnData` was unnecessary. |
| Creative tab label | Union | Both lang keys remain. |

## GameTests

Both loaders run in-world GameTests from development-only mods: 207 on Forge
(`./scripts/gradle.sh :jurassicraft:runGameTestServer`) and 131 on Fabric
(`./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithJade -PwithEmi`).
Their classes, structures and test-only data are excluded from the production JARs.

The vehicle record test and the dinosaur birth test failed intermittently. Vanilla
starts a test one tick after placing its structure, while newly forced chunks
load their entity sections asynchronously; until those chunks are entity-ticking,
`getEntitiesOfClass` misses entities spawned by the test. `GameTestEntityGate`
wraps every registered test body so it starts only after its structure's chunks
are loaded and entity-ticking. Assertion failures are rescheduled so they still
fail the test immediately. With the gate, Forge passed 15 consecutive full runs.

Tests changed while merging:

- Bonus-loot tests read the server loot tables, since the bonus pools now live in the JSON.
- Park structure tests expect entity-free template placement followed by `ParkHangingEntities`.
- The plant-fossil test also covers modded coal ores through the shared coal tag.
- The combinator test checks the stable slot set and per-mode face filtering.
- The birth test searches for eggs around the mother, who may move while recovering.
- The goat, natural-spawn, village and loot tests call the real loader sinks and config.
- The Fabric coal test tags a stand-in block as `c:coal_ores` from the GameTest mod, since Fabric API 0.77 ships no such block tag, and checks that the shared coal tag picks it up.
- The ancient-tree layout reference moved to `src/gametestCommon` so both suites read it.
- The helicopter altitude test feeds ascend and descend through the vehicle control packet and checks that a jeep and an explorer ignore those bits.

## Known gaps against 1.12.2

- Fossil ore attempts are independent per species instead of sharing 32 attempts per chunk.
- Plant fossils replace tagged coal ores from the usual ore stage only; untagged and later ores are unchanged.
- Some machine and animal animations are simplified; species-specific procedural motion and the exact attack pose differ.
- Village buildings use weighted jigsaw pools without upstream's per-village limits; custom villages that replace vanilla house pools need their own entries.
- Paddock rarity is approximate, and the missing upstream cryonics loot table uses laboratory loot.
- The hybridizer has no recipes because the imported version registers no hybrid species.
- Third-party structure search may skip the visitor centre; `/locate structure jurassicraft:visitor_centre` works.
- The dart gun has a new survival recipe.
- Genetic item variants use separate IDs instead of damage values; 1.12 worlds and items are not migrated.
- A surviving lone dinosaur parent does not form a new family, as upstream.

## Loader limits

- Install the same loader's JAR on the server and every client. Cross-loader multiplayer and moving worlds between loaders are not supported.
- Forge and Fabric configuration files differ in format and are not converted.
- Saves from the earlier separate Fabric alpha are not migrated.
- Claim mods such as Open Parties and Claims see the vehicle, not its driver, when vehicles break foliage; this is untested in claimed areas.
