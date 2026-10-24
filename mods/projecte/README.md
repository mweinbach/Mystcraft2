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
values and player knowledge. The Fabric build runs beside it in Tekxit 4, and also with it removed
([tekxit4-testing.md](../../docs/tekxit4-testing.md#projecte-fabric-port-in-tekxit-4-2026-10-24)).

## Status

All four phases are done. Every upstream item, block and mechanic, from the EMC mapper and transmutation to the matter
tools, rings, amulets, pedestal, explosives and fuels, has a GameTest of its core effect on both loaders. On Fabric,
the Trinkets slots have GameTests too. Every Fabric port's CI GameTests compare live ProjectE EMC with the port's
expected values. In Tekxit 4 the Fabric jar boots on the dedicated server and in the client, with and without Item
Alchemy. The client opens ProjectE's screens and shows its EMI recipes, EMC tooltips and key bindings.
[PARITY.md](PARITY.md) has the phase plan, the test list and what remains unverified: in-screen interaction, HUD,
renderers and the Forge client.

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
./scripts/gradle.sh -p mods/projecte/fabric runGametest -PwithTrinkets   # adds the trinket-slot tests
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
