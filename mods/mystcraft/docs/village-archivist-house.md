# Classic village archivist house

Source: pinned 1.12 `VillageCreationHandlerArchivistHouse` and
`ComponentVillageArchivistHouse`. The source creation handler is registered
unconditionally. Its native village piece has weight 20, with the old village
size-dependent piece limit chosen between size and size+1. The source house is
9×12×7 with a roof overhang, eight bookshelves, two treasure lecterns, an empty
two-block writing desk and one native archivist villager. It does not supply
ink, paper or starting desk pages and does not use a custom trade screen.

The modern adapter uses the original oak/cobblestone shell, floor, roof,
windows and furnishings in a native structure template. A one-cell Z margin
represents its original roof overhang. A custom, registered SinglePoolElement
codec retains loot/config/villager marker behavior if an unplaced jigsaw piece
is saved and reloaded. The shared resource is generated reproducibly by
`tools/build_archivist_template.py` from the source layout.

Method-only mixins add weight 20 to the five native plains/desert/savanna/snowy/
taiga house pools when their shuffled candidate list is requested. Existing
native or other-mod pool entries are preserved; frozen registries and private
pool fields are not mutated. A max-size hook includes the house's 12-block
height in native jigsaw space calculations. Village pieces remain subject to
native jigsaw connectors, terrain placement, collision tests and chunk saving.
The template's front door aligns with its modern north-facing street
connector; torch attachment states use modern supported directions.

Modern jigsaw candidate weights and size budgets differ from 1.12's separate
piece-count limit. The adapter does not claim identical village distribution,
one-house-per-village behavior, architectural adaptation to each modern
village biome, or byte-identical terrain clearing. The 1.12 house was a fixed
oak/cobblestone design too; no new biome palette is invented.

`config/mystcraft-village.json` is read before world generation on both loaders:

```json
{
  "villageDeskGen": true,
  "archivistEnabled": true
}
```

`villageDeskGen` corresponds to source `generation.villageDeskGen`: false
removes only the desk, retaining the house and its two loot lecterns.
`archivistEnabled` corresponds to the source archivist enable setting: false
suppresses the villager, retaining the building. Restart to apply edits.
Neither switch is misrepresented as a source option disabling the whole house.

The two original lectern positions use the existing source-ranked exploration
page selection. A native Villager receives the registered Mystcraft archivist
profession, normal villager type for the biome, and persistence; the existing
native profession/trade system supplies offers. The custom lectern blocks are
registered as its POI states. No duplicate trader/menu implementation is added.
Cobblestone foundations fill air/liquid downward within the writable chunk.

`VillageArchivistSmoke` verifies all five actual pool hooks, registry-aware
piece codec roundtrip, real template placement and counts, page-bearing
lecterns, an empty configured desk, native archivist profession, disabled
markers and restoration of fixture blocks/entities. Build and production
runtime results are reported separately from this implementation description.

## Permanent career on modern villagers

The 1.12 archivist career does not depend on a modern job-site memory. Native
1.19.2 `ResetProfession` requires absent `JOB_SITE`, profession other than
NONE/NITWIT, XP zero and level at most one; it then changes the career to NONE.
Spawned archivists therefore receive one villager XP, keeping novice level and
normal offers while preserving the source career even with the desk disabled.
A one-use initialization tag applies that same profession/XP on the first native
villager tick and is immediately removed. This covers MCA's asynchronous native
villager replacement: its converter preserves command tags and profession but
omits villager XP. It does not repeatedly force a career after initialization or
change unrelated villagers. The shared helper verifies the profession and actual
page/booster offers after forty server ticks while the house fixture remains
intact. The forty-tick profession and actual page/booster trade checks are included in the plain-loader suites recorded in [validation.md](validation.md). The final Tekxit suite also passed with MCA present; earlier diagnostics and the final artifact-specific results are distinguished in the central validation record.
