# Version-four native biome surfaces

New compiled generation plans freeze `ClassicTerrainSpec.Version=4`. Only version-four normal/amplified generators invoke the specialized surface adapter. Existing version-zero vanilla generators and frozen version-one, two and three terrain retain their previous algorithms; their unexplored chunks are not silently upgraded. No new generation-plan fields are required.

## Source and modern identities

Pinned Mystcraft `9bc8ddc061845df0cd5ea47f4fdef773cff786ae`, `BiomeWrapperMyst.genTerrainBlocks`, delegates to the wrapped native biome's `genTerrainBlocks`. Its specialized overrides are native Minecraft1.12.2 behavior, rather than additional Mystcraft symbol handlers. The independent native reference is the [1.12.2 source mirror at12ae74c](https://github.com/KealJones/mc-1.12.2-source_files/tree/12ae74ca50912e7f3fe279eeabd3ec02526856e6/src/minecraft/net/minecraft/world/biome): `BiomeMesa`, `BiomeTaiga`, `BiomeHills` and `BiomeSwamp`. The four downloaded files were byte-checked against that immutable revision. Reference source and extraction tools remain test-only under `work/terrain-math/specialized/`; production contains adapted algorithms, not a bundled copy of the reference source tree.

| Modern native biome | Source behavior |
| --- | --- |
| `minecraft:badlands` | Mesa red-sand cap, orange filler, seeded terracotta strata, limited exposed-stone traversal |
| `minecraft:eroded_badlands` | Mesa Bryce pillar height/roof noise and full pillar column traversal |
| `minecraft:wooded_badlands` | Mesa forest plateau coarse-dirt/grass threshold |
| `minecraft:old_growth_pine_taiga`, `old_growth_spruce_taiga` | Mega/mega-spruce coarse dirt above1.75, podzol above−0.95, otherwise grass |
| `minecraft:windswept_hills` | Extreme-hills stone surface above noise1 |
| `minecraft:windswept_gravelly_hills` | Mutated-hills gravel below−1 or above2; otherwise ordinary hill stone threshold |
| `minecraft:windswept_forest` | Extra-trees hills retain grass/dirt rather than the exposed-stone override |
| `minecraft:swamp` | Fixed native seed2345 noise; pool at y62 only when noise>0 and the highest occupied voxel is at62; lily above when noise<0.12 |

Specialized dispatch requires the exact `minecraft` namespace and listed path. Modded biomes with similar names, mangrove swamps and new mountain families are not guessed to be native1.12 equivalents. They retain the prior default surface/material mapping. The source default terrain state machine and chunk-seeded bedrock RNG are retained after the taiga/hills/swamp overrides. Swamp's pool height is the original fixed62, not adjusted to a modified sea level.

## Deliberate native quirks and bounded adaptations

Mesa's band offset samples X on both noise axes, matching its original `getBand` implementation. Its pillar coordinates swap local X/Z within each chunk, and its point sampler preserves the original expression evaluation order and negative/zero floor behavior. Substituting modern simplex point sampling changed11 swamp threshold decisions in a129×129 coordinate audit despite numerical differences below7e−15; the version-four sampler therefore retains the original arithmetic.

The native Mesa biome stores a mutable singleton `worldSeed`; pillar and roof noise initialize from its **previous** seed before updating to the current world's seed. First-instance behavior uses seed0. Cross-world visitation history and concurrent singleton mutation are unsuitable persisted generation inputs. Version four deterministically freezes fresh-instance seed0 for Bryce pillar/roof samplers, while terracotta bands remain Age-seeded. This is an explicit adapter, not a claim to reproduce every historical world-visitation sequence.

As in earlier surface versions, modern chunk columns use natural world X/Z alignment rather than the old primer's transposed local storage convention. Modern quart biome identity/storage and temperature remain adapters. These surfaces run before native structure/population placement and do not scan or request neighboring chunks. They operate only on the supplied chunk's0..255 terrain columns.

## Independent numerical evidence

An extraction of the native `BiomeMesa` methods, retaining its original control flow and `java.util.Random`, compared1,260 Mesa/Bryce/wooded columns across five seeds, positive/negative/distant/world-edge coordinates, seven surface-noise inputs, three heights and an internal air gap: **322,560 block values matched, with zero RNG continuation drift**. The permanent120-case golden set stores SHA256 of entire reference columns and the following random long, rather than expectations computed by the implementation under test.

The original native `NoiseGeneratorPerlin`/`NoiseGeneratorSimplex` compared16,641 swamp noise samples with **zero numerical error**. The51 permanent noise vectors cover dry surfaces, water pools and lily pools. Tests also check exact taiga/hills threshold equality and explicit namespace mapping. Evidence: `work/terrain-math/specialized/comparison.log`, `swamp-comparison.log`; permanent tests: `ClassicSpecializedSurfaceTest`, `classic-mesa-golden.csv`, `classic-swamp-golden.csv`.

These independent comparisons do not establish loader runtime or graphical acceptance. Build/runtime results must be recorded against the actual new packaged artifacts separately.
