# Environment graphical fixture

Use a disposable test world and matching rebuilt server/client (visual protocol3).
This creates a normal classic descriptive book; activate its panel to allocate
an Age. It does not change an existing saved Age's frozen settings.

```
/give @p mystcraft:descriptive_book{display:{Name:'{"text":"Environment Check"}'},Props:{Seed:"22345"},Pages:[{linkpanel:{}},{symbol:"mystcraft:terrainflat"},{symbol:"mystcraft:biome",Biome:"minecraft:plains"},{symbol:"mystcraft:bioconsingle"},{symbol:"mystcraft:weatherrain"},{symbol:"mystcraft:lightingbright"},{symbol:"mystcraft:modcolorcyan"},{symbol:"mystcraft:colorcloud"},{symbol:"mystcraft:modcolormagenta"},{symbol:"mystcraft:colorgrass"},{symbol:"mystcraft:modcolorblue"},{symbol:"mystcraft:colorfoliage"},{symbol:"mystcraft:modeast"},{symbol:"mystcraft:rainbow"},{symbol:"mystcraft:nohorizon"},{symbol:"mystcraft:modfull"},{symbol:"mystcraft:modnorth"},{symbol:"mystcraft:modnoon"},{symbol:"mystcraft:sunnormal"},{symbol:"mystcraft:moondark"},{symbol:"mystcraft:starsdark"}]} 1
```

Expect cyan cloud tint, magenta grass and blue biome-tinted leaves. Native fixed
spruce/birch leaf tints are outside the original biome-color-provider path.
Rainbow is a broad translucent stationary colored arc/ring; turn through every
heading and look above the horizon. It must remain present at night and during
rain. East supplies yaw-90; time does not rotate the rainbow. Rain is liquid even
in cold/dry biomes. A separate fixture replacing WeatherRain with WeatherSnow
should show flakes in warm plains and permit snow layers there; WeatherCloudy
should darken the environment without drops/flakes; WeatherStorm should also
thunder. Do not treat rare random lightning absence in a short capture as failure.

Enable clouds in video settings and compare their height near128 with the same
fixture containing Skylands (42.5). Nether contributes200; multiple height
contributions average in source order. NoHorizon removes both the sky-colored
lower disk and black below-horizon void disk. Verify below the horizon in a
separate void terrain fixture where terrain does not conceal that area.

Vanilla `/time set` and `/weather` commands can affect multiple loaded levels;
using `/execute in` does not make vanilla time changes local. Record these
side effects explicitly in a disposable world. Sleeping/resetting precipitation
briefly disables the four forced weather controllers for12000ticks; their native
rain interpolation takes a few ticks to settle. Do not use a previously allocated
book to compare changed page settings: its canonical environment is frozen.

## Smoke-mod operator commands

The rebuilt opt-in smoke mod registers commands usable by permission-level4
operators; console callers must provide the target player name:

```
/mystcraft_visual_fixture rain PlayerName
/mystcraft_visual_fixture snow PlayerName
/mystcraft_visual_fixture storm PlayerName
/mystcraft_visual_fixture cloudy PlayerName
/mystcraft_visual_fixture rainbow PlayerName
/mystcraft_visual_fixture lightning PlayerName
/mystcraft_lightning_fixture PlayerName
```

The first command family gives an **unallocated** seed22345 fixture book. Each
panel activation creates the corresponding real Age through the production book
flow; the command does not mutate canonical world settings directly. All modes
include cyan clouds, magenta grass, blue foliage, an unconditional stationary
rainbow, NoHorizon and one explicit sun. `clear`, `rainbow` and `lightning` use
WeatherOff; other modes force the named source weather controller. `lightning`
also authors a red/blue gradient consumed by EnvLightning. These are disposable
fixtures with ordinary classic feature-dummy costs, not guaranteed stable Ages.

The second command family spawns the actual synced magenta lightning entity
8blocks along the player's look direction, allowing repeatable client renderer
verification without waiting for the rare source random event. This fixture
sets native visualOnly to prevent damage/fire; ordinary production bolts keep
their real behavior. It does not prove the random scheduling path by itself.

Flat source terrain can expose stone rather than tinted vegetation. Place grass
blocks and ordinary oak leaves in the Age to check the respective tint channels.
Use both a real spawned fixture bolt and the gradient-authored Age to distinguish
entity rendering/packet correctness from explicit-effect scheduling.
