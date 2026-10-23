# JurassiCraft for Minecraft 1.19.2

Status: **in progress**. One port of JurassiCraft 1.12.2 for **Forge and Fabric**,
with shared gameplay, assets and save data. It targets
[Tekxit 4 (Official)](https://www.technicpack.net/modpack/tekxit-4-official.1921233),
Minecraft 1.19.2 and Java 17. Use Forge 43.5.0, or Fabric Loader 0.16.14 or
later with Fabric API 0.77.0 or later. Install the JAR matching your loader on
the server and every client. Both JARs use the mod ID `jurassicraft` and cannot
be installed together. Cross-loader multiplayer and moving worlds between
loaders are not supported.

The port includes the ten species enabled by the imported 1.12.2 version, fossil
deposits and nests, plaster wrapping, cleaning and grinding, DNA processing,
dinosaur incubation and coelacanth cultivation. It also includes prehistoric
plants and trees, feeding, breeding, harvestable dinosaur carcasses, skeletal
displays, low-security electric fencing, vehicles, park signs, murals, insect
cultures, goats, darts, records, journals, action figures, and a field guide.
Geneticist and paleontologist villagers, their village buildings, the visitor
centre and raptor paddock are included. Cartographers can sell a map to the
visitor centre. Many original textures, sounds, animations, and model
components are reused.

How the former separate Forge and Fabric ports were merged, and every remaining
gap against 1.12.2, is recorded in [PARITY.md](PARITY.md).

## Configuration

Natural goat and dinosaur spawning are disabled by default, as upstream. The
same options exist on both loaders, in each loader's native format:

| Setting | Forge | Fabric |
| --- | --- | --- |
| Spawning, fossil, plant and structure generation | `config/jurassicraft-common.toml` | `config/jurassicraft-fabric.json` |
| Vehicle foliage destruction (`destroyBlocks`) | `config/jurassicraft-vehicles.toml` | `config/jurassicraft-vehicles.properties` |
| Vehicle camera | `config/jurassicraft-vehicle-client.toml` | `config/jurassicraft-vehicle-client.properties` |

Forge's files are TOML. Fabric's common file is flat JSON and its vehicle files
are Java properties (`key=value`). Fabric releases up to 0.1.0-alpha.1 wrote
those properties files under the `.toml` names; Fabric reads such a file once,
writes the `.properties` file and keeps the old one as `.toml.migrated`. On both
loaders, options added by a newer version are written into an existing file with
their defaults, and missing, invalid or out-of-range values use the default.
Forge re-reads an edited file on its own; Fabric re-reads its files after
`/reload`.

Restart after changing the generation settings; existing chunks and structures
are unaffected. Dinosaur metabolism, growth, and herd behavior also have game
rules.

## Controls

| Action | Forge default | Fabric default |
| --- | --- | --- |
| Change vehicle seat | Ctrl+1 to Ctrl+9 | Keypad 1 to 9 |
| Helicopter autopilot | Ctrl+A | Home |
| Helicopter rotation lock | Ctrl+D | End |
| Helicopter ascend / descend | Space / Left Ctrl | Space / Left Ctrl |
| Helicopter rotate | Left / Right | Left / Right |
| Camera zoom in / out | Page Down / Page Up | Page Down / Page Up |
| Vehicle record player | R | R |
| Put down a shoulder microraptor | C | C |

Fabric's key mappings cannot require a modifier key, so its defaults avoid the
upstream Ctrl chords. All keys can be changed in the controls menu. Helicopters
ascend and descend with their own keys, Space and Left Ctrl by default. While
you are piloting, those presses do not also jump or sprint. A vehicle-only binding
shares its physical key with an ordinary control without a red conflict warning.
Forge applies that only on the controls screen, through a client mixin; the
binding comparison used while flying is unchanged. Fabric does the same on its
controls list. Vanilla Fabric gives each physical key to a single binding, so
JurassiCraft also delivers a shared key's presses to its vehicle-only bindings,
and to the ordinary binding vanilla would otherwise have chosen if a vehicle
binding took the key. Conflicts between other bindings keep vanilla's behaviour.
Seat chords are unmarked as well. Descend, autopilot and rotation
lock still show Forge's gold modifier color against each other, because Left Ctrl
is the modifier for those chords and all three are active while flying. The rotor needs time
to reach flying speed. Use the record player key with a music disc in your main hand to
insert or swap it, or with an empty hand to eject it.

Right-click an imprinted dinosaur with an empty hand to choose wander, follow,
or sit. Sneak-right-click it once it is more than 75% grown to access its
inventory. Sneak-right-click an owned, sitting microraptor to carry it on your
shoulder. Dinosaurs form families, care for young, and retain their
relationships across saves. Sneak-use a dinosaur spawn egg in the air to cycle
its gender, or use it on a block while sneaking to spawn a baby. Use a gestated
coelacanth on water to release it. The field guide opens as a manual when used
in the air, or examines a dinosaur when used on one.

## Integrations

Jade shows dinosaur and machine information on both loaders. JEI (Forge) and
EMI (Fabric) show machine recipe previews and support transfer into machine
input slots. The JARs do not bundle these optional mods; the Fabric descriptor
lists Jade and EMI as suggestions.

Fossil blocks and the wooden fence gates carry both the Forge (`forge:fossils`,
`forge:fence_gates/wooden`) and the Fabric convention tags (`c:fossils`,
`c:wooden_fence_gates`). On Fabric the wood's fire odds are registered through
Fabric API's `FlammableBlockRegistry`, so other Fabric mods see them. Bonus chest
and fishing loot is added only to loot tables a datapack has not replaced, as on
Forge.

Every item has an EMC value or a documented exclusion for ProjectE 1.1.0
(Forge) and Item Alchemy 1.1.3 (Fabric), shipped as data in both JARs. See
[emc/README.md](emc/README.md) and [docs/emc.md](../../docs/emc.md). The
values were checked against both mods in GameTests (`-PwithProjectE`,
`-PwithItemAlchemy`). Machine steps and mob drops are not mapped automatically,
so values for later content need updating in `emc/policy.json`.

## Limitations

The port is still in progress. Some machine, animal, and vehicle behaviors
are approximations; see the known gaps in [PARITY.md](PARITY.md). Both loaders
have walked the visitor centre (its laboratory rooms included), the raptor
paddock, and the village buildings in GameTests, and a Forge development client
has walked the visitor centre and paddock. Chunk-edge fences, walls, vines,
mushrooms and levers are shaped by replaying the one-shot neighbour cascade
once, during worldgen, when the whole footprint is loaded. Other blocks are
put back, including a chest and its loot tag, and entities are not placed. A
chunk a player has already inhabited does not start that pass and is left
alone. A broken fence stays broken. The structure walk checks those blocks
against a one-shot placement. A second
development client saw an Open to LAN world and could not connect to the
advertised address. Dinosaur and machine animation are still being checked
separately.
Genetic item variants use separate IDs rather than legacy item damage values,
and there is no migration for 1.12 worlds or for saves from the earlier
separate Fabric alpha.

## Source layout

Gameplay, registries, recipes, loot, worldgen and assets live in `src/main` and
are shared by both loaders. `src/forge` and `src/fabric` contain only loader
adapters, descriptors, access transformer or widener, and the few resources
whose format is loader-specific. GameTests and their structures live in
`src/gametestCommon`, `src/gametestForge` and `src/gametestFabric` and are
never packaged. The Fabric Loom build in `fabric/` is isolated from the root
ForgeGradle build.

The original 1.12.2 source, assets, and build files remain under
[upstream/](upstream/) as a porting reference; that snapshot is not compiled or
packaged. See [UPSTREAM.md](UPSTREAM.md) for the baseline and licensing history.

## Build and test

From the workspace root:

```sh
./scripts/gradle.sh :jurassicraft:build
./scripts/gradle.sh -p mods/jurassicraft/fabric build
python3 mods/jurassicraft/scripts/check_loader_parity.py
python3 -m unittest discover -s mods/jurassicraft/tools/tests
./scripts/gradle.sh :jurassicraft:runGameTestServer
./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithJade -PwithEmi
```

The JARs are `build/libs/jurassicraft-forge-1.19.2-<version>.jar` and
`fabric/build/libs/jurassicraft-fabric-1.19.2-<version>.jar`. The parity script
checks that both contain the same gameplay resources and no GameTest code.
Set `JURASSICRAFT_TEST_JAR` to either JAR to run the opt-in distribution test in
`tools/tests/test_built_jar.py`.

The Forge GameTest server deletes `run/gameTestServer/world` before each run,
writes its world and logs to `run/gameTestServer`, and exits after printing a
pass/fail summary. The Fabric run uses a disposable world
under `fabric/build/gametest` and writes `fabric/build/gametest-results.xml`.
Validate Forge game data with `./scripts/gradle.sh :jurassicraft:runData`. To
launch a development client with optional integrations, add `-PwithJei` and/or
`-PwithJade` to `./scripts/gradle.sh :jurassicraft:runClient`, or `-PwithJade`
and/or `-PwithEmi` to `./scripts/gradle.sh -p mods/jurassicraft/fabric runClient`.
