# Historical scaffold inventory

This records the starting state only. Current implementation status is in [classic-parity.md](classic-parity.md).

## Historical starting inventory

At the start of this review the 1.19.2 module contained only its Forge entry point. Every gameplay row below was missing. Work added during this iteration must be reported separately with concrete validation; registering an item or shipping its texture alone does not complete a row.

| Area | Classic reference evidence (under `src/main/`) | Required behavior / acceptance | Baseline |
| --- | --- | --- | --- |
| Linking book creation | `java/com/xcompwiz/mystcraft/data/RecipeLinkingbook.java`, `item/ItemLinkbookUnlinked.java` | Exactly one link-panel page plus leather produces an unlinked book; preserve page link-effect NBT. Right-click a single unlinked book to record position, dimension and orientation. | Not implemented |
| Linking interaction | `item/ItemLinkbook.java`, `item/LinkItemUtils.java`, `client/gui/GuiBook.java`, `inventory/ContainerBook.java` | Open the book UI; explicit panel activation links. Opening a book alone must not teleport. Validate held book and destination on the server. | Not implemented |
| Link rules and effects | `linking/LinkController.java`, `linking/LinkListenerBasic.java`, `data/InkEffects.java` | Cross-dimension travel; normal books reject same-dimension travel; Intra-Linking enables it and Intra-Linking-Only rejects cross-dimension links. Preserve supported flags, orientation and momentum rules. | Not implemented |
| Physical books | `entity/EntityLinkbook.java`, `tileentity/TileEntityBookstand.java`, `tileentity/TileEntityBookRotateable.java`, `block/BlockLectern.java` | Books left behind when linking, Following behavior, placed books, bookstands and lecterns; dedicated-server persistence and multiplayer interaction. | Not implemented |
| Descriptive books | `item/ItemAgebook.java`, `tileentity/TileEntityBookBinder.java`, `inventory/ContainerBookBinder.java` | Bind ordered pages and a title into a descriptive book; generate/persist an Age; repeat links target the same Age. | Not implemented |
| Writing and collection | `tileentity/TileEntityDesk.java`, `inventory/ContainerWritingDesk.java`, `item/ItemFolder.java`, `item/ItemPortfolio.java`, `page/Page.java` | Page copying with ink/paper, ordered page management, folder/portfolio storage and persistence, page/link-panel distinctions. | Not implemented |
| Ink mixing | `tileentity/TileEntityInkMixer.java`, `data/InkEffects.java`, `item/ItemInkVial.java` | Ink production/consumption, paper to link panels, additives affecting linking properties. Preserve classic recipes and probabilities where supported. | Not implemented |
| Items and crafting | `data/ModItems.java`, `data/ModBlocks.java`, `resources/assets/mystcraft/recipes/` | Inventory includes page, descriptive/linking/unlinked books, booster, folder, portfolio, desk, ink vial and glasses; functional blocks include ink mixer, binder, receptacle, stand, lectern, desk, crystal and modifier. Match recipes and container returns. | Not implemented |
| Original visual/audio identity | `resources/assets/mystcraft/textures/`, `gui/`, `sounds/`, `sounds.json`, `lang/` | Original book covers, pages, symbols, panel/cover GUI, block models and linking sounds, adapted to modern resource formats. Resource presence is separate from rendered parity. | Not implemented |
| Age lifecycle and saves | `world/agedata/AgeData.java`, `world/agedata/AgeDataLoaderManager.java`, `world/storage/WorldInfoMyst.java`, `world/AgeController.java` | Persistent Age identity, destination allocation/loading, title/seed/page data and restart-safe links. Never substitute an arbitrary vanilla dimension as a generated Age. | Not implemented |
| Writing grammar and worldgen | `data/ModSymbols.java`, `data/SymbolRules.java`, `symbol/`, `world/ChunkProviderMyst.java`, `world/BiomeProviderMyst.java` | Ordered symbol parsing; random completion; terrain, biome controllers, celestial bodies, lighting/colors, weather, structures and environmental effects. | Not implemented |
| Classic exploration | `world/gen/structure/ComponentScatteredFeatureSmallLibrary.java`, `world/gen/structure/ComponentVillageArchivistHouse.java`, `villager/`, `treasure/` | Libraries, symbol discovery/loot, archivist trading and other classic progression. | Not implemented |
| Crystals and portals | `block/BlockCrystal.java`, `block/BlockBookReceptacle.java`, `block/BlockLinkPortal.java`, `block/BlockStarFissure.java` | Crystal generation, book-driven portals and star-fissure escape behavior. | Not implemented |
| Classic instability | `instability/InstabilityManager.java`, `instability/InstabilityController.java`, `instability/InstabilityProfiler.java`, `instability/decay/`, `world/profiling/` | Original resource profiling, instability effects and decay. This is classic behavior; no new Mystcraft2 stability mechanic is requested or specified here. | Not implemented |
| Administration and integrations | `command/`, `api/`, `integration/`, `imc/` | Classic administration and addon contracts need explicit modern equivalents or documented omissions. | Not implemented |

To browse evidence, append the path to the [pinned source directory](https://github.com/Mystcraft/Mystcraft-Legacy/tree/9bc8ddc061845df0cd5ea47f4fdef773cff786ae/src/main). Java paths after the first full package in a row are relative to `java/com/xcompwiz/mystcraft/`; resource paths after the first resource entry are relative to `resources/assets/mystcraft/`.

