# Exploration and survival page acquisition

Source: Mystcraft-Legacy at the revision pinned in `NOTICE.md`, specifically
`MapGenScatteredFeatureMyst`, `ComponentScatteredFeatureSmallLibrary`,
`WorldGenMystCrystalFormation`, `SymbolCrystalFormation`, `LootTableHandler`,
`SymbolManager`, `BlockLectern`, `RenderLectern`, `InventoryVillager`, and
`VillagerTradeSystem`.

## Libraries

`ClassicLibraryFeature` uses the original scattered grid: spacing 32 chunks,
separation 8, candidate offsets 0–23, salt 14357617, and correct floor division
for negative coordinates. It runs only in the Overworld and `mystcraft:age_*`
dimensions. The source has its biome restriction commented out; modern loader
hooks can insert the feature into every biome without excluding modded biomes.
The function itself checks dimension and candidate chunk. Fabric uses an
all-biomes `BiomeModifications` selector; Forge uses a no-filter custom biome
modifier. Both insert the same placed-feature keys, including in modded biomes.
The runtime harness verifies the actual loaded generation settings of every
registered biome, then exercises the same real placement path in a controlled
fixture; that controlled test is not a claim of a natural exploration search.

`assets/mystcraft/exploration/small_library.json` exports all 694 source block
placements in order, including 47 bookshelves, cobblestone, oak planks, correctly
oriented cobblestone stairs/slabs and cobwebs. It retains the original chest and
five lectern positions. The building rotates in four directions. The port places
it inside the candidate chunk instead of beginning at the chunk boundary, skips
water and steep terrain (>5 blocks across sampled footing), refuses to overwrite
block entities, checks world-generation write boundaries, and limits downward
foundations to 12 blocks. Those conservative modern placement rules differ from
the unrestricted legacy scattered-structure placement. It is currently a feature,
not a registered Structure/structure set, so `/locate structure` does not find it.

The real custom lectern stores one persistent page, link panel, linking/descriptive
book or filled map. Sneaking with an empty hand recovers the display item. Normal
book use opens a server-validated linking session. Breaking it drops its item
once. Its placed renderer uses original lectern wedge geometry and texture plus
actual page glyph item models or articulated book geometry. A native one-slot display menu now opens for empty/page/map displays, with
filtered insertion, exact-payload extraction and player inventory. Stored linking
books open the existing server-validated read screen immediately with the native
display container underneath. The cover exposes the source's pickup slot at
(41,21); inner pages hide that slot. Extracting it returns to the empty inventory
view. Opening/inserting a book uses the same source-bound session rather than
client destinations, without an extra Read click. Both loaders
expose the source's all-face single-item inventory through their native item APIs.
Filled maps use the full native map canvas in the placed world renderer, with
the original background and source transforms. Right-clicking maps or loose
pages opens the single-slot inventory, not an enlarged map/page canvas; this
matches `GuiBook` hiding its book widget when `ContainerBook.getBook()` rejects
non-linking items. The server requests native update packets for every player in that
world each tick, matching the source update loop. Optional linking-book labels
reuse the dual client/server configuration and source 25-block limit.

The earlier “page collection GUI” and “free display rotation” gap descriptions
were inaccurate: source lecterns accept one page/book/map, not folders or
portfolios. Their inherited GUI is `ContainerBook`'s display slot and read-only
book-page view. `TileEntityLectern` rounds stored yaw to quarter turns, while
`RenderLectern` ignores stored yaw/pitch and uses the four block facings. The port
persists those signed rotations without inventing additional visual rotation.
The inventory item now uses the original separate twelve-quad OBJ geometry,
UVs/normals and `lectern_texture.png`, through native custom-item rendering on
both loaders; it does not substitute the placed Techne model. These new paths require validation of the rebuilt artifact; the native
fixture checks inventory/NBT/two-viewer/reach invariants, not graphical map pixels.

## Page loot

The custom loot function `mystcraft:symbol_page` samples actual implemented modern
symbol controls and live-registry biome/fluid/material pages through `ExplorationPages`. The independent source card-rank map must
not be confused with the grammar-rule rank. Source rank weights are recalculated
against available controls. The legacy stack calculation is retained, including
its exclusive maximum for stacks greater than one. Library lecterns make up to
100 weighted attempts to choose a rank >=3 page, then remain empty if none exists.

The library chest uses the pinned source's single mixed treasure pool. It makes 4–8 rolls, plus `floor(uniform(1,2) * luck)` bonus rolls. The fixed entry weights are ink vial 50, booster 1000, leather 50 and paper 50. Leather counts are 1–3 and paper counts 1–6; each vial or booster draw yields one item. Each ranked page also participates individually with its source-normalized card weight and exclusive-maximum page stack rule. The registered loot function selects from that combined pool against the actual biome/fluid/material registry.

Functional boosters produce seven rank-0, four rank-1, four rank-2 and one rank-3-or-higher page, including eligible rank-5 pages. Opening replaces one pack with a nonstackable folder containing ordered page payloads. A larger pack stack requires a free inventory slot before generation or payment. See [dynamic-pages.md](dynamic-pages.md).

Lecterns now run the full treasure pool, scan its output in order for an accepted display item, and reject pages below rank 3. They retry up to 100 complete pool generations, matching `LootTableHandler.generateLecternItem`, rather than drawing directly from a page-only distribution.

The original four vanilla chest hooks target desert pyramid, jungle temple, stronghold library and simple dungeon. Their weight-10 `TreasureGenWrapper.addLoot` body is empty in the pinned source. The modern hooks preserve that no-op as a weight-10 empty entry in the main pool, or first pool when a modern table has no pool names. They do not invent a treasure drop from the unfinished source wrapper. Reload hooks copy resource JSON and preserve existing datapack/mod entries.

## Crystal formations

`ClassicCrystalFeature` exits immediately outside `mystcraft:age_*`; it never
modifies ordinary Overworld terrain. In Ages it attempts one formation per 15
chunks, using 1–3 lines, each 6–12 blocks long, angles 15–155 degrees and six-neighbor
cross thickness. It searches upward from the dimension bottom for the first air
pocket, then roots two blocks below it, preserving the original underground/cave
placement intent rather than adding an unrelated ore patch. Modern minimum build
height replaces the legacy fixed zero; bedrock, block entities and unwritable
world-generation positions are protected. Version-three frozen generation separately executes authored Crystal Formation effects with their selected material through `ClassicPopulation`. The globally injected exploration feature exits before RNG consumption for frozen authored-generation plans. Thus version-three Ages without the page get no compatibility crystals, and an authored crystal effect is not duplicated by that feature. Older native/version-one/version-two generators retain their dimension-gated compatibility behavior. Far-reaching writes use the persistent deferred-crystal queue and replay into already loaded FULL chunks.

## Archivist shop

Normal, nonsneaking interaction with an enabled adult archivist now opens `ArchivistMenu` and `ArchivistScreen` on both loaders. The original `gui/tradeshop.png` supplies the 176×181 background. Three page cards, their prices and stock, a booster control, player inventory and currency total replace the generic merchant screen for this path. Purchase buttons support native mouse and keyboard focus and send only an index 0–3. The server validates the active menu, same level, living villager, profession, configuration, customer and eight-block distance before payment. Turning the feature off or changing the customer invalidates the open menu.

The three cards draw from minimum ranks 1, 2 and 3, with no upper rank limit. Each starts with three copies. The price is `4 * (1 + actual rank)`, so 8/12/16 emeralds are minimum prices, not fixed prices. An empty card lazily selects a new page and resets its stock to three, as the original `getShopItem` does. Boosters cost 20 emeralds and start with stock five.

Every 12,000 total world ticks, one of seven equally likely restock outcomes occurs. One does nothing, three add one booster up to eight, and the remaining three each add one copy to their respective nonempty page card up to five. Restocking uses world game time, not daylight or wall-clock time. Opening a previously unused shop catches up elapsed saved-world time. Each stock processes at most 1,024 intervals per game tick; further intervals remain pending and purchases stay disabled until caught up. The stock owns its random stream, so spreading work across ticks does not interleave villager AI draws. This scheduling limit prevents a new shop in an old save from freezing a server and does not skip source restock steps.

Purchases count emeralds and emerald blocks from the main 36-slot inventory. Blocks are worth nine emeralds; the server stages exact change and the purchased item on a copy before committing inventory and stock. Insufficient currency or storage leaves both unchanged. This preserves intended currency semantics while avoiding the original non-atomic deduction/rollback bugs. The port stores selected page payloads, counts, booster stock and a long restock timestamp in UUID-keyed overworld SavedData. This adapts the source entity `Mystcraft/Trade` storage and avoids the source's truncated integer timestamp read.

The real profession, lectern POI, native career offers and MCA career initialization remain intact. Sneaking bypasses the custom shop, preserving access to the native merchant adapter where the entity permits it. Those fallback offers retain modern work schedules, demand and reputation mechanics; they do not control the custom shop's stock or prices. The source house is adapted into all five native village house pools with two treasure lecterns, an empty configurable desk and a native archivist. See [village-archivist-house.md](village-archivist-house.md).

## Validation scope

`ArchivistEconomySmoke` exercises the actual loaded loot table, fixed source entry-weight boundaries and counts, native luck bonus rolls, minimum-rank card stock, clock boundaries and bounded catch-up, saved-data round trips, native inventory change/full-inventory rollback, and real menu purchase/rejection paths. `ExplorationSmoke` now checks mixed 4–8-roll treasure instead of incorrectly requiring every roll to be a page. The 0.4 production shop passed actual Forge and Fabric focused checks and graphical purchase tests. Exact artifact hashes and complete-suite/restart results belong in the runtime records. An initial Tekxit fixture incorrectly counted whole-table output as source rolls: seed 45022 produced eight valid Mystcraft pages plus a Simply Swords diamond chakram. The loaded table retained the exact source pool and appended four Simply Swords pools and one Amethyst Imbuement hammer pool. The revised helper validates the loaded source schema and executes it independently, then requires the whole table to retain an exact NBT/count multiset of the same seeded source output. Extra outputs must come from item entries declared in those appended pools. The source roll bounds remain strict; no production loot or pack mods were changed. Forge uses its native named-context loader for the isolated test table; Fabric uses its native Gson decoder. Final helper validation is recorded separately from the earlier full-suite artifacts.
