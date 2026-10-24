# Gridwright

A clean-room, Extra Utilities 2–style utility mod for Minecraft 1.19.2 on **Forge 43.5.0** and
**Fabric (Loader 0.16.14, Fabric API 0.77.0+1.19.2)**, built from one shared core. Phase 1 adds a
player-owned, cable-free power grid ("Grid Power", GP), the GP producers, a family of fuel generators
that make the pack's normal energy, and the resonator, the first GP consumer. Phase 2a adds transfer
nodes and pipes for items and fluids, their upgrades, retrieval nodes and filters.

Read [`NOTICE.md`](NOTICE.md) first: Gridwright contains no Extra Utilities 2 code, art, sounds or
language files, and is not affiliated with it. Every number below is Gridwright's own.

- Mod ID `gridwright`, package `ports.gridwright`, licence MIT ([`LICENSE`](LICENSE)).
- Status: **in progress** — logic and GameTests on both loaders; the machine screens and block
  models have not been looked at in a client yet.

## Features (phase 1)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| Grid Power | — | Every producer and consumer belongs to the player who placed it. If that player is on a scoreboard team, the block joins the team's grid instead (and follows them if they change teams). GP is a rate, never stored. Each tick a grid's **capacity** is the sum of its producers' output and its **demand** the sum of its working consumers' needs; while demand exceeds capacity **every** consumer on that grid stops. Blocks placed by commands or machines have no owner and join no grid. Right-click any grid block to see its grid's capacity and demand. |
| Diminishing returns | — | Within one grid, the k-th strongest producer of one block type (from 0) adds `output × 32 / (32 + k)`. Mixing producer types pays more than repeating one. |
| Solar panel | `solar_panel` | 4 GP while the sun is above the horizon with open sky above, 2 GP in rain or a thunderstorm, 0 at night, underground or in dimensions without a day cycle. Day and night come from the time alone, so a storm halves output but never turns noon into night. |
| Lunar panel | `lunar_panel` | The night-time mirror of the solar panel. |
| Water mill | `water_mill` | 3 GP per horizontal side touching flowing (not source) water, up to 12 GP. |
| Wind mill | `wind_mill` | `(1 + blocks above sea level / 16, capped at 6) × open sides / 8 × weather` GP, where "open sides" counts air among the 8 blocks around it, rain multiplies by 1.5 and thunder by 2. Nothing below sea level or without a sky. |
| Resonator | `resonator` | GP consumer. Turns items into other items; while it has a recipe and room for the result it asks its grid for the recipe's GP every tick and advances only while the grid is powered. Recipes are data (`gridwright:resonating`), so packs can add their own. |
| Survivalist generator | `survivalist_generator` | Any furnace fuel at 10 energy/t for the fuel's full burn time. Needs no GP or crystals to build. |
| Furnace generator | `furnace_generator` | Any furnace fuel at 40/t for half the burn time (twice the survivalist's total). |
| Culinary generator | `culinary_generator` | Food at 40/t; `800 × hunger × (1 + 2 × saturation)` total. Returns bowls and bottles. |
| Lava generator | `lava_generator` | Lava buckets at 200/t for 2,500 ticks; returns the bucket. |
| Potion generator | `potion_generator` | Brewed potions at `20 × 2^steps`/t for 400 ticks (awkward 1 step, effect 2, long/strong 3, splash +1, lingering +2). Returns glass bottles from drinkable potions. Water bottles do not burn. |
| Ender generator | `ender_generator` | Ender pearls at 80/t, eyes of ender at 160/t, 400 ticks each. |
| Redstone generator | `redstone_generator` | Redstone dust at 40/t for 200 ticks, blocks for 1,800 ticks. |
| Crafting parts | `grid_crystal`, `machine_frame` | The crystal is resonated from redstone; the frame is the generator chassis. |

**Generators** hold 200,000 energy, push up to 2,000/t into every adjacent block that accepts
**Forge Energy** (Forge) or **Team Reborn Energy** (Fabric), and let other mods extract up to
2,000/t. They never accept energy. A generator pauses (keeping its fuel) while its store is full, and
only starts an item whose container (bucket, bowl, bottle) fits in its output slot. Hoppers and pipes
may insert fuel on any face and take only containers. Energy and GP are deliberately separate: there is
no GP↔energy conversion in phase 1.

### Resonating recipes

| Input | Output | GP | Ticks |
| --- | --- | ---: | ---: |
| 1 redstone dust | 1 grid crystal | 4 | 100 |
| 1 gravel | 1 flint | 1 | 40 |
| 3 rotten flesh | 1 leather | 2 | 100 |

### Progression and crafting

1. Craft a **solar panel** (glass, lapis, redstone, smooth stone) or a **water mill** (planks, sticks,
   iron, redstone) and a **resonator** (iron, redstone, note block, smooth stone). None of them need GP.
2. Resonate redstone into **grid crystals**; craft a **machine frame** (iron, smooth stone, crystal).
3. Frames make the specialised generators and the **wind mill**; a crystal makes the **lunar panel**.
   The **survivalist generator** (cobblestone around a furnace, redstone) needs nothing from Gridwright.

Every block and item has a survival recipe in [`src/main/resources/data/gridwright/recipes`](src/main/resources/data/gridwright/recipes).

## Features (phase 2b: utility items and blocks)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| Angel Ring | `angel_ring` | Carried anywhere in the inventory (Gridwright has no Curios/Trinkets dependency), it lets a survival or adventure player fly while their grid (their own, or their team's) has 16 GP to spare. It draws those 16 GP only while the player is actually flying, as a consumer, so an overloaded grid grounds them. Flight the ring granted is marked on the player and taken back when the ring leaves the inventory or the grid cannot pay, with 10 s of Slow Falling if they were in the air; flight from creative mode or another mod is never touched. |
| Golden Lasso | `golden_lasso` | Right-click a passive creature to carry it (one at a time; the lasso glints), right-click a block to let it out where you clicked. Hostile creatures, players, bosses (ender dragon, wither, elder guardian, warden and anything in `#gridwright:lasso_immune`, which also pulls in `#forge:bosses` / `#c:bosses` when present) and other players' pets are refused. A released creature keeps its data (name, age, health) but gets a fresh UUID. |
| Builder's Wand | `builders_wand` | Right-click a block: up to 9 copies of it go on the clicked face of the connected same-type blocks in that plane, nearest first, taken from the inventory (free in creative). Each placement goes through `ItemStack.useOn`, so Forge's `EntityPlaceEvent`, spawn protection, world border and adventure rules apply; Fabric has no place event, so the wand first asks `UseBlockCallback` (which claim mods hook) for each block. |
| Destruction Wand | `destruction_wand` | Right-click a block: up to 9 matching blocks on that face with open space in front break as if the player mined them with an iron tool, through `ServerPlayerGameMode.destroyBlock`, so Forge's `BreakEvent` and Fabric's `PlayerBlockBreakEvents` (claim mods) can cancel each one. Blocks harder than 5 (obsidian), unbreakable blocks and blocks with block entities are left alone. Main hand only. |
| Watering Can | `watering_can` | Right-click water to fill (400 uses). Hold right-click on a block: every 4 ticks, each plant in the 3x3x3 area around it has a 25% chance of a random growth tick, farmland there is fully wetted and fire is put out. |
| Bag of Holding | `bag_of_holding` | A 54-slot portable chest whose contents live in the item. Bags, shulker boxes and anything else that may not go in a shulker box are refused; the open bag's own inventory slot is locked against clicks, number-key and offhand swaps, throwing and double-click collecting, and the screen closes if that slot stops holding the bag. |
| Pocket Workbench | `pocket_workbench` | Right-click for a 3x3 crafting grid anywhere; it stays open while the workbench is in the inventory and returns the grid's items on close. (Gridwright's reading of the plan's "crafting table-style items".) |
| Drums | `stone_drum`, `iron_drum`, `reinforced_drum`, `gargantuan_drum` | One fluid each: 16, 256, 4,096 and 65,536 buckets. Forge `IFluidHandler` and Fabric `Storage<FluidVariant>` (transactional) on every side; buckets and other containers fill and empty them by hand; an empty hand shows the contents. Broken drums keep their fluid (empty ones stay stackable); the comparator reads 1 to 15 by fill. Stored amounts convert between Forge millibuckets and Fabric droplets on load. |
| Trash cans | `trash_can`, `fluid_trash_can`, `energy_trash_can` | Destroy everything given to them and hold nothing. The item can is a one-row screen and takes hopper, pipe and Forge item handler / Fabric item storage input; the fluid can accepts any fluid through either loader's fluid API (and buckets by hand); the energy can accepts any Forge Energy / Team Reborn Energy. |
| Compressed blocks | `compressed_<kind>` up to `octuple_compressed_cobblestone` | Nine of a tier craft into one of the next and one crafts back into nine: cobblestone to octuple (9^8), dirt to quadruple, gravel and sand to double, netherrack to sextuple. Each tier is harder to break than the last. EMC is exactly 9^tier of the base block, so compressing never gains EMC. |
| Spikes | `wooden_spike`, `iron_spike`, `golden_spike`, `diamond_spike` | Hurt any creature standing on them (one hit per invulnerability window): wooden 1 damage and never kills, iron 4, golden 2 and diamond 7. Golden and diamond kills count as a player's (a no-client stand-in player takes the credit), so they drop experience and player-only loot; iron kills do not. |
| Sound Muffler | `sound_muffler` | Sounds played within 8 blocks are played at 10% volume; several mufflers do not stack. Forge muffles on the client through `PlayLevelSoundEvent` (sounds the server sends and the client's own); Fabric, which has no sound event, uses one small mixin on the server's sound broadcast. |
| Wards | `spawn_ward`, `greater_spawn_ward`, `ender_ward` | Each ward stops one creature behaviour in the cube around it (measured from the ward's centre). The spawn ward stops hostile creatures spawning naturally within 16 blocks; the greater spawn ward reaches 48 blocks but draws 8 GP from its owner's grid through the normal consumer API, and stops working when the grid is short or the ward has no owner. The ender ward stops endermen teleporting from, within or into 16 blocks. Spawners, spawn eggs, commands and passive creatures are unaffected. Right-click a ward to see whether it is working. Forge uses `LivingSpawnEvent.CheckSpawn` (natural spawns only) and `EntityTeleportEvent.EnderEntity`; Fabric, which has neither event, uses one small mixin on each of the same vanilla checks. |

Recipes (all survival): spikes are three swords of the material over four of the material (four
spikes); drums are a cauldron wrapped in stone, iron, diamond and iron blocks, or netherite and crying
obsidian; trash cans are cobblestone and stone slabs around a cactus, sponge or redstone block; the
Angel Ring is gold, feathers, ghast tears, a diamond and a grid crystal; the lasso gold, string and an
ender pearl; the wands two sticks with gold or a diamond; the watering can iron around a bowl; the bag
leather, gold, two chests and an ender pearl; the muffler wool and a note block; the pocket workbench
a crafting table and leather; the spawn ward gold, torches and glowstone; the greater spawn ward four
spawn wards, glowstone and a grid crystal; the ender ward obsidian, iron and an eye of ender. See [`docs/PARITY.md`](docs/PARITY.md) for the loader-by-loader detail.

## Features (phase 2a: transfer)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| Transfer pipe | `transfer_pipe` | Carries nothing itself: a node's network is every pipe joined to the node's back or sides (up to 1,024 pipes) and every inventory or tank touching those pipes. Pipes join pipes, the back and sides of nodes, and anything with item or fluid storage. |
| Transfer node | `transfer_node` | Sits on an inventory (place it against the block). Once per second it pushes one item from that inventory to the next inventory on its network that accepts it, taking turns through the targets (nearest first). It never sends to the block it sits on or to other nodes. |
| Fluid transfer node | `fluid_transfer_node` | The same for tanks: 100 mB per operation. |
| Retrieval node | `retrieval_node` | The reverse: pulls one item per operation from the network into the inventory it sits on. |
| Fluid retrieval node | `fluid_retrieval_node` | Pulls 100 mB per operation from tanks on the network into its tank. |
| Speed upgrade | `speed_upgrade` | +1 operation per second each (counts up to 64, at most 4 operations a tick). 0.25 GP each. |
| Stack upgrade | `stack_upgrade` | A whole stack (64 items, or 1,000 mB) per operation. One counts. 2 GP. |
| World interaction upgrade | `world_interaction_upgrade` | When the node's front has no inventory/tank: a transfer node **mines** the block (as an unenchanted diamond pickaxe, only if every drop fits on the network; never block entities or unbreakable blocks), a retrieval node **places** a block item from the network, a fluid transfer node **pumps** a source block (1,000 mB) and a fluid retrieval node **pours** 1,000 mB as a source (no water in ultrawarm dimensions). Only for an owned node, inside the world border, and only where the loader's block-break event (`BlockEvent.BreakEvent` / `PlayerBlockBreakEvents.BEFORE`) lets the owner change that block, so claim and protection mods apply. One counts. 4 GP. |
| Item filter | `item_filter` | Right-click to edit: nine ghost slots (click with an item, shift-click from the inventory, click empty-handed to clear), allow/deny list and NBT matching. In a node's filter slot it limits what the node moves, mines or places. For fluid nodes, list a container of the fluid (a water bucket lists water). An empty allow list allows nothing. |

Upgrades go in a node's four upgrade slots and draw their GP from the **owner's grid**. They only work
while that grid is powered; without an owner or enough GP the node keeps working at the base rate
(one item or 100 mB a second) with no world interaction. A node without upgrades needs no GP or owner.
Movement is two-phase (simulate, then commit only what the destination accepts), so nothing is lost or
duplicated. Item transfer uses Forge item and fluid handler capabilities on Forge and the Fabric
Transfer API on Fabric (droplets are converted to whole millibuckets).

| Recipe | Ingredients |
| --- | --- |
| 8 transfer pipes | stone slabs, glass, redstone |
| Transfer node / fluid transfer node | transfer pipe, redstone, stone, and a hopper / bucket |
| Retrieval node / fluid retrieval node | the matching transfer node, ender pearl, grid crystal, redstone |
| 2 speed upgrades | gold, redstone, grid crystal |
| Stack upgrade | iron, redstone, chests, grid crystal |
| World interaction upgrade | diamond pickaxe, pistons, grid crystal, redstone block |
| Item filter | string, paper, redstone |

### EMC

Generated by `python3 scripts/emc.py generate gridwright` from [`emc/policy.json`](emc/policy.json)
(never edit the outputs by hand). Grid crystals are priced as one redstone; every resonating recipe,
including datapack additions, is checked for EMC gain in ProjectE and Item Alchemy. See
[`emc/README.md`](emc/README.md).

## Layout

- `src/main` — shared gameplay, registries, recipes, assets and data.
- `src/forge`, `src/fabric` — the loader adapters behind [`Platform`](src/main/java/ports/gridwright/platform/Platform.java):
  registration, menus, fuel times, energy push, Forge capabilities / Fabric Transfer + TR Energy.
- `fabric/` — the isolated Loom build; the root Gradle build compiles Forge.
- `src/gametestCommon` — GameTests shared by both loaders, with small loader adapters in
  `src/gametestForge` and `src/gametestFabric`. [`gametest-loader-only.txt`](gametest-loader-only.txt)
  is empty: every test runs on both loaders.

## Build and test

```sh
./scripts/gradle.sh :gridwright:build
./scripts/gradle.sh -p mods/gridwright/fabric build
python3 mods/gridwright/scripts/check_loader_parity.py
./scripts/gradle.sh :gridwright:runGameTestServer -PwithProjectE
./scripts/gradle.sh -p mods/gridwright/fabric runGametest -PwithItemAlchemy
python3 scripts/emc.py check
```

Textures are regenerated with `python3 mods/gridwright/scripts/generate_textures.py` and
`python3 mods/gridwright/scripts/generate_transfer_textures.py` (need Pillow).

## Phase 2+ plan

In order of usefulness to the pack; each is a separate PR with survival recipes, EMC and GameTests on
both loaders.

Phase 2b (the Angel Ring, lasso, wands, watering can, bag, workbench, drums, trash cans, compressed
blocks, spikes, sound muffler and wards) is done; see above.

1. ~~**Transfer nodes and pipes** (item and fluid) with speed, stack, filter and world-interaction upgrades~~ — phase 2a, above.
2. **GP ↔ energy converter** block, deliberately lossy, so GP can feed FE/TR machines and vice versa.
3. **Quantum Quarry** (GP-powered void-dimension quarry) — respects claim/protection mods.
4. **Ender Quarry** — area quarry that checks block-break permission for its owner.
5. **Mechanical User / Mechanical Miner** (fake-player blocks; protection-aware).
6. **Deep Dark** dimension.
7. **Wards** (mob-spawn control); an optional Curios/Trinkets slot for the Angel Ring.
8. More GP producers (fire, lava, manual, dragon-egg mills) and the **Rainbow generator**; lava
    fluid input for the lava generator; overclocking upgrades.

## Known gaps

- Client-side rendering (block models, screens, creative tab) is untested; only compiled.
- Phase 2b has no cursed lasso, creative wands or drum, wing cosmetics or spike enchantments. Wards
  cover natural spawns and enderman teleports only (no per-mob filters or other behaviours).
- No JEI/REI/EMI category for resonating recipes yet.
- Transfer nodes do not yet move energy or use Mekanism chemicals; there is no round-robin per item
  type, no redstone control and no node-to-node ender links.
- GP state is recomputed every tick from loaded blocks; unloaded producers stop counting, so a grid
  spread across unloaded chunks reports only what is loaded (by design, but worth knowing).
