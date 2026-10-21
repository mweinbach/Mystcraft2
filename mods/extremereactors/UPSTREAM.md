# Upstream attribution

This port combines three sources under three sets of terms. Each JAR carries this
file as `META-INF/ExtremeReactors-UPSTREAM.md` and the files in
[licenses/](licenses/) under `META-INF/licenses/extremereactors/`.

| Part | Source | Revision | Terms | Where it lives |
| --- | --- | --- | --- | --- |
| Extreme Reactors 2 (code, assets, data) | [ZeroNoRyouki/ExtremeReactors2](https://github.com/ZeroNoRyouki/ExtremeReactors2), branch `1.19` | `127cb0311a7d7a6dd43fbde2fd88837bef2739d1` (2023-08-21) | MIT, as declared in its `mods.toml` | `src/main/java/it/zerono/mods/extremereactors`, `src/main/resources` |
| ZeroCore 2 multiblock API | [ZeroNoRyouki/ZeroCore2](https://github.com/ZeroNoRyouki/ZeroCore2), branch `1.19`, `lib/multiblock` only | `dd1c1357beb837ab97ad81606b5a02a86c642a9d` (2023-08-12) | MIT, [ZeroCore2-multiblock-LICENSE.md](licenses/ZeroCore2-multiblock-LICENSE.md) | `src/main/java/ports/extremereactors/multiblock` |
| `corelib`, loader adapters, tests, tooling | Written for this port | this repository | MIT, [LICENSE](LICENSE) | `src/main/java/ports/extremereactors/{corelib,platform}`, `src/forge`, `src/fabric`, `src/gametest*`, `src/test`, `scripts`, `emc` |
| Machine and storage block textures | Drawn for this port by `scripts/generate_textures.py` | this repository | MIT, [LICENSE](LICENSE) | `src/main/resources/assets/bigreactors/textures/block` (not the ores), `textures/fluid/fluid.fuelcolumn.*` |

## Lineage

Extreme Reactors descends from erogenousbeef's Big Reactors, which ZeroNoRyouki
continued as Extreme Reactors and later rewrote as Extreme Reactors 2 on top of
his ZeroCore library. ZeroCore's multiblock API in turn derives from
erogenousbeef's BeefCore; its MIT license credits both. The English language
file still credits Erogenous Beef, ZeroNoRyouki and ABookFreak. Neither upstream
repository is a fork.

The repository first proposed as the source, `Colonyoiconnector/extreme-reactors`,
was rejected. It has no source code, only two prebuilt JARs that are not
Extreme Reactors and appear to be malware droppers. Nothing from it was built,
run or used, and it should not be linked.

## License provenance

**Extreme Reactors 2.** The pinned revision has no license file and no copyright
line. Its `src/main/resources/META-INF/mods.toml` declares `license="MIT"`. Its
Java files carry a warranty disclaimer and "DO NOT REMOVE OR EDIT THIS HEADER";
every imported file that has the header keeps it unchanged, including the
edited ones. [licenses/ExtremeReactors2-NOTICE.md](licenses/ExtremeReactors2-NOTICE.md)
records the declaration and the MIT text it refers to.

**ZeroCore 2.** The mod declares `license="All rights reserved"` on every branch
from 1.16.3 to `master`. The exception is `lib/multiblock`, which ships its own
MIT license (Copyright 2016 ZeroNoRyouki, derived from BeefCore). Only that
package was imported, into `ports.extremereactors.multiblock`; its license is
copied verbatim to `licenses/`. Nothing else from ZeroCore 2 is used: no code,
assets, language strings or data.

## The clean-room ZeroCore replacement

Extreme Reactors 2 imports about 207 ZeroCore classes, of which 13 come from the
MIT multiblock package. The other classes cover blocks and block entities, item
and fluid handlers, energy, networking, recipes, menus and container data,
configuration, tags, client models and rendering, the computer-port API and
debugging helpers. They are replaced by `ports.extremereactors.corelib`, about
240 classes and 20,000 lines written for this port and licensed with it.

Rules followed while writing `corelib`:

- No all-rights-reserved ZeroCore source, binary or asset was read, copied or
  translated. Contributors and subagents were told the same.
- The design comes only from how Extreme Reactors 2 calls ZeroCore (class and
  method names, parameter types, return values used), the behavior visible in
  game and in Extreme Reactors' own data, and the vanilla, Forge and Fabric APIs.
- The API shape Extreme Reactors 2 sees is kept wherever that avoids edits to
  its code, so most of Extreme Reactors 2 compiles with only its imports changed
  from `it.zerono.mods.zerocore.*` to `ports.extremereactors.corelib.*` or
  `ports.extremereactors.multiblock.*`. Where `corelib` had to choose a
  behavior, the choice is documented in the class.
- `corelib` reaches loader services only through `ports.extremereactors.platform`.

Persisted NBT keeps the names Extreme Reactors 2 itself writes. `corelib` stores
its synchronized block entity state under `zcvase_payload`, because Extreme
Reactors' loot tables copy machine settings from that compound.

Without ZeroCore's language file, the multiblock API's validation keys
(`zerocore:api.multiblock.validation.*`) would show untranslated. The port adds
English messages of its own wording for them to the end of `en_us.json`, followed
by the two strings `corelib`'s energy and gauge bars show in their tooltips.

## Changes to Extreme Reactors 2

Imported files keep their package, class names and upstream header. Beyond the
import mapping, these upstream files were changed:

- `ExtremeReactors`: no longer the Forge `@Mod` class. Each loader's entrypoint
  calls `initialize()`, `onCommonInit()` and `onInterModProcess(imc)`. The IMC
  methods are unchanged.
- `api/internal/InternalDispatcher`: upstream ran a call directly when made from
  its own mod container and sent other mods' calls through Forge IMC. The port
  cannot detect the caller. Extreme Reactors' own registrations run inside
  `runInternal()`; other calls are queued per method until
  `ExtremeReactors.onInterModProcess` runs them after Forge IMC messages of the
  same method. The order is the same on both loaders, and calls made after
  loading now apply instead of being dropped.
- `api/internal/modpack/wrapper/ApiWrapper`: the config directory comes from the
  platform layer instead of `FMLPaths`.
- `config/Config`: the client and common specs load
  `config/extremereactors/<type>.toml` through `corelib.config.ConfigSpec`
  instead of Forge `ModConfig`. The file names are unchanged; they are read once
  at startup on both loaders.
- `config/Worldgen`: `generatesYellorite()`, `generatesAnglesite()` and
  `generatesBenitoite()` combine `enableWorldGen` with each ore's switch.
  Upstream never read these options; both loaders' ore registrations now do.
- `config/conditions/ConfigCondition`: the Forge condition serializer moved to
  `src/forge`; Fabric registers the same `bigreactors:config_condition` as a
  resource condition. The option switch is shared.
- `gamecontent/Content`: registration goes through the port's `DeferredRegister`
  on vanilla registry keys. Fluids are `FluidDefinition` records created by the
  platform layer, with the same tints, densities, light levels and rarities;
  fluid blocks use `ModLiquidBlock` and buckets `PlatformBucketItem`. Block
  entity and menu types are created by the platform layer. The biome modifier
  codec moved to the Forge adapter (`ForgeBiomes`, `OreBiomeModifier`), and
  `OreBiomeModifier` adds nothing when its ore's switch is off.
  `onCommonInit` wraps Extreme Reactors' API registrations in
  `InternalDispatcher.runInternal`.
- `gamecontent/fluid/ReactorFluidType` and `ReactantFluidBlock`: fluid
  definitions and a `ModLiquidBlock` subclass instead of Forge fluid types.
- `gamecontent/ItemGroups`: tabs are created by `ModItemGroup.create`; the
  Patchouli handbook is not added to the general tab.
- `ReactantHelper.onVanillaTagsUpdated` is subscribed through the platform
  tags-updated event. Upstream declared the handler but never registered it, so
  its moderator cache only expired by time.
- `CoilMaterialRegistry`, `ReactantMappingsRegistry`, `ModeratorsRegistry`: the
  unused `@Mod.EventBusSubscriber` annotation is dropped; fluids are looked up in
  the vanilla registry.
- `FluidPortType.create(Mekanism)` returns an empty handler, so Mekanism fluid
  ports are inert until the Mekanism integration is ported.
- `ReactorComputerPortEntity`, `TurbineComputerPortEntity`: they implement
  `corelib`'s `IComputerPort` instead of holding the ComputerCraft connector.
- Client models and renderers: no `@OnlyIn`; Forge `NonNullConsumer` became
  `java.util.function.Consumer`; model data is `corelib`'s. The fuel rod model
  takes its base quads through `ModRenderHelper.getQuads` and uses vanilla's
  identity rotation, and `ClientFuelRodsLayout` asks the platform whether a
  neighbor renders in the solid layer. `BlockVariantsModelBuilder.addBlock` lost
  its fourth argument.
- `proxy/ClientProxy`: installed by the client entrypoint through
  `ExtremeReactors.setProxy()`. Screens are registered through the platform
  layer instead of `MenuScreens`, and the Patchouli setup is not called.
- Screens (`client/screen/*` in `common`, `reactor`, `turbine`, `reprocessor`
  and `fluidizer`): no `@OnlyIn`, and Forge `NonNullSupplier` became
  `java.util.function.Supplier`. Nothing else changed.
- `compat/patchouli/PatchouliCompat`: only `HANDBOOK_ID` is kept, so the screens
  compile; the rest of the class needs ZeroCore's Patchouli component.

In the MIT multiblock package:

- `MultiblockRegistry` listens through the platform's level-unload and
  level-tick-start hooks instead of the Forge event bus. Both loaders fire the
  tick for server and client levels, so `MultiblockClientRegistry` was removed.
- `AbstractMultiblockController` creates its sync provider through `corelib` and
  wraps the payload in `multiblockData`, so the reference part forwards it.
  On the client, `markMultiblockForRenderUpdate` also drops each connected
  part's cached model data, so casings and glass switch to their connected
  variants when the machine assembles or breaks.

Resources:

- Each height anchor in the four ore placed features keeps only its `absolute`
  form. Upstream listed `absolute`, `above_bottom` and `below_top` together,
  which 1.19.2's anchor codec silently decodes as `absolute`, so ores generate
  at the same heights as upstream: yellorite 0 to 32, deepslate yellorite -64
  to -48, anglesite 5 to 200 and benitoite 3 to 126.
- Recipes that upstream wrapped in a single-recipe `forge:conditional` are
  flattened into top-level Forge `conditions` with equivalent
  `fabric:load_conditions`. Steel-gated recipes use `forge:tag_empty` /
  `fabric:tags_populated`.
- Shared `c:` item tags are added for Extreme Reactors' materials
  (`c:yellorium_ingots`, `c:uranium_ingots`, `c:cyanite_ingots` and so on), and
  the `forge:` tags link to them. `src/fabric/resources/data/forge/tags` gives
  Fabric the `forge:` tags Forge itself defines, with vanilla members and
  optional `c:` equivalents, so recipes, moderators, coils and reactant mappings
  resolve on both loaders.
- EMC data for ProjectE and Item Alchemy, generated from `emc/policy.json` by
  `scripts/emc.py`.
- Fabric-only `assets/forge/models/item/bucket_drip.json` and
  `assets/bigreactors/textures/item/bucket_fluid_overlay.png` stand in for
  Forge's dynamic bucket model. The overlay is the port's own texture.
- The multiblock validation messages appended to `en_us.json`.
- The machine textures are redrawn (see [Machine textures](PARITY.md#machine-textures)).
  Every reactor, turbine, reprocessor and fluidizer part, the fuel column fill
  and the nine storage blocks, including graphite, have new images under the
  same file names, drawn by `scripts/generate_textures.py`. The controllers'
  active and error faces gained animation metadata, as the idle face and the
  charging ports already had. Upstream's glass was 32×32 and is now 16×16. Models,
  blockstates, ores, items, fluids other than the fuel column, and GUI textures
  are upstream's.

## Not imported

| Upstream files | Why |
| --- | --- |
| `datagen/*` (19 files) | The generated resources are imported as they are; the port does not regenerate them. |
| `compat/jei/*` (20 files) | JEI and EMI integration is planned for a later pull request. |
| `compat/patchouli/*` (2 files), apart from `PatchouliCompat.HANDBOOK_ID` | Needs ZeroCore's Patchouli multiblock component, which is all rights reserved. |
| `FluidPortHandlerMekanism` | Uses Mekanism's Forge-only gas API. |
| `proxy/ProxySafeReferent` | Not needed without Forge's `DistExecutor`. |

The energizer is not part of the 1.19 branch, so it is not in scope.
