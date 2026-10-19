# Classic generation plans

Reference: Mystcraft-Legacy `1.12/develop`, pinned commit `9bc8ddc061845df0cd5ea47f4fdef773cff786ae`, LGPL-3.0. This documents the current implementation and its adapters; it is not a claim that all generated chunks match 1.12.

New execution plans persist an independent `ClassicGeneration` compound, schema version 1, with terrain specification version 4. The frozen data includes ordered biome registry identities, selected materials with full block-state properties, controller mode, ordered alteration/population effects, and their assigned symbol seeds. Random biome completion resolves against the actual registry once before persistence. Existing terrain versions zero through three keep their frozen generation/surface behavior. Versions one and two do not acquire the later ordered plan features merely by upgrading, and version three does not acquire version-four specialized surfaces. The `ClassicGeneration` compound remains schema version1; its nested terrain version selects the frozen builder.

The compiler preserves the source biome LIFO queue and newest matching block category, including sea selection before terrain selection. Duplicate terrain/biome controllers replace earlier controllers in execution mode; they contribute separate instability accounting. Leftover biome/material parameters cost 100/50, and Clear discards pending parameters for 20% of their cost. The strict compatibility parser remains separate from completed-grammar execution.

## Biome controllers

Single, Grid and Tiled preserve the ordered palette and source minimum palette sizes. Grid uses `floorMod((x >> 4) + (z >> 4), count)`. Tiled intentionally preserves the source's different scaling between biome queries and density generation. Tiny, Small, Medium, Large and Huge use the source integer LCG, `GenLayerBiomeMyst`, two initial `GenLayerZoomMyst` passes, zero through four extra zooms, Smooth and Voronoi. The unused Island/FuzzyZoom parent has no effect because BiomeMyst does not consult it.

The independent rectangular sampler in `scripts/classic_biome_oracle.py` supplies golden values for all five zoom sizes, including negative and distant coordinates. `ClassicBiomeLayersTest` checks those values and sampling-order independence. Biome holders/codecs preserve modded registry identities instead of storing numeric IDs. Floating-island biomes are also retained in the biome source's possible set.

Native uses Minecraft 1.19.2's Overworld climate biome source, rather than a claimed byte-identical 1.12 native biome distribution. The 1.19 biome storage/jitter has four-block resolution, so one-block biome boundaries and mixed island columns are adapted to modern quart cells. Legacy terrain depth/variation profiles map modern vanilla identities; modded biome identities survive, but their removed 1.12 terrain-profile fields have a documented fallback.

## Specialized surfaces

New version-four normal/amplified plans dispatch explicit native-biome overrides for badlands bands/Bryce/wooded plateaus, old-growth taiga ground cover, windswept hill surfaces and swamp pools/lilies. [The source audit and independent goldens](classic-specialized-surfaces.md) describe exact thresholds and the deterministic adaptation of Mesa's previous-world-seed singleton. Version-three and earlier surfaces retain their saved behavior. No new grammar productions or static symbols are implied by this surface version.

## Geometry and population

Caves, ravines, spheres and tendrils adapt the pinned `MapGenAdvanced` neighborhood seeding and traversal. They run after the biome surface pass, matching ChunkProviderMyst. Each chunk gets fresh mutable generator instances, preserving deterministic results under concurrent generation. Bedrock and the source liquid replacement rule are preserved. The original ravine's unusual lower-Y loop is retained, rather than silently repaired. Huge trees and floating islands have their own source comparison/adaptation notes in their implementation documentation.

Obelisks use the original one-in-128 attempt, four-by-four foundation and two-by-two twelve-block column. Surface/deep lakes, spikes and crystal lines use the original algorithms, probabilities and material-category selection. Skylands uses the original seven-octave cutoff `76 + (int) noise`, removes liquids, and is applied after surface and population stages; its cloud/horizon settings are handled by the visual settings compiler. When the filter removes a generated block, it also removes its live or packed block-entity data; retained structure blocks keep their exact data. Modern neighboring-chunk decoration and structure write ordering remain an adapter boundary: the source intercepted every primer write, whereas the modern path filters completed chunk stages.

Dungeons use eight classic attempts at Y 0..255 with the modern `MONSTER_ROOM` feature for contents. Dense Ores preserves the eight ore types, counts, sizes, targets and height ranges; actual veins use the modern ore feature, so vein geometry and random consumption differ from vanilla 1.12. The old Forge-only DenseOresEvent extension point has no shared cross-loader port here.

Vanilla structure symbols select explicit native structure sets; absent structure symbols suppress those structures for new plans. Modern native templates/placement are adapters, not a port of every 1.12 structure piece. Existing Ages retain their previous structure behavior. Modern world-generation writes avoid replacing block entities and respect writable-region/build-height bounds.

## Validation scope

`ClassicGenerationSmoke` tests real registry/state NBT round trips, parameter consumption/costs, controller replacement, negative Grid coordinates, Tiled density scaling, actual cave changes in independent real ProtoChunks with preserved bedrock, and version-four Mesa/swamp dispatch contrasted with retained version-three behavior. Synthetic biome fixtures fill native section palettes before advancing to BIOMES; they do not request live chunks or queue live lighting work. These checks require the separate server smoke harness. Parent builds and runtime execution determine validation status; source presence alone does not establish passing loader or modpack behavior.
