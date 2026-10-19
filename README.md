# Minecraft 1.19.2 ports

A workspace for bringing older Minecraft mods to **Minecraft 1.19.2**.
Each port lives in `mods/<mod-id>` and produces its own JAR. The default Forge
tooling pins Java 17, Forge 43.5.0, ForgeGradle 6.0.54, and Gradle 8.8. Fabric
ports use independent Gradle builds so their loader tooling stays isolated.

| Port | Status | Notes |
| --- | --- | --- |
| [Mystcraft](mods/mystcraft/) | In progress | Forge + Fabric development port with linking, descriptive books, generated Ages, writing and portals. Full classic parity remains in progress. |
| [ICBM](mods/icbm_port/) | In progress | Fabric alpha: payload simulation, persistent flight/exposure, energy, delivery, defenses, screens and optional JEI. |

Mystcraft supports Forge and Fabric; ICBM targets Fabric. Install only the
Mystcraft variant matching your game loader.

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
```

JARs land in `mods/<mod-id>/build/libs/`; Mystcraft Fabric JARs land in
`mods/mystcraft/fabric/build/libs/`. Fabric also requires Fabric API on server
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

## Layout

```text
mods/                   One independently packaged project per port
templates/forge-mod/    Starter used by scripts/ports.py new
gradle/forge-mod.gradle Shared Java, Forge, resources, and run configuration
scripts/                Setup, Gradle environment, and port management
docs/                   Porting and development instructions
```

`./scripts/gradle.sh build` builds the default variants of all ports. Build
Mystcraft Fabric separately with the command above. GitHub Actions checks
metadata, tests the port-management tool, builds both Mystcraft variants and
ICBM, verifies Mystcraft loader parity, runs ICBM's dedicated-server GameTests,
and uploads the resulting JARs.
Add mod-specific dependencies and tests to that port's `build.gradle` and source
tree. Ports do not depend on one another unless explicitly declared.

## Origins and licensing

This repository started as a fork of [Mystcraft2](https://github.com/Mystcraft/Mystcraft2).
Its original README, license, and CLA workflow are preserved under `mods/mystcraft/`.
The upstream CLA workflow is archived rather than applied to unrelated ports.
The existing root [LGPLv3 license](LICENSE) remains in place. Imported code and
assets retain their upstream licenses; each port records its own licensing and
attribution. A folder in this collection does not imply an official upstream release.
