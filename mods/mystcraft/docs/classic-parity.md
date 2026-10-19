# Classic Mystcraft parity target

This port targets **Mystcraft 1 for Minecraft 1.12.2**, not the proposed Mystcraft2 remake. This inventory records the verified classic reference and the current development port; it is not a claim of complete gameplay parity. New Mystcraft2 stability designs are outside scope. Classic instability now has a shared source-derived profiling, scoring, persisted-deck and effect implementation; its modern adapters and validation limits are documented separately.

## Verified upstream

- [Mystcraft/Mystcraft-Legacy](https://github.com/Mystcraft/Mystcraft-Legacy) is the official organization's open-sourced Mystcraft 1 implementation. Its default branch is `1.12/develop`.
- Reference commit: [`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`](https://github.com/Mystcraft/Mystcraft-Legacy/tree/9bc8ddc061845df0cd5ea47f4fdef773cff786ae). All paths below refer to this immutable tree, checked 2026-10-18.
- [`build.gradle`](https://github.com/Mystcraft/Mystcraft-Legacy/blob/9bc8ddc061845df0cd5ea47f4fdef773cff786ae/build.gradle) targets Forge `1.12.2-14.23.5.2770`; `build.properties` records Mystcraft `0.13.7.06`.
- The root [LICENSE](https://github.com/Mystcraft/Mystcraft-Legacy/blob/9bc8ddc061845df0cd5ea47f4fdef773cff786ae/LICENSE) is LGPL version 3. Preserve that license, attribution, and modification provenance when adapting implementation or shipped artwork. No separate resource license was found in this tree. This is usable source, not a binary recovered by decompilation.
- The upstream [README](https://github.com/Mystcraft/Mystcraft-Legacy/blob/9bc8ddc061845df0cd5ea47f4fdef773cff786ae/README.md) requires a CLA for contributions **to that upstream repository**; it does not establish an approval gate for local development.
- The previously recorded [Mystcraft2 revision](https://github.com/Mystcraft/Mystcraft2/tree/55ff806e6229b932542ba1bd1c9a7096b24dea73) is documentation-only and must not be described as the classic implementation.

## Current development status

The starting module contained only an entry point. The [historical inventory](starting-inventory.md) preserves that baseline. The current port has substantial shared gameplay on Forge and Fabric, but is still a development build. Source implementation, automated runtime evidence and graphical observations are different levels of proof; see [validation.md](validation.md) for tested artifact scope.

| Area | Implemented behavior | Remaining classic parity / limits |
| --- | --- | --- |
| Linking books | Original recipe/panel properties; recorded destinations; single-use server activation; main/offhand, entity, stand, lectern and receptacle sources | Optional LookingGlass camera integration and old addon APIs are separate |
| Link effects | Intra Linking, Intra Linking Only, Following, Disarm, Maintain Momentum, Generate Platform and Relative; native entity travel and transactional rollback; original sound precedence and endpoint particles | Pack-specific inventories/capabilities still require testing |
| Physical books | Original open-book/stand/lectern geometry and textures; persistent interactive entities; source hurt overlay, Following glint and Disarm lightning | Living-style motion, air, death delay and minecart pickup; adapter boundaries below; source uses a fixed open pose, not an opening animation |
| Book presentation | Original cover/page layout, descriptive trim, titles/authors, glyph pages, availability/visited panel effects, optional labels and receptacle orientation | See [book-presentation-parity.md](book-presentation-parity.md); external cameras and seasonal/addon renderers are separate, not missing core cover-color features |
| Descriptive books and Ages | Binder costs, ordered original pages, lazy persistent allocation, identity/seed/title and independent state; separate frozen completed execution | No legacy 1.12 save migration; registry/config differences affect completion |
| Writing and collections | Original wide auto-arranged page workspace and art, storage tabs, target rename, paid right-drag copies, owned bulk movement, search/sort and ordered-folder dragging | [page-browser.md](page-browser.md) records source pixel scrolling, original widgets and remaining embedded-book interaction limits; the source workspace is not free-form |
| Ink and modifiers | Original mixer layout/additives, paid seeded panel output and container returns; modifier permissions; native ink fluid/bucket, loader automation and desk tanks | Capability/transfer behavior remains loader-specific; exact runtime evidence is recorded in [validation.md](validation.md) |
| Symbol grammar | **145/145 static symbols have source compiler/runtime paths**; weighted source grammar, completion, ordered parameters, duplicate-controller costs, registry biome/fluid pages | Acceptance is not exhaustive runtime parity; see [static audit](static-symbol-implementation-audit.md), [classic grammar](classic-grammar.md) and [dynamic pages](dynamic-pages.md) |
| Terrain and generation | Frozen legacy plans; source flat/void/Nether/End v1 and normal/amplified v2; version3 biome/material/feature plans; new version4 specialized badlands, taiga, hills and swamp surfaces; nine biome controllers and source algorithms | [specialized surfaces](classic-specialized-surfaces.md), [classic-terrain.md](classic-terrain.md), [generation-plan.md](generation-plan.md), [huge-trees.md](huge-trees.md) and [native structures](classic-native-structures.md) identify modern adapters |
| Sky, colors and weather | Ordered celestial/color/lighting providers, environment colors, weather controllers and saved synchronized state | See [celestial-implementation.md](celestial-implementation.md) and [environment-implementation.md](environment-implementation.md); final visual checks are artifact-specific |
| Exploration | Source libraries/lecterns, registry-aware source treasure pools, booster packs, original archivist shop and persistent stock, native village archivist house and Age crystals with far-chunk placement safeguards | [Village house](village-archivist-house.md) documents modern jigsaw distribution and configuration; artifact-specific runtime results are recorded in [validation.md](validation.md) |
| Portals and escape | Crystal graph portals/receptacles, title-derived portal surface tint, player/item/mob linking and source-derived star-fissure escape | Mod entity/renderer combinations need pack sessions |
| Classic instability | Source score/cost pipeline, chunk profiling, persisted seeded decks, environmental effects and five decay colors including black | [Instability audit](classic-instability-audit.md) distinguishes source defects, baseline/scheduler/physics adapters and configuration; no Mystcraft2 stability design |
| Art and sound | Original imported art, composed static/dynamic glyphs and original linking/fissure sounds | Current asset inventory is [asset-provenance.md](asset-provenance.md); presence alone is not rendered-state proof |
| Administration/addons | Shared loader-neutral registration/networking; source-derived crafting, respawn, identity, home-dimension and entry/departure permission configuration | See [validation.md](validation.md) for artifact-specific runtime results; original addon/IMC contracts and 1.12 save/config migration are not promised |

This table describes the current 0.4 **source**, with the integrations above implemented. [validation.md](validation.md) records the exact tested artifacts, build results, dedicated-server checks and graphical observations. Earlier test results remain evidence for their named artifact hashes, not automatic coverage of later changes.

## Loader and server acceptance

Both variants compile the same shared gameplay, registries, recipes and resources. Loader adapters handle lifecycle, registration, networking, menus, entity transfer and client hooks. A packaged-resource check compares every shared resource and verifies mapped client mixins. Each loader still needs its own dedicated-server and graphical validation; matching resources alone cannot establish runtime parity.

The server test helper is a separate optional JAR, never part of production. It exercises real server APIs and menus with fixture players, chunk-readiness checks and transactional assertions. These tests complement real graphical clients; they do not substitute for two simultaneously connected human clients, survival progression or a long-running modpack world.

Tekxit 4 server 20.0.0 is an actual Fabric 1.19.2 compatibility target. Pack-specific failures and exact artifact hashes are recorded in [the Tekxit test log](../../../docs/tekxit4-testing.md). Cross-loader multiplayer, world conversion and existing 1.12 saves are not promised.

## Source fidelity boundaries

The reference is the pinned official development source, not a demonstrated binary-identical reconstruction of the distributed 1.12 release. Modern registry, save, biome, rendering and world-height APIs require explicit adaptations. Unsupported gameplay is rejected or documented; merely registering its item, symbol or artwork is not counted as a completed feature.

## Book health lifecycle adaptation

World books retain classic `MaxHealth` (default 10) and lowercase `damage` on the actual book stack. Pickup, saving and custom-entity transport retain that durability. Suffocation is ignored, fire damage doubles and ignites the book, wet contact attacks for one damage, and the saved `DecayTimer` attacks once per 10,000 ticks. Repeated hits use the classic living-entity 20-tick hurt window, including applying only the difference for stronger hits during its first half. Damaged books show a proportional item durability bar; malformed non-finite health values are normalized.

The modern entity remains an `ItemEntity` subclass for both loaders' item-drop and dimension-transfer interoperability, with explicit passive living-style gravity and fluid motion instead of item buoyancy/merging. It now has air-supply drowning, fall damage, a moving 20-tick death delay, and exact-stack hopper-minecart transfer. A loader hook prevents stationary hoppers from treating physical books as loose items. Native `Entity.baseTick` supplies modern fire/lava and portal handling; this is not a binary recreation of the old living class. Wetness uses modern `isInWaterOrRain` (water/rain contact). See [book-presentation-parity.md](book-presentation-parity.md) for the remaining adapter limits and [validation.md](validation.md) for the tested revision.

The source `crafting.linkbook.enabled` option is available in `config/mystcraft-crafting.json`, defaulting to `true`. Set it to `false` and restart to reject leather/panel linking-book crafting; existing books and binding remain available. The serializer stays registered for save/network compatibility. The dedicated server owns crafting validation; clients with different local configuration may still show a recipe preview but cannot take a disabled output. Invalid types and unknown keys stop initialization with the config path.
