# Blast implementation and remaining differences

Catalog and default quantities derive from ICBM Classic's MIT `ExplosiveInit`,
`ConfigBlast`, and blast implementations at the pinned upstream revision.
The Java code here is a new server-side implementation using vanilla entities,
particles, status effects and Fabric world tick callbacks; no upstream assets.

All 27 registered IDs are represented, including four upstream non-content slots.
23 content payloads are executable. Reserved hypersonic/missile slots are no-ops,
as upstream. Tier and delivery-specific default fuses are exposed by the enum.

Distinct implemented mechanics: condensed vanilla power6; burning arrow shrapnel
(30/damage11); falling anvil fragments (25/damage30); fragmentation (15 explosive
rays plus damage11 arrows); fire placement radius14; lingering 600-tick radius20
debilitation/chemical/contagious fields; inward/outward force; narrow energy-budget
breaching (7 deep/15376 energy); sonic ejected blocks; resistance-filtered radius30
thermobaric/radius50 nuclear destruction; nuclear pig/villager mutation; radius55
antimatter removal; radius30 hot/cold terrain transformations; lift fields;
random local ender relocation; temporary redmatter attraction and consumption;
100-tick smoke emitters; harmless colorful dust gas. EMP delegates to registered technology handlers.

These are **not complete blast parity**. Differences requiring follow-up:
- Condensed propagation/damage uses vanilla, not upstream ray energy logic.
- Projectile collision physics are vanilla; fragmentation uses immediate traced
  secondary bursts, not the upstream moving explosive fragment entity.
- Gas is a persisted spherical field; no upstream cell diffusion, hazmat
  integration, toxicity capability, or independently carried contagious illness.
- Large destruction uses an explicit resistance/distance rule, not upstream
  threaded ray propagation. It respects unbreakable blocks, including antimatter
  (upstream antimatter can optionally destroy unbreakable terrain).
- Redmatter is a stationary 120-second queued field; feeding, merging,
  motion, variable size, antimatter cancellation and full entity are outstanding.
- Antigravity lifts entities for120 seconds; falling blocks regain vanilla
  gravity immediately and do not reproduce custom flying-block rendering.
- Ender uses safe random local relocation; GPS/custom destination and cross-world
  transplantation are outstanding.
- Rot uses coarse dirt/plant decay; custom radioactive block replacements,
  radiation capability and custom mutation mobs remain outstanding.
- Color retains upstream visual-only intent using vanilla dust particles. Hot/cold transforms cover vanilla base terrain and fluids;
  upstream full material classification and day/night changes are outstanding.
- Configuration uses the Fabric JSON format described below; legacy Forge
  config files, full material replacement lists and optional integration knobs
  are not imported.

Work is persisted per dimension through PersistentState. All edits are on server
world ticks, skip unloaded chunks/build limits/border, and never retain tickets.
At most4096 candidate block positions are visited per world tick (512/job),
256 pending effects are admitted, and each entity query affects at most256
entities. Unloaded portions are skipped permanently. Multiple active blasts are
served round-robin (eight jobs per world tick), so every admitted job receives a
turn within32 ticks even when earlier jobs are long lived. Queued
source entity attribution is not persisted. These limits are intentional port
performance bounds, not upstream behavior parity.


## Server configuration

`Payloads.initialize()` creates `config/icbm_port/payloads.json` with an entry for
all27 catalog IDs. Operators with permission level2 can run
`/icbm_payloads reload`; restarting the server also loads it. Omitted entries and
fields retain typed defaults. Unknown IDs/fields, wrong JSON types, fractional
integers, nonfinite values and out-of-range values reject the complete reload;
the previous working configuration remains active and an error is reported.
A malformed file at startup logs an error and uses defaults. Files over1MiB are
rejected. This file is server configuration and is not client-synchronized.

Every entry exposes:

| Field | Valid range | Effects which consume it |
| --- | --- | --- |
| enabled | boolean | All types; disabled queued effects end on their next service turn |
| radius | 0–80 | Queued terrain/gas/gravity effects, condensed, attractive/repulsive, ender and mutation; EMP through the defense callback |
| entityDamage | 0–2147483647 | Shrapnel, anvil, fragmentation arrows, breaching, thermobaric/nuclear, antimatter and redmatter |
| fragments | 0–128 | Shrapnel, anvil and fragmentation |
| durationTicks | 0–72000 | Gas, smoke, antigravity and redmatter |
| energy | 0–20000 | Breaching budget, thermobaric/nuclear resistance threshold, fragmentation burst power |
| blockFuseTicks | 0–72000 | Payload block activation |
| cartFuseTicks | 0–72000 | Bomb cart activation |
| grenadeFuseTicks | 0–72000 | Grenade activation |

Unused fields are retained in the uniform schema but have no effect on that
family. Condensed uses vanilla explosion power from radius, capped16; its
entityDamage and energy fields do not replace vanilla explosion calculations.
Attractive/repulsive vanilla burst power and fragmentation burst power are also
capped16. Existing queued effects retain their initial radius, including through
save/reload; duration, damage, energy and enabled changes apply on subsequent
ticks. Existing primed delivery forms retain their already assigned fuse.
The redmatter defaults intentionally retain this port's bounded radius12 and
2400-tick lifespan, rather than claiming upstream variable-size entity parity.
Gas potion durations/amplifiers, toxicity growth, force strengths, breaching
width/depth and transport offsets remain fixed implementation parameters.

Saved queue loading rejects nonfinite/out-of-world coordinates and unknown types,
clamps progress to the stored cube size and72000-tick age limit, inspects at most
4096 saved entries, and accepts at most256 jobs. Deferred jobs share that admission
cap and are included in serialization. Dedicated-server queue tests exercise
resuming an interrupted thermal transformation, preservation of bedrock, the
4096-candidate world budget,512-candidate job shares, malformed saved state and
combined immediate/deferred admission.

Every admitted job increments a separate persisted elapsed lifetime each server
world tick, including ticks when it receives no work slot. Gas, smoke, antigravity
and redmatter expire by their configured duration and retire within at most32
additional ticks of service latency under a full queue. Expired fields apply no
further effects when served. Processed age remains separate for first-effect and
pulse scheduling. Legacy saves without elapsed lifetime use prior processed age
as a compatibility fallback. A full256-job load test saves and restores fields
near expiry, then verifies all retire within the bounded service latency.
The debilitation integration test removes its own live field at completion to
prevent accumulating persistent test work across reruns.
