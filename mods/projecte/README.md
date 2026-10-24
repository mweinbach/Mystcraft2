# ProjectE for Minecraft 1.19.2

An unofficial, in-progress port of [ProjectE](https://github.com/sinkillerj/ProjectE) 1.1.0 (the last 1.19.2 release,
tag `PE1.1.0`) to **Fabric and Forge** from upstream's own sources. Upstream ProjectE is Forge-only; Tekxit 4 runs on
Fabric, so the Fabric build is the primary target. The mod id stays `projecte`, so pack configs, KubeJS scripts, data
packs (`pe_custom_conversions`) and other mods' ProjectE integrations see the same names as upstream.

Upstream attribution and every edit to upstream files are in [UPSTREAM.md](UPSTREAM.md), credits in
[NOTICE.md](NOTICE.md), and what has been verified on each loader in [PARITY.md](PARITY.md).

| Loader | Requirements | EMC storage and items | Accessory slots |
| --- | --- | --- | --- |
| Fabric | Loader 0.16.14+, Fabric API 0.77.0+1.19.2; Commons Math, Night Config, maven-artifact and fabric-permissions-api bundled | ProjectE's API; items through the Transfer API | Trinkets (optional) |
| Forge | Forge 43.1.3+ (built against 43.5.0) | ProjectE's API and capabilities | Curios (optional) |

The two builds cannot be mixed in one game, and worlds do not migrate between them. Item Alchemy keeps its own EMC
values and player knowledge; running it beside the Fabric build is checked in phase 4.

## Status

Phases 1 and 2 of 4 are verified: the EMC mapper, player knowledge, transmutation, the Philosopher's Stone,
collectors, relays, condensers, the Alchemical Chest, and the Dark/Red Matter tools, armour and furnaces and Gem
armour pass GameTests on both loaders. Everything else upstream has is registered on both loaders but not yet verified; [PARITY.md](PARITY.md) has the phase plan and the test list. No
client has been launched yet.

Do not install either JAR in a world you care about.

## Build and test

```sh
./scripts/gradle.sh :projecte:build                         # Forge jar: mods/projecte/build/libs
./scripts/gradle.sh -p mods/projecte/fabric build           # Fabric jar: mods/projecte/fabric/build/libs
python3 mods/projecte/scripts/check_loader_parity.py        # after both builds
./scripts/gradle.sh :projecte:runGameTestServer
./scripts/gradle.sh -p mods/projecte/fabric runGametest
./scripts/gradle.sh -p mods/projecte/fabric runGametest -PwithMekanism   # beside the Mekanism port
./scripts/gradle.sh -p mods/projecte/fabric runGametest -PwithPortingLib # beside Porting Lib
python3 mods/projecte/scripts/generate_fabric_tags.py --check
```

The Fabric `build` includes `checkFabricClashes`, which fails if the port's Forge API layer adds a method to a
vanilla class that Porting Lib also adds, or patches a call Porting Lib patches (see [UPSTREAM.md](UPSTREAM.md)).

## Layout

| Path | Contents |
| --- | --- |
| `src/main` | Upstream ProjectE API, code, assets and generated data, shared by both loaders |
| `src/forge` | Forge `mods.toml`, upstream's access transformer, and the Curios and The One Probe integrations |
| `src/fabric` | Fabric metadata, access widener, mixins, the Forge API mirror (`ports.projecte.lib`), the Trinkets-backed Curios replacement and the tag bridges |
| `src/test` | Upstream's JUnit tests, run on both loaders |
| `src/datagen` | Upstream's data generators (`runData` only) |
| `src/gametestCommon`, `src/gametestForge`, `src/gametestFabric` | The port's GameTests, a development-only mod |
| `fabric/` | The isolated Loom build of the Fabric variant |
| `scripts/` | Fabric tag-bridge generator and the loader parity check |
