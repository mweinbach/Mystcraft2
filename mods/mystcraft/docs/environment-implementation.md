# Classic environment controls

Source: official `1.12/develop`, commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`. Reference classes are
`SymbolColorCloud`/`SymbolColorCloudNatural`,
`SymbolColorGrass`/`SymbolColorGrassNatural`,
`SymbolColorFoliage`/`SymbolColorFoliageNatural`, `WorldProviderMyst`, `AgeController`,
`WeatherControllerToggleable`, `SymbolWeatherRain/Snow/Storm/Cloudy`,
`BiomeWrapperMyst`, and terrain/skylands symbols. Rainbow references are `SymbolDoodadRainbow` and `RenderRainbow`.

## Implemented behavior

Frozen visual settings version 3 add cloud, grass and foliage providers,
cloud height, horizon, lower-void visibility and weather mode. Versions 1/2
retain their frozen color/celestial settings and native modern cloud height;
they are not recompiled. Network version 3 transmits bounded explicit fields
and rejects earlier wire formats. Forge requires protocol 3 and Fabric uses
`mystcraft:age_visuals_v3`; clients cannot submit environment profiles.

Cloud colors consume the same ordered color/gradient modifier stack as the
source, average provider colors in source order, and use the source's distinct
0.95 rain/thunder blending factors, day multipliers and 12000-tick gradient
units. Grass/foliage consume a plain color. Their first provider wins: the
original static-provider combiner discarded the return value of Color.average.
Natural grass/foliage sample the native color map with bounded position-adjusted
temperature and rainfall, rather than taking swamp/special-biome color overrides.
The modern tint adapter samples the requested position; it does not reproduce
all classic biome-blend neighborhood details.

New profiles use classic cloud height 128. Nether contributes 200/horizon128,
Void contributes 0/horizon0, Skylands contributes42.5/horizon0, End contributes
horizon0. Multiple contributions average in registration order, as the original
AgeController setters do. End-sky also contributes horizon0. Native modern cloud
geometry, render distance and user cloud settings remain in charge. NoHorizon suppresses both lower horizon/void geometry in the custom celestial renderer and is promoted. New completed Ages use that renderer. Older native-sky frozen plans remain unchanged.

Rain and Storm force rain in otherwise dry/cold biomes and clamp temperature
up to0.20. Snow forces snow in dry/warm biomes and clamps temperature down to0.10.
Storm additionally enables thunder. Cloudy sets weather strength without allowing
precipitation. The four controllers retain a persistent12000-tick disable counter
when precipitation is reset, then enable themselves again. Source temperature
clamps remain in effect even during that disabled interval. Other cycling weather
controllers keep their existing independent saved timers.

Server snow/ice chunk checks use a call-scoped temperature override and invoke
native placement checks; precipitation checks select the per-Age rule. The biome
registry object and its temperature cache are never changed. Liquid-rain checks
are overridden in the Age server level; native lightning uses these checks.
Client temperature/precipitation lookups apply the same rules on the client game
thread. This is a weather/placement bridge, not a complete per-world biome-wrapper
port: unrelated server entity/mod temperature queries outside the scoped weather
checks still see their registered biome temperature. Rain-strength interpolation
and lightning positioning retain modern vanilla behavior.

The upper sky disk now interpolates radial rings into the current native fog
color. Classic drew its display list with fixed-function GL_FOG enabled; the
modern position/color shader had no fog and left an abrupt colored-disk edge.
This is an explicit modern approximation of the source fog integration, pending
new graphical comparisons, rather than a pixel-exact restoration.

Rainbow is a real ordered celestial object consuming only the angle modifier.
Its yaw uses the assigned per-terminal seed when no angle is supplied. The source
always renders it, including night and rain: its proposed day-brightness code is
commented out. The shared renderer draws both semicircles at45degrees, radius100,
width50, 50 overlapping HSV bands with value200 and alpha0.2. Geometry is cached
once and released on resource reload/disconnect. Upstream's first patch has a
negative square-root input; this invalid patch is clipped rather than uploading
NaN vertices. Frozen body kind RAINBOW and wire v3 carry the actual settings.
NoHorizon suppresses both restored sky-colored lower disk and black void disk in
completed profiles, which contain explicit realized celestial families. Existing
legacy/native-sky saved profiles remain untouched.

## Validation additions

`AgeEnvironmentTest` exercises source RGB/weather vectors, first-static-provider
behavior, source cloud/horizon values and ordered averaging, old-save migration,
full execution-stream celestial RNG advancement, controller precedence, bounded
values, modifier-clear/dangling accounting and the exact disable interval.

`AgeVisualNetworkSmoke` includes all six color channels, heights, visibility and
weather in an actual binary round trip. `AgeEnvironmentSmoke` allocates a real
warm-plains Snow Age, exercises native snow placement checks and cross-world
biome isolation, freezes/saves its visual settings, checks sleep/reset persistence,
and distinguishes Rain/Storm/Cloudy at the server's liquid-rain API.

Historical candidate13 validation completed both loader builds and dedicated full/restart checks. Actual Tekxit4 completed 135 full assertions and two fresh-JVM persistence checks (flat evidence `work/tekxit4/v03-candidate13-bclib-*`), including real weather placement and cross-world isolation. All static environment controls are promoted. This evidence predates the later additive-sunset/lower-enclosure source corrections; those require a new parent build and graphical check.

## Remaining core adapter limits

The source wraps the entire biome per Age. The modern port scopes server biome-temperature changes to precipitation, snow/ice placement and Age liquid-rain/lightning checks, with client game-thread climate/tint adaptation. Entity/mod code calling a registered biome temperature outside these scopes still sees native temperature; this remains a real core compatibility gap, not an unimplemented Rain/Snow controller. No global biome/cache mutation is used to conceal it. A universal world-aware replacement would require auditing every biome consumer or a world-owned biome wrapper; it must preserve registry/network identity on both loaders.

Grass/foliage retain native modern position sampling rather than exact legacy neighborhood blending. Native precipitation geometry, audio/particles, lightning positioning and cloud mesh remain Minecraft1.19.2 adapters. Core weather rules execute through them; shader/Iris rendering compatibility is separate and not required by classic core behavior.

No new visual fields or plan versions are needed for the source renderer corrections. Existing v1/v2/v3 frozen values, weather timers, packet bounds and server authority remain unchanged.
