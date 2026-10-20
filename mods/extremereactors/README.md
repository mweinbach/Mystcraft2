# Extreme Reactors for Minecraft 1.19.2

Status: **in progress (alpha)**. A port of ZeroNoRyouki's
[Extreme Reactors 2](https://github.com/ZeroNoRyouki/ExtremeReactors2) for
**Fabric and Forge**, built from one shared core. It targets
[Tekxit 4 (Official)](https://www.technicpack.net/modpack/tekxit-4-official.1921233),
Minecraft 1.19.2 and Java 17. Use Fabric Loader 0.16.14 or later with Fabric API
0.77.0 or later, or Forge 43.5.0. Install the JAR matching your loader on the
server and every client. Both JARs use Extreme Reactors' mod id `bigreactors`
and cannot be installed together. Cross-loader multiplayer and moving worlds
between loaders are not supported.

The port includes Extreme Reactors 2's basic and reinforced reactors and
turbines, the reprocessor and the fluidizer, with the upstream reactor, turbine,
fuel, waste, moderator and coolant simulation. It also includes yellorite,
anglesite and benitoite ores, ingots, dusts, storage blocks, reactant and
coolant fluids, and the wrench. Blocks, models, textures and the fuel rod and
rotor animations are Extreme Reactors' own.

**This release has no machine screens.** Right-clicking a controller or port
opens nothing yet; the screens are being rebuilt with Extreme Reactors' textures
and layouts in the next pull request. Until then, machines are switched on by a
redstone port set up with NBT (see [PARITY.md](PARITY.md)). That file lists every
gap against upstream, including the ComputerCraft, JEI and Patchouli
integrations that are not ported yet.

## Interoperability

| | Fabric | Forge |
| --- | --- | --- |
| Energy | Team Reborn Energy (`teamreborn:energy`, bundled), 1 E per FE | Forge Energy |
| Items and fluids | Fabric Transfer API | Forge capabilities |
| Tags | `c:` tags such as `c:yellorium_ingots` and `c:uranium_ingots`, plus the `forge:` tags upstream uses | `forge:` tags plus the same `c:` tags |

Every item has an EMC value or a documented exclusion for ProjectE 1.1.0
(Forge) and Item Alchemy 1.1.3 (Fabric), shipped as data in both JARs. See
[emc/README.md](emc/README.md) and [docs/emc.md](../../docs/emc.md).

## Configuration

Both loaders read `config/extremereactors/common.toml` and
`config/extremereactors/client.toml`, with upstream's options and defaults.
Modpacks can add reactants, moderators, coolants and coils with
`config/extremereactors/modpack_api.json`, as described in
[upstream/README.md](upstream/README.md); an example is in
[upstream/modpack_api.json](upstream/modpack_api.json).

## Source layout

Extreme Reactors 2 lives in `src/main/java/it/zerono/mods/extremereactors` and
ZeroCore 2's MIT multiblock API in `src/main/java/ports/extremereactors/multiblock`.
The rest of ZeroCore 2 is not open source, so `ports.extremereactors.corelib`
replaces it with an independent implementation written for this port. Assets
and data live in `src/main/resources` and are shared by both loaders.
`src/forge` and `src/fabric` contain only loader adapters, descriptors and the
few loader-specific resources. GameTests live in `src/gametestCommon`,
`src/gametestForge` and `src/gametestFabric`, JUnit tests in `src/test`; neither
is packaged. The Fabric Loom build in `fabric/` is isolated from the root
ForgeGradle build.

[UPSTREAM.md](UPSTREAM.md) records the pinned upstream revisions, their licenses,
the clean-room boundary around `corelib`, and every change made to upstream
files.

## Build and test

From the workspace root:

```sh
./scripts/gradle.sh -p mods/extremereactors/fabric build
./scripts/gradle.sh :extremereactors:build
python3 mods/extremereactors/scripts/check_loader_parity.py
./scripts/gradle.sh -p mods/extremereactors/fabric runGametest -PwithItemAlchemy
./scripts/gradle.sh :extremereactors:runGameTestServer -PwithProjectE
```

The JARs are `fabric/build/libs/extremereactors-fabric-1.19.2-<version>.jar` and
`build/libs/extremereactors-forge-1.19.2-<version>.jar`. Both builds also run the
JUnit tests. The parity script checks that the JARs contain the same gameplay
resources and that both loaders run the same GameTests.

The Fabric GameTest run uses a disposable world under `fabric/build/gametest` and
writes `fabric/build/gametest-results.xml`. The Forge GameTest server writes its
world and logs to `run/gameTestServer` and prints a pass/fail summary. To start
a development client, run `./scripts/gradle.sh -p mods/extremereactors/fabric runClient`
or `./scripts/gradle.sh :extremereactors:runClient`.

## License

Extreme Reactors 2 is MIT-licensed by ZeroNoRyouki, and ZeroCore 2's multiblock
API is MIT-licensed by ZeroNoRyouki with credit to erogenousbeef's BeefCore; see
[licenses/](licenses/). The port's own code is MIT-licensed; see [LICENSE](LICENSE).
