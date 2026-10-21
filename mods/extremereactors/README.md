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
coolant fluids, and the wrench. Blocks, models and the fuel rod and rotor
animations are Extreme Reactors' own. The machine textures are a cohesive set
redrawn for this port; ores, items, fluids and screens keep upstream's (see
[PARITY.md](PARITY.md#machine-textures)). The port also adds cosmetic,
client-side machine effects: control rod drives, a Cherenkov glow, steam,
alarms and SCRAM visuals (see [PARITY.md](PARITY.md#machine-effects)).

Every upstream machine screen is ported with Extreme Reactors' own textures and
layouts: the reactor, turbine, reprocessor and fluidizer controllers, and the
access, fluid, charging, redstone, control rod and injector ports. Right-click a
controller or port to open its screen. [PARITY.md](PARITY.md) lists every gap
against upstream.

## Interoperability

| | Fabric | Forge |
| --- | --- | --- |
| Energy | Team Reborn Energy (`teamreborn:energy`, bundled), 1 E per FE | Forge Energy |
| Items and fluids | Fabric Transfer API | Forge capabilities |
| Tags | `c:` tags such as `c:yellorium_ingots` and `c:uranium_ingots`, plus the `forge:` tags upstream uses | `forge:` tags plus the same `c:` tags |
| Energy shown | `E` | `FE` |
| Computer ports | CC: Restitched 1.101.2 | CC: Tweaked 1.101.3 |
| Recipe viewers | JEI 11.6 or EMI 1.1.24 | JEI 11.6 or EMI 1.1.24 |
| Handbook (the Extreme Book) | Patchouli 1.19.2-77 | Patchouli 1.19.2-77 |
| Mekanism gas port | This repository's Mekanism port, `mekanism:gas_handler` | Mekanism 10.3.9 |

The integrations are optional. With Patchouli, the handbook is in the general
creative tab and each machine screen's help button opens its entry. EMI uses
the port's own plugin, so it does not need JEI.

Every item has an EMC value or a documented exclusion for ProjectE 1.1.0
(Forge) and Item Alchemy 1.1.3 (Fabric), shipped as data in both JARs. See
[emc/README.md](emc/README.md) and [docs/emc.md](../../docs/emc.md).

## Configuration

Both loaders read `config/extremereactors/common.toml` and
`config/extremereactors/client.toml`, with upstream's options and defaults.
The worldgen switches (`enableWorldGen` and each ore's `...EnableWorldGen`)
apply when a server starts. The cluster count, cluster size and height options
are inert, as upstream; datapacks can override the placed features instead.
Modpacks can add reactants, moderators, coolants and coils with
`config/extremereactors/modpack_api.json`, as described in
[upstream/README.md](upstream/README.md); an example is in
[upstream/modpack_api.json](upstream/modpack_api.json).

Clients also read `config/extremereactors/effects.toml`, the port's own file,
which turns each machine effect on or off and sets their range, glow strength,
particle density and alarm volume. It is read when the client starts.

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

The `showcaseClient` run on either loader starts a development client with the
GameTest mod, which adds the `/ershowcase` command. `/ershowcase build` clears
an area next to the player and builds a passive reactor, an active reactor, a
turbine, a reprocessor and a fluidizer, loads them with fuel and inputs, then
keeps the reprocessor and fluidizer powered, the reprocessor watered and the
active reactor's steam vented. `/ershowcase plant` builds a running 9×9×9
reinforced reactor and a 7×12×7 reinforced turbine with every reinforced port
variant, for reviewing textures; the turbine's power taps and active fluid port
are on its east face. Its turbine vents all its exhaust and one reactor fluid
port outputs steam into the open. Each build replaces the previous one,
including the machines `/ershowcase` keeps supplied. `/ershowcase rods <insertion>`,
`/ershowcase scram` and `/ershowcase start` set the rods of, SCRAM and restart
the showcase reactors, and `/ershowcase turbines on|off` turns the showcase
turbines on and off, for reviewing the machine effects.
`/ershowcase open <part>` opens a part's screen in the current build, for
example `reactor_controller` or `turbine_fluidport`; both builds use the same
part names. Neither the command nor the
GameTest mod is in the release JARs. After editing
`scripts/generate_textures.py` or `scripts/generate_sounds.py`, run it to redraw
the textures or resynthesize the effect sounds.

Add `-PwithCompat` to a client run to load ComputerCraft, Patchouli and JEI, and
on Forge Mekanism; add `-PwithEmi` as well to load EMI instead of JEI.
`-PwithMekanism` adds Mekanism to a GameTest server or development client. On
Forge that is Mekanism 10.3.9. On Fabric it is this repository's Mekanism port,
which the Fabric build compiles against and does not ship inside its JAR.
`reactorMekanismPortFollowsMekanism` then checks the gas port. The Fabric
showcase client also gains `/ershowcase gas`, which builds a running reactor
whose Mekanism port feeds steam into a pressurized tube and a chemical tank.
Run `-PwithMekanism` apart from `-PwithProjectE` and `-PwithItemAlchemy`:
Mekanism's steel enables the steel recipes, so those EMC audits derive other
values.

## License

Extreme Reactors 2 is MIT-licensed by ZeroNoRyouki, and ZeroCore 2's multiblock
API is MIT-licensed by ZeroNoRyouki with credit to erogenousbeef's BeefCore; see
[licenses/](licenses/). The port's own code is MIT-licensed; see [LICENSE](LICENSE).
