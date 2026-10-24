# Blast and simulation implementation

This package adapts MIT Java behavior from the pinned ICBM Classic source:
`ExplosiveInit`, `BlastTNT`, `BlastThermobaric`, `BlastGasBase`, `BlastMutation`,
`BlastAntiGravitational`, `EntityFlyingBlock`, and redmatter entity/logic. The
module packages the upstream MIT notice. No upstream artwork is imported.

## Execution and persistence

All27 upstream definitions exist, with23 content variants and4 reserved/no-content
slots. `Payloads.detonate` remains the delivery entrypoint. `detonateScaled`
accepts an absolute radius override, checks the server and bounds, applies API
before/after events, and reports admission. Jobs retain the API action source,
including UUID attribution, across saves. `removeNear` cancels queued work and
singularities and settles captured blocks safely. Unknown/nonfinite saved data
is rejected; progress and lifetime are clamped.

The queue admits256 jobs including deferred work. It serves up to8 jobs per
server tick with512 work units each, round-robin. Every job's persisted elapsed
lifetime advances every world tick. Timed fields stop applying effects when
expired and retire within32 ticks of service latency at full load. Processed age
is separate for first-effect and pulse scheduling. Legacy saves use processed
age when the elapsed field is absent. No code accesses worlds off thread, holds
chunk tickets, or edits unloaded/out-of-border/out-of-height terrain.

## Propagation and terrain

Condensed and attractive/repulsive terrain use the upstream16-axis cube-surface
ray algorithm:0.3-block steps,0.225 air energy loss, resistance plus0.3 scaled by
0.3, and0.7–1.3 per-ray starting energy jitter. Jitter is deterministic by ray ID,
which makes interrupted work reproducible. Thermobaric/nuclear use the source
angular grid (ceil(pi*radius) polar slices, twice that many yaw slices),0.5-block
steps,1.125 air energy loss and resistance charged once per entered block. Their
liquid resistance is0.25. Collection completes before destruction, so removing
one ray's blocks cannot erase shielding for later rays. Collection, ray energy,
step and destruction cursor all persist. Each blast retains at most65536 affected
positions. This explicit cap can truncate a very large crater; large grids may
take many ticks. The older simplified sphere threshold is not used by new
condensed/thermobaric/nuclear jobs.

Other families retain distinct mechanics: burning damage11 shrapnel30 arrows;
25 falling anvil fragments;15 explosive fragment rays plus arrows; incendiary
ignition; seven-deep energy-budget breaching; sonic ejection; thermal vanilla
material/fluid conversion; maximal-damage antimatter; smoke and color particles.
Nuclear completion invokes mutation and the exposure module's contamination
work. EMP invokes registered defense handlers. Antimatter cancels nearby
redmatter singularities.

Gas uses saved six-neighbor diffusion with directional collision bounds adapted
from `BlastGasBase`, rather than applying through all blocks in a sphere. There
are at most65536 visited cells. Blocked cells prevent a sealed solid enclosure
from leaking. Only living targets whose feet occupy an affected cell are passed
to `ExposureContent.expose` every5 service ticks. Toxicity, armor protection,
contagion and radiation belong to the exposure package. Gas cells currently grow
to the configured radius as work permits rather than reproducing the exact
upstream radius-over-time curve; previously blocked cells are not automatically
reopened after wall removal. Visual particle spread is cosmetic.

## Conserved block entities and redmatter

`SimulationContent.register()` registers `flying_block` and
`redmatter_singularity`; `SimulationClient.register()` supplies independent
renderers using ordinary Minecraft render layers and permitted vanilla textures. A flying block captures complete blockstate and block-entity NBT. Restored core-bound blocks wait up to 100 ticks for their core to load, then resume homing or release conservatively if it never appears.
The source BE is removed before clearing its block, preventing inventory scatter.
On landing it either restores the block/BE or drops one state/NBT-bearing item.
Completion is one-shot, including repeated settlement. Save/load preserves its
cargo, remaining no-gravity lift and lifetime. Unloaded boundaries stop movement
and settle in loaded terrain. Multi-part doors, beds, piston heads and paired
chests are not split. Block data from a held-item carrier can be constructed via
`FlyingBlockEntity.fromCargo` without consuming a world block. Passengers pause
movement until released. Antigravity captures real flying blocks with remaining
field duration; they regain gravity and settle afterward.

Redmatter is a saved moving/no-clip entity. **Deliberate departure (after alpha.4):** upstream (and the port up to alpha.4) searched terrain with shell rays inside a cube-bounded region and captured a few blocks per tick, which players saw as a slow, square suction. The core now owns a spherical *event horizon*: a solid ball that grows one block of radius per tick, and every breakable block whose centre lies inside it is erased by `SphereEraser` (direct chunk-section writes with per-block client deltas, no drops, no neighbour updates). Sky light for the opened columns is collected per chunk and written straight into the light arrays every 20 ticks, and whenever the core moves, collapses or unloads, so the light thread, which also serves chunk loading, never builds a backlog. The horizon is capped by the configured radius, default **300** (was 12), which is larger than antimatter's 275; `/icbm blast` and the API may pass sizes up to 384 for this family. A detonation's size argument becomes that core's cap. Growth across all cores shares a per-server-tick budget of 12 ms and 32,768 blocks; chunks the horizon passes while unloaded are recorded (at most 4,096) and cleared when they load. A core that is moved more than two blocks finishes its old crater as a scar and restarts its horizon at the new position. At lifetime end (default **6,000** ticks, was 2,400) or collapse the crater is finished the same way.

While the horizon grows, at most 3 blocks per tick (48 in flight per core) are lifted from just beyond it as `FlyingBlockEntity` debris, sampled along golden-angle spiral directions so the stream is spherical. They bind to the core UUID, home in without terrain collision and are consumed at its centre. The binding survives saves; loss of the core restores ordinary settling behaviour.

The gravitational field reaches `max(24, horizon + 24)` blocks and is a true sphere. Players are looked up every tick; other entities are re-scanned every 5 ticks, nearest 256 kept. Pull acceleration is `0.02 + 0.18 × falloff²` with a 3 blocks/tick speed cap, and players receive velocity packets so they are dragged in too. Anything that reaches the core, `max(1, 0.08 × size)` from its centre (checked along each entity's latest `Entity.move` segment, recorded by `mixin/EntityMixin`, so fast entities cannot tunnel through), is consumed: living entities, **including survival and adventure players**, take the configured `entityDamage` (default 2,000) from the dedicated `icbm_port.redmatter` damage source, which bypasses armour and protection, and die with "*X* was consumed by red matter" (or "by *owner*'s red matter"). **Creative and spectator players are immune**, following the vanilla convention for environmental kill sources. The owner appears only in the death message, not as the attacker, so PvP settings and claim mods do not silently exempt victims. Teleports, spawns, saved-data loads and direct `setPosition` repositioning keep upstream's end-point test.

The core grows with its horizon (size `1 + 0.15 × horizon`, at most 70), starves when unfed and fully grown, merges overlapping cores keeping the larger cap (the absorbed core's unfinished crater, including chunks its horizon passed while unloaded, is finished as a scar around its own anchor), retains momentum and obeys its configured lifetime. Size, horizon, cap, anchor, deferred chunks, owner, lifetime and capture/consumption counters survive saves. Tests cover placed-block priming, a spherical footprint (cube corners survive), pulling and killing a survival player and a mob, creative immunity, saved progress and antimatter neutralization.

The former ender-eye placeholder is replaced with an original animated dark sphere, four tumbling accretion discs and a horizon-scaled halo, plus converging portal and ink particles sent to players within 160 blocks of the horizon. This uses ordinary solid/translucent entity layers, without the upstream shader or beam mesh. Upstream per-block mass accounting, explosion-specific momentum coefficients, custom audio and every optional redmatter branch are not identical. No upstream artist assets were copied.

## Antimatter

**Deliberate departure (after alpha.4):** upstream's `BlastAntimatter` (and the port up to alpha.4) swept a cube layer by layer over many ticks, which players saw as a slow scan. Antimatter now erases a solid sphere, default radius **275** (was 55, so 5×), through `Scar`/`SphereEraser`. Sections fully inside the sphere are swapped for empty ones, keeping their biomes. Partially covered sections are edited in place. Nothing drops, no neighbour or shape updates run, and block entities inside the sphere are deleted without scattering their contents. Heightmaps are fixed per column. Opened columns get full sky light written directly into the light arrays, one queued array per section; block light is re-checked only where a light source was removed. Each finished chunk is resent to the players watching it as a single chunk packet. Unbreakable blocks (hardness below 0), positions outside the world border or build height and unloaded chunks are skipped. Unloaded chunks inside the sphere are recorded and cleared when they next load. Admission, config and the API/claim checks in `detonateScaled` are unchanged.

Scars run nearest chunks first and share a per-world-tick wall-clock budget of 1 s; at most 64 scars clear at once and at most 512 are stored (the rest wait for unloaded chunks); all persist across saves. Accepted scars are never evicted: when the limits are reached a new antimatter detonation is refused, and other crater work (legacy sweeps, red matter cores) waits and retries. A chunk that loads is only handed to its scars once the server can actually reach it, so a load event that fires early cannot strand it. Antimatter sweeps saved mid-way by older versions (up to 256) finish as scars; any beyond the 64 slots stay queued and saved, and convert as earlier scars finish. When a red matter core collapses, moves (re-anchors), is neutralized by antimatter or is absorbed by another core, its unfinished crater (including the part of a half-finished horizon shell) becomes a scar too; if all 64 slots are busy the core keeps that work (saved with it) and retries each tick. Measured on an Apple-silicon development machine (GameTest server, solid stone from bedrock to y 63, centre at y 64, five runs): a radius-275 scar touches 1,004 chunks and removes 28.0 million blocks in **one tick, 375–717 ms** of server time (the high figure is the first, JIT-cold run). Building the 1,016 resend packets took 40–49 ms (11.7–25.8 MiB in total, depending on how many light arrays travel with them; the 625 chunks within view distance 12 took 6–11 ms). The light thread finished the queued crater light in 2.8 s (71 ticks) while the server kept ticking. Sky light read 15 at the crater floor at the same moment, and loading the next 1,369 fresh chunks took 5.4 s, against 5.2 s before the blast. 5× fits comfortably under the 1–2 s limit, so the radius was not reduced. Red matter growing to its 300 horizon took about 1,190 ticks (60 s) at a mean server tick of 6.9–7.6 ms. The longest tick was 51 ms, and only two ticks went over 50 ms. A fresh 25-chunk load straight afterwards took 258 ms. Reproduce with `./scripts/gradle.sh :icbm_port:runGametest -PicbmBenchmark [-PicbmBenchmarkRadius=N]`.

Antimatter still neutralizes red matter cores inside its radius and, when none were neutralized, applies its entity-damage pulse. A brief flash of `FLASH`/`EXPLOSION_EMITTER` particles and a sound mark the detonation.

GPS-customized ender missiles carry target dimension/position independently of
flight. Their payload moves nearby entities and conserved blocks to matching
offsets in an already loaded target world. Occupied block destinations and
unloaded terrain are skipped rather than overwritten/force-loaded. An ender
payload with no custom destination retains safe local random entity relocation.

## Configuration

`config/icbm_port/payloads.json` is generated on initialization. Permission-level2
operators can run `/icbm_payloads reload`. Invalid JSON, unknown IDs/fields,
nonfinite numbers, fractional integers and out-of-range values reject the whole
reload while retaining working settings. Missing fields use typed defaults;
files over1MiB are rejected. Legacy Forge config files are not imported.

The file carries `config_version` (now 2). On load, a version-1 file whose antimatter radius, red matter radius or red matter duration still equals the old default (55, 12, 2,400) is moved to the new default (275, 300, 6,000) and rewritten; values a server owner changed are kept. A file claiming a newer version than the mod understands is rejected.

| Field | Range | Consumers |
| --- | --- | --- |
| enabled | boolean | All payloads; queued work ends at next service |
| radius |0–80; antimatter/redmatter 0–384| Queued fields, condensed bounds, force, mutation, ender, EMP callback; antimatter sphere radius (default 275); red matter horizon cap (default 300) |
| entityDamage |0–2147483647| Condensed, arrows/anvils, breach, thermobaric/nuclear, antimatter/redmatter |
| fragments |0–128| Shrapnel, anvil, fragmentation |
| durationTicks |0–72000| Gas, smoke, antigravity, singularity |
| energy |0–20000| Condensed/force rays, breaching, thermobaric/nuclear, fragmentation bursts |
| blockFuseTicks/cartFuseTicks/grenadeFuseTicks |0–72000| New corresponding delivery activations |

Queued radius, initial ray energy and captured block lift are snapshots; enabled,
damage and field durations are checked live. Existing primed fuses retain their
assigned value. Unused fields remain in the uniform schema. Fragmentation bursts
still use vanilla power capped16 and immediate collision traces rather than the
upstream moving explosive-fragment entity. Source-specific sounds, custom
fragment models, exhaustive material rules and full Forge integration remain
differences. Dedicated-server tests exercise registry/timing, projectiles,
mutation, gas, budgets, lifetime/reload, inventory conservation, shielding,
singularity consumption and diffusion walls. Alpha.4 also sends a bounded presentation packet per admitted detonation, driving
client shockwave/particle effects and vanilla sound events independently of the
terrain work queue. Primed casings pulse and flash; missile exhaust follows
synchronized flight/EMP state. Original models and effects are approximate
replacements. Full-pack alpha.4 client inspection confirmed redmatter consumption, core/ring motion and selected machines; audio, shader
packs and two-client presentation need broader testing.
