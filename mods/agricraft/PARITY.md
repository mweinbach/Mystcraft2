# AgriCraft 4.0.6 → 1.19.2 parity

Upstream: [AgriCraft/AgriCraft](https://github.com/AgriCraft/AgriCraft) branch `1.20.1`, revision
`e16ced0b88dd675487234a95fda63736c96edfbd` (4.0.6). Status words: **ported** (upstream code runs, with
the 1.19.2 changes listed), **tested** (also covered by a GameTest on both loaders), **planned** (a later
phase of this port), **absent** (not in 4.0.6).

## Features

| Feature | Status | Notes |
| --- | --- | --- |
| Crop sticks (wooden, iron, obsidian), cross crop sticks | tested | Planting with the seed item, the planted/cross block states, fluid logging. |
| Plant growth, growth stages, fertility (soil humidity/acidity/nutrients, light, biome, dimension, block, fluid, season) | tested | Wheat on wet farmland grows to its final stage. |
| Genome and the six stats (gain, growth, strength, resistance, fertility, mutativity) | tested | Stat inheritance never leaves the stat range or moves more than one step from a parent allele. |
| Cross-breeding, cloning and mutations (JSON `agricraft/mutations`) | tested | Wheat + potato on cross sticks yields the carrot mutation and nothing unrelated. |
| Weeds (spawning, growth, spreading, killing plants), rake | tested (spawning, removal) | |
| Harvest and clipping products | tested (harvest) | |
| Seed analyzer and its screen; journal and its pages | tested (analyzer logic, journal research) | Screens are ported but not yet checked in a client. |
| Trowel, magnifying glass + overlay, magnifying helmet recipe, clipper, rakes, seed bag, debugger | tested (trowel, seed bag, helmet recipe) | Shift-scrolling the seed bag sorts it on both loaders and the server applies the choice (upstream: Fabric client only). As upstream, the soil tooltip under the magnifying glass is Fabric-only. |
| Vanilla seed conversion, bone meal denial, farmland kept wet under crops | ported | Farmland: see 1.19.2 changes. |
| Resource items (coal pebble, copper nugget, diamond/emerald/quartz shards, netherite sliver, amathyllis petal) and their 3×3 recipes | ported | |
| Default plant, soil, mutation, fertilizer and weed data for vanilla | tested (loads with the server) | Taken unchanged from the 4.0.6 jars. |
| Compat datapacks and resource packs for other mods (`datapacks/<modid>`, `resourcepacks/<modid>`) | ported | Registered only when that mod is loaded, as upstream. |
| `/agricraft seed` and `/agricraft dump` commands | ported | |
| Config (ResourcefulConfig) | ported | Uses ResourcefulConfig 1.0.20, the 1.19.2 build Tekxit 4 ships. |
| ModMenu / Forge config screen | planned (phase 3) | Upstream's `ForgeMenuConfig` and the ModMenu entry point are removed for now. |
| EMI, JEI, REI plugins | planned (phase 3) | |
| Jade / WTHIT tooltips | planned (phase 3) | |
| Botania (mana growth condition, horn harvest), Fabric Seasons / Serene Seasons, PneumaticCraft, Industrial Foregoing, Mystical Agriculture plugins | planned (phase 3) | |
| Irrigation (tank, channel, valve, sprinkler) | planned (phase 2) | Not in 4.x; to be ported from AgriCraft 3 (branch `1.16.5`, MIT). |
| Greenhouses | absent | Not in AgriCraft 4.0.6 (or 3.x). |
| Torchflower plant and mutation | absent on 1.19.2 | Torchflowers were added in Minecraft 1.19.4; the data and models are removed. Its lang keys remain unused. |

## 1.19.2 changes

- **Data registries.** 1.20.1 registers plants, weeds, soils, mutations and fertilizers as datapack
  registries that the server syncs to clients. 1.19.2 has no datapack registries for mods (and Fabric API
  0.77 has no `DynamicRegistries`), so `AgriDataRegistries` loads the same JSON paths
  (`data/<ns>/agricraft/<type>/`) and tags (`data/<ns>/tags/agricraft/<type>/`) in a reload listener into
  frozen `MappedRegistry` instances, and sends them to remote clients on join and after `/reload`
  (`agricraft:data_registries`). Consequences:
  - `/reload` reloads AgriCraft data, which 1.20.1 does not.
  - `AgriApi` methods that take a `RegistryAccess` ignore it.
  - The mutation-complexity cache (`AgriMutationHandler`) is still computed once per game session, as
    upstream.
- **Creative tabs.** 1.19.2 tabs are built per item. The main tab lists every item but the debugger:
  upstream shows it only to players with the operator-items permission, and 1.19.2 tabs have no permission
  check, so it is left out (`/give @s agricraft:debugger`); the seeds tab lists one seed per loaded plant, sorted by id, and the
  creative search is rebuilt when a server sends its plants.
- **GUI drawing.** 1.20's `GuiGraphics` does not exist; `client/gui/AgriGuiGraphics` is a small facade over
  1.19.2's `PoseStack`/`GuiComponent` calls with the subset the screens, overlay and journal drawers use.
  The public API type `JournalPageDrawer` therefore takes an `AgriGuiGraphics`.
- **Fluid light.** Upstream's `BlockStateBaseMixin` (lava-logged crops emit lava light) is replaced by the
  crop block's light function, which takes the brighter of the plant's light and 15 when lava-logged.
- **Farmland.** The 1.19.4 `maintains_farmland` block tag does not exist; `FarmBlockMixin` makes farmland
  count crops on AgriCraft crop sticks, so it does not dry into dirt beneath them.
- **Lava buckets on Fabric.** `BucketItemMixin` uses MixinExtras' `@ModifyExpressionValue` on the
  `Fluids.WATER` checks so a lava bucket fills lava-loggable crop sticks; upstream Forge has no
  equivalent, and neither does this port's Forge build.
- **Block properties.** `Material.PLANT` (crop) and `Material.WOOD` (analyzer) replace 1.20's map
  colour/push reaction builders. `noParticlesOnBreak()` does not exist before 1.19.4, so breaking crop
  sticks shows break particles.
- **Compat packs.** 1.19.2 enables newly found data packs automatically, so
  `register_packs_by_default = false` cannot keep the compat datapacks off; disable them in the pack screen.
- **Recipes, loot, commands, damage sources, entity level access, registries** use their 1.19.2 forms
  (`CustomRecipe(id)`, `SimpleRecipeSerializer`, `LootContext.Builder`, `DamageSource.CACTUS`,
  `Registry.*`, `Entity.level`).
- **Model loading.** Fabric uses Fabric API 0.77's `ModelLoadingRegistry` and Forge
  `ModelEvent.RegisterAdditional` to load the seed, crop and weed models that no block state references.
- **Fixes over 4.0.6.** A loaded trowel plants off crop sticks only into empty space above a soil (upstream
  could overwrite the block above whatever was clicked), and seed-analyzer slot edits made through its
  screen mark the block entity for saving (upstream could lose them on chunk reload). Both have GameTests.
  From review, also with GameTests:
  - The analyzer menu closes once its analyzer is broken or replaced, so nothing goes into a detached
    inventory; hoppers put only a journal into the journal slot (upstream accepted any item).
  - Crop updates without a plant or weeds clear the client's cached ones, so raked weeds and clipped plants
    stop rendering at once (upstream kept them until the chunk reloaded).
  - The seed bag takes seeds out only for a slot that accepts them and returns what the slot does not take;
    it takes seeds in only after the slot gave them up, as vanilla's bundle does (upstream could delete seeds,
    or copy seeds out of a slot that refuses pickup). It holds seeds only: a loaded trowel was turned into a seed.
  - Shift-scrolling the seed bag sends the chosen sorter to the server (`agricraft:seed_bag_sorter`), which
    sorts the real bag. Upstream changed only the Fabric client's copy, so planting kept the old order; Forge
    had no scroll handler, and now has one.
  - The magnifying helmet recipe matches only a helmet and a magnifying glass; upstream ignored other items in
    the grid, and crafting consumed them.
  - Light-emitting plants (Nitor Wart, `agricraft:brightness`) set the crop's `LIGHT` state in the level as
    they grow; upstream only passed the new state to `sendBlockUpdated`, so they kept their planting light.
    Light is capped at 15, and crop sticks go dark when the plant is removed.
  - `agricraft:redstone` (Redstodendron) overrides `getRedstonePower`; upstream overrode `getBrightness`, so it
    glowed and never emitted a signal. When the power changes, the crop updates its neighbours and the blocks
    around them (it is a strong source), and comparators read the same power (upstream's analog output was always 0).
    The other `MinecraftPlantModifiers` already use the hook their names describe.
  - Removing a powering crop by breaking it, losing its soil, an explosion or a piston also updates the blocks
    around its neighbours (`CropBlock#onRemove`). The emitted power is taken from the loaded plant again when
    the chunk reloads, so later changes are still announced.
  - Side safety: only the server changes the world, moves items or rolls drops. This covers crop clicks (planting,
    harvesting, removing cross sticks), the rake, the trowel's off-stick planting, the seed bag, the analyzer's
    shift-click and vanilla seed conversion; the client returns the same result and does nothing else. The fungus
    modifier no longer casts a `ClientLevel` to `ServerLevel`; 4.0.6 crashed the client when bone-mealing a mature
    crimson or warped fungus crop on nylium. The burn, thorns and wither modifiers act only on the server; bushy
    still slows entities on both sides, as cobwebs do. The seed falls back to block placement only after the
    soil and crop checks, where 4.0.6 placed it before them (`orElse(super.useOn(...))`).
  - Persistence: every crop change marks the block entity for saving; 4.0.6 lost newly spawned and spread weeds
    on unload. A loaded crop with weeds gets an initial weed growth stage, where 4.0.6 set the plant's stage.
    Loading the analyzer clears slots that are missing from the tag, so clients drop extracted items. A hopper
    taking the journal out updates the analyzer's journal model. A different seed put into an occupied slot is
    handed back; 4.0.6 deleted it, and refused to stack stacks of different sizes.
  - A loaded trowel used on the soil under a planted crop leaves that plant alone; 4.0.6 overwrote its genome.
    Iron or obsidian sticks added to a bare plant keep their material; 4.0.6 left them wooden, so they rendered,
    dropped and burned in lava as wood.
  - Seeds, seed bags, trowels and cross-breeding never plant a species that is not loaded (an unknown id, or a
    disabled datapack): the planting is refused and nothing is consumed. 4.0.6 changed the crop and then crashed
    the server in `onPlanted`. A crop whose species stops loading keeps its genome through saves and drops its
    light and redstone power; malformed ids from NBT or data resolve to nothing instead of throwing.
  - Crop hitboxes are cached per species and growth stage; 4.0.6 cached them per stage only, so wheat planted
    after carrots kept the carrot's shape. Crops and the mutation complexities look their data up again after
    `/reload`; 4.0.6 kept the first world's.
- **Datagen.** Upstream's Forge datagen classes are not ported; the generated data is shipped as files
  (see `NOTICE.md`).

## Validation

- GameTests on both loaders: `src/gametestCommon` (crop, analyzer and EMC suites), run by
  `ForgeGameTests` and the Fabric `fabric-gametest` entrypoints. No test is loader-only.
- `scripts/check_loader_parity.py` compares the two jars (resources, shared classes, mixins and refmaps,
  descriptors, access widener/transformer, nested MixinExtras).
- Not yet done: a client session (screens, overlay, journal, models) and multiplayer play-testing.
