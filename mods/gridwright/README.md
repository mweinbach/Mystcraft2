# Gridwright

A clean-room, Extra Utilities 2–style utility mod for Minecraft 1.19.2 on **Forge 43.5.0** and
**Fabric (Loader 0.16.14, Fabric API 0.77.0+1.19.2)**, built from one shared core. Phase 1 adds a
player-owned, cable-free power grid ("Grid Power", GP), the GP producers, a family of fuel generators
that make the pack's normal energy, and the resonator, the first GP consumer. Phase 2a adds transfer
nodes and pipes for items and fluids, their upgrades, retrieval nodes and filters. Phase 2c adds the
quantum and ender quarries, the mechanical miner and user, and the **Deep Dark**, a dark, ore-rich
cave dimension reached through a portal block. Phase 2d adds a lossy GP ↔ energy converter, more GP
producers, overclock upgrades and recipe-viewer categories. Phase 3 completes the generator family
(fifteen kinds), and adds the chunk loading ward, the mechanical crafter, the energy transfer node,
redstone control for every node and the cursed lasso.

Read [`NOTICE.md`](NOTICE.md) first: Gridwright contains no Extra Utilities 2 code, art, sounds or
language files, and is not affiliated with it. Every number below is Gridwright's own.

- Mod ID `gridwright`, package `ports.gridwright`, licence MIT ([`LICENSE`](LICENSE)).
- Status: **in progress** — logic and GameTests on both loaders. The Fabric jar (phase 2d) passed the
  Tekxit 4 client probe (models, textures, names, creative tab, EMI); no machine screen has been
  opened in a client yet, the Forge client has not been launched, and nothing from phase 3 has been
  seen in a client.

Read [DESIGN.md](DESIGN.md) when adding content. It records the approved 32x32 art direction,
materials, compact model patterns, interaction guidance and visual review expectations.

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
| Deep Dark portal | `deep_dark_portal` | Right-click to go to the Deep Dark at the same x/z, or back from it. Glows (light 10). See [Deep Dark](#deep-dark-phase-2c). |
| Deep Dark | dimension `gridwright:deep_dark`, biome `gridwright:deep_caverns` | An enclosed, unlit cave world between a bedrock floor and roof, with about 2–2.5× vanilla ore and monster-only spawns. |

**Generators** hold 200,000 energy, push up to 2,000/t into every adjacent block that accepts
**Forge Energy** (Forge) or **Team Reborn Energy** (Fabric), and let other mods extract up to
2,000/t. They never accept energy. A generator pauses (keeping its fuel) while its store is full, and
only starts an item whose container (bucket, bowl, bottle) fits in its output slot. Hoppers and pipes
may insert fuel on any face and take only containers. Energy and GP are deliberately separate: the
phase 2d power converter is the only bridge between them, and every round trip through it loses three
quarters of the power (see below).

## Quarries and mechanical blocks (phase 2c)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| Quantum quarry | `quantum_quarry`, `quarry_actuator` | Mines a **virtual** world, never real terrain: one block every 40 ticks (divided by 1 + speed level), drawn from a weighted worldgen-style table (about 2.5% vanilla ore in the overworld) and dropped through the block's real loot table. Needs a quarry actuator on each of its four horizontal sides and **32 GP + 16 GP per upgrade level**. The n-th block depends only on the world seed, the quarry's position, n, the marker and the upgrades. Modded ores in `c:ores` / `forge:ores` join the table at a small weight (ids naming the nether or the end go to those tables, `deepslate_` ids to the deep layers); `#gridwright:quantum_quarry_excluded` removes blocks (including the base stone). Waits, holding the mined block and drawing nothing, while its output is full; breaking a quarry drops what it holds. |
| Biome marker | `biome_marker` | Use it to record the biome you stand in; in the quantum quarry's marker slot it picks the table: nether and end biomes mine those dimensions' blocks, badlands have four times the gold, mountains emeralds, oceans clay and gravel, deserts sand. Plains without one. |
| Ender quarry | `ender_quarry` | Mines real terrain inside a closed loop of fences or walls (`#gridwright:quarry_boundary`, any shape, at most 1,024 boundary blocks and 128 blocks across) that touches it, layer by layer from its own height to the bottom of the world, at 0.25 blocks/t (× 1 + speed). Runs on **Forge Energy / Team Reborn Energy** (100,000 store, accepts 10,000 per transfer) at 400 per block + 100 per speed, 200 per fortune level, 200 for silk touch. Skips air, fluids, unbreakable blocks, `#gridwright:unminable` (wither-immune blocks and spawners by default), block entities, and every block its owner may not break; works only in loaded chunks. |
| Mechanical miner | `mechanical_miner` | Breaks the block in front of it as its owner would with the tool in its tool slot (or bare-handed): a player's break time, only the drops that tool harvests, tool wear. 4 GP while working; pauses on redstone. |
| Mechanical user | `mechanical_user` | Every 20 ticks right-clicks the block in front with the next stack from its nine input slots (places blocks, flips levers, fills buckets, uses bone meal…); if that does nothing, uses the item in the air. Same-item leftovers go back to the input; anything else to its output (overflow drops in front). 2 GP while working; waits while no output slot is free; pauses on redstone. |
| Upgrades | `speed_upgrade` (the transfer nodes' speed upgrade), `fortune_upgrade`, `silk_touch_upgrade` | Go in a quarry's three upgrade slots: speed counts up to 8 in a quarry, fortune up to 3, silk touch replaces fortune. Quarries mine with a netherite pickaxe carrying these enchantments. |

**Protection.** Machines act only as their owner, through a fake player with the owner's name and
UUID; a machine placed without an owner does nothing. Before any break the quarries and the miner
check spawn protection, the world border and adventure-mode rules, then fire the loader's break event
(Forge `BlockEvent.BreakEvent`, Fabric `PlayerBlockBreakEvents.BEFORE`), so claim mods can refuse it;
refused blocks are skipped (ender quarry) or retried (miner, shown as *Protected*). The user's click goes
through Forge `RightClickBlock` / Fabric `UseBlockCallback`; a refused click ends the action, and its use
in the air happens only where the owner may also break the block in front and every block the item
could aim at. GameTests on both loaders install a stand-in claim listener and check that protected
blocks are neither mined nor used.

All four have nine output slots that hoppers and pipes may empty from any face; their other slots
accept only what they use. See [`docs/PARITY.md`](docs/PARITY.md) for how they compare with the
public descriptions of the originals.

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
4. The **mechanical miner** and **user** are cobblestone, a dispenser, redstone and an iron pickaxe or a
   piston. The **ender quarry** is a frame with obsidian, ender pearls, a diamond pickaxe and a hopper.
5. The **quantum quarry** (frame, crystals, eyes of ender, two diamond blocks) and its four **actuators**
   (frame, obsidian, diamond pickaxe each) are the late game; **upgrades** use a crystal with sugar and
   redstone (speed), lapis blocks, gold and diamonds (fortune) or emeralds and string (silk touch).

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

## Features (phase 2d: power extras)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| Power converter | `power_converter` | Gridwright's only bridge between GP and **Forge Energy** (Forge) / **Team Reborn Energy** (Fabric), and a deliberately lossy one. Sneak-use it with an empty hand to switch mode; use it to see what it is doing. **Making energy** (default): while it has room for 64 energy it asks its owner's grid for 16 GP as a normal consumer, and on every tick the grid is powered it makes 64 energy (4 per GP), pushes up to 1,024/t into its neighbours and lets other mods extract; it never accepts energy. **Making GP**: it accepts up to 1,024 energy/t and never gives any out; while its grid has any demand it burns up to 256/t into GP at **16 energy per GP** (at most 16 GP), as a producer with the usual diminishing returns. Stores 16,000. |
| Fire mill | `fire_mill` | 8 GP while fire, soul fire or a lit campfire is directly below it. |
| Lava mill | `lava_mill` | 4 GP per horizontal side touching a lava **source**, up to 16 GP. |
| Manual mill | `manual_mill` | 16 GP while wound. Each use (holding use repeats it about every 4 ticks) adds 10 ticks of turning, up to 40, and costs the player 0.025 exhaustion. Only a real player can crank it: a connected, living, non-spectator player in the server's player list, so fake players, machines and deployers cannot. |
| Dragon egg mill | `dragon_egg_mill` | 64 GP while a dragon egg sits on top of it. |
| Rainbow generator | `rainbow_generator` | 512 GP while one generator of **every** kind (all fifteen since phase 3) is running within 4 blocks (a 9x9x9 cube centred on it). "Running" means the generator stored energy in the last second, so a full or unfuelled generator does not count. Another rainbow generator within 8 blocks stops both, so one set of generators never feeds two. Use it to see which kinds are missing. |
| Lava generator input | — | The lava generator now also takes **lava piped in** through Forge `IFluidHandler` or Fabric `Storage<FluidVariant>` on any face (insert-only, 4-bucket tank, lava only), so phase 2a's fluid transfer nodes can feed it. Each bucket's worth burns like a lava bucket, leaving no bucket behind; fuel in the slot is used first. Other generators refuse fluid. |
| Overclock upgrade | `overclock_upgrade` | Use on any generator to install (up to 4, stacks to 4); sneak-use the generator with an empty hand to take them back; breaking it drops them. Each upgrade adds the fuel's base rate again but costs a tenth of its total: with 4 a fuel burns 5x as fast for 60% of its energy. Speed is bought with efficiency, never made from nothing. |

**Why the converter cannot loop.** One GP drawn becomes 4 energy per tick, and 4 energy per tick burned
becomes a quarter of a GP; going the other way, 16 energy becomes 1 GP and 1 GP becomes 4 energy. Every
round trip, through any number of converters, keeps at most 25%. Gridwright's generators never accept
energy and overclocking only trades efficiency for speed, so the only way from energy back to GP is the
converter. A loop through another mod's machines (GP → energy → their generator or storage → energy →
GP) would need those machines to multiply energy by four just to break even. `PowerGameTests` checks
that 16 GP in returns 4 GP, that a primed converter loop with no other producer runs down to zero
without its stored energy ever rising, and that a generator's 10 energy/t becomes 0.625 GP.

### Recipe viewers (JEI, REI, EMI)

The resonator's recipes have a **Resonating** category (input, output, GP and time, with the resonator as
its workstation) in JEI (Forge and Fabric), REI and EMI. All three are optional: Gridwright compiles
against their APIs only and ships none of them. JEI finds the plugin by `@JeiPlugin` on Forge and the
`jei_mod_plugin` entrypoint on Fabric; EMI by `@EmiEntrypoint` / the `emi` entrypoint; REI by
`@REIPluginClient` on a small Forge subclass / the `rei_client` entrypoint. With EMI and JEI both
installed, the JEI plugin stays quiet so EMI does not list the category twice. All three plugins draw
from one viewer-independent list ([`ResonatingDisplays`](src/main/java/ports/gridwright/compat/ResonatingDisplays.java)).
`-PwithJei`, `-PwithRei` and `-PwithEmi` add the viewers to the development runs, alone or together.

Recipes: the fire mill is bricks, iron, flint and a machine frame; the lava mill obsidian, iron, a
bucket and a frame; the manual mill planks, sticks, a lever and iron; the dragon egg mill end stone,
obsidian, eyes of ender and a frame (the egg itself is not consumed); the rainbow generator diamonds,
grid crystals, glowstone and a frame; the power converter iron, copper, grid crystals and a frame; the
overclock upgrade redstone, gold and a grid crystal.

### EMC

Generated by `python3 scripts/emc.py generate gridwright` from [`emc/policy.json`](emc/policy.json)
(never edit the outputs by hand). Grid crystals are priced as one redstone; every resonating recipe,
including datapack additions, is checked for EMC gain in ProjectE and Item Alchemy. See
[`emc/README.md`](emc/README.md).

## Features (phase 3)

| Feature | Blocks/items | Behaviour |
| --- | --- | --- |
| TNT generator | `tnt_generator` | Gunpowder at 80/t for 200 ticks, TNT at 160/t for 600 ticks. |
| Pink generator | `pink_generator` | Anything in `#gridwright:pink_generator_fuels` (pink dye, wool, concrete, glass, tulips, peonies…; packs add their own) at 60/t for 400 ticks, blocks for 1,200. Shulker boxes are not in the tag, so their contents can never be burnt. |
| Nether star generator | `nether_star_generator` | A nether star at 500/t for 24,000 ticks (20 minutes, 12 million energy). |
| Death generator | `death_generator` | Rotten flesh, bones and string at 40/t for 200 ticks; spider eyes and phantom membranes 60/t for 300; bone blocks 40/t for 1,800; zombie and skeleton skulls and creeper heads 200/t for 1,200; wither skeleton skulls 400/t for 2,400. |
| Enchantment generator | `enchant_generator` | Enchanted books and gear: 40/t per enchantment level (curses count for nothing, at most 1,000/t) for 400 ticks. The enchantments burn off and the item comes back: a book as a plain book, gear with only its curses, its damage and name unchanged. |
| Slime generator | `slime_generator` | Slimeballs 40/t for 400 ticks, slime blocks for 3,600, magma cream 80/t for 400. |
| Frosty generator | `frosty_generator` | Snowballs, snow blocks, powder snow buckets (bucket returned) and ice; packed ice burns as nine ice and blue ice as nine packed ice. |
| Halitosis generator | `halitosis_generator` | Dragon's breath at 400/t for 600 ticks, returning the bottle. |
| Chunk loading ward | `chunk_loading_ward` | Keeps the 3×3 chunks around it loaded (as `/forceload` would: entity-ticking) while its owner's grid pays **36 GP** (4 per chunk). It must first be put **under contract**: use it with a golden or cursed lasso holding a villager, which is used up. The contract stays with the dropped item. Each player may run `gridwrightChunkWardsPerPlayer` wards at once (game rule, default 4); lowering it makes wards over the new limit let go of their chunks. A ward its grid has not paid for in 10 seconds lets its chunks go, so an unpowered ward cannot keep a base loaded. The wards holding chunks are saved with the world and get their tickets back when the server starts. Right-click to see what it is doing. |
| Mechanical crafter | `mechanical_crafter` | An autocrafter on 4 GP. Its 3×3 grid is the pattern and the ingredient store at once: lay the ingredients out as a crafting table wants them, then let hoppers or pipes top them up. Every 10 ticks, while every slot can spare an ingredient, it crafts one; results and leftovers (buckets, bottles) go to its nine output slots, which are the only ones pipes may empty. The last of each stackable ingredient stays as the pattern; unstackable ones (milk buckets, tools) are used up and the crafter remembers which slot each goes back into. Pipes only add an ingredient where the pattern wants it, to the emptiest such slot. Pauses on redstone. |
| Energy transfer node | `energy_transfer_node` | Sits on anything that gives **Forge Energy / Team Reborn Energy** and every tick moves up to 1,000 energy (plus 1,000 per powered speed upgrade, ×4 with a powered stack upgrade) to the energy stores on its pipe network, taking turns. It takes from its source only what the targets accept. Pipes now also join energy stores. |
| Node redstone control | all nodes | A button on every node's screen switches between ignoring redstone (the default), working only with a signal and working only without one. A node switched off draws no GP. |
| Cursed lasso | `cursed_lasso` | A golden lasso that also takes hostile creatures. Bosses, `#gridwright:lasso_immune`, players and other players' pets are still refused. |

The rainbow generator now needs one of each of the fifteen generators running, a nether star
generator included, and gives 512 GP instead of 128.

Recipes: each new generator is a machine frame with its fuel on top and around it (a nether star and
crying obsidian over a redstone block for the nether star generator, dragon's breath and purpur over
an end crystal for the halitosis generator); the chunk loading ward is eyes of ender, grid crystals
and obsidian around a frame; the mechanical crafter a crafting table, pistons, iron and grid crystals;
the energy transfer node a transfer pipe, redstone, a redstone block and stone; the cursed lasso a
golden lasso with soul sand, blaze powder and ghast tears.

## Deep Dark (phase 2c)

**Getting there.** Craft a **Deep Dark portal** (four obsidian, an eye of ender, three cobbled
deepslate and a machine frame), place it and right-click it. A moment later you are in the Deep Dark
at the same x and z. Gridwright looks for two blocks of air over a sturdy floor with no lava or fire
next to it, up to 3 blocks sideways and 12 up or down from your height (kept between y 6 and 119);
if there is none it digs a 3×3×3 room, gives it a stone floor where one is missing and seals any
fluid or gravel around it. Bedrock, unbreakable blocks and block entities are never removed. A
glowing **return portal** is placed beside you (or under you if there is no room), or reused if one
already touches the spot you stand in. Arrivals always keep the whole 3×3 room inside the world border.

**Getting back.** Right-click any Deep Dark portal in the Deep Dark. You return to the exact place and
dimension you entered from (remembered per player in the overworld's saved data, so it survives
restarts). If that spot has since been built over (or the floor under it is gone), the nearest clear spot is used; if the dimension
no longer exists, or there is no record (for example you arrived another way), you come out on the
overworld surface at your current x/z, or at world spawn if that column is lava or void (surface
fallbacks move to the nearest column inside the world border and avoid magma, cactus, berry bushes
and the like). If not even the world spawn is safe, the portal says so and you stay put. The return
portals Gridwright places are marked `generated=true` and **drop nothing** when broken, so trips cannot
mint free portals; carry a crafted one if you want a spare. A player can use a portal at most once
every 3 seconds, so a double click cannot bounce you straight back. Fake players (machines) never
travel. If the dimension is missing (a datapack removed it) the portal says so and does nothing.

**The dimension** (all datapack JSON under
[`src/main/resources/data/gridwright`](src/main/resources/data/gridwright), so packs can override any
part of it):

| Part | File | Design |
| --- | --- | --- |
| Dimension | `dimension/deep_dark.json` | Noise generator with the settings below and a fixed `gridwright:deep_caverns` biome. |
| Dimension type | `dimension_type/deep_dark.json` | y 0–127, no skylight, ceiling, ambient light 0, fixed night clock, `natural: false` (compasses and clocks spin), monsters spawn at block light 0, beds and respawn anchors do **not** work (explode, as in the Nether), no raids, not piglin-safe, 1:1 coordinates. |
| Terrain | `worldgen/noise_settings/deep_dark.json` | Solid stone with large caverns (vanilla `cave_cheese` noise, stretched) and noodle tunnels; density is forced solid toward y 0 and y 127, so the bedrock floor (y 0–4) and roof (y 123–127) are never breached. Deepslate below y≈48. No aquifers and sea level 0, so caves are dry; water comes from springs and lava from rare underground lava lakes and from vanilla caves dug below y 9. |
| Biome | `worldgen/biome/deep_caverns.json` | Near-black fog, no weather, cave ambience. Monsters only (zombie, skeleton, spider, creeper, cave spider, enderman, zombie villager, witch) with weights at or above the overworld's and larger groups; no passive, ambient or water mobs. Dungeons, springs, rare lava lakes, vanilla cave carvers. |
| Ores | `worldgen/placed_feature/deep_dark/*.json` | Vanilla configured ore features, per chunk: coal 60 (vanilla upper band 30, 2×), iron 25 (10, 2.5×), copper 32 (16, 2×), gold 10 (4, 2.5×), redstone 10 (4, 2.5×), lapis 5 (2, 2.5×), diamond 12 (7, ≈1.7×, only below y 48, always rarer than iron), plus granite, diorite, andesite, tuff and gravel blobs at about vanilla rates. Because the Deep Dark is only 128 blocks tall, ore per block of stone is higher still. |
| Biome tag | `tags/worldgen/biome/is_deep_dark.json` | `#gridwright:is_deep_dark` lists the biome so packs can target it. |

**Modded ores and features.** The biome is deliberately **not** in `#minecraft:is_overworld` (or any
`c:`/`forge:` overworld tag): modded overworld features would otherwise flood it unpredictably. To add
another mod's ore, target `#gridwright:is_deep_dark` with a Forge biome modifier
(`forge:add_features`) or Fabric `BiomeModifications.addFeature(BiomeSelectors.tag(...))`, or override
`deep_caverns.json` in a datapack.

**Mystcraft.** `gridwright:deep_dark` is an ordinary static datapack dimension, so it sits alongside
Mystcraft without changes:

- Mystcraft treats only `mystcraft:age_<n>` levels with `AgeLevelData` as Ages; the Deep Dark is never
  one, so decay, instability, Age visuals (gated on the `mystcraft:age` effects id), Age weather and
  Age PvP rules do not apply to it, and its id cannot collide with Mystcraft's namespace.
- Linking books treat it like the Nether or any other non-Age dimension: a book written there links
  back to the exact spot it was written (Mystcraft refuses the link if that spot is later filled in),
  the dimension gets a stable identity from `DimensionIdentityData`, and `LinkPermissions` can allow
  or deny it by id.
- Portal trips use vanilla `ServerPlayer#teleportTo`, which fires Forge's `PlayerChangedDimensionEvent`
  and Fabric's `AFTER_PLAYER_CHANGE_WORLD`, so Mystcraft's per-player dimension identity stays current
  and players who log out in the Deep Dark are not ejected on login.
- Mystcraft's library and crystal exploration features only generate in the overworld and Ages, and the
  Deep Caverns biome is not in the tags Mystcraft's `has_exploration` list uses.

## Layout

- `src/main` — shared gameplay, registries, recipes, assets and data.
- `src/forge`, `src/fabric` — the loader adapters behind [`Platform`](src/main/java/ports/gridwright/platform/Platform.java):
  registration, menus, fuel times, energy push, Forge capabilities / Fabric Transfer + TR Energy.
- `fabric/` — the isolated Loom build; the root Gradle build compiles Forge.
- `src/gametestCommon` — GameTests shared by both loaders, with small loader adapters in
  `src/gametestForge` and `src/gametestFabric`. [`gametest-loader-only.txt`](gametest-loader-only.txt)
  is empty: every test runs on both loaders.

## Build and test

Fabric is the primary loader (see [AGENTS.md](AGENTS.md)); run its build and GameTests first.

```sh
./scripts/gradle.sh -p mods/gridwright/fabric build
./scripts/gradle.sh -p mods/gridwright/fabric runGametest -PwithItemAlchemy
./scripts/gradle.sh :gridwright:build
./scripts/gradle.sh :gridwright:runGameTestServer -PwithProjectE
python3 mods/gridwright/scripts/check_loader_parity.py
# Optional recipe viewers, alone or together (CI runs all three on both loaders):
./scripts/gradle.sh -p mods/gridwright/fabric runGametest -PwithJei -PwithEmi -PwithRei
./scripts/gradle.sh :gridwright:runGameTestServer -PwithJei -PwithEmi -PwithRei
python3 scripts/emc.py check
```

Textures are regenerated with `python3 mods/gridwright/scripts/generate_textures.py` and
`python3 mods/gridwright/scripts/generate_transfer_textures.py` (need Pillow); `generate_textures.py` also
draws the phase 2d tiles from `power_textures.py` and the phase 3 tiles from `phase3_textures.py`. All of
it is placeholder programmer art.

The [32px texture and compact-model proposal](docs/texture-plan.md) includes before/after sheets,
an offline review page and an optional preview resource pack. It is a partial art study outside the
production assets; smaller models still need matching collision and occlusion changes before shipping.

## Phase 2+ plan

In order of usefulness to the pack; each is a separate PR with survival recipes, EMC and GameTests on
both loaders.

Phase 2b (the Angel Ring, lasso, wands, watering can, bag, workbench, drums, trash cans, compressed
blocks, spikes, sound muffler and wards), phase 2c (items 3 to 6) and phase 2d (items 2 and 8, plus
the JEI/REI/EMI resonating category) are done; see above.

1. ~~**Transfer nodes and pipes** (item and fluid) with speed, stack, filter and world-interaction upgrades~~ — phase 2a, above.
2. ~~**GP ↔ energy converter** block, deliberately lossy, so GP can feed FE/TR machines and vice versa~~ — phase 2d, above.
3. ~~**Quantum Quarry**~~ — done in phase 2c (virtual table rather than a void dimension).
4. ~~**Ender Quarry**~~ — done in phase 2c.
5. ~~**Mechanical User / Mechanical Miner**~~ — done in phase 2c.
6. ~~**Deep Dark** dimension.~~ Done in phase 2c (see [Deep Dark](#deep-dark-phase-2c)).
7. ~~**Wards** (mob-spawn control)~~ — phase 2b. An optional Curios/Trinkets slot for the Angel Ring is still open.
8. ~~More GP producers (fire, lava, manual, dragon-egg mills) and the **Rainbow generator**; lava
    fluid input for the lava generator; overclocking upgrades~~ — phase 2d, above.
9. ~~The rest of the generator family, the chunk loading ward, the mechanical crafter, the energy
    transfer node, node redstone control and the cursed lasso~~ — phase 3, above.

Phase 4 candidates: the **terraformer** (a multiblock that changes biomes, using the biome marker), an
**indexer**-style storage terminal for pipe networks, and a GP **enchanter**.

## Known gaps

- Client-side rendering (block models, screens, creative tab) is untested; only compiled.
- Phase 2b has no cursed lasso, creative wands or drum, wing cosmetics or spike enchantments. Wards
  cover natural spawns and enderman teleports only (no per-mob filters or other behaviours).
- Transfer nodes do not move Mekanism chemicals; there is no energy retrieval node, no round-robin per
  item type and no node-to-node ender links.
- The quarries do not chunk-load by themselves (a chunk loading ward beside one keeps its 3×3 chunks
  loaded); the ender quarry waits while the chunks it mines are unloaded, and re-reads its boundary
  only when it is broken and placed again.
- The mechanical crafter has no speed upgrades, ignores recipes that depend on the crafting player,
  and checks a changed grid against the actual contents first, so a partly used-up grid that happens
  to form another recipe switches to it.
- The chunk loading ward's 36 GP is counted like any consumer, so a grid that cannot pay for two wards
  at once overloads and runs neither; on Forge its game rule is registered by reflection into vanilla's
  private rule factory.
- The mechanical user does not attack or interact with entities (no shearing sheep or milking cows).
- The Deep Dark is datapack-defined, so on worlds created without Gridwright it appears on the next
  load; the client has not been launched, so its look (fog, darkness, the "experimental settings"
  warning some versions show for datapack worldgen) is unverified. Fabric's GameTest server builds its
  world from built-in registries and has no Deep Dark, so the datapack is fully decoded in a private
  registry copy on both loaders; Forge's GameTest server does generate the dimension, where arrival is
  also tested in real terrain. The teleport itself (vanilla `teleportTo`) is not run end to end, since
  test players have no network connection.
- No damage-over-time "darkness" effect in the Deep Dark (see [`docs/PARITY.md`](docs/PARITY.md)).
- The JEI, REI and EMI categories are compiled and their plugins checked on a server (REI's displays
  in full; EMI's stacks need a client, so only its wiring), but their screens have not been looked at
  in a client.
- The power converter has no screen and no redstone control; the manual mill has no animation.
- GP state is recomputed every tick from loaded blocks; unloaded producers stop counting, so a grid
  spread across unloaded chunks reports only what is loaded (by design, but worth knowing).
