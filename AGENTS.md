# Workspace instructions

This repository is a collection of independent Forge ports targeting Minecraft
1.19.2. Use Java 17, Forge 43.5.0, and the checked-in Gradle wrapper. Do not
upgrade the Minecraft version as part of a port.

- Read the target mod's README, `port.properties`, and upstream attribution.
- Keep mod-specific work under `mods/<mod-id>`. Shared configuration lives in
  `gradle/forge-mod.gradle`; individual dependency declarations belong in each
  mod's `build.gradle`.
- Run `./scripts/setup.sh` if needed, then use `./scripts/gradle.sh` in this cloud
  environment so Java and proxy trust are configured.
- Validate metadata with `python3 scripts/ports.py check`. For tooling changes,
  run `python3 -m unittest discover -s scripts/tests -v`. Build the affected port
  with `./scripts/gradle.sh :<mod-id>:build`; build all ports for shared changes.
- Each task already has an isolated environment. Use the existing checkout;
  do not create a worktree unless requested.
- Do not describe a scaffold as a working gameplay port. Keep status, provenance,
  and known gaps current. Preserve upstream licenses and attribution.
- Do not accept the Minecraft EULA on the user's behalf.
