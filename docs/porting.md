# Porting a mod

## Start a project

Check the upstream project's code and asset licenses, then run the `ports.py new`
command in the root README. It creates a project from `templates/forge-mod/`.
Add upstream license text and notices before copying code. Record the exact source
revision in `port.properties` and explain any separately licensed assets in the
port README.

`port.properties` uses single-line `key=value` entries without escapes. Keep
metadata values readable and avoid quotes in fields expanded into TOML.
`mod_id` must match the directory name and contain 2-64 lowercase letters,
digits, or underscores, beginning with a letter. Use these statuses:

| Status | Meaning |
| --- | --- |
| `scaffold` | Build and loader entry point only |
| `in-progress` | Implementation underway, incomplete gameplay |
| `playable` | Core gameplay works, remaining gaps documented |
| `released` | A tested release exists, with its version recorded |

## Keep ports independent

The root Gradle settings discover `mods/*/port.properties`. The shared build
configures Java, official Mojang mappings, Forge, metadata expansion, source JARs,
and reobfuscation. Each project's `build.gradle` runs after that configuration,
so use it for mod-specific dependencies, access transformers, and data generators.

Declare any intentional dependency on another port explicitly, including its
`mods.toml` dependency. Development runs load the selected port only. Additional
ports must be added to that run's `mods` configuration when testing integration.
Do not copy shared state or make every JAR depend on the whole collection.

Data generators write to each project's `src/generated/resources/`. Commit useful
generated data, but keep `build/`, `run/`, and generator caches out of Git.

## Check a port

Build `:<mod-id>:build` first. Then check client loading and dedicated-server
loading separately, including registry entries, resource loading, world creation,
saved-world compatibility, and the gameplay being ported. Keep client-only classes
away from common initialization. A successful JAR build does not prove gameplay.

Each run uses `mods/<mod-id>/run/<client|server|data>`. These directories contain
private local saves and settings and are ignored. Accept the Minecraft EULA only
after reading it. To use an IDE, import the root Gradle project with JDK 17 and
run `:<mod-id>:genIntellijRuns` or `:<mod-id>:genEclipseRuns` as appropriate.

## Work in the cloud environment

Run `./scripts/setup.sh`, then use `./scripts/gradle.sh` for builds. Source
`scripts/env.sh` if you need `java` or `javac` directly. The wrapper sets a local
Gradle cache and translates inherited HTTP proxies and the system Java CA store
into ignored Gradle settings. TLS and artifact checksum verification stay enabled.
The shared build limits Gradle to two workers and a 3 GB heap.

The cloud environment supports compilation and headless data generation.
A graphical client requires a display and graphics support. A dedicated server
requires your EULA acceptance. Neither is started by environment setup.

## Fabric builds in the collection

The Forge template remains the default for new scaffolds. ICBM is the explicit
Fabric port, under `mods/icbm_port`. Its `port.properties` sets
`mod_loader=fabric`; the root settings include it as an independent Gradle build
so Loom and ForgeGradle do not configure the same project. Its local settings,
dependencies, mod manifest, and license stay inside its directory.

The root `build` includes both loaders' artifacts and unit checks. Use
`./scripts/gradle.sh :icbm_port:runGametest` for Fabric server gameplay tests.
The generated JARs still require their respective loaders and cannot be loaded
together in a single standard Forge or Fabric game instance.
