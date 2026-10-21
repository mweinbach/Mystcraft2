# Upstream attribution

Each JAR carries this file as `META-INF/Mekanism-UPSTREAM.md` and the files in [licenses/](licenses/) under
`META-INF/licenses/mekanism/`.

| Part | Source | Revision | Terms | Where it lives |
| --- | --- | --- | --- | --- |
| Mekanism (API, core code, assets, generated data) | [mekanism/Mekanism](https://github.com/mekanism/Mekanism), branch `1.19.x`, tag `v1.19.2-10.3.9.13` | `71e96905ef7ea47f0b65c78c1a9ac5e2bdabd04d` (2023-07-15) | MIT, Copyright (c) 2017-2023 Aidan C. Brady, [LICENSE](LICENSE) | `src/main/java/mekanism`, `src/main/resources`, `src/test/java` |
| Forge API mirror, loader adapters, tests, tooling | Written for this port | this repository | MIT, [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt) | `src/forge`, `src/fabric`, `fabric/`, `build.gradle` |
| Forge bucket models and fluid masks (Fabric jar only, unmodified) | Minecraft Forge universal jar | `1.19.2-43.5.0` | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt) | `src/fabric/resources/assets/forge` |
| Forge's own `forge:` tag values (Fabric jar only), merged into the Fabric tag bridges | Minecraft Forge universal jar | `1.19.2-43.5.0` | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt) | `scripts/forge-tags.json`, `src/fabric/resources/data/forge/tags` |

`v1.19.2-10.3.9.13` is the last Mekanism release for Minecraft 1.19.2. Only the official repository was used.

## What was imported

Copied unchanged, then edited as listed below:

- `src/api/java` and `src/main/java` (merged into `src/main/java`), `src/main/resources`, the checked-in datagen
  output `src/datagen/generated/mekanism` (merged into `src/main/resources`) and the JUnit tests in `src/test`.
- `mekanism.client.jei.MekanismJEIRecipeType`, `mekanism.client.jei.interfaces` and `mekanism.client.jei.recipe`.
  They do not use the JEI API; GUIs use them for recipe-viewer click areas and ghost slots.

Not imported: the JEI plugin, CraftTweaker, ComputerCraft, OpenComputers 2, Curios, Wildfire's Gender Mod,
The One Probe/Jade/WTHIT, ProjectE and Flux Networks integrations, the datagen sources, the Defense module and the
Blockbench/Blender model sources.

## The Forge API mirror

Upstream Mekanism is a Forge mod, and the shared sources keep their `net.minecraftforge` references; the Forge
build compiles them against Forge itself. The Fabric build (`fabric/build.gradle`) copies them to
`fabric/build/generated/sources/fabricShared` and rewrites, line for line, every `net.minecraftforge.` reference to
`ports.mekanism.lib.` and the few uses of Forge-only constructors and static methods on vanilla classes to the
port's equivalents in `ports.mekanism.lib.patched`. The rule list is `forgeOnlyVanillaMembers` in that build file.

`ports.mekanism.lib` (in `src/fabric/java`) reimplements the subset of Forge API shapes Mekanism uses: fluid stacks,
capabilities and lazy optionals, item/fluid/energy handlers, registries and deferred registers, config specs, the
event bus and the events Mekanism listens to, networking channels, client model and render hooks, and the methods
Forge adds to vanilla classes (injected interfaces in `lib.common.extensions` and `lib.client.extensions`, applied by
the mixins in `ports.mekanism.fabric.mixin`). It was written from the API shapes Mekanism calls, not copied from
Forge. Fabric loader services sit behind `ports.mekanism.platform.Platform`.

## Hand edits to upstream files

Every edit beyond the scripted import rewrite:

| File | Edit | Reason |
| --- | --- | --- |
| `mekanism/api/MekanismAPI.java` | Removed `getJeiHelper()` and the `mekanism.api.integration.jei` package | JEI integration not ported |
| `mekanism/common/Mekanism.java` | Removed the CraftTweaker content registration block | CraftTweaker integration not ported |
| `mekanism/common/integration/MekanismHooks.java` | Removed Curios, ComputerCraft, OC2, ProjectE, TOP, Flux Networks, Wildfire Gender and Recipe Stages detection and hooks; their `*Loaded` flags stay `false` | Integrations not ported |
| `mekanism/common/integration/computer/ComputerCapabilityHelper.java` | `addComputerCapabilities` adds nothing | ComputerCraft and OC2 not ported |
| `mekanism/common/integration/energy/EnergyCompatUtils.java` | Removed the Flux Networks energy compat | Flux Networks not ported |
| `mekanism/client/key/MekanismKeyHandler.java`, `client/render/hud/MekanismHUD.java`, `common/content/gear/mekasuit/ModuleChargeDistributionUnit.java`, `common/tile/TileEntityChargepad.java`, `common/item/interfaces/IJetpackItem.java` | Removed the Curios slot branches | Curios not ported |
| `mekanism/common/network/PacketHandler.java` | Removed `PacketModeChangeCurios`; the packet and `MekanismCurioRenderer` were deleted | Curios not ported |
| `mekanism/common/content/qio/QIOCraftingWindow.java` | Removed Recipe Stages checks | Recipe Stages not ported |
| `mekanism/common/item/gear/ItemHazmatSuitArmor.java`, `ItemSpecialArmor.java` | Removed the Wildfire Gender capability | Gender integration not ported |
| `mekanism/client/ClientRegistration.java`, `client/render/RenderTickHandler.java` | Removed the JEI recipe-screen listener (`guiOpening`) | JEI integration not ported |
| `pack.mcmeta` | Wrote in the pack formats upstream's build expanded (`pack_format` 10, resource 9, data 10) | The port's builds do not expand upstream's template variables |
