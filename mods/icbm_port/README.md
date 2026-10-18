# ICBM Fabric Port

An unofficial, in-progress recreation of ICBM Classic for **Minecraft 1.19.2**, **Fabric Loader 0.16.14+**, **Fabric API 0.77.0+1.19.2**, and **Java 17**. This is a playable alpha with substantial behavior differences from the original. The [parity audit](docs/PARITY.md) tracks the remaining work.

This independent module belongs to the Mystcraft project. Mystcraft uses Forge and ICBM uses Fabric, so their JARs require separate game instances.

## Build and run

From the repository root:

```sh
./scripts/gradle.sh :icbm_port:build
./scripts/gradle.sh :icbm_port:runGametest
./scripts/gradle.sh :icbm_port:runClient
```

Alternatively, use this directory's `./gradlew` (Windows: `gradlew.bat`) with Java 17. Install `build/libs/icbm-fabric-port-0.1.0-alpha.2.jar` and Fabric API on both client and server. The sources JAR and GameTest mod are not runtime dependencies.

## Gameplay

The creative tab and survival recipes expose the implemented content:

- **23 payload families:** condensed, shrapnel, incendiary, debilitation, chemical, anvil, repulsive, attractive, fragmentation, contagious, sonic, breaching, thermobaric, nuclear, EMP, exothermic, endothermic, antigravitational, ender, antimatter, redmatter, colors and smoke. Blocks respond to ignition and redstone; fuse lengths vary by payload. Condensed and breaching blocks activate immediately.
- **Delivery:** payload missiles, ten tier-one grenades and bomblets, 23 bomb carts, direct and ballistic handheld launchers, held-item action missiles, and mixed cluster missiles. Craft a cluster with bomblets to load it; empty clusters cannot fire. Held-item missiles carry tools or blocks through load/unload crafting and recover their casing; supported impact actions include mining, striking and block placement. Item cargo can travel by parachute or balloon. Grenades and carts support dispensers.
- **Launchers:** the original directional launcher, three base tiers and a cruise launcher. Load a missile by hand or inventory, charge with redstone, and fire with a pulse or the screen. Empty-hand use opens the controls; sneak-use unloads ammunition. Tiered vertical bases require adjacent frames. Supports, connectors and remote screens form a bounded local control network. GPS, laser and radio tools set targets or trigger launches.
- **Defense:** radar emits a redstone signal for nearby missiles, powered SAM launchers consume firework rockets to launch guided interceptors, and EMP disables missiles and machines temporarily. Defusers recover compatible explosives. Antidote clears status effects. Concrete variants, reinforced glass, spikes and glass controls provide protection and utilities.
- **Configuration:** `config/icbm_port/payloads.json` is generated at startup. Operators can apply validated changes with `/icbm_payloads reload`. Invalid changes preserve the previous configuration.

See [launcher controls](docs/LAUNCHERS.md), [defense controls](docs/DEFENSE.md), [recipe mappings](docs/RECIPES.md), and [payload behavior](src/main/java/dev/icbm/port/payload/PORTING-NOTES.md) for details. Large effects alter terrain; development should use a test world.

## Current limits

This is not a complete or behavior-identical port. Major remaining work includes upstream blast propagation and persistent radiation/toxicity, full redmatter and flying-block simulation, energy API compatibility, radar/EMP interfaces and multiblocks, complete radio networks, remote chunk flight, original animation/sound replacements, and optional-mod integrations. Targets use a Minecraft animation arc; direct missiles expire after 200 loaded ticks. No flight path force-loads distant chunks. Existing Forge worlds and saved items are not converted.

Models are original JSON using vanilla textures. Upstream artwork is excluded under its separate license. Distinctive art and interactive client/multiplayer playtesting remain unfinished.

## Validation

`build` compiles common and client sources, runs JUnit tests, and produces a remapped JAR. `runGametest` exercises the implementation on a dedicated Fabric server. These tests establish the documented contracts, not full upstream parity. Resource tooling checks model/texture references, registration coverage and recipe reachability against the Minecraft 1.19.2 client JAR. See [VALIDATION.md](docs/VALIDATION.md) for the current results.

## Attribution

Based on ICBM Classic by Built Broken Modding and contributors, pinned to `prod/1.12` commit `9c1d985a375adc2868ff47707b3a636452d0b63a`. MIT code and team-created recipe data are adapted with the [upstream notice](LICENSE-ICBM-CODE.md) packaged in both JARs. Upstream textures, models, sounds and language files are not imported. See [UPSTREAM.md](UPSTREAM.md).
