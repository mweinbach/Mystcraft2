# JurassiCraft for Minecraft 1.19.2

Status: **in progress**. This Forge port includes the ten species enabled by
the imported 1.12.2 version, fossil deposits and nests, plaster wrapping,
cleaning and grinding, DNA processing, dinosaur incubation and coelacanth
cultivation. It also includes prehistoric plants and trees, feeding, breeding,
harvestable dinosaur carcasses, skeletal displays, low-security electric
fencing, vehicles, park signs, murals, insect cultures, goats, darts, records,
journals, action figures, and a field guide. Geneticist and paleontologist
villagers, their village buildings, the visitor centre and raptor paddock are
included. Cartographers can sell a map to the visitor centre. Many original
textures, sounds, animations, and model components are reused.

Natural goat and dinosaur spawning are disabled by default, as upstream. They
can be enabled in `config/jurassicraft-common.toml`. This file also controls
natural fossil, plant, and structure generation. Restart after changing the
generation settings; existing chunks and structures are unaffected.

While seated in a vehicle, use Ctrl+1 through Ctrl+9 to change seats. Press
the configurable vehicle record player key (R by default) with a music disc in
your main hand to insert or swap it, or with an empty hand to eject it.
The helicopter pilot can toggle autopilot with Ctrl+A and rotation lock with
Ctrl+D; its rotor needs time to reach flying speed.

Right-click an imprinted dinosaur with an empty hand to choose wander, follow,
or sit. Sneak-right-click it once it is more than 75% grown to access its
inventory. An owned, sitting microraptor can ride on your shoulder; press C by
default to put it down. Dinosaurs form families, care for young, and retain
their relationships across saves. Dinosaur metabolism, growth, and herd behavior
also have game rules. Sneak-use a dinosaur spawn egg in the air to cycle its
gender, or use it on a block while sneaking to spawn a baby.

JEI and Jade are optional; the mod supplies machine recipe views, recipe
transfer, and dinosaur and machine tooltips when they are installed. The mod
JAR does not bundle either dependency.

The port is still in progress. World-generation distributions and some machine,
animal, and vehicle behaviors are approximations. Plant fossils replace tagged
coal ores from the usual ore generation stage; untagged ores and ores generated
later are not replaced. Some machine and animal animations are simplified;
species-specific procedural motion differs from upstream. The
hybridizer mode has no recipes because the imported version registers no hybrid
species. Large structures, interactions with other mods, and some audiovisual
details still need broader in-game testing. Village buildings use weighted
jigsaw pools and do not enforce the original per-village building limits.
As upstream, a surviving lone dinosaur parent does not automatically form a new
family.
Carcasses form and drop carried items before Forge's death event is fired when
the carcass is exhausted, matching upstream. Visitor centres can be found with
`/locate structure jurassicraft:visitor_centre`;
third-party structure search APIs still skip their custom placement. Paddock
rarity is approximate, and the missing upstream cryonics loot table uses
laboratory loot. The dart gun has a new survival recipe. Genetic item variants
use separate IDs rather than legacy item damage values, and there is no
migration for old worlds.

The original 1.12.2 source, assets, and build files remain under
[upstream/](upstream/) as a porting reference; that snapshot is not compiled or
packaged. See [UPSTREAM.md](UPSTREAM.md) for the baseline and licensing
history. The workspace's LGPL-3.0-only license does not establish the license
of the imported upstream files.

Build from the workspace root with `./scripts/gradle.sh :jurassicraft:build`.
Validate game data with `./scripts/gradle.sh :jurassicraft:runData` and run the
resource checks with `python3 -m unittest discover -s mods/jurassicraft/tools/tests`.
To launch a development client with optional integrations, add `-PwithJei` and/or
`-PwithJade` to `./scripts/gradle.sh :jurassicraft:runClient`.

The optional Forge GameTest server exercises in-world behavior. Run it from
the workspace root:

```sh
./scripts/gradle.sh :jurassicraft:runGameTestServer
```

The run creates a test world and logs in `mods/jurassicraft/run/gameTestServer`.
Check the console for the number of tests run and their pass/fail summary; the
server exits after the tests finish. This test run is not part of the normal
build.
