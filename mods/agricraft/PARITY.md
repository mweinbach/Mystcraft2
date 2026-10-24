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
| ModMenu / Forge config screen | ported | Upstream's `ModMenuConfig` (Mod Menu 4.2 `modmenu` entrypoint) and `ForgeMenuConfig` (mod-list config button), both opening ResourcefulConfig's screen. Not yet opened in a client. |
| EMI plugin (mutation, produce, clipping, growth requirement categories) | tested (data, wiring) | EMI 1.1.24. Screens are not yet checked in a client. See [Recipe viewers](#recipe-viewers). |
| JEI plugin (the same four categories, plant ingredient type, magnifying helmet crafting extension) | tested (data, wiring) | JEI 11. Screens are not yet checked in a client. See [Recipe viewers](#recipe-viewers). |
| REI plugin (the same four categories) | tested (data, wiring) | Not in 4.0.6 (upstream has no REI plugin); original port code for REI 9.2. Screens are not yet checked in a client. See [Recipe viewers](#recipe-viewers). |
| Jade / WTHIT tooltips (crop species, stats and growth, soil properties, irrigation fluid level) | tested (lines) | Jade 8 on both loaders (Fabric `jade` entrypoint, Forge `@WailaPlugin`); WTHIT 5 on Fabric through the `waila:plugins` entry in `fabric.mod.json` (upstream's `wthit_plugins.json` needs WTHIT 6+). The irrigation provider is AgriCraft 3's. See [Integrations](#integrations). |
| Botania (mana growth condition and plant modifier, Horn of the Wild harvest) | ported | Both loaders, as upstream, when Botania is loaded and `compat.botania` is on. Compiled against Botania 1.19.2-440.2; not run with Botania. |
| Fabric Seasons plugin | ported (stub, as upstream) | Upstream 4.0.6 claims the season logic and reports `ANY` everywhere, so season requirements never block growth on Fabric; kept as is. |
| Serene Seasons, PneumaticCraft, Industrial Foregoing, Mystical Agriculture plugins | absent | Present in the 4.0.6 sources but never initialised (Serene Seasons is commented out), so they do nothing upstream; not ported. |
| Irrigation (tank, channel, hollow channel, valve, sprinkler) | tested | Not in 4.x; ported from AgriCraft 3 (branch `1.16.5`, MIT). See [Irrigation](#irrigation-from-agricraft-3). |
| Greenhouses | absent | Not in AgriCraft 4.0.6 (or 3.x). |
| Torchflower plant and mutation | absent on 1.19.2 | Torchflowers were added in Minecraft 1.19.4; the data and models are removed. Its lang keys remain unused. |

## Irrigation (from AgriCraft 3)

AgriCraft 4 has no irrigation. The irrigation blocks are ported from AgriCraft 3 (branch `1.16.5`, revision
`4b52a422`, MIT) without InfinityLib:

- **Tank** (`irrigation_tank`, 8,000 mB per block): fills from water buckets and rain, empties into empty
  buckets. Touching tanks that fill a cuboid merge into one multi-block that pools the water; breaking a block
  splits it and shares the water out by amount, filling from the bottom layer up. A ladder can be fitted to a tank wall and climbed. A sufficiently full
  tank leaves a water source when broken (configurable). Water evaporates in ultra-warm dimensions.
- **Channel** (`irrigation_channel`, 500 mB) and **hollow channel** (`irrigation_channel_hollow`): water
  levels balance between connected tanks and channels. The **channel valve** (`agri_channel_valve`) is fitted
  to a channel by using it on one; a valved channel is opened and closed by hand, a valved hollow channel by
  redstone. Closed valves block the flow.
- **Sprinkler** (`sprinkler`): hangs under a channel, draws its water and, while active, moistens farmland
  in a 7×7 area up to five blocks below and gives plants there an extra growth tick (20% chance per pass).
- Fluid APIs: Forge exposes tanks through the `IFluidHandler` capability, Fabric through Fabric Transfer
  `Storage<FluidVariant>` (81 droplets per mB). Channels and sprinklers are not exposed, as in AgriCraft 3.
- Config (`irrigation` category): tank and channel capacity, rain fill rate, the water block on breaking,
  sprinkle interval, sprinkler growth chance and water use, with AgriCraft 3's defaults.

Differences from AgriCraft 3:

- **Wood textures.** AgriCraft 3 crafts and renders tanks and channels from any plank (InfinityLib dynamic
  models and NBT). Here the recipes take any plank, but every tank and channel uses the oak plank texture
  and the item carries no wood type.
- **Tank water.** AgriCraft 3 places an invisible `tank_water` fluid block so entities can swim in a tank;
  it is not ported, so an entity inside a tank stands on its floor rather than swimming.
- **Sprinkler particles** use vanilla splash (or cloud in ultra-warm dimensions) instead of AgriCraft 3's
  custom particle; the client particle setting (off/decreased/all) is respected.
- **Block hardness and sounds** are this port's choice (wood: hardness 2 for tanks and channels, 1 for sprinklers; axe-mineable).
- AgriCraft 3's unused irrigation network classes (`IrrigationNetworkLayer` and friends) are not ported.
- Fixes: toggling a valve no longer breaks the sprinkler below; un-forming a multi-block shares out the
  origin's stored amount from the bottom layer up instead of converting its level (AgriCraft 3 kept the level,
  and since a multi-block's height excludes one tank floor while each single tank excludes its own, stacking
  and removing an empty tank made water), and first loads members in unloaded chunks so they keep their part
  (they store no water and would come back empty); buckets use the vanilla filled-bucket logic, so a stack of empty buckets is not
  consumed; the valve wheel animates from block-state changes; clients are synced when a component becomes
  empty or full, not only on large changes.

## Recipe viewers

The viewers are optional: their APIs are compile-only, and each plugin loads only when its viewer is installed
(EMI 1.1.24, JEI 11.6 on Forge / 11.8 on Fabric, REI 9.2). Registration: Forge by `@EmiEntrypoint`, `@JeiPlugin`
and the `@REIPluginClient` subclass `forge/compat/AgriCraftReiForgePlugin`; Fabric by the `emi`,
`jei_mod_plugin` and `rei_client` entrypoints. All three show the lists built by the shared
`compat/AgriRecipeDisplays` (sorted by id):

- **Mutation**: two parent seeds give the child seed (valid mutations only, i.e. all three plants loaded).
- **Produce** and **clipping**: a seed and its possible products / clip products, for plants that have any.
- **Growth requirement**: the interactive page (strength and growth-stage buttons, fertile light levels, soil
  humidity/acidity/nutrients, seasons when a season mod is active, the plant rendered on a cycling suitable
  soil). Its drawing, buttons and tooltips are upstream's, moved into one client class,
  `compat/client/CropRequirementView`, that the EMI recipe widget, the JEI category and the REI widget share.
- Seeds are compared by species (EMI comparison, JEI subtype interpreter, REI item comparator); EMI hides the
  NBT-less seed, as upstream.

1.19.2 changes and simplifications:

- **Drawing.** EMI 1.1.24 widgets and JEI 11 categories draw with a `PoseStack`, so the upstream `GuiGraphics`
  calls go through `AgriGuiGraphics`; the requirement page's block/plant rendering uses 1.19.2's
  `Vector3f` rotations and the shared buffer source.
- **EMI.** Upstream builds the requirement page from EMI button, drawable and tooltip widgets; here one
  widget delegates to `CropRequirementView` (same buttons, drawing and tooltips). `CropClippingRecipe` is a
  subclass of `CropProduceRecipe` instead of a copy. Upstream listed mutations with missing plants as
  empty seeds; they are left out.
- **JEI.** The four categories, the plant ingredient type and the magnifying helmet extension map onto JEI 11
  unchanged apart from the `PoseStack` signatures. Recipe objects are the shared display records instead of
  `AgriPlant`/`AgriMutation`; the produce category lists only plants with products (upstream listed every
  plant). Because EMI shows JEI plugins' categories itself, the JEI plugin registers nothing but the seed
  subtype interpreter when EMI is installed, so the categories are not listed twice.
- **REI** (original port code, MIT): the same four categories on the JEI layouts and textures, plant icons
  with name/description tooltips, and the interactive requirement page.
- **Remote servers.** On 1.19.2 AgriCraft's plants and mutations reach remote clients in the
  `agricraft:data_registries` packet, after the recipes and tags the viewers build their lists from. Once the
  data is applied, `compat/RecipeViewers` reloads EMI (`EmiReloadManager.reload()`) and REI
  (`RoughlyEnoughItemsCoreClient.reloadPlugins`), both called reflectively and only when present. JEI 11 has
  no public reload, so its plugin adds the missing recipes, plant ingredients and seeds through JEI's runtime
  API (`IRecipeManager.addRecipes`, `IIngredientManager.addIngredientsAtRuntime`). Limitation: after a
  `/reload` that removes or changes plants, JEI keeps the old entries until it restarts (rejoin). In
  singleplayer the registries are shared in-process and none of this runs.
- Not verified: the screens (layout, rendering, buttons) have not been checked in a client session, and the
  remote-data refresh has not been exercised against a dedicated server with a client.

## Integrations

- **Tooltips.** `compat/AgriTooltipLines` holds the crop, soil and irrigation lines once; upstream's Jade and
  WTHIT plugins each carried a copy of the crop and soil logic. Jade 8.7/8.9 (`IBlockComponentProvider`,
  `config.jade.plugin_agricraft.{crop_block,soil,irrigation}`) and WTHIT 5.36 (`IBlockComponentProvider` at
  `TooltipPosition.BODY`) only format those lines. `TooltipGameTests` checks the lines for a planted crop, a
  soil and a filled tank/channel; the overlays themselves have not been seen in a client.
- **Config screens.** Mod Menu (Fabric) and the Forge mod list open ResourcefulConfig's `ConfigScreen` for
  `AgriCraftConfig`, as upstream.
- **Botania.** Upstream's common classes (`compat/botania`: `ManaGrowthCondition`, `ManaPlantModifier`,
  `AgriHornHarvestable`, `BotaniaPlugin`) are shared; `compat/fabric/botania/BotaniaFabricPlugin` registers
  the horn lookup and the `ManaNetworkCallback` pool cleanup, and `forge/compat/BotaniaForgePlugin` the
  `ManaNetworkEvent` cleanup and the horn capability (attached by `AttachCapabilitiesEvent`, since this port
  has no Forge-specific crop block entity). Fix over 4.0.6: `ManaPlantModifier` skips crops whose pool lookup
  returns nothing instead of failing.
- **Seasons.** `common/plugin/fabric/FabricSeasonPlugin` is upstream's stub (always `ANY`).
- Development runs: `-PwithJade` (both loaders) and `-PwithWthit` (Fabric) add the tooltip mods.

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
  sticks shows break particles; the crop block state points at particle-only models (`block/crop_particle/*`:
  oak planks, iron block, obsidian, the textures upstream's stick models name) instead of `minecraft:block/air`,
  so those particles are not the missing texture.
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
  - A harvest, clip or rake product whose tag resolves to no item (e.g. Petinia's `#agricraft:nuggets/tin` with
    no tin mod loaded) drops nothing; 4.0.6 called `nextInt(0)` and crashed the server on harvest and block
    drops. A product with `max < min` drops `min`, and stat rolls never use a max below 1. Plants with a
    `required` product that resolves to nothing are hidden from the creative tab and never come out of a
    mutation, as AgriCraft 3 disabled them (4.0.6 ignores the flag); planted ones still grow and drop nothing,
    and `/agricraft_seed` still gives their seeds.
- **Datagen.** Upstream's Forge datagen classes are not ported; the generated data is shipped as files
  (see `NOTICE.md`).

## Validation

- GameTests on both loaders: `src/gametestCommon` (crop, analyzer, irrigation, EMC, viewer and tooltip suites), run by
  `ForgeGameTests` and the Fabric `fabric-gametest` entrypoints, plus `TankFluidGameTests`, which has a
  Forge (fluid capability) and a Fabric (Transfer API) version under the same name. No test is loader-only.
  `ViewerGameTests` checks the shared viewer displays against the registries and, run with
  `-PwithEmi -PwithJei -PwithRei`, that each installed viewer's plugin is wired up.
- `scripts/check_loader_parity.py` compares the two jars (resources, shared classes, mixins and refmaps,
  descriptors, access widener/transformer, nested MixinExtras).
- Not yet done: a client session (screens, overlay, journal, models, irrigation renderers and particles) and
  multiplayer play-testing.
