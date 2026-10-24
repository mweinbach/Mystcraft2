# Gridwright — clean-room notice

Gridwright is an original Minecraft mod written for this repository. It offers gameplay *in the
style of* Extra Utilities 2 (a player-owned, cable-free power grid, a family of fuel generators, and
a power-consuming transformation machine) so that a 1.19.2 pack can have those mechanics on Forge and
Fabric. It is **not** a port, fork, decompilation or translation of Extra Utilities 2, and it is not
affiliated with or endorsed by Extra Utilities 2 or its author, RWTema.

## Why clean-room

Extra Utilities 2's source repository states "All rights reserved". Its code, textures, models,
sounds and language files therefore cannot be used here. Unlicensed third-party rewrites of it are
treated the same way.

## What was not used

- No Extra Utilities 2 source code was cloned, read, copied, translated or paraphrased.
- No Extra Utilities 2 textures, models, sounds, language files or other assets were used.
- No unlicensed rewrite of Extra Utilities 2 (for example `Hellish-Mods/reignited-utilities`, which
  has no licence) was cloned, read or used.
- The names "Extra Utilities" and `extrautils2` are not used for this mod's name, mod ID, package,
  registry names or assets.

## What was used

Only public descriptions of how the original *behaves* from a player's point of view, reached through
web search summaries of community wiki pages and forum/video descriptions (the FTB wiki pages
themselves refused automated access during this work):

- Grid Power is owned by a player rather than carried by cables; it is a rate, not stored energy.
- Producers (solar and lunar panels, water and wind mills, and others) add power; machines draw it.
- When demand exceeds supply every consumer on the grid stops, rather than some of them.
- Several producers of one type give diminishing returns.
- The generator family burns different item classes (any furnace fuel, food, lava, potions, ender
  items, redstone) into a standard energy unit, and the "resonator" turns items into other items
  using Grid Power.

Every number, formula, recipe, item and block name in Gridwright (GP outputs, diminishing-returns
constant, generator rates and durations, buffer sizes, resonator costs and times, crafting recipes)
was chosen for this repository and balanced against its other ports. Where the result resembles the
original's behaviour, that is because it implements the same publicly described idea, not because
any of the original's data was copied.

### Phase 2b (utility items and blocks), 2026-10-23

The same rules applied. The live pages could not be fetched (no network access from the build
machine, and the search tool often answered from its own summary of the wikis rather than a fetched
page), so these are the public, player-facing descriptions that were used, with where they came from:

- Angel Ring: creative-style flight while carried, drawing the owner's Grid Power, lost when the grid
  is short. Search summaries of the FTB Wiki page "Angel Ring (Extra Utilities 2)" and its "Grid
  Power" page. Gridwright's cost (16 GP, only while flying) is its own.
- Golden Lasso: right-click a passive creature to carry it, right-click a block to let it out; no
  hostile creatures, players or bosses. Search summaries of the FTB Wiki "Golden Lasso" page.
- Builder's Wand and Destruction Wand: place, or break, up to 9 blocks matching the clicked block on
  the clicked face. Search snippets of <https://ftb.fandom.com/wiki/Builder%27s_Wand_(Extra_Utilities_2)>
  and <https://ftb.fandom.com/wiki/Destruction_Wand>.
- Watering Can: filled from water, held down on plants to give them extra growth ticks in a small
  area. Search summaries; the area, rate and capacity are Gridwright's.
- Bag of Holding: a portable double chest; a bag cannot go inside a bag. Search summaries.
- Drums: one fluid each, kept when broken; 16, 256, 4,096 and 65,536 buckets. Search snippets of
  <https://ftbwiki.org/Drum_(Extra_Utilities_2)>.
- Trash cans: an item can that voids anything piped in, and fluid and energy versions. Search summaries.
- Compressed blocks: nine make one of the next tier and one crafts back to nine; cobblestone to
  octuple (9^8), dirt to quadruple, gravel and sand to double, netherrack to sextuple. Search summaries
  of the FTB Wiki "Compressed Cobblestone" page.
- Spikes: wooden spikes never kill, iron spikes kill without experience, better spikes count as a
  player's kill (experience and player-only drops). Search summaries; the summaries disagreed on
  which of golden and diamond gives what, so Gridwright gives both the player-kill credit and makes
  diamond hit harder.
- Sound muffler: quietens sounds within about 8 blocks. Search summaries (which were unsure whether
  this block is in Extra Utilities 2 or only its predecessor).
- The **Pocket Workbench** (a carried crafting table) is Gridwright's own reading of "crafting
  blocks/table-style items" in its plan; the summaries found no such item in Extra Utilities 2.
- **Wards** (spawn ward, greater spawn ward, ender ward) are Gridwright's own design, added to the
  phase at the coordinator's request. The idea of a placed block that keeps hostile spawns or
  endermen out of an area is a common one across mods and was taken only as that general idea; no
  page about any particular mod's ward or spawn-blocking block was consulted. Radii, GP cost and
  recipes are Gridwright's.

No textures, models, sounds, language strings or recipe layouts were taken from any of these pages;
every value and recipe above was chosen for this repository.

## Art

All textures were generated by [`scripts/generate_textures.py`](scripts/generate_textures.py) (with
[`scripts/utility_textures.py`](scripts/utility_textures.py) for phase 2b), small deterministic scripts
written for this mod that draw flat programmer-art tiles. The machine
screens are drawn with plain rectangles and no texture.

## Name and ID check

Before choosing the name "Gridwright" and the mod ID `gridwright` (2026-10-23):

- Modrinth search for "gridwright" returned no projects, and `modrinth.com/mod/gridwright` was 404.
- CurseForge slug lookup for `gridwright` (via the public cfwidget API) found no project.
- GitHub repository search for "gridwright minecraft" returned no results.

## Licence

Gridwright's code and art are released under the MIT licence in [`LICENSE`](LICENSE). The bundled
Team Reborn Energy library in the Fabric JAR is MIT-licensed by its authors.
