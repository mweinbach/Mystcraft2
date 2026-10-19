# Workspace instructions

This repository is a collection of independent ports targeting Minecraft 1.19.2.
Use Java 17 and the checked-in Gradle wrapper. Forge 43.5.0 is the default;
`mods/icbm_port` explicitly targets Fabric and uses an independent included
build. Mystcraft supports both Forge and Fabric; its Fabric variant uses Loader
0.16.14 and Fabric API 0.77.0+1.19.2. Respect each port's loader. Do not upgrade
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
  Run ICBM's server tests with `./scripts/gradle.sh :icbm_port:runGametest`.
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
