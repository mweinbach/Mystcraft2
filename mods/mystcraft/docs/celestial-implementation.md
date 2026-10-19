# Celestial implementation and validation status

Reference: official classic Mystcraft `1.12/develop`, commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`. This slice ports classic sky behavior;
it does not introduce Mystcraft 2 stability rules.

Historical graphical captures established two suns, differing motion, a stationary
sun, phased moon, twinkle stars and End sky. Later candidate13 loader builds and
packaged dedicated checks passed; actual Tekxit4 completed 135 assertions and two
fresh-JVM persistence checks. These results establish execution of the tested
paths, not pixel-for-pixel renderer parity. Source corrections recorded at the
end of this document postdate those artifacts and await a new build/capture.

## Shared realized state

`AgeVisualSettings` writes version 3 and contains `AgeCelestialSettings`.
The plan records ordered body kind, per-symbol seed, the exact Java Random
continuation seed used by twinkle geometry, realized period/yaw/phase, body
color gradient, per-body sunset gradient, and the final global sunset gradient.
These realized values are frozen in the canonical Age saved entry rather than
rerolled by clients or on restart.

Existing visual version-1 saves read as `legacyVanilla=true`. The missing-state
migration in `AgeManager` also preserves legacy vanilla celestial behavior.
New plans without any explicit celestial terminal retain the previous vanilla
sky and time behavior. No automatic conversion of old Age skies is performed.

New allocation executes the completed classic grammar stream. `AgeSymbolCompiler`
passes its ordered execution pages to `AgeVisualCompiler.parseExecution`; every
terminal advances the independent third Java Random stream, including dynamic
biomes and materials. Modifier ownership, clear/dangling costs and repeated
controllers are handled in execution order. The earlier explicit-only parser
remains available for compatibility and focused tests; its old limitations are
not limitations of new canonical Age allocation.

## Executed controls

The single ordered modifier machine in `AgeVisualCompiler` shares colors,
gradients and factors with celestial consumers. Cardinal angle and phase
modifiers use the source angle-averaging equations. Sun and moon periods use
24000 times the pending factor; normal and twinkle star periods use 240000.
Zero periods are stationary. Orbit phases, moon phases, multi-sun daylight
selection, dawn selection, sunset windows and twinkle pulses use the classic
formulas in shared `CelestialMath`.

Normal sun/moon bodies retain the original fixed quad sizes 30/20 and vanilla
textures. The length modifiers change periods; they are not body-size controls.
No new unsupported size-control behavior was invented.

Repeated body pages add repeated bodies in the original order. Dark sun, moon
and star terminals add no body and do not erase earlier bodies. Completed grammar supplies absent required celestial families. In focused explicit-only parsing, a custom plan without a sun has classic no-sun daylight angle 0.5.

`ColorHorizon` attaches the current gradient to the next applicable sun/moon.
A remaining sunset gradient supplies the final global fallback. Suns otherwise
use the original orange sunset fallback; moons render a horizon fan only with
a per-body sunset gradient, with the source 0.3 alpha scale. A horizon color
without an explicit celestial terminal is rejected with a diagnostic instead
of being accepted without a rendering effect.

Normal stars use the original 10842 sphere sampling stream. Twinkle stars use
10 independently generated 100-sample layers and source pulse offsets. End sky
uses six tiled vanilla End-sky faces, source tint default, and suppresses the
ordinary lower horizon/void geometry.

## Rendering, gameplay and synchronization

`AgeSkyRenderer` is shared. It renders a sky dome, ordered bodies/backgrounds,
per-body 16-segment sunset fans, and the lower horizon where applicable. Rain
fades normal bodies and stars. GPU star buffers are bounded and closed on
resource reload or disconnect. Render state is restored afterward.

Forge uses its native dimension-effects `renderSky` hook. Fabric uses a narrow
conditional sky-renderer mixin. The Fabric API has no public removal operation
for dynamically registered sky-renderer keys; permanent registrations would
otherwise override an old vanilla-sky Age on a later server with the same key.
The conditional hook leaves legacy Ages and ordinary dimensions with their
native renderer. Client time-of-day hooks use the same sun selection as the
server so native sky darkening and the lightmap agree with the rendered plan.

The server-side Age level uses the shared daylight angle and advances sleeping
players to the earliest moving-sun dawn, preserving the source 24000-boundary
fallback when no sun moves. Explicit time commands and normal tick increments
remain distinct from sleep advancement.

The presentation wire format is explicitly version 3. It sends bounded ordered
body records and gradients from server to client only, before Mystcraft
teleports where possible, and on login, dimension changes, respawn and reload.
Old Forge presentation protocols reject through channel negotiation. Fabric
waits up to 40 ticks for initial channel advertisement and then disconnects a
client lacking the required visual protocol with a version-mismatch message.

## Evidence still required

`AgeCelestialTest` contains source-vector tests for parameter ownership/order,
per-body Java Random defaults/continuation, periods and stationary bodies,
negative time and moon-phase boundaries, multiple suns, dawn selection,
sunset/twinkle windows, body repetition/dark omission, frozen NBT and migration.
`AgeVisualNetworkSmoke` also includes body-plan packet roundtrips.

The historical centralized candidate builds passed those unit tests. The Forge captures
under `work/client-test/forge-candidate1-*` establish two separate suns,
differing motion, a stationary sun at the same screen position across time,
moon phases, pulsing stars and a purple End background without sun/moon.
The orange-sunset capture shows a horizon color patch, but also a sharp
sky/fog boundary at the modern flat-dome edge; that is not evidence of
pixel-perfect legacy sunset or horizon blending. Explicit per-body values
and fan equations are ported, while that visual blend remains approximate.
Fabric graphical checks and ordinary-world return captures must be reported
separately from its passing packaged server checks. Tekxit without shaders and Tekxit with its optional shader
pipeline are separate compatibility results. Any shader pipeline bypassing the
hook remains an explicit compatibility gap rather than a claim of support.

The completed grammar and environmental controls are implemented; their absence
in earlier revisions of this document was stale. All 145 static catalog entries
have executable source paths, which is not a claim of pixel parity.

## Current source audit corrections and graphical scope

Pinned `SymbolSunNormal`/`SymbolMoonNormal` set additive SRC_ALPHA/ONE blending
before calling `SunsetRenderer.renderHorizon`, which does not replace that state.
The shared renderer now preserves this additive fan instead of darkening the sky
with alpha compositing. The black below-horizon cap now uses source y=-4, includes
the original four walls and lid, and precedes the sky-colored lower plane.
These changes are shared by both loaders and do not rewrite frozen visual plans.

Remaining renderer differences: the upper dome's radial fog interpolation adapts
removed fixed-function GL_FOG rather than reproducing it pixel-for-pixel; modern
native sky/cloud geometry, draw distance and resource textures differ. Shader
pipelines and LookingGlass are outside this core adapter. A native-sky legacy Age
is intentionally not converted to a custom renderer merely to reinterpret a
later NoHorizon page: its frozen plan remains authoritative. New completed Ages
already use the custom renderer and execute NoHorizon.

Actual 0.4 Fabric and Forge clients rendered the additive sunset fan above
terrain and the black enclosure below the world. Fabric also showed colored sky
for the matched NoHorizon Age, then returned to the ordinary Overworld. The
captures used different advancing sky-clock times, so their brightness is not a
controlled loader-to-loader pixel comparison. Exact artifacts, screenshot scope
and timing are recorded in [validation.md](validation.md) and its graphical
evidence record.

A controlled same-camera fan/no-fan comparison and comparison to a running 1.12
client remain additional coverage; the recorded checks do not imply every
proposed graphical fixture step was executed.
