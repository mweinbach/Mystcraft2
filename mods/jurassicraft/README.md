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
| Vehicle foliage destruction (`destroyBlocks`) | `config/jurassicraft-vehicles.toml` | `config/jurassicraft-vehicles.toml` |
| Vehicle camera | `config/jurassicraft-vehicle-client.toml` | `config/jurassicraft-vehicle-client.toml` |

Restart after changing the generation settings; existing chunks and structures
are unaffected. Dinosaur metabolism, growth, and herd behavior also have game
rules.

## Controls

| Action | Forge default | Fabric default |
| --- | --- | --- |
| Change vehicle seat | Ctrl+1 to Ctrl+9 | Keypad 1 to 9 |
| Helicopter autopilot | Ctrl+A | Home |
| Helicopter rotation lock | Ctrl+D | End |
| Helicopter rotate | Left / Right | Left / Right |
| Camera zoom in / out | Page Down / Page Up | Page Down / Page Up |
| Vehicle record player | R | R |
| Put down a shoulder microraptor | C | C |

Fabric's key mappings cannot require a modifier key, so its defaults avoid the
upstream Ctrl chords. All keys can be changed in the controls menu. Helicopters
climb and descend with the jump and sprint keys; the rotor needs time to reach
flying speed. Use the record player key with a music disc in your main hand to
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
input slots. The JARs do not bundle these optional mods.

## Limitations

The port is still in progress. World-generation distributions and some
machine, animal, and vehicle behaviors are approximations; see the known gaps
in [PARITY.md](PARITY.md). Large structures, multiplayer, interactions with
other mods, and some audiovisual details still need broader in-game testing.
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

The Forge GameTest server writes its world and logs to `run/gameTestServer` and
exits after printing a pass/fail summary. The Fabric run uses a disposable world
under `fabric/build/gametest` and writes `fabric/build/gametest-results.xml`.
Validate Forge game data with `./scripts/gradle.sh :jurassicraft:runData`. To
launch a development client with optional integrations, add `-PwithJei` and/or
`-PwithJade` to `./scripts/gradle.sh :jurassicraft:runClient`, or `-PwithJade`
and/or `-PwithEmi` to `./scripts/gradle.sh -p mods/jurassicraft/fabric runClient`.
