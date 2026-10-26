# Classic Mystcraft for Minecraft 1.19.2

An unofficial, in-progress port of XCompWiz’s classic Mystcraft 1.12.2 for **Fabric and Forge**, with shared gameplay, assets and save data. The target remains Minecraft 1.19.2 because **Tekxit 4 uses Fabric 1.19.2**. No Mystcraft2 stability design is included.

Use Java 17. The standalone baselines are Forge 43.5.0 or Fabric Loader 0.16.14 with Fabric API 0.77.0+1.19.2. Tekxit 4 server 20.0.0 supplies Fabric Loader 0.18.4 and the same API version. Install the matching loader’s JAR on the server and every client; this does not provide cross-loader multiplayer or 1.12 save migration.

## Implemented development scope

- Linking books, physical interactive book entities, bookstands, server-validated activation sessions, and source-book preservation.
- Descriptive books with lazy Age allocation, unique identities and seeds, independent time/weather, persistent chunks and restoration after server restart.
- Ordered symbol pages, original glyph artwork, book binding, writing-desk copying, folders and portfolios, ink mixing and source-derived linking modifiers.
- Crystal portal networks, book receptacles, original book/stand/lectern models and imported classic GUI art.
- Source-based libraries and village archivist houses, real loot lecterns, registry-aware weighted page acquisition, booster packs, Age crystal formations and native archivist trades.
- Native black ink, buckets/containers, loader fluid automation, server configuration and linking permissions.

The current 0.4 source has compiler-to-runtime paths for **145/145 static classic symbols**, source weighted grammar completion, ordered modifiers and costs, nine biome controllers, frozen generation plans, and classic instability profiling/decks/effects. Versioned terrain includes source-derived flat, void, Nether, End, normal and amplified density; existing Age plans remain frozen. Dynamic biome/fluid pages use installed registry identities and original glyph composition. These are source implementation claims; per-artifact build, dedicated-server, restart and graphical evidence is recorded in [validation.md](docs/validation.md). Neither the catalog count nor a passing suite proves that every combination matches 1.12.

The writing desk now uses the original wide, automatically arranged workspace, storage tabs, target rename, a navigable embedded book with server-validated linking, and paid right-drag copying; folders and portfolios retain search, sorting and owned-page transactions. Unallocated descriptive books can fill their first existing blank page for ink; allocated Age plans remain immutable. Book presentation includes core panel/page effects, Following glint, Disarm lightning, hurt tint and source sound/particle dispatch. LookingGlass live destination cameras were an optional external integration; editable custom cover colors were not found in the pinned core renderer.

Craft **The Agewriter's Guide** with a book and a feather, then right-click it to open its illustrated parchment reader. Chapter navigation gives quick access to examples, instability advice and recipes. The recipe unlocks when you obtain a book. This port-specific guide covers workstation recipes, finding and copying pages, an ordered example Age, safe linking and instability. Its example is a starting point, not a guarantee of stability. See [in-game-guide.md](docs/in-game-guide.md).

**This remains a development port, not full classic parity or a production-ready release.** Modern biome resolution/surfaces, native structure templates, fluid APIs, entity physics and rendering retain explicit adapter boundaries. Original addon/IMC contracts, optional LookingGlass cameras and 1.12 save migration are not reproduced. Current validation and remaining test limits are maintained centrally; earlier evidence applies only to its recorded artifacts. See [classic-parity.md](docs/classic-parity.md), [symbol-porting.md](docs/symbol-porting.md) and [validation.md](docs/validation.md).

Version **0.4.0-dev** adds the remaining source-derived shop, lectern, book lifecycle, page browser and specialized surface work. Both builds passed83 unit tests each; dedicated suites passed150 checks on Forge and145 on Fabric and the full Tekxit4 server pack, with fresh-JVM persistence checks and graphical checks on both plain loaders. Exact artifact scope and remaining long-play limits are in [validation.md](docs/validation.md). Accepted 0.3 evidence remains in [historical-validation-0.3.md](docs/historical-validation-0.3.md).

## Build and test

```sh
./scripts/gradle.sh :mystcraft:build
./scripts/gradle.sh -p mods/mystcraft/fabric build
python3 mods/mystcraft/scripts/check_loader_parity.py
```

Server GameTests (`./scripts/gradle.sh :mystcraft:runGameTestServer`, `./scripts/gradle.sh -p mods/mystcraft/fabric runGametest`) audit EMC coverage. With `-PwithProjectE` (Forge) or `-PwithItemAlchemy` (Fabric) they also compare against the real EMC mod. Every item has an EMC value or a documented exclusion; linking and descriptive books, decay and the link modifier get none. See [emc/README.md](emc/README.md) and [docs/emc.md](../../docs/emc.md).

Production artifacts are generated under `build/libs/` (Forge) and `fabric/build/libs/` (Fabric). Use a fresh disposable world for development testing. The optional smoke helpers are separate artifacts, enabled by `-PenableSmokeTests`; they are not included in the production JARs. See [server-testing.md](docs/server-testing.md) and the repository’s [Tekxit evidence](../../docs/tekxit4-testing.md).

## Reference and attribution

The behavioral/art reference is [Mystcraft-Legacy 1.12/develop at 9bc8ddc](https://github.com/Mystcraft/Mystcraft-Legacy/tree/9bc8ddc061845df0cd5ea47f4fdef773cff786ae), version 0.13.7.06, under LGPL-3.0. Original attribution and license are retained in [NOTICE.md](NOTICE.md), [LICENSE](LICENSE), and [asset-provenance.md](docs/asset-provenance.md). This is not an official Mystcraft release.
