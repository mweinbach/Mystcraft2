# Authored native structure adapters

Pinned `SymbolMineshafts`, `SymbolNetherFort`, `SymbolStrongholds`, and
`SymbolVillages` delegate their terrain/population phases to native 1.12
MapGen classes. All four ignore their `registerLogic(..., seed)` argument:
the native generators seed themselves from the world. Repeated pages therefore
use the same structure identity and world-seed placement stream. The adapter
retains that behavior rather than inventing independent per-page salts.
Frozen alteration entries still retain their original symbol seeds and order.

For executable classic generation plans, `ClassicStructures.select` exposes
only explicitly authored controllers. No authored controllers means an empty
native structure-set stream. Mineshafts, strongholds and villages select only
the corresponding vanilla set. Nether fortresses select the fortress entry
from `nether_complexes`; bastion entries are removed. Other native structures
(outposts, monuments, temples, mansions, cities, shipwrecks, ruins and so on)
are not enabled by these four pages. Legacy pre-plan generators retain their
previous native selection for save compatibility.

Modern fortress biome tags would otherwise suppress an explicitly authored
fortress in a plains Age. An additional native generation call retains the
original NetherBridge ability to run in an Age of another biome. It uses the
same native structure placement and registered structure identity, preserving
ordinary structure-start saving/references and avoiding unregistered wrappers.
An existing valid start is retained; duplicate pages cannot create extra starts
at an already occupied native structure identity.

This is a native 1.19.2 adapter, not a port of every 1.12 vanilla structure
piece. Native spacing, concentric rings, piece selection, jigsaw villages,
height adjustment, biome tags and locate internals differ between versions.
A fortress-specific native placement search bypasses modern locate biome
caches, so authored fortresses remain locatable even in a plains-only Age.
Exact 1.12 village/fortress architecture, 1.12 positions and cross-version
structure migration are not promised.

# Floating islands

`ClassicFloatingIslands` mechanically adapts pinned `MapGenFloatingIslands`:
range five, per-neighbor rate 1/192, source seeded neighboring-chunk sweep,
flattened main node, 40–51 irregular satellite nodes and original walking-node
geometry. Modified column biomes are converted to the modern 4×4 quart storage
grain over the entire vertical column. Surface materials use explicit modern
biome counterparts. The source's invalid IBlockState-to-Block comparison is
repaired to the intended stone test. Its surface octave generator was built
from an unseeded random before the generation sweep; the adapter instead
freezes that noise to the symbol seed to make chunk generation deterministic.
These adaptations are explicit and do not claim byte-identical 1.12 chunks.
