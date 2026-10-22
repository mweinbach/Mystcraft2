# Upstream attribution

Each JAR carries this file as `META-INF/Mekanism-UPSTREAM.md` and the files in [licenses/](licenses/) under
`META-INF/licenses/mekanism/`.

| Part | Source | Revision | Terms | Where it lives |
| --- | --- | --- | --- | --- |
| Mekanism (API, core code, assets, generated data) | [mekanism/Mekanism](https://github.com/mekanism/Mekanism), branch `1.19.x`, tag `v1.19.2-10.3.9.13` | `71e96905ef7ea47f0b65c78c1a9ac5e2bdabd04d` (2023-07-15) | MIT, Copyright (c) 2017-2023 Aidan C. Brady, [LICENSE](LICENSE) | `src/main/java/mekanism`, `src/main/resources`, `src/test/java` |
| Mekanism Generators | same tag | same revision | same MIT license | `src/generators/java`, `src/generators/resources` |
| Mekanism Tools | same tag | same revision | same MIT license | `src/tools/java`, `src/tools/resources` |
| Mekanism Additions | same tag | same revision | same MIT license | `src/additions/java`, `src/additions/resources` |
| Forge API mirror, loader adapters, tests, tooling | Written for this port | this repository | MIT, [licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt) | `src/forge`, `src/fabric`, `fabric/`, `build.gradle` |
| Forge bucket models and fluid masks (Fabric jar only, unmodified) | Minecraft Forge universal jar | `1.19.2-43.5.0` | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt) | `src/fabric/resources/assets/forge` |
| Forge's own `forge:` tag values (Fabric jar only), merged into the Fabric tag bridges | Minecraft Forge universal jar | `1.19.2-43.5.0` | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt) | `scripts/forge-tags.json`, `src/fabric/resources/data/forge/tags` |

`v1.19.2-10.3.9.13` is the last Mekanism release for Minecraft 1.19.2. Only the official repository was used.

## What was imported

Copied unchanged, then edited as listed below:

- `src/api/java` and `src/main/java` (merged into `src/main/java`), `src/main/resources`, the checked-in datagen
  output `src/datagen/generated/mekanism` (merged into `src/main/resources`) and the JUnit tests in `src/test`.
- `src/generators/java`, `src/generators/resources` and the checked-in datagen output
  `src/datagen/generated/mekanismgenerators` (merged into `src/generators/resources`), via
  `scripts/import_upstream.py`.
- `src/tools/java`, `src/tools/resources` and the checked-in datagen output
  `src/datagen/generated/mekanismtools` (merged into `src/tools/resources`), via the same importer.
- `src/additions/java`, `src/additions/resources` and the checked-in datagen output
  `src/datagen/generated/mekanismadditions` (merged into `src/additions/resources`), via the same importer.
  Baby mobs, balloons, the walkie-talkie, the voice server and the biome and structure modifiers are registered.
  The walkie-talkie recipe is the upstream shaped recipe.
- `mekanism.client.jei.MekanismJEIRecipeType`, `mekanism.client.jei.interfaces` and `mekanism.client.jei.recipe`.
  They do not use the JEI API; GUIs use them for recipe-viewer click areas and ghost slots.
- The JEI plugin (`mekanism.client.jei`, `mekanism.generators.client.jei`, `mekanism.tools.client.jei`) and
  `mekanism.api.integration.jei.IMekanismJEIHelper`. Fluid ingredients go through JEI's `IPlatformFluidHelper` so the
  same categories compile on Forge and Fabric. Recipe transfer is `FormulaicRecipeTransferInfo`,
  `GhostIngredientHandler` and `QIOCraftingTransferHandler`. The QIO handler's client shuffle check reads the item
  viewer's synchronized counts and capacities instead of `getFrequency()`, which is null on the client for the
  portable dashboard.

Not imported: CraftTweaker, OpenComputers 2 and Flux
Networks integrations, the datagen sources, the Defense module and the Blockbench/Blender model sources. ProjectE's
recipe mappers, NSS serializers and contents processor are in `src/forge` and load only when ProjectE is present.
Curios is
ported as an optional Forge adapter and Trinkets as the Fabric adapter. Wildfire's Gender Mod is optional on both
loaders; both have a 1.19.2 build. The look-at
providers are imported: Jade and the shared tooltip data are in `src/main`, The One Probe is in `src/forge`, and
WTHIT is in `src/fabric`. Upstream's Forge WTHIT plugin is not built into the Forge JAR. The ComputerCraft package
is imported in `src/main`; the Fabric peripheral provider is port code in `src/fabric`. OpenComputers 2 is not.

## The Forge API mirror

Upstream Mekanism is a Forge mod, and the shared sources keep their `net.minecraftforge` references; the Forge
build compiles them against Forge itself. The Fabric build (`fabric/build.gradle`) copies them to
`fabric/build/generated/sources/fabricShared` and rewrites, line for line, every `net.minecraftforge.` reference to
`ports.mekanism.lib.` and the few uses of Forge-only constructors and static methods on vanilla classes to the
port's equivalents in `ports.mekanism.lib.patched`. The rule list is `forgeOnlyVanillaMembers` in that build file.

`ports.mekanism.lib` (in `src/fabric/java`) reimplements the subset of Forge API shapes Mekanism uses: fluid stacks,
capabilities and lazy optionals, item/fluid/energy handlers, registries and deferred registers, config specs, the
event bus and the events Mekanism listens to, including `ServerStartingEvent` and `ServerStoppingEvent` for the
walkie-talkie voice server, networking channels, client model and render hooks, and the methods
Forge adds to vanilla classes (injected interfaces in `lib.common.extensions` and `lib.client.extensions`, applied by
the mixins in `ports.mekanism.fabric.mixin`). It was written from the API shapes Mekanism calls, not copied from
Forge. Fabric loader services sit behind `ports.mekanism.platform.Platform`.

## Hand edits to upstream files

Every edit beyond the scripted import rewrite:

| File | Edit | Reason |
| --- | --- | --- |
| `mekanism/api/MekanismAPI.java` | `getJeiHelper()` looks up `MekanismJEIHelper` only after `ModList` reports JEI. The helper interface references JEI's ingredient API | JEI is optional; the API class must not load JEI until something asks for the helper |
| `mekanism/common/Mekanism.java` | Removed the CraftTweaker content registration block | CraftTweaker integration not ported |
| `mekanism/common/integration/MekanismHooks.java` | `CuriosLoaded` is set when `curios` or `trinkets` is loaded. The accessory adapter is loaded by name. `WildfireGenderModLoaded` loads the gender adapter by name. `ProjectELoaded` is set when `projecte` is loaded, and `NSSHelper.init` is called by name. OC2, Flux Networks and Recipe Stages stay undetected and those flags stay `false`. ComputerCraft and The One Probe are unchanged | Curios, Trinkets, Gender Mod and ProjectE types stay in the loader source sets. The shared hook cannot mention them |
| `mekanism/common/integration/lookingat/wthit/MekanismWTHITPlugin.java` | `addBlockData` and `addEntityData` are cast to `IServerDataProvider` | WTHIT 5.36 also overloads those methods with `IDataProvider`. The cast keeps upstream's server-data provider |
| `mekanism/common/integration/computer/ComputerCapabilityHelper.java` | `addComputerCapabilities` attaches the ComputerCraft capability and skips the OpenComputers 2 branch | OC2 is not ported. The ComputerCraft helper is the upstream class |
| `mekanism/common/integration/computer/ComputerMethodMapper.java` | `isEnabled` is also true when `mekanism.gametest.computer` is set | GameTests check method lists without CC: Tweaked installed. The capability is still attached only when that mod is loaded |
| `data/mekanism/parameter_names/computer.json`, `data/mekanismgenerators/parameter_names/computer.json` | Copied from the Mekanism and Mekanism Generators 10.3.9.13 release jars | The annotation processor that writes these files was not imported. Methods bind without them; the files restore parameter names |
| `mekanism/common/integration/energy/EnergyCompatUtils.java` | Removed the Flux Networks energy compat | Flux Networks not ported |
| `mekanism/client/key/MekanismKeyHandler.java`, `client/render/hud/MekanismHUD.java`, `common/content/gear/mekasuit/ModuleChargeDistributionUnit.java`, `common/tile/TileEntityChargepad.java`, `common/item/interfaces/IJetpackItem.java` | The Curios branches call `CuriosIntegration`, which delegates to Curios or Trinkets. The mode key sends `AccessoryResult` slot id and index | The shared classes cannot mention Curios or Trinkets types |
| `mekanism/common/network/PacketHandler.java` | Registers `PacketModeChangeCurios`. The packet reads the stack through `CuriosIntegration.getCurioStack` | The Curios `SlotResult` type is not shared. `MekanismCurioRenderer` is Forge-only; Fabric's renderer is inside `FabricTrinkets` |
| `mekanism/common/content/qio/QIOCraftingWindow.java` | Removed Recipe Stages checks | Recipe Stages not ported |
| `mekanism/common/item/gear/ItemHazmatSuitArmor.java`, `ItemSpecialArmor.java` | `gatherCapabilities` and hazmat `initCapabilities` call `GenderCapabilityHelper.addGenderCapability` | The helper loads the loader adapter by name, so these items do not import the Gender Mod |
| `mekanism/client/ClientRegistration.java` | Registers `JeiScreenWatcher` on the forge bus only when JEI is loaded. The watcher is the upstream `RenderTickHandler.guiOpening` listener, moved so `RenderTickHandler` does not reference JEI | JEI is optional. Fabric posts `ScreenEvent.Opening` from `MinecraftMixin`; that event was not in the mirror |
| `pack.mcmeta` | Wrote in the pack formats upstream's build expanded (`pack_format` 10, resource 9, data 10) | The port's builds do not expand upstream's template variables |
| `mekanism/tools/common/material/BaseMekanismMaterial.java`, tool item constructors, `MekanismTools.registerTiers` | The material no longer implements `Tier` itself. `MaterialItemTier` delegates the same stats. Tool constructors and tier registration use `asTier()` | `Tier` and `ArmorMaterial` both declare `getEnchantmentValue` and `getRepairIngredient`. Fabric's remapper cannot map one method onto both |
| `mekanism/tools/common/item/ItemMekanismArmor.java` | `initCapabilities` calls `ToolsGenderCapabilityHelper` | Same chest capability upstream attached, without importing the Gender Mod |
| `mekanism/tools/common/integration/gender/ToolsGenderCapabilityHelper.java` | The six chestplate values are `GenderArmorSettings`. `attach` goes through `GenderCapabilityHelper` | `MekanismGenderArmor` implemented `IGenderArmor` directly, which neither loader can share |
| `mekanism/additions/common/MekanismAdditions.java`, `registries/AdditionsItems.java`, `client/AdditionsClientRegistration.java` | Registers blocks, items, baby mobs, balloons, obsidian TNT and the walkie-talkie. The voice server starts and stops with the server, and the client sets the channel model override and the voice key | Upstream behaviour, wired on both loaders |
| `mekanism/additions/common/config/AdditionsConfig.java`, `AdditionsClientConfig.java` | Server config keeps the obsidian TNT delay and radius, the baby spawn weights, `voiceServerEnabled` (default false) and `VoicePort` (36123). The client config has `voiceKeyIsToggle` (default false) | Upstream voice options |
| `mekanism/common/config/MekanismModConfig.java` | When `mekanism.gametest.skipConfigWatcher` is set, the handler removes NightConfig's file watch after Forge registers it and before correction or a later save. The property is unset in production, so the watch stays | Forge 43.5.0 reloads a config on the watcher thread while correction, or a GameTest `ConfigValue.set`, autosaves the same file. The reload parses a torn `additions.toml` or `world.toml`. A torn `additions.toml` puts `VoicePort` back to 36123 while the walkie test is binding a free port |
| `mekanism/additions/client/render/entity/layer/BabyEndermanHeldBlockLayer.java` | Renders the carried block with vanilla's five-argument `renderSingleBlock` | Forge's seven-argument overload is not on Fabric, and the dropped arguments were an empty model-data and a null render type |
| `mekanism/additions/common/world/modifier/BabyEntitySpawnBiomeModifier.java`, `BabyEntitySpawnStructureModifier.java` | The biome and structure holders are read with `value()`. The biome blacklist uses the holder's resource key | Forge's `Holder.get()` is the supplier method. Vanilla's method is `value()`, and both loaders have it. Looking the biome instance up in the built-in registry misses the world's copy of that biome |
| `mekanism/additions/common/entity/EntityBalloon.java` | A balloon latched to a mob scans for the floor down to the level's minimum build height, and does not pull the mob when there is no floor | Upstream stops the scan at Y=0 and treats a missing floor as Y=-1, which lifts anything below zero toward the surface |
| Additions walkie-talkie item, voice client and voice server | Imported from upstream. The voice socket claims a one-time session token from the game channel instead of matching the socket IP. One recipient's length prefix and payload are written under one lock. `VoiceInput` blocks on a microphone read instead of polling an empty buffer. The walkie-talkie recipe is copied. Fabric posts `ServerStartingEvent` and `ServerStoppingEvent`, and Additions has its own packet channel for the session token | Microphone capture and speaker playback were not run |
| `mekanism/common/network/to_server/PacketPortableTeleporterTeleport.java` | `handle` calls `teleport(ServerPlayer)`, which returns whether the player moved. The frequency check, energy cost and teleport are unchanged | GameTests run that server path without a packet context |
| `mekanism/common/util/MekanismUtils.java` | `getLastKnownUsername` skips the profile lookup when the server or its profile cache is missing | The GameTest server has no profile cache, and a security owner sync would otherwise crash while writing the update tag |
| `mekanism/common/lib/multiblock/Structure.java` | A queued formation runs on the next tick that ticks the block, instead of only when `gameTime` is exactly one past the queue timestamp | A skipped tick used to drop the update, and a structure that is already valid does not scan again |
| `mekanism/common/integration/projecte/IngredientHelper.java` | `addAsConversion` adds the conversion only when `ProjectEMapperGuard.permits` accepts the ingredient map | Upstream maps every machine recipe. Ore multipliers, the sawmill, coal gasification and chemical recipes would undercut the static EMC table. The guard uses integer division against `data/mekanism/projecte_mapper_guard.json` and refuses anything that is not a plain item already in that table |
