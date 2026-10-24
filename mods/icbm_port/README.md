# ICBM Fabric Port

An unofficial ICBM Classic recreation for **Minecraft 1.19.2**, **Fabric Loader 0.16.14+**, **Fabric API 0.77.0+1.19.2**, and **Java 17**, maintained inside the Mystcraft project. This release implements the major gameplay systems; exact source differences and platform limits are recorded in the [parity audit](docs/PARITY.md).

Mystcraft uses Forge and ICBM uses Fabric, so their JARs require separate game instances.

## Build and run

From the repository root:

```sh
./scripts/gradle.sh :icbm_port:build
./scripts/gradle.sh :icbm_port:runGametest
./scripts/gradle.sh :icbm_port:runMaterialGametest
./scripts/gradle.sh :icbm_port:runClient
# Optional recipe browsers during development:
./scripts/gradle.sh :icbm_port:runClient -PwithEmi
./scripts/gradle.sh :icbm_port:runClient -PwithJei
./scripts/gradle.sh :icbm_port:runClient -PwithRei
```

Alternatively, use this directory's `./gradlew` (Windows: `gradlew.bat`) with Java 17. Install `build/libs/icbm-fabric-port-0.2.0-alpha.4.jar` and Fabric API on both client and server. Team Reborn Energy API 2.3.0 is bundled. EMI 1.1.24, JEI 11.8.1.1034 and REI 9.2.x are optional and installed separately. Tekxit 4 already supplies EMI; see the [Tekxit integration guide](docs/TEKXIT.md). The sources JAR and GameTest mod are not runtime dependencies.

## Gameplay

- **23 payload families:** condensed, shrapnel, incendiary, debilitation, chemical, anvil, repulsive, attractive, fragmentation, contagious, sonic, breaching, thermobaric, nuclear, EMP, exothermic, endothermic, antigravitational, ender, antimatter, redmatter, colors and smoke. Blocks respond to ignition and redstone. Terrain effects use saved, bounded work queues and resistance-aware rays; gas diffuses through open spaces. Chemical, viral and radiation exposure persists, nuclear blasts contaminate terrain, antigravity lifts actual blocks, antimatter erases a radius-275 sphere in a single tick, and a moving red matter singularity grows a radius-300 spherical event horizon that drags in and kills anything not in creative or spectator mode.
- **Delivery:** payload missiles, ten tier-one grenades and bomblets, 23 bomb carts, direct and ballistic handheld launchers, mixed cluster missiles and held-item action missiles. Load and unload cargo through crafting. Player-owned action missiles can use tools and items through their normal interactions. Parachutes and balloons carry items, blocks, entities or projectiles. Grenades and carts support dispensers.
- **Launchers and flight:** directional prototype, three base tiers and cruise launcher; inventories, batteries, external Fabric energy, frames, supports, connectors and remote screens. GPS, laser and radio tools configure targets and launches. Targeted flight has warmup, climb, arc/ascent, saved long-range travel and descending reentry. Bounded temporary tickets allow distant arrival. Ender payload destinations can cross dimensions.
- **Defense:** powered radar with incoming-flight detection and radio channels, SAM ammunition and guided interceptors, configurable EMP stations with vertical range extenders, and validated machine screens. Radio relays connect loaded receivers. Defusers, antidote, concrete variants, reinforced glass, spikes and glass controls provide utilities and protection.
- **Integration:** shared material tags for cross-mod recipe substitutions, reload-aware material fallbacks, transactional rechargeable batteries, executable extension API with cancellation/source events, operator blast/cleanup commands, optional EMI/JEI/REI displays for NBT-bearing load/unload recipes, and the upstream Mekanism atomic-alloy explosive recipes when Mekanism is installed.

See [launchers](docs/LAUNCHERS.md), [delivery](docs/DELIVERY.md), [defense](docs/DEFENSE.md), [exposure](docs/EXPOSURE.md), [energy](docs/ENERGY.md), [API and commands](docs/API.md), [recipes](docs/RECIPES.md), [OreDictionary compatibility](docs/ORE_DICTIONARY.md), and [payload behavior](src/main/java/dev/icbm/port/payload/PORTING-NOTES.md).

## Changes since alpha.4 (deliberate departures from upstream)

Antimatter no longer sweeps a cube layer by layer: it deletes a solid sphere, radius 275 by default (5× the former 55), in the detonation tick by writing chunk sections directly. Nothing drops and no neighbour updates run; sky light is written straight into the light arrays and chunks are resent to watching players. Measured cost for r=275 on a development machine: 28 million blocks in one tick of about 0.4–0.7 s, with crater lighting finished on the light thread within about 3 s.

Red matter no longer sucks in a square. Its event horizon is a sphere that grows one block per tick, within a 12 ms per-tick budget, up to 300 by default, larger than antimatter. It reaches 300 in about a minute at a mean server tick of about 7 ms. A true-sphere gravity field drags entities in, players included. Anything that reaches the core dies from a dedicated damage source ("was consumed by red matter"). Creative and spectator players are immune, following vanilla convention. Existing `payloads.json` files that still carry the old defaults (55/12/2,400 ticks) migrate automatically; customized values are kept.

Explosive blocks use flat, TNT-style faces: a distinct side, top and shared bottom per payload, with matching item icons. They replace the earlier casings, whose painted bevels faked depth on a plain cube. See [before/after](docs/images/explosives-compare.png) and [payload behavior](src/main/java/dev/icbm/port/payload/PORTING-NOTES.md).

## Alpha.4 changes

Redmatter now expands its terrain search beyond cleared inner shells and draws captured blocks into the core instead of letting them settle back into the crater. Its search progress and captured-block binding survive saves. Regression tests cover placed-block activation, distant terrain consumption and antimatter neutralization.

An original industrial visual layer adds glyph payload labels, six missile bodies, explosive casings (flat TNT-style faces since alpha.4), launcher tiers, defense hardware and major tools. Nuclear, chemical, incendiary, and redmatter missiles do not share a silhouette. Detonation color, shape, and vanilla sound come from the same payload record as the label. Primed blocks pulse and flash; missile exhaust follows synchronized flight/EMP state; radar, SAM and EMP hardware animate from server state. Redmatter has an animated dark core and accretion discs. Detonations send bounded client shockwave/particle effects and use vanilla sounds. These renderers use ordinary Minecraft layers without requiring a custom shader. See [asset provenance](docs/ASSET_PROVENANCE.md).

Alpha.4 release validation passed 16 JUnit tests, the 92 gameplay GameTests of that time, the five-reload material suite and the resource audit. The current tree runs 21 JUnit tests and 109 gameplay GameTests (one is a benchmark that only measures when run with `-PicbmBenchmark`). The final alpha.4 JAR passed the full Tekxit client checks, six actual-pack integration cases and live EMI checks before and after reload, with direct inspection of redmatter and selected models/animations. Earlier alpha.3 results remain historical evidence. See [validation](docs/VALIDATION.md).

## Configuration

Startup generates payload and battery settings under `config/icbm_port/`, plus `config/icbm-flight.json`, `config/icbm_port-exposure.json` and `config/icbm-port-delivery.properties`. Payload and battery settings support validated operator reloads with `/icbm_payloads reload` and `/icbm energy reload`; other settings apply at restart. Subsystem documents explain limits and defaults.

## Compatibility and release status

This remains an alpha while multiplayer and survival balance receive broader testing. Gameplay is adapted to modern Fabric APIs and bounded server work; it is not a binary-compatible Forge port. Legacy worlds, items and configuration files are not converted. Original models and animations replace parts of the presentation, but do not reproduce upstream rendering, shaders or audio. Building blocks and cargo materials may reference vanilla block textures. Material icons and bomb-cart inventory models now use original art. All radio commands, ownership/faction integration and optional Forge-only integrations are not reproduced. Upstream artwork is excluded by its separate license; no upstream artist assets were copied. Visual readability, multiplayer presentation, survival balance and the overall play experience still need broader testing.

Every item has an EMC value derived from its recipe or set explicitly, shipped as data for Item Alchemy (the Fabric EMC mod) and in ProjectE's format. The nuclear, antimatter and red matter tiers are valued at their cheapest recipe, including the atomic-alloy and uranium recipes that load with Mekanism (priced at Mekanism's own shipped values), so crafting and burning them never gains EMC with or without Mekanism. Ordinary recipes are also priced with the items Mekanism adds to ICBM's material tags (its sulfur dust in the battery recipe, for instance), and the cheaper path sets the value. For ProjectE, the data also values the wither skeleton skull at a third of a nether star. `./scripts/gradle.sh :icbm_port:runGametest -PwithItemAlchemy` checks the values against Item Alchemy. See [emc/README.md](emc/README.md) and [docs/emc.md](../../docs/emc.md).

[VALIDATION.md](docs/VALIDATION.md) records build, server, resource and client checks. These establish implemented behavior, not exact upstream equivalence.

## Attribution

Based on ICBM Classic by Built Broken Modding and contributors, pinned to `prod/1.12` commit `9c1d985a375adc2868ff47707b3a636452d0b63a`. MIT code and team-created recipe data are adapted with the [upstream notice](LICENSE-ICBM-CODE.md) packaged in both JARs. Upstream textures, models, sounds and language files are not imported. See [UPSTREAM.md](UPSTREAM.md).
