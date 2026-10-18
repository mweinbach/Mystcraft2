# Upstream attribution

Source: [BuiltBrokenModding/ICBM-Classic](https://github.com/BuiltBrokenModding/ICBM-Classic)

Baseline: `prod/1.12`, revision `9c1d985a375adc2868ff47707b3a636452d0b63a`.
The source targets Minecraft 1.12.2 / Forge. This module targets Minecraft 1.19.2 /
Fabric and is an unofficial recreation with documented adaptation boundaries.

`src/main/java/dev/icbm/port/core/DirectFlight.java` adapts upstream
`src/main/java/icbm/classic/content/missile/logic/flight/DirectFlightLogic.java`.
The upstream MIT code notice is preserved in [LICENSE-ICBM-CODE.md](LICENSE-ICBM-CODE.md)
and packaged in both binary and source JARs. That notice's relative asset-license
link describes the original upstream layout; the upstream asset terms can be read
[at the pinned revision](https://github.com/BuiltBrokenModding/ICBM-Classic/blob/9c1d985a375adc2868ff47707b3a636452d0b63a/src/main/resources/assets/icbmclassic/LICENSE.md).

Upstream textures, models, sounds, and language files are not imported. New model
JSON references vanilla Minecraft textures. MIT team-created recipe data is adapted
with per-file mappings in `tools/upstream_recipe_mapping.json`. Gameplay systems
are reconstructed from the source families listed in `docs/PARITY.md`. No Mystcraft code or assets are
included in this independently built module. The ICBM code license is distinct
from the repository's root Mystcraft license.

The nested Team Reborn Energy API 2.3.0 is MIT licensed and retains its license
inside its bundled JAR. Optional JEI 11.8.1.1034 is a separate runtime dependency;
its code and assets are not redistributed by this module.

See [PORTING.md](PORTING.md) for implementation status and behavior differences.
