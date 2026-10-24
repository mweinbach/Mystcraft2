# Upstream attribution

Each JAR carries this file as `META-INF/ProjectE-UPSTREAM.md`. Notices and credits are in [NOTICE.md](NOTICE.md).

| Part | Source | Revision | Terms | Where it lives |
| --- | --- | --- | --- | --- |
| ProjectE (API, code, assets, generated data, JUnit tests, data generators) | [sinkillerj/ProjectE](https://github.com/sinkillerj/ProjectE), branch `mc1.19.x`, tag `PE1.1.0` | `97d51a5a675d46c2106682de21c3a364d14e6a6c` | MIT, Copyright (c) 2020 Sin Tachikawa, [LICENSE](LICENSE) | `src/main`, `src/test`, `src/datagen`, `src/forge/resources/META-INF` |
| Forge API mirror, Fabric adapters, mixins, GameTests, tooling | Written for this port; the Forge API mirror adapted from this repository's Mekanism port | this repository | MIT, [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt) | `src/fabric`, `src/gametest*`, `fabric/`, `scripts/`, `build.gradle` |
| Forge's own `forge:` tag values (Fabric jar only), merged into the Fabric tag bridges | Minecraft Forge universal jar | `1.19.2-43.5.0` | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt) | `scripts/forge-tags.json`, `src/fabric/resources/data/forge/tags` |

PE1.1.0 is the last ProjectE release for Minecraft 1.19.2. Only the official repository was used.

## What was imported

Upstream's tree was copied unchanged in commit `c1ad5169`:

- `src/api/java` and `src/main/java`, merged into `src/main/java`.
- `src/main/resources` and the checked-in datagen output `src/datagen/generated/projecte`, merged into
  `src/main/resources`. Upstream's `META-INF` (`mods.toml`, `accesstransformer.cfg`) moved to `src/forge/resources`.
- The data generators, in `src/datagen/java`. They run only through `runData` and never enter a JAR.
- The JUnit tests (`GraphMapperTest`, `HiddenFractionSpecificTest`, `SerializationTest`, `CustomConversionMapperTest`
  and the API tests), in `src/test/java`. They run in `test` on both loaders.

## Edits to upstream files

Shared code stays upstream's. The edits, each listed here:

| File | Change | Why |
| --- | --- | --- |
| `integration/IntegrationHelper`, `integration/curios/*`, `integration/top/*` | Moved unchanged from `src/main` to `src/forge`; Fabric has its own `IntegrationHelper` and Trinkets-backed `curios` classes in `src/fabric` | Curios and The One Probe have no Fabric 1.19.2 builds |
| `integration/crafttweaker/nss/CrTNSSResolver`, `ExpandCrTTypes` | The `IFluidStack` import, `fromFluid(IFluidStack)` and the `IFluidStackExpansion` sit in `// region forge-only` blocks | CraftTweaker for Fabric 1.19.2 has no fluid stack API |
| `integration/jei/world_transmute/WorldTransmuteRecipeCategory` | `addIngredient(ForgeTypes.FLUID_STACK, stack)` became `addFluidStack(fluid, amount, tag)` | JEI's common API, which both loaders have; same slot contents |
| `src/forge/resources/META-INF/mods.toml` | Upstream's Gradle tokens filled in (`[43,)`, `[1.19.2]`, `[43.1.3,)`; the version from `port.properties`) and the `updateJSONURL` removed | The repository's shared Forge build expands only `mod_version`; upstream's update feed would offer upstream's jar |

`// region forge-only` ... `// endregion forge-only` blocks are blanked (markers included) in the Fabric copy, so line
numbers still match the shared source.

## How the Fabric build compiles the same sources

`fabric/build.gradle` copies `src/main/java`, `src/test/java` and `src/gametestCommon/java` into
`build/generated/sources/fabricShared` and rewrites them line for line; the checked-in sources are never changed.

- `net.minecraftforge.*` becomes `ports.projecte.lib.*`, the port's Forge API mirror.
- Constructors, static helpers and fields Forge adds to vanilla classes (`new KeyMapping`, `ItemTags.create`,
  `ItemBlockRenderTypes.setRenderLayer`, `restoringBlockSnapshots`, `CreativeModeTab`, the entity-type builder
  methods, ...) become calls to `ports.projecte.lib.patched` or vanilla equivalents. The list is
  `forgeOnlyVanillaMembers` in `fabric/build.gradle`.
- Every method Forge adds to a vanilla class is supplied on Fabric by an injected interface default named
  `projecte$<name>`, and the shared sources' uses are renamed to match. The names are in
  [src/fabric/vanilla-extensions.txt](src/fabric/vanilla-extensions.txt) (a path regex limits a name to the files
  that mean Forge's method). Methods whose descriptors contain the mirror's own types (`getCapability`,
  `canPerformAction`, `initCapabilities`, `getToolModifiedState`, `getFluidType`, ...) keep their Forge names, since
  no other layer can declare the same descriptor.

### Coexisting with other Forge-to-Fabric layers

A Fabric pack such as Tekxit 4 also carries the Mekanism port's Forge API layer and Porting Lib (nested by Create and
many other mods). Two layers that add the same method to a vanilla class make the JVM refuse the call; two
`@Redirect`s of one call, or an `@Overwrite` of a patched method, fail when mixins apply. So:

- Every injected default with a vanilla- or JDK-only descriptor is `projecte$`-prefixed (above).
- Fabric API's `FabricItem` hooks are answered only on the ProjectE classes that override the Forge hook:
  `ItemPEMixin` (`allowNbtUpdateAnimation`), `ChargeAttributesMixin` (`getAttributeModifiers`) and
  `CraftingRemainderMixin` (`getRecipeRemainder`). The `projecte$getCraftingRemainingItem` default defers to
  `FabricItem.getRecipeRemainder`, so ProjectE's EMC mapper sees remainders other mods declare.
- The mixins use injects and `WrapOperation`s, never `@Redirect` or `@Overwrite`. Wrapped calls run ProjectE's hook
  only for ProjectE's own objects (`PEHooks.ours`) and call the original for everything else.
- Only the hooks ProjectE overrides or listens to are wired. Forge hooks no ProjectE class uses (model loaders,
  arrow, bow and elytra hooks, piglin and enderman checks, portal and light hooks, key-binding conflict GUI, ...) are
  not ported.

`./scripts/gradle.sh -p mods/projecte/fabric checkFabricClashes` (part of `check`) runs `scripts/fabric_clashes.py`
against the Porting Lib versions Flux Networks 1.19.2-7.2.1.16 and Create 0.5.1-i build 1630 nest;
`-PclashJars=a.jar,b.jar` adds more jars. Against the Mekanism port's four Fabric jars (`10.3.9-port.1`, as
installed in the Tekxit test instance) and Create 0.5.1-i build 1630 it reports 0 method and 0 patch clashes.
`runGametest -PwithMekanism` and `runGametest -PwithPortingLib` run the GameTests with those layers loaded.

### Forge-only and Fabric-only classes

| Forge jar only | Why |
| --- | --- |
| `integration/top/*` | The One Probe has no Fabric build |
| `integration/crafttweaker/nss/ExpandCrTTypes$IFluidStackExpansion` | CraftTweaker Fabric has no fluid stacks |
| `moze_intel/projecte/shaded/org/apache/commons/math3/**` | Upstream relocates Commons Math into the jar; Fabric nests the library instead |

| Fabric jar only | Why |
| --- | --- |
| `ports/projecte/**` | The Forge API mirror, Fabric entrypoints, networking, events and mixins |
| Inner classes of `integration/curios/*` | The Trinkets-backed replacements |

## Tests not taken from upstream

Upstream has no GameTests. The shared GameTests in `src/gametestCommon` were written for this port; the Fabric-only
ones in `src/gametestFabric` cover the Transfer API adapters. They are a separate development-only mod and never
enter a JAR.
