# Minecraft 1.19.2 ports

A workspace for bringing older Minecraft mods to **Minecraft 1.19.2**.
Each port lives in `mods/<mod-id>` and produces its own JAR. The default Forge
tooling pins Java 17, Forge 43.5.0, ForgeGradle 6.0.54, and Gradle 8.8. Fabric
ports use independent Gradle builds so their loader tooling stays isolated.

| Port | Status | Notes |
| --- | --- | --- |
| [Mystcraft](mods/mystcraft/) | In progress | Forge + Fabric development port with linking, descriptive books, generated Ages, writing and portals. Full classic parity remains in progress. |
| [ICBM](mods/icbm_port/) | In progress | Fabric alpha: payload simulation, persistent flight/exposure, energy, delivery, defenses, screens and optional JEI. |
| [JurassiCraft](mods/jurassicraft/) | In progress | Forge + Fabric alpha with shared gameplay: fossils, dinosaurs, genetics, plants, laboratories, and park equipment. |
| [BuildCraft](mods/buildcraft/) | In progress | Fabric + Forge port of BuildCraft 8.0.x: engines, item/fluid/kinesis pipes, tank, pump, mining well, quarry, oil wells and the distiller/heat exchanger fuel chain, with Team Reborn Energy/Forge Energy at 1 MJ = 10 units. |
| [Advanced Genetics](mods/advancedgenetics/) | In progress | Forge + Fabric port of the 1.0.3 release with shared gameplay: all genes, six machines, Forge Energy or Team Reborn Energy, and server GameTests on both loaders. Survival and multiplayer play-testing remain. |
| [Extreme Reactors](mods/extremereactors/) | In progress | Fabric + Forge alpha of Extreme Reactors 2 with shared gameplay: reactors, turbines, reprocessor and fluidizer with the upstream simulation and screens, Team Reborn Energy/Forge Energy, and server GameTests on both loaders. The ComputerCraft, JEI and Patchouli integrations are not ported yet. |

Mystcraft, JurassiCraft, BuildCraft, Advanced Genetics and Extreme Reactors support Forge and Fabric;
ICBM targets Fabric. Install the variant matching your game loader;
a mod's Forge and Fabric JARs cannot be installed together.

## Get started

On Linux, install the workspace JDK and prepare the build:

```sh
./scripts/setup.sh
./scripts/gradle.sh build
```

Setup needs Python 3, curl, and tar. It downloads Eclipse Temurin 17 and verifies
the vendor-provided SHA-256 before extracting it into the ignored `.local/`
directory. A working Java 17 installation can also be supplied through `JAVA_HOME`.
The first Forge build downloads and prepares Minecraft and can take several minutes.

On Windows, install JDK 17, set `JAVA_HOME`, and use `gradlew.bat` in place of
`./scripts/gradle.sh`. On macOS, use an installed JDK 17 with `./gradlew`.

```sh
python3 scripts/ports.py list
./scripts/gradle.sh :mystcraft:build
./scripts/gradle.sh -p mods/mystcraft/fabric build
./scripts/gradle.sh :mystcraft:runClient
./scripts/gradle.sh :mystcraft:runServer
./scripts/gradle.sh :mystcraft:runData
./scripts/gradle.sh :icbm_port:build
./scripts/gradle.sh :icbm_port:runGametest
./scripts/gradle.sh :icbm_port:runClient
./scripts/gradle.sh :jurassicraft:build
./scripts/gradle.sh -p mods/jurassicraft/fabric build
./scripts/gradle.sh :jurassicraft:runGameTestServer
./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithJade -PwithEmi
./scripts/gradle.sh :buildcraft:build
./scripts/gradle.sh -p mods/buildcraft/fabric build
./scripts/gradle.sh :buildcraft:runGameTestServer
./scripts/gradle.sh -p mods/buildcraft/fabric runGametest
./scripts/gradle.sh :advancedgenetics:build
./scripts/gradle.sh -p mods/advancedgenetics/fabric build
./scripts/gradle.sh :advancedgenetics:runGameTestServer
./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest
./scripts/gradle.sh :extremereactors:build
./scripts/gradle.sh -p mods/extremereactors/fabric build
./scripts/gradle.sh :extremereactors:runGameTestServer
./scripts/gradle.sh -p mods/extremereactors/fabric runGametest
```

JARs land in `mods/<mod-id>/build/libs/`; Mystcraft, JurassiCraft, BuildCraft, Advanced Genetics and Extreme
Reactors Fabric JARs
land in `mods/<mod-id>/fabric/build/libs/`. Fabric also requires Fabric API on server
and clients. Client, server, and data runs use
separate directories under that mod's `run/`. A graphical display is required
for the client. Read and accept the Minecraft EULA yourself before running a
server. Setup does not accept it for you.

## Add a port

```sh
python3 scripts/ports.py new examplemod --name 'Example mod' \
  --upstream https://github.com/owner/examplemod --license MIT
./scripts/gradle.sh :examplemod:build
```

The command copies `templates/forge-mod/`, sets the package and metadata, and
creates a loader scaffold. It does not download upstream code. Gradle discovers
projects from `mods/*/port.properties`; no root build edits are needed. Metadata
defaults to `mod_loader=forge`. For a manually added Fabric port, set
`mod_loader=fabric` and provide its own `settings.gradle`, Loom build, and
`fabric.mod.json`; it is included as an independent composite build. The `new`
command continues to generate Forge scaffolds.
Check the upstream license before importing source or assets, retain its notices
and license text in the port, and record the imported revision in `port.properties`.
See [the porting guide](docs/porting.md) and [contribution guide](CONTRIBUTING.md).
To give a port's items EMC values for ProjectE and Item Alchemy, follow
[docs/emc.md](docs/emc.md).

## Layout

```text
mods/                   One independently packaged project per port
templates/forge-mod/    Starter used by scripts/ports.py new
gradle/forge-mod.gradle Shared Java, Forge, resources, and run configuration
scripts/                Setup, Gradle environment, and port management
docs/                   Porting and development instructions
```

`./scripts/gradle.sh build` builds the default variants of all ports. Build
the Fabric variants separately with the commands above. GitHub Actions runs
these checks in parallel, one job per port, plus a tooling job. The `build`
job keeps the existing check name and passes only when every port job passes:

- checks metadata, EMC coverage, and the port-management, EMC, ICBM, and JurassiCraft tooling tests;
- builds ICBM and both variants of Mystcraft, JurassiCraft, BuildCraft, Advanced Genetics,
  Extreme Reactors, and Mekanism;
- verifies loader parity for each dual-loader port;
- runs ICBM's dedicated-server GameTests and the Forge and Fabric GameTest
  suites of Mystcraft, JurassiCraft, BuildCraft, Advanced Genetics, Extreme
  Reactors, and Mekanism;
- uploads the resulting JARs.

Add mod-specific dependencies and tests to that port's `build.gradle` and source
tree.

## Origins and licensing

This repository started as a fork of [Mystcraft2](https://github.com/Mystcraft/Mystcraft2).
Its original README, license, and CLA workflow are preserved under `mods/mystcraft/`.
The upstream CLA workflow is archived rather than applied to unrelated ports.
The existing root [LGPLv3 license](LICENSE) remains in place. Imported code and
assets retain their upstream licenses; each port records its own licensing and
attribution. A folder in this collection does not imply an official upstream release.
