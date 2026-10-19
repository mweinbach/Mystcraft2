# Classic ranks, fluids and watched-factor configuration

Source reference: official classic1.12 commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`:
`ModSymbolsFluids.modsLoaded/loadDefaults`, `ModSymbols.registerBiomes`,
`ModSymbolsModifiers`, `InstabilityData.initialize`, `InstabilityBlockManager`
and the source block-instability/fluid IMC handlers.

## Source findings and implemented correction

Classic automatically registers symbols **and watched instability factors** for
eligible registered non-water/lava fluid blocks. It skips gaseous/negative-density
fluids, blacklisted entries and fluids without usable blocks. Ordinary fluids use
card rank4, grammar rank4, sea allowed, accessibility1.0 and flat factor0.25.
The previous modern page registry created these symbols but omitted their watched
factors. This is a real ordinary mod-fluid gap, including Tekxit fluids, rather
than an optional ore addon feature.

The shared implementation now registers these factors before chunk profiling.
All65 named fluid overrides in the pinned `loadDefaults` are copied verbatim to
`assets/mystcraft/classic_fluid_defaults.json`. They match an actual legacy
translation key, not a guessed registry-path alias: unknown modern modded fluids
use the ordinary1/.25 defaults even if their name contains oil, mana or an ore.
Mystcraft's own black ink has its exact known source identity and uses1/0.
Examples from the source include oil1/0, mana-6/-2 and ender sea-banned72/6.
A modern mod's changed translation key can be mapped explicitly by its operator;
its old name is not silently inferred.

Classic does **not** automatically invent watchers for every registered/tagged
mod ore. The ten fixed built-in watchers remain exact. Additional ore/block
factors require explicit operator/addon registration. Current watch identity is
block registry ID, so all modern states of that block share factors; source
state-specific IMC granularity is not claimed.

Material descriptors retain the source's35 fixed definitions and category/rank
predicates, including source metadata-to-modern-block mappings. No guessed modded
ore/material rank is added. Biome symbols use source card2/grammar1; End remains
unranked by default. Grammar and acquisition ranks remain independent.

## Bounded operator file

Both loaders read `config/mystcraft-symbols.json` at server startup, before
spawn-chunk profiling. Changes require a server restart. Invalid configuration
rejects startup instead of applying only part of the requested table. Defaults:

```json
{
  "fluids": {},
  "biomes": {},
  "cardRanks": {},
  "watchedBlocks": {}
}
```

Example shape (replace example IDs with **real registered IDs** in the pack):

```json
{
  "fluids": {
    "example:molten_metal": {
      "seaBanned": true,
      "grammarRank": 4,
      "cardRank": 4,
      "accessible": 18.0,
      "total": 3.0
    }
  },
  "biomes": {
    "example:rare_biome": {"grammarRank": null, "cardRank": 5}
  },
  "cardRanks": {"mystcraft:modmat_diamond_ore_0": 6},
  "watchedBlocks": {
    "example:custom_ore": {"accessible": 60.0, "total": 1.0}
  }
}
```

`fluids` additionally permits `blacklisted:true`; `biomes` permits the same.
A sea ban removes only SEA eligibility while leaving FLUID consumers available.
Null grammar rank disables random grammar selection while retaining explicit
usage; null card rank disables ordinary acquisition. Generic `cardRanks` override
static or dynamic symbol acquisition without changing grammar categories.
`watchedBlocks` provides explicit block factors analogous to source block-factor
IMC, and takes precedence over an automatic fluid factor for the same block.
It does not automatically classify that block as a material symbol.

The file is limited to1MiB and each map to4096 entries. Ranks are integer0..16 or
null. Factors must be finite and lie within±1,000,000; negative factors are valid
classic stabilizing behavior. Fluid, biome and watched-block IDs must exist;
unknown fields and wrong JSON types reject. This is an operator interface, not
a freely editable survival book property or a new Mystcraft2 stability system.

## Saved profiles and baseline migration

Profiles now allocate a65536-cell map only when a watched block is encountered,
not once per registered fluid for every Age. Missing maps represent zero samples,
which preserves score arithmetic without excessive memory for large fluid packs.
Profiles persist a complete `Watchers` factor signature separately from the sparse
sample maps. A changed watcher set or factor table invalidates old samples.
An automatically derived baseline is also invalidated until enough fresh samples
exist; no replacement baseline is invented. Explicit operator baselines remain
persisted and current file-provided baselines still apply. Age effect deck state
is not reset by this profile migration.

Old dense profiles without a signature remain compatible only when their saved
block-key set exactly matches the current watcher set. Adding the previously
missing fluid watchers therefore triggers a deliberate resample rather than
silently dividing new fluid counts by old unrelated chunk totals. Changing
operator factors also invalidates the signature so automatic baselines cannot
retain obsolete scales.

## Validation boundaries

`SymbolTuningTest` checks ordinary and exact named defaults, independent nullable
ranks, sea bans and strict validation. `InstabilityFluidSmoke` checks every eligible
fluid in the **actual server registry**, fluid score arithmetic, sparse allocation
and persisted profile/baseline migration without changing the operator's config.
Builds and packaged executions are parent-owned; record their actual results
separately. Fabric has no universal Forge density API: its native lighter-than-air
attribute supplies the gas exclusion adapter. Existing frozen Age execution and
material metadata are not rewritten when operator tuning changes.
