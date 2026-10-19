# Static classic symbol implementation audit

Reference: official classic Mystcraft `1.12/develop`, pinned commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`.

All 145 fixed registry entries now have a traceable compiler-to-runtime path.
83 previously false `modern_implemented` flags were promoted in both identical
catalog copies, bringing the source implementation count from62 to145. These
flags mean an implemented modern execution path, **not** complete legacy world,
visual or interaction parity, and **not** a claim that all145 controls have been
exercised in a packaged client/server session.

The audit found one real core dispatcher omission: `envmeteor` had an implemented
Meteor entity, bootstrap registration, spawner callback and ticking-chunk runtime,
but was absent from the classic compiler acceptance set. Parent confirmed the
runtime wiring and authorized adding that terminal. This was the only unhandled
fixed registry entry found in the final source-path review.

## Evidence map

The machine-readable [audit](static-symbol-implementation-audit.json) lists every
symbol and its compiler/runtime source files. The independent audit classifier
is closed over an explicit145-entry set; it checks catalog identity, classification
uniqueness, source-file existence, the Meteor dispatch gate and both loader PvP
hooks. It does not execute Java or assert that file existence alone proves parity.
The paths were also reviewed for actual dispatch/consumer calls.

| Source/runtime family | Fixed symbols |
| --- | ---: |
| colors | 13 |
| celestials | 11 |
| biome controllers | 9 |
| weather | 9 |
| terrain | 6 |
| generation features | 18 |
| environment effects | 5 |
| source dummy costs | 3 |
| visual modifiers | 17 |
| pvp | 1 |
| no sea | 1 |
| star fissure | 1 |
| color modifiers | 16 |
| materials | 35 |

Run the repeatable source check with:

```
python3 mods/mystcraft/scripts/audit_static_symbols.py --write-report
```

It passes145/145 classification and145 implementation flags. The shared-loader
resource/parity check also passes439 identical resources at this audit point.
No Gradle builds were run by this audit agent. Parent owns both loader builds,
packaged smoke execution and the expanded multi-seed completion audit.

Generation ownership review confirmed all18 feature terminals and all35 fixed
material descriptors. Core ownership confirmed dummy terminals intentionally
apply source costs without placing a feature. Source Rainbow rendering is
unconditional, stationary and angle-controlled; NoHorizon suppresses both lower
sky surfaces in completed custom-sky profiles. Weather has separate strength,
precipitation and temperature rules, rather than a single rain flag. Environmental
lightning consumes the shared ordered gradient stack and the runtime uses a synced
colored lightning entity; Meteor now passes the core acceptance gate.

## Remaining parity limits and core gaps

No additional unhandled **fixed static symbol** was found. Dynamic biome/fluid
families and third-party symbols are not part of this145-entry count. Malformed
pages, unknown symbols and plans exceeding the documented bounded budgets still
reject; metadata promotion does not bypass validation.

Implementation and compatibility boundaries outside this symbol inventory:

- Classic `black_ink` is implemented as a placeable fluid with bucket/vial
  containers and native Forge/Fabric automation adapters. Desk fractional
  transactions and mixer container behavior are documented in
  [classic ink fluid](classic-ink-fluid.md); tested artifact scope is recorded in
  [validation.md](validation.md). This functionality does not enlarge the static
  symbol count or establish compatibility with every external fluid consumer.
- Native biome climate, structure templates/spacing, dungeon contents and ore
  vein placement use1.19 adapters. They execute but do not reproduce every1.12
  biome population result. Floating islands use modern biome quart storage;
  Skylands filters world-generation stages rather than intercepting every source
  block placement. See [generation plan](generation-plan.md).
- Server temperature overrides cover weather/rain checks and native snow/ice
  placement through scoped calls. Unrelated entity/mod temperature reads outside
  these scopes still see the registered biome. Grass/foliage natural color uses
  the requested position rather than all legacy neighborhood-blend details.
- Cloud geometry and weather interpolation are native modern behavior. Explicit
  radial sky/fog blending substitutes for fixed-function GL_FOG; it requires
  graphical comparison and is not pixel-identical by assertion. Existing frozen
  visualv1/v2 profiles retain their earlier native-cloud/sky behavior.
- The source-path count does not establish addon API compatibility, cross-loader
  world migration, every UI interaction or complete acquisition/persistence parity.

See [environment status](environment-implementation.md) and
[celestial status](celestial-implementation.md) for implementation and validation
boundaries. Runtime reports should record actual assertions/captures independently
of this catalog count.

## Executable exhaustive server witness check

The asynchronous `mystcraft_symbol_audit` command supplies145 catalog witnesses across
four deterministic seeds (0,1,-1 and0x123456789abcdef), for580 actual
`AgeAllocation.compile` preparations. Every witness uses a known complete root
skeleton and appends the target in source-valid context: material descriptors
choose a matching sea/terrain/structure consumer, biome controllers get a real
palette, and visual modifiers receive their actual angle/factor/phase/color/
gradient consumers. ModClear deliberately clears pending values; it is not
incorrectly tested as an isolated world controller.

Assertions check marked authored NBT retention, consumed queue targets, unchanged
caller-owned pages, the complete independent per-terminal Random stream, frozen
visual/execution serialization and mandatory executable allocation state. An
additional identical full preparation for each target checks determinism. The
check snapshots canonical Age and loaded-world counts and requires zero changes.
It creates no canonical Age identity and generates no world chunks.

This is an executable acceptance/preparation test, not a claim that all145
renderers, generators or effects were observed in a live world. The parent must
record its actual execution result before reporting these580 witnesses as passed.

## Frozen terrain versions

The static catalog count is independent of terrain builder version. Current new allocations freeze terrain specification version4 for specialized native surfaces; the ordered `ClassicGeneration` schema remains1, and saved version0–3 generators keep their previous algorithms. This does not add symbols or increase the145-entry source-path count. See [specialized surfaces](classic-specialized-surfaces.md) for native reference goldens and [validation.md](validation.md) for artifact-specific runtime results. The final 0.4 plain-loader suites include version-four native surface checks and frozen-version-three contrast; this does not establish every authored combination or a passing Tekxit run.
