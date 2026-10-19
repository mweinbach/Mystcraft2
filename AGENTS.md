# Workspace instructions

This repository is a collection of independent ports targeting Minecraft 1.19.2.
Use Java 17 and the checked-in Gradle wrapper. Forge 43.5.0 is the default;
`mods/icbm_port` explicitly targets Fabric and uses an independent included
build. Mystcraft, JurassiCraft and Advanced Genetics support both Forge and
Fabric; their Fabric variants use Loader 0.16.14 and Fabric API 0.77.0+1.19.2. Respect each port's loader. Do not upgrade
Minecraft as part of a port.

- Read the target mod's README, `port.properties`, and upstream attribution.
- Keep mod-specific work under `mods/<mod-id>`. Shared configuration lives in
  `gradle/forge-mod.gradle` for Forge; Fabric ports configure Loom in their own
  build. Individual dependency declarations belong in each mod's `build.gradle`.
- Run `./scripts/setup.sh` if needed, then use `./scripts/gradle.sh` in this cloud
  environment so Java and proxy trust are configured.
- Validate metadata with `python3 scripts/ports.py check`. For tooling changes,
  run `python3 -m unittest discover -s scripts/tests -v`. Build the affected port
  with `./scripts/gradle.sh :<mod-id>:build`; build all ports for shared changes.
  Run ICBM's server tests with `./scripts/gradle.sh :icbm_port:runGametest`
  and Advanced Genetics' with `./scripts/gradle.sh :advancedgenetics:runGameTestServer`
  and `./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest`.
- Each task already has an isolated environment. Use the existing checkout;
  do not create a worktree unless requested.
- Do not describe a scaffold as a working gameplay port. Keep status, provenance,
  and known gaps current. Preserve upstream licenses and attribution.
- Do not accept the Minecraft EULA on the user's behalf.

## Mystcraft loader support

Keep gameplay, registry definitions, recipes, assets and destination NBT shared
under `mods/mystcraft/src/main`. Forge adapters belong in `src/forge`, Fabric
adapters in `src/fabric`. Do not implement separate gameplay for the loaders.
After Mystcraft changes, build both variants:

- `./scripts/gradle.sh :mystcraft:build`
- `./scripts/gradle.sh -p mods/mystcraft/fabric build`
- `python3 mods/mystcraft/scripts/check_loader_parity.py`

The Fabric build intentionally isolates Loom from the root ForgeGradle build.
Server/client loader combinations must match; cross-loader multiplayer and world
migration are not promised. Keep classic 1.12 parity gaps explicit.

## JurassiCraft loader support

JurassiCraft follows the same layout: gameplay, registries, recipes, loot,
worldgen, assets and NBT live in `mods/jurassicraft/src/main`; Forge adapters in
`src/forge`, Fabric adapters in `src/fabric`, and the Loom build in
`mods/jurassicraft/fabric`. Shared code reaches loader services only through
`Platform`/`ClientPlatform`. Where loaders differed, keep the variant matching
upstream 1.12.2, otherwise the union, and record it in
`mods/jurassicraft/PARITY.md`. GameTests and their fixtures live in
`src/gametestCommon`, `src/gametestForge` and `src/gametestFabric` and must stay
out of production JARs. The Fabric mixin package may contain only mixins.
After JurassiCraft changes, run:

- `./scripts/gradle.sh :jurassicraft:build`
- `./scripts/gradle.sh -p mods/jurassicraft/fabric build`
- `python3 mods/jurassicraft/scripts/check_loader_parity.py`
- `python3 -m unittest discover -s mods/jurassicraft/tools/tests`
- `./scripts/gradle.sh :jurassicraft:runGameTestServer`
- `./scripts/gradle.sh -p mods/jurassicraft/fabric runGametest -PwithJade -PwithEmi`

## Advanced Genetics loader support

Advanced Genetics uses the same layout: gameplay, registries, recipes, assets and
data live in `mods/advancedgenetics/src/main`; Forge adapters in `src/forge`,
Fabric adapters in `src/fabric`, and the Loom build in `mods/advancedgenetics/fabric`.
Shared code reaches loader services only through `Platform`. Fabric gene hooks
must fire where Forge fires the matching event. Shared GameTests live in
`src/gametestCommon` and run on both loaders through each loader's `TestPlatform`.
They must stay out of production JARs. Mixin packages may contain only mixins.
After Advanced Genetics changes, run:

- `./scripts/gradle.sh :advancedgenetics:build`
- `./scripts/gradle.sh -p mods/advancedgenetics/fabric build`
- `python3 mods/advancedgenetics/scripts/check_loader_parity.py`
- `./scripts/gradle.sh :advancedgenetics:runGameTestServer`
- `./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest`
