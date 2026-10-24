# Workspace instructions

This repository is a collection of independent ports targeting Minecraft 1.19.2.
Use Java 17 and the checked-in Gradle wrapper. Do not upgrade Minecraft as part of a port.

**Fabric first.** Fabric is the primary loader for every port in this workspace, and Forge support is
optional. Build, test and verify on Fabric first; nothing is done until it works on Fabric. Keep a
port's Forge variant working where that is cheap, but a Forge-only difficulty never blocks or reshapes
Fabric work: ship it on Fabric, record the Forge gap in the port's README or PARITY file, and list any
Fabric-only GameTest in `mods/<mod>/gametest-loader-only.txt` with its reason. New ports start on
Fabric. In the command lists below, run the Fabric commands first.

Fabric variants use Loader 0.16.14 and Fabric API 0.77.0+1.19.2 in independent Loom builds
(`mods/<mod>/fabric`, or the port's own build for Fabric-only ports such as `mods/icbm_port`). The
root Gradle build compiles the optional Forge variants with Forge 43.5.0.

- Read the target mod's README, `port.properties`, and upstream attribution.
- Keep mod-specific work under `mods/<mod-id>`. Shared configuration lives in
  `gradle/forge-mod.gradle` for Forge; Fabric ports configure Loom in their own
  build. Individual dependency declarations belong in each mod's `build.gradle`.
- Run `./scripts/setup.sh` if needed, then use `./scripts/gradle.sh` in this cloud
  environment so Java and proxy trust are configured.
- Each port's gate is `mods/<mod>/scripts/check_loader_parity.py`; use the shared
  `scripts/loader_parity.py` helpers for the common artifact checks instead of
  copying them between ports. Every gate runs `assert_built`, `check_descriptors`
  with the license, `check_artifact_pair` (each jar's mixin refmaps in that same
  jar, entrypoint classes, `depends` floors against the Fabric build, access
  widener with at least one entry, nested libraries, GameTest fixtures) for every
  jar pair it owns, and `check_loader_gametests`. A GameTest that only one loader
  registers must be listed in `mods/<mod>/gametest-loader-only.txt` with a reason;
  move tests to shared code and delete their lines rather than adding new ones.
  The gates run on Python 3.9+: `tomllib` on 3.11+, otherwise `tomli` or the copy
  `./scripts/setup.sh` installs in `.local/tomli`. CI uses 3.12.
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
migration are not promised. Keep classic 1.12 parity gaps explicit in `mods/mystcraft/PARITY.md`.

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
must fire where Forge fires the matching event. Record loader services and known
gaps in `mods/advancedgenetics/PARITY.md`. Shared GameTests live in
`src/gametestCommon` and run on both loaders through each loader's `TestPlatform`.
They must stay out of production JARs. Mixin packages may contain only mixins.
After Advanced Genetics changes, run:

- `./scripts/gradle.sh :advancedgenetics:build`
- `./scripts/gradle.sh -p mods/advancedgenetics/fabric build`
- `python3 mods/advancedgenetics/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :advancedgenetics:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/advancedgenetics/fabric runGametest -PwithItemAlchemy`

## AgriCraft loader support

AgriCraft (`mods/agricraft`) is a backport of upstream AgriCraft's `1.20.1` branch
(4.0.6, MIT) with upstream's packages and mod id. Upstream's `common/` module is
`src/main`, `forge/` is `src/forge` and `fabric/` is `src/fabric`; the Loom build is
`mods/agricraft/fabric`. Keep upstream code, assets and data; record every 1.19.2
change and gap in `mods/agricraft/PARITY.md` and provenance in `NOTICE.md`. Plants,
weeds, soils, mutations and fertilizers load through `AgriDataRegistries` (no
datapack registries on 1.19.2). Shared GameTests live in `src/gametestCommon`, run by
`ForgeGameTests` and the Fabric `fabric-gametest` entrypoints, and stay out of
production JARs. The mixin package may contain only mixins. After AgriCraft
changes, run:

- `./scripts/gradle.sh :agricraft:build`
- `./scripts/gradle.sh -p mods/agricraft/fabric build`
- `python3 mods/agricraft/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :agricraft:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/agricraft/fabric runGametest -PwithItemAlchemy`

## Gridwright loader support

Gridwright (`mods/gridwright`) is a clean-room, Extra Utilities 2–style mod. Never
read, copy, translate or paraphrase Extra Utilities 2 code, art, sounds or lang
files (all rights reserved), nor unlicensed rewrites of it; work only from public
behaviour descriptions and record sources in `mods/gridwright/NOTICE.md`. It uses
the same layout as Advanced Genetics: shared code in `src/main` reaches loader
services only through `Platform`; Forge adapters in `src/forge`, Fabric adapters in
`src/fabric`, the Loom build in `mods/gridwright/fabric`. Shared GameTests live in
`src/gametestCommon`. Grid Power stays separate from Forge Energy / Team Reborn
Energy except through a deliberate converter block. After Gridwright changes, run:

- `./scripts/gradle.sh :gridwright:build`
- `./scripts/gradle.sh -p mods/gridwright/fabric build`
- `python3 mods/gridwright/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :gridwright:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/gridwright/fabric runGametest -PwithItemAlchemy`

## BuildCraft loader support

BuildCraft uses the same layout: gameplay, registries, recipes, assets and EMC
data in `mods/buildcraft/src/main`; Forge adapters in `src/forge`, Fabric
adapters in `src/fabric`, and the Loom build in `mods/buildcraft/fabric`. Power
crosses blocks only as Forge Energy or Team Reborn Energy at 1 MJ = 10 units;
items and fluids use Forge capabilities or the Fabric Transfer API. Shared
GameTest bodies live in `src/gametestCommon/.../BCTests.java` and run on both
loaders. Keep upstream gaps in `mods/buildcraft/docs/PARITY.md`. After
BuildCraft changes, run:

- `./scripts/gradle.sh :buildcraft:build`
- `./scripts/gradle.sh -p mods/buildcraft/fabric build`
- `python3 mods/buildcraft/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :buildcraft:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/buildcraft/fabric runGametest -PwithItemAlchemy`

## Mekanism loader support

Mekanism uses the same layout: gameplay, registries, recipes, assets and EMC data
in `mods/mekanism/src/main`; Forge metadata in `src/forge`, Fabric adapters in
`src/fabric`, and the Loom build in `mods/mekanism/fabric`. Fabric rewrites
`net.minecraftforge` references in the shared sources onto `ports.mekanism.lib`.
Forge methods that Porting Lib also adds to vanilla classes are named
`mekanism$<name>` on Fabric (`mods/mekanism/parity/20-porting-lib.md`); the
Fabric `build` fails if a new one clashes.
After Mekanism changes, run:

- `./scripts/gradle.sh :mekanism:build`
- `./scripts/gradle.sh -p mods/mekanism/fabric build`
- `python3 mods/mekanism/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :mekanism:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/mekanism/fabric runGametest -PwithItemAlchemy`
- `./scripts/gradle.sh -p mods/mekanism/fabric runGametest -PwithPortingLib -PwithCraftTweaker`

## ProjectE loader support

ProjectE (`mods/projecte`) is upstream sinkillerj/ProjectE `mc1.19.x` (PE1.1.0,
MIT) on Forge and Fabric, with mod id `projecte`. Use only the official
repository; never read or copy Fabricated Exchange or other remakes. It uses the
Mekanism layout: upstream sources in `src/main`, Forge metadata and the Curios /
The One Probe integrations in `src/forge`, the Forge API mirror
(`ports.projecte.lib`), mixins and Trinkets replacement in `src/fabric`, and the
Loom build in `mods/projecte/fabric`. Every Forge method injected into a vanilla
class whose descriptor has only vanilla or JDK types is named `projecte$<name>`
(`src/fabric/vanilla-extensions.txt`); mixins must not `@Redirect` or
`@Overwrite`, and wrapped calls run ProjectE hooks only for ProjectE objects
(`PEHooks.ours`), so the layer coexists with the Mekanism port and Porting Lib.
Record upstream edits in `mods/projecte/UPSTREAM.md` and verification status in
`mods/projecte/PARITY.md`. After ProjectE changes, run:

- `./scripts/gradle.sh :projecte:build`
- `./scripts/gradle.sh -p mods/projecte/fabric build` (includes `checkFabricClashes`)
- `python3 mods/projecte/scripts/check_loader_parity.py`
- `python3 mods/projecte/scripts/generate_fabric_tags.py --check`
- `./scripts/gradle.sh :projecte:runGameTestServer`
- `./scripts/gradle.sh -p mods/projecte/fabric runGametest`
- `./scripts/gradle.sh -p mods/projecte/fabric runGametest -PwithMekanism -PwithPortingLib`

## Extreme Reactors loader support

Extreme Reactors uses the same layout: Extreme Reactors 2's gameplay, assets and
data live in `mods/extremereactors/src/main`; Forge adapters in `src/forge`,
Fabric adapters in `src/fabric`, and the Loom build in
`mods/extremereactors/fabric`. ZeroCore 2 is all rights reserved except its MIT
multiblock package: never read, copy or translate other ZeroCore 2 code or
assets, and implement what Extreme Reactors needs in `ports.extremereactors.corelib`
from its call sites, observable behavior and the vanilla, Forge and Fabric APIs.
Record changes to upstream files in `mods/extremereactors/UPSTREAM.md` and gaps
in `mods/extremereactors/PARITY.md`. Shared GameTest bodies live in
`src/gametestCommon/.../ExtremeReactorsGameTests.java` and every one must be
forwarded by both loaders. After Extreme Reactors changes, run:

- `./scripts/gradle.sh :extremereactors:build`
- `./scripts/gradle.sh -p mods/extremereactors/fabric build`
- `python3 mods/extremereactors/scripts/check_loader_parity.py`
- `python3 scripts/emc.py check`
- `./scripts/gradle.sh :extremereactors:runGameTestServer -PwithProjectE`
- `./scripts/gradle.sh -p mods/extremereactors/fabric runGametest -PwithItemAlchemy`

## Fluid Physics support

`mods/fluidphysics` is a **Fabric-only library**, not a gameplay port. It gives
modded fluids the part of Forge's `FluidType` behaviour vanilla applies only to
water and lava: swimming, drowning, current pushing, the fall distance modifier,
extinguishing, boat support and path types. Forge already has `FluidType`, so
there is no Forge variant and no parity gate. The Extreme Reactors and Mekanism
Fabric builds consume it through `include(...)`. Keep its limitations in
`mods/fluidphysics/README.md` current and test it with:

- `./scripts/gradle.sh -p mods/fluidphysics build`
- `./scripts/gradle.sh -p mods/fluidphysics runGametest`

## MetalRender

`mods/metalrender` is a **clean-room**, MIT-licensed Fabric renderer that draws
terrain with Apple Metal and composites it into Minecraft's OpenGL frame through
`IOSurface`. `https://github.com/webblepebbles/MetalRender` is a feature reference
only: never copy its code, shaders or assets. The mod must stay inactive (vanilla
OpenGL terrain) on Linux, Windows and dedicated servers. The committed natives in
`src/main/resources/natives/macos/` are rebuilt by `compileNative` on macOS; commit
them whenever `src/main/native` changes. Keep the verified/unverified lists in
`mods/metalrender/README.md` current and test with:

- `./scripts/gradle.sh -p mods/metalrender build` (JUnit, JNI hardware tests and `nativeSelfTest` on macOS)
- `./scripts/gradle.sh -p mods/metalrender runGametest`
- `./scripts/gradle.sh -p mods/metalrender runAutotestClient` (macOS, opens a game window; writes `build/autotest-client/autotest/report.txt`)
