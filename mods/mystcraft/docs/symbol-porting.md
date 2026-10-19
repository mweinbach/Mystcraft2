# Classic symbol data and execution contract

The authoritative reference is official Mystcraft Legacy commit
[`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`](https://github.com/Mystcraft/Mystcraft-Legacy/tree/9bc8ddc061845df0cd5ea47f4fdef773cff786ae), under LGPL v3.
`classic-symbols.json` inventories static registrations, literal/loop-expanded block
modifiers, color values, grammar groups/parameter order/ranks, source registration
expressions, base production expressions and legacy remapping expressions. Its
identical runtime copy is `assets/mystcraft/classic_symbols.json`. Metadata is not
an executable world generator: reference `implemented: false` records incomplete
full classic behavior; `modern_implemented` identifies the 145 static entries with current modern
compiler-to-runtime paths. These flags do not prove exhaustive runtime parity.

There are 145 static registrations: 94 in `ModSymbols`, 16 color modifiers and 35
blockstate modifiers (including stone and log metadata loops). Runtime biome,
fluid and addon families are explicitly recorded separately. There cannot be a
universal finite list of all addon symbol IDs: classic biome tokens use numeric
1.12 registry IDs, and fluid blockstate symbols depend on the installed modpack.
The fixed catalog does not fabricate those IDs from the current vanilla registry.

`SymbolRules` associates terminals with grammar parents and preceding parameters.
Examples include terrain/sea blocks before a terrain generator; biome tokens before
a biome controller; period/angle/phase sequences before sun or moon; color/gradient
sequences before sky/fog/star effects; and material tokens before structural features.
The original grammar additionally inserts missing productions with weighted random
completion, handles redundant/invalid symbols, consumes modifier state in order and
applies classic instability. `ModClear`, dummy features, darkness tokens and repeated
modifiers are meaningful, not automatically discarded. None of that evaluation is
implemented simply by looking up a catalog entry. The current source implements a separate weighted grammar engine and ordered
execution compilers. A catalog lookup or generic prefix parser alone does not
establish classic grammar parity.

The new versioned source grammar entry point is documented in [classic-grammar.md](classic-grammar.md). Its completed execution list is separate from the written pages; the bounded parser below remains the compatibility path for older saved plans. Runtime support is validated after completion rather than inferred from metadata.

## Shared API

`net.mystcraft.minecraft.writing.PageData` keeps classic `symbol`, `linkpanel` and
`Quality` keys. `legacySymbol(ItemStack|CompoundTag)` returns the unmodified token;
`symbol(ItemStack|CompoundTag)` returns an optional lowercase `ResourceLocation`
view. Invalid or unknown legacy strings remain stored even if no valid modern
identifier can be produced. Raw separate linking-panel items normalize to an empty `linkpanel` compound in
`payload` while preserving all existing metadata; canonical tagged page items also
qualify. It never guesses numeric biome migrations or expands
old aliases. Aliases can expand to several ordered symbols, so future migration
must retain the original pages and apply an explicit versioned mapping separately.

- `payload(ItemStack)` returns a copied page payload; `applyPayload(ItemStack, CompoundTag)` copies it into a page.
- `setSymbol(ItemStack, ResourceLocation)` and `setLegacySymbol(ItemStack|CompoundTag, String)` set the symbol.
- `isBlank(ItemStack)`, `isLinkPanel(ItemStack|CompoundTag)` and `makeLinkPanel(ItemStack|CompoundTag)` distinguish page kinds.
- `linkProperties(ItemStack|CompoundTag)` and `addLinkProperty(ItemStack, String)` retain ordered classic link-panel properties.
- `quality(ItemStack, String)` and `setQuality(ItemStack, String, int)` preserve quality traits.
- `readPages(CompoundTag)` and `writePages(CompoundTag, List<CompoundTag>)` use `Pages` as a list of page **payloads**, not full `ItemStack.save` wrappers. Each entry is copied; order, duplicate pages, blank entries, quality and unknown addon keys survive. Callers own container capacity limits and paid item transfers.

`SymbolRegistry.all()` and `find(ResourceLocation|String)` expose immutable metadata.
`outcome(String)` distinguishes `IMPLEMENTED`, `KNOWN_UNPORTED` and `UNKNOWN`.
All 145 current static entries return `IMPLEMENTED`; `KNOWN_UNPORTED` remains an
API distinction for any future catalog entry without an execution path. This is
source status, not a claim that all combinations have passed packaged runtime tests.
Unknown input is retained, not silently substituted with a random symbol. `PageItem` supplies a blank creative
variant and catalog-backed symbol variants, with a meaningful classic English label and explicit implementation-status
tooltip. Original four-word glyphs are displayed using generated models and the
shared `PageItemModels.value` client predicate; original poems, word components
and model values are retained in the catalog. See `asset-provenance.md`. Parent Age registration owns the page item and descriptive-book suppliers.

## Current 0.4 execution contract

New Age allocation uses `AgeSymbolCompiler.compile`, the source reverse grammar
parse and weighted completion, then generation/visual execution in terminal order.
The 226 static grammar productions are supplemented by installed biome/material
rules. Original written payloads remain separate from the frozen execution list,
per-symbol seeds and diagnostics. Duplicate controllers replace previous ones and
charge source costs; dangling modifiers and Clear have their original costs.
See [classic-grammar.md](classic-grammar.md) for exact RNG, provenance and bounds.

All 145 static entries have a traceable execution path in the
[implementation audit](static-symbol-implementation-audit.md). Nine biome
controllers, native registry biome/fluid pages, full material states and ordered
features are described in [generation-plan.md](generation-plan.md) and
[dynamic-pages.md](dynamic-pages.md). Registry-native identifiers do not guess old
numeric biome mappings. New allocations use terrain version4; terrain versions1/2/3 and old
generation-zero plans remain frozen; normal/amplified version2 implements classic density rather than selecting
a modern vanilla approximation. Modern surfaces, biome resolution, structures and
population still have explicit adapter boundaries.

Celestial, color, lighting and weather/environment consumers are documented in
[celestial-implementation.md](celestial-implementation.md) and
[environment-implementation.md](environment-implementation.md). Classic symbol
costs feed the [instability runtime](classic-instability-audit.md), including
profiling and persisted decks; they are not an invented count-of-pages score.
Repeated Star Fissure terminals are retained in execution while feature enablement
coalesces under the source callback short-circuit.

## Historical bounded compiler compatibility

Earlier development builds accepted only 62 static controls through
`AgeSymbolCompiler.parse`. That strict parser remains a compatibility path for
older frozen plans; it is not the new-allocation grammar. It rejected duplicate
controllers and unsupported consumers, used fixed-plains/native approximations in
some paths, and did not perform weighted completion. Those historical restrictions
must not be read as the current 0.4 feature inventory. Existing saved generators and
visual settings are not silently recompiled when support expands. Historical test
results establish their recorded artifacts only. Current 0.4 build, plain-loader
runtime and graphical evidence is recorded in [validation.md](validation.md);
The final Tekxit server suite and fresh-JVM persistence checks also passed; see that record for exact artifact scope.

## Weather cycle runtime

`AgeWeatherCycle` ports the timer transitions and duration/cooldown ranges from
`WeatherControllerBase` and `SymbolWeatherNormal/Slow/Fast`. Normal rain durations
are 12,000–23,999 ticks with 12,000–179,999 cooldowns; thunder durations are
3,600–15,599 with the same cooldown range. Slow doubles those bounds; fast halves
them. Independent rain/thunder counters toggle at zero and choose the subsequent
interval on the following tick, matching source order (thunder before rain).
Per-Age RNG state persists with the counters so restarting cannot reroll weather;
legacy used an unsaved random instance, so deterministic restart continuity is
an intentional modern adaptation.

`AgeLevelData` owns these controllers and disables vanilla weather-cycle advancement
to avoid double-decrement/toggle; `AgeServerLevel` advances once per level tick.
Vanilla still interpolates rain/thunder intensity, sends weather packets and
performs biome-appropriate precipitation/lightning. Normal/off/always/slow/fast
never mutate another Age's state. Source rain/snow/storm/cloud controls and cloud color/height now have modern
adapters described in [environment-implementation.md](environment-implementation.md). Celestial parameters execute only with their supported
consumers. See [validation.md](validation.md) for the tested build/runtime scope;
source-informed tests alone are not full classic parity.

## Integration requirements

The binder stores player-written pages in their submitted order; an Age record
must snapshot them at first creation, separately from any completed execution
symbol list. The desk copies actual source payloads using paper/ink and must
never turn a selected unknown token into a known one. Generated Ages use a
separate frozen evaluator result with recorded seed, random completion decisions,
consumed parameter groups, implemented effects and diagnostics.
`IMPLEMENTED` records an actual source execution path; verification evidence
must additionally identify the tested artifact and behavior. Dynamic material/biome registration needs modern state IDs
plus an explicit legacy identity/migration map, especially for Tekxit4 content.

Meaningful tests cover ordered/copy-isolated NBT, lossless unknown legacy tokens,
modifier ordering, conflicting/malformed controls, numeric-biome rejection and
bounded compiler input. Build, runtime and graphical evidence apply to specific artifacts; consult
[validation.md](validation.md) for their scope rather than treating catalog
status as proof of full classic behavior.
