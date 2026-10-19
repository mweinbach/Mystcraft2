# Pinned classic instability and decay audit

Reference: official Mystcraft-Legacy1.12 commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`. This documents the current port of
classic scoring, decks and hazards. It does not introduce the proposed Mystcraft2
stability system, a guessed page-count score or unsupported-symbol penalties.
This revision updates implementation status only; runtime results and artifact
hashes remain in [validation.md](validation.md).

## Original pipeline and current implementation

`AgeController` combines source symbol/controller/dangling costs, book base
instability, watched-block profiling, bonus providers and debug values. Difficulty
multipliers are0.25/0.5/1/1.75. A block profile must contain **more than400** chunks
and have a baseline before automatic scoring contributes. The port implements
that gating rather than inventing a score when profiling is unavailable.

New allocation freezes source costs from the completed grammar execution.
Duplicate controllers and remaining modifier values have their source costs;
ModClear applies the source partial cost. These costs are independent of catalog
implementation flags. Unknown symbols still reject before Age reservation.

`ChunkProfiler`'s logical256-layer cell-frequency/accessibility arithmetic is
ported to `InstabilityProfile`. Positive watched contributions subtract baseline
allowances, negative contributions remain negative, then values sum and round.
Passability uses modern empty collision shapes, and logicalY0..255 is retained
rather than extending the original formula into negative terrain. Only already
ticking chunks are sampled, once per distinct chunk; profiling does not generate
or force-load its own sample area. Whole-profile score recalculation happens
when more than100 additional samples have accumulated, or the baseline changes.

The default baseline comes from the first401 distinct profiled Overworld chunks,
rather than the old separate dummy-world generator. Player edits can therefore
affect a newly established automatic baseline. An explicit operator baseline is
also supported. This is an explicit modern baseline adapter, not identical
sampling of an untouched classic dummy world.

The five seeded, persisted decks preserve pinned multiplicities, prefix stopping
and thresholds: basic0, harsh2500, destructive10000, eating15000 and death20000.
Provider repetition determines effect levels. Budgets round down to the smallest
500 cost. Removed cards reconcile against the source deck without reshuffling
preserved cards. Deck state is separate from watched-profile migration.

## Watched blocks and mod fluids

The ten fixed watchers match `InstabilityData.initialize`: coal/lapis/iron,
emerald/redstone/gold/diamond, crystal, glowstone and quartz. Modern deepslate
counterparts and arbitrary mod ores are not guessed aliases.

The pinned `ModSymbolsFluids.modsLoaded` additionally registers every eligible
ordinary fluid block. The port now supplies that missing automatic path: ordinary
registered non-water/lava fluids receive accessibility1.0/flat0.25, with65 exact
legacy translation-key overrides. Source black ink uses1/0. Unknown modern mod
names retain generic defaults; no ore/oil/mana alias is invented from a substring.
Gaseous/lighter-than-air, excluded and non-placeable fluid entries are filtered
through the native loader adapter. See [symbol tuning](symbol-tuning.md) for exact
eligibility, configuration, source defaults and Fabric density limitations.

Sample arrays allocate only when a watched block occurs. Missing arrays represent
zero samples, avoiding an unused65536-cell allocation for every registered fluid
in every Age. A complete factor signature is persisted separately. Changing
watchers or factors resets incompatible samples and the automatically derived
baseline until resampling; no replacement values are invented. Explicit operator
baselines survive. Source state-specific IMC is currently adapted to block-ID
factors shared by that block's modern states.

## Executing effect providers

Automatic decks now execute source potion duration/amplifier and enemy targeting,
random entity-section selection, scorching, lightning/explosion probabilities,
crumble mappings, decay seed heights/probabilities and three accelerated random
tick attempts per ticking section. Coordinate LCG streams are seeded and
persisted; the old source constructed nondeterministic temporary streams.

Authored EnvAccel/EnvExplosions/EnvLightning/EnvMeteor/EnvScorch effects freeze
explicit occurrence counts and execute independently of automatic profile
readiness. Meteor is a registered real entity/spawner adapter. Colored lightning
consumes the shared ordered gradient queue, freezes each occurrence in execution
order, evaluates its color at total game time/12000 and synchronizes a source RGB
snapshot to the native branch renderer at alpha0.51. Ordinary native striking
behavior remains separate from visual-only operator fixtures.

Native modern explosions replace the old advanced explosion API and skip an
unloaded affected neighborhood. Effects and decay do not explicitly request
unloaded neighbor chunks. Black decay is an implemented explicit hazard but is
**not inserted into automatic decks**: its provider registration is commented
out in the pinned source. Green/yellow handler registration is also TODO in the
pinned source and is not advertised as completed gameplay.

## Implemented decay algorithms

Spreading decay tests all six orthogonal neighbors, skips its exact own state,
and converts with probability1/difficulty:

- Red: air20; otherwise resistance clamped0..1000, negatives treated as1000,
  truncated and floored at1. Hardness1/resistance10.
- Blue: air20; otherwise clamped hardness, truncated **before** multiplying by2,
  floored at1. Hardness5/resistance2.
- Purple: air8, other decay5, liquids3; otherwise
  `max(1,int(2*clampedHardness+clampedResistance))*10`. Hardness50/resistance100.
- White: air50, nonair1; contact deals one magic damage. Hardness50/resistance100.

Black ports the coherent1/10 subsidence versus1/5 horizontal corruption branch.
Horizontal corruption can erase liquids. Falling retains arbitrary block state
and full block-entity NBT through entity saves, checks insertion before removing
the source, and retains BE data on fallback items. Failed fallback insertion keeps
the falling entity available to retry. Temporary never-pickup items prevent
merging outside transaction ownership. Native fall physics and safe spawn-veto
rollback replace the source custom collision/remove-before-spawn ordering.

Decay blocks drop no ordinary item, remove themselves outside Mystcraft Ages,
and reuse original colored block art. Spreading uses a scheduled20-tick pulse,
loaded-neighbor checks and update flag2. The engine persists scheduled ticks.
Conversion considers at most six neighbors and skips out-of-height positions.
The scheduled interval is a documented adapter, not a claim that the broken
source ExtraTicks/decay timing has been reconstructed exactly.

## Pinned source defects retained as provenance

The audited Java snapshot contains incompatible handler-map declarations and
lookup types, stale metadata calls after property conversion, state-versus-block
comparisons, undeclared locals and contradictory black onBlockAdded branches.
`EffectExtraTicks` references an undeclared `state`; decay blocks disable random
ticking while ExtraTicks requires random-ticking states. `DecayHandler` feedback
instability is commented out, so no per-conversion score is invented.

The port preserves the coherent probability, hardness, contact and falling-data
behavior and documents scheduler/physics adapters. The old audit's statements
that the pipeline, black decay and effect seeding were deferred described an
**earlier decay-only development stage**. They are historical findings, not the
current implementation status.

## Operator configuration

`config/mystcraft-instability.json` defaults to:

```json
{"globalEnabled":true,"difficulty":2,"baseline":null}
```

These enable/difficulty defaults match pinned `Mystcraft.java`.
`baseline:null` retains an existing compatible baseline or allows Overworld
profiling when none exists. An explicit map replaces the baseline with
nonnegative integer allowances, e.g. `{"minecraft:diamond_ore":4000}`; omitted
watchers receive zero allowance. An empty object deliberately grants no allowance.
Valid watcher IDs include fixed classic blocks, automatically registered eligible
fluid blocks and explicitly configured addon-equivalent watched blocks.

The file is read once per server session. Unknown keys, wrong JSON types,
fractional/out-of-range integers, negative allowances and unknown watched IDs
reject. An unreadable/invalid instability file logs the error and disables
**automatic** effects for that session while retaining saved data for repair.
Authored environmental effects remain separate source gameplay.

`config/mystcraft-symbols.json` supplies source fluid/biome/card-rank options and
explicit block-factor registration. It loads before spawn-chunk profiling, has
strict size/type/value bounds and requires restart. Unlike an invalid enable/
baseline file, an invalid symbol/factor file rejects startup to avoid publishing
partial grammar and factor tables. See [symbol-tuning.md](symbol-tuning.md).

## Source and test entry points

Shared source lives under `minecraft/instability`: `InstabilityProfile`,
`InstabilityWorldData`, `InstabilityDecks`, `InstabilityRuntime`,
`InstabilityEffects`, decay blocks/propagation and custom falling entities.
Registry adapters and native chunk/PvP/weather hooks live in their loader layers.
`InstabilitySmoke`, `InstabilityFluidSmoke` and `DecaySmoke` exercise the current
pipeline, factor migration and real hazards. These are validation entry points,
not a statement that the newest candidate has already passed them. Consult the
parent-owned validation report for actual execution results.
