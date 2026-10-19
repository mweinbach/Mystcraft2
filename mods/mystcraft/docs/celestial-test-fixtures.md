# Celestial graphical fixtures

These are test commands, not survival recipes or evidence that rendering has
passed. Run the long give commands through the dedicated-server console/RCON
or a command block; ordinary chat has a shorter command limit. Use a fresh
book and Age after changing pages, because plans are frozen at allocation.
The seed is fixed to remove random test variation.

Before departure, bind a normal return linking book in the Overworld and put it
in a safe inventory slot. A normal held descriptive book stays at its source.
The GUI activation should use the ordinary server-owned link session.

## Two moving suns, opposite moon, and twinkle stars

```mcfunction
/give @p mystcraft:descriptive_book{display:{Name:'{"text":"Celestial Check"}'},Props:{Seed:"12345"},Pages:[{linkpanel:{}},{symbol:"mystcraft:terrainflat"},{symbol:"mystcraft:biome",Biome:"minecraft:plains"},{symbol:"mystcraft:bioconsingle"},{symbol:"mystcraft:weatheroff"},{symbol:"mystcraft:lightingbright"},{symbol:"mystcraft:modcolorcyan"},{symbol:"mystcraft:colorsky"},{symbol:"mystcraft:modcolorpurple"},{symbol:"mystcraft:colorskynight"},{symbol:"mystcraft:modcolorwhite"},{symbol:"mystcraft:modcolorblue"},{symbol:"mystcraft:colorfog"},{symbol:"mystcraft:modcolorred"},{symbol:"mystcraft:modcoloryellow"},{symbol:"mystcraft:colorhorizon"},{symbol:"mystcraft:modfull"},{symbol:"mystcraft:modeast"},{symbol:"mystcraft:modnoon"},{symbol:"mystcraft:sunnormal"},{symbol:"mystcraft:modcolorcyan"},{symbol:"mystcraft:colorhorizon"},{symbol:"mystcraft:moddouble"},{symbol:"mystcraft:modeast"},{symbol:"mystcraft:modnoon"},{symbol:"mystcraft:modrising"},{symbol:"mystcraft:sunnormal"},{symbol:"mystcraft:modfull"},{symbol:"mystcraft:modnorth"},{symbol:"mystcraft:modend"},{symbol:"mystcraft:moonnormal"},{symbol:"mystcraft:modcolorwhite"},{symbol:"mystcraft:modfull"},{symbol:"mystcraft:modeast"},{symbol:"mystcraft:starstwinkle"}]} 1
```

All modifier channels are explicitly consumed. The plan has:

- A sun with period 24000, yaw -90 degrees, phase offset 0; an orange sunset gradient.
- A second sun with period 48000, yaw -90 degrees, phase offset -0.125; a cyan sunset gradient.
- A moon with period 24000, yaw 0, phase offset -0.5, using the source moon-phase atlas.
- Ten white twinkle layers with period 240000 and yaw -90 degrees.
- Cyan daytime and purple nighttime sky providers, averaged in classic provider order; a pale-blue fog provider and bright lighting.
- Flat plains terrain and weather disabled, to keep the horizon and sky clear.

After entering, run the following. Vanilla `/time set` changes every loaded dimension, even under `execute in`; this is an intentional disposable-test clock change, not an Age-only command:

```mcfunction
/time set 0
```

Sun 1 is at the zenith. Sun 2 is approximately 35 degrees away from the zenith
along the same orbital plane, so look nearly straight up with FOV about 100
to capture both. They should be separate textured sun quads. The moon is below
the horizon at this time, and stars should have zero daytime brightness.

```mcfunction
/time set 3000
```

The two suns should now be about 51 degrees apart, on opposite sides of the
zenith. This proves different angular speeds rather than two copied static
textures. At time 12000, the first sun is below the horizon while the second
still supplies daylight.

```mcfunction
/time set 36000
```

Both suns are below the horizon. The moon is overhead at phase 1 and the white
star layers should pulse independently over a 100-tick cycle, with slow sky
rotation. The nighttime sky is purple-tinted; bright lighting keeps terrain
visible. At time 24000 versus time 0, the moon has advanced one atlas phase,
although it is below the horizon in both those daytime captures.

For individual sunset fans, inspect the horizon while advancing time rather
than expecting both suns to set together. The orange and cyan fans belong to
their own suns; the blue fog provider is independent.

## Stationary-body variation

On a fresh copy of the first fixture, replace the **second** sun's `moddouble`
page with `modzero` before first allocation. At times 0, 3000 and 12000 that
sun remains fixed at altitude angle -0.125 (45 degrees from the zenith), while
the first sun continues moving. This changes the factor, not the rendered
quad size. Do not edit an already allocated book's pages to test this.

## End-sky background and dark terminals

```mcfunction
/give @p mystcraft:descriptive_book{display:{Name:'{"text":"End Sky Check"}'},Props:{Seed:"12345"},Pages:[{linkpanel:{}},{symbol:"mystcraft:terrainflat"},{symbol:"mystcraft:biome",Biome:"minecraft:plains"},{symbol:"mystcraft:bioconsingle"},{symbol:"mystcraft:weatheroff"},{symbol:"mystcraft:lightingbright"},{symbol:"mystcraft:modcolorpurple"},{symbol:"mystcraft:starsendsky"},{symbol:"mystcraft:sundark"},{symbol:"mystcraft:moondark"}]} 1
```

This separate Age should have a purple-tinted, six-sided tiled vanilla End
background and no sun or moon quads. End sky suppresses lower-horizon/void
drawing. The dark terminals add no bodies; they do not erase bodies that were
registered earlier. A separate book with `SunNormal` followed by `SunDark`
should still show that one normal sun.

## Exit and negative controls

Return to the Overworld using the saved return book. Its native sky, lightmap
and weather behavior must be unchanged. The vanilla test commands above have
changed its clock too; that shared command side effect is not an Age clock
isolation failure. Disconnect/reconnect and restart the server:
realized celestial periods, phases, seeded twinkle layers and colors should
remain the same. Check default Ages without celestial pages, and old version-1
visual saves, retain their prior native vanilla sky.

Run screenshots on Forge and Fabric separately. Tekxit without shaders and
with its optional shader pipeline are separate compatibility checks. Confirm
there are exactly the planned bodies; a default vanilla sun/moon/star layer
must not be drawn on top of a custom plan.
