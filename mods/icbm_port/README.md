# ICBM Fabric Port — initial prototype

Target: Minecraft **1.19.2**, Fabric Loader **0.16.14+**, Fabric API
**0.77.0+1.19.2**, and **Java 17**.

This is an unofficial port in progress. It contains a first gameplay slice;
the full ICBM feature set is not implemented. See [PORTING.md](PORTING.md).

## Build and run

From this directory, with a Java 17 JDK installed:

```sh
./gradlew build
./gradlew runGametest
./gradlew runClient
```

Windows: use `gradlew.bat` instead. The distributable mod is
`build/libs/icbm-fabric-port-0.1.0-alpha.1.jar` (not the `-sources.jar`). Install it
with the matching Fabric API on both the client and server.

This port is an independent Fabric build inside the Mystcraft mod collection.
From the workspace root, use `./scripts/gradle.sh :icbm_port:build` or
`./scripts/gradle.sh :icbm_port:runGametest`. The root `build` task builds both
Mystcraft (Forge) and ICBM (Fabric); each produces a separate mod JAR. They use
different mod loaders and cannot be installed in the same game instance.

## Try the first features

Use the ICBM Fabric Port creative tab, craft the items, or run:

```mcfunction
/give @s icbm_port:condensed_explosive
/give @s icbm_port:condensed_missile 16
/give @s icbm_port:launcher
```

- **Condensed explosive:** ignite with flint and steel, a fire charge, redstone,
  or a burning player-fired projectile. A normal fuse lasts five seconds.
  Nearby explosions can chain-prime it with a shorter fuse.
- **Direct-fire launcher:** placement faces it like a dispenser, including up
  or down. Use a condensed missile on it to load one; the front changes to an
  observer face. Apply a redstone pulse to fire after four ticks. Clear the
  block immediately in front first. Sneak-use with an empty hand to unload.
  Holding power does not automatically fire newly loaded ammunition.
- **Missile:** travels in a straight line and detonates on collision. Its flight
  expires after ten loaded seconds or on reaching unloaded terrain, the world
  border, or the build height limit.

Models deliberately use vanilla textures and are placeholders. Prototype
explosions affect the world; use a test world while developing.

## Validation

`build` compiles the common/client sources, runs JUnit tests for adapted flight
math, and creates a remapped production jar. `runGametest` starts a dedicated
Fabric test server to exercise registration and gameplay behavior. The test mod
is a separate source set and is not packaged into the production jar.

Manual client checks still needed: model appearance, item use and inventory
interaction, creative tab display, multiplayer rendering, and recipe discovery.

Verified on October 18, 2026: production build passed; all **4 JUnit tests** and
**8 dedicated-server GameTests** passed. All **29 model/texture references** were
also checked against the Minecraft 1.19.2 client resources and this module.

## Attribution

Based on ICBM Classic by Built Broken Modding and contributors. The direct-flight
normalization is adapted from its MIT-licensed `DirectFlightLogic`. Copyright
and license notices are included in `LICENSE-ICBM-CODE.md` in the jar.
New Java code and JSON resources in this module use the MIT license. Vanilla
textures are referenced by Minecraft resource identifiers, not copied here.
