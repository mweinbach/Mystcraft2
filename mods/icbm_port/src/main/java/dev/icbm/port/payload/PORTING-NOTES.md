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

Redmatter is a saved moving/no-clip entity. In alpha.4 its terrain search expands independently of core growth, so an emptied inner shell no longer prevents it from finding distant terrain. Each tick it tests at most32 shell rays (at most256 samples each) and captures at most4 blocks. Shell radius is bounded by the configured radius and a hard limit70; saved search radius/cursor preserve progress. Captured blocks bind to the core UUID, move inward without terrain collision and are consumed at its center, rather than settling back into the crater. The binding survives saves; loss of the core restores ordinary settling behavior.

Entities are consumed inside upstream's kill range, `max(1, 0.08 × size)`. Because the port's pull can move an entity more than that diameter per tick, the test uses the entity's latest movement step rather than its end position, so fast items cannot pass through the core and be flung beyond the pull radius.

The core pulls entities with distance falloff, grows by feeding, starves when unfed, merges overlapping cores, retains momentum and obeys its configured lifetime. Size, owner, lifetime and capture/consumption counters survive saves. Maximum configured size defaults12; source starts at1, minimum0.25. Tests cover placed-block priming, consumption of terrain six blocks away after inner shells are empty, persisted search progress and antimatter neutralization.

The former ender-eye placeholder is replaced with an original animated dark sphere and four tumbling accretion discs. This uses ordinary solid/translucent entity layers, without the upstream shader or beam mesh. Upstream per-block mass accounting, explosion-specific momentum coefficients, custom audio and every optional redmatter branch are not identical. No upstream artist assets were copied.

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

| Field | Range | Consumers |
| --- | --- | --- |
| enabled | boolean | All payloads; queued work ends at next service |
| radius |0–80| Queued fields, condensed bounds, force, mutation, ender, EMP callback, redmatter maximum size |
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
