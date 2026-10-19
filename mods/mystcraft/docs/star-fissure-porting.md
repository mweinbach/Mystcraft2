# Star Fissure source port

Reference: official Mystcraft-Legacy commit
`9bc8ddc061845df0cd5ea47f4fdef773cff786ae`, specifically `BlockStarFissure`,
`SymbolStarFissure`, `WorldGenMystStarFissure`, `RenderStarFissure`, and the
`LinkController` / `LinkListenerBasic.handleMomentum` path. LGPL attribution is
retained in the module license and notice.

A `mystcraft:starfissure` page enables the feature. Classic execution retains repeated
terminals without rejecting the completed grammar. The source AgeController uses
`flag = flag || mod.populate(...)`: after one population callback succeeds, Java
short-circuits later callbacks, so repeated fissure registrations do not imply
multiple successful fissures in that spawn-chunk pass. The pre-grammar compatibility
parser retains its former duplicate rejection for old bounded plans. Age activation prepares
the arrival point, then generates the fissure once near the Age spawn, records
its origin and `FissureGenerated` in canonical saved state, and retains that state
on later links/restart. It does not regenerate over later player edits.

The original 10–17 noise bands, paired Z rows, random averaging, interval sorting
and adjacent-band overlap are preserved. The source's Y=0 bedrock-floor location
becomes the modern level's minimum build Y. Shaft clearing stops before the
exclusive maximum build height. The old unbounded retry/clear loops are replaced
by finite footprints, a 1,024-column / 262,144-position work budget, world-border
checks and a preflight that refuses to destroy foreign block entities. If the first
site intersects a foreign block entity, generation tries deterministic translations
of the same geometry: at most 25 sites and 96 distinct chunk loads across the
search. It preserves the original inventory and freezes the selected exact columns
before placement, so partial retries cannot move the feature to another site. If
all candidates are unsafe, activation fails without deleting those block entities.
A partial same-footprint retry may complete existing fissure cells. The placement RNG is
derived deterministically from the Age seed instead of sharing a population RNG.
These are explicit modern adaptations rather than an assertion of identical
worldgen coordinates.

The fissure is unbreakable, has no loot or item, emits light level 6, exposes a
0.1-block-high outline and has no physical collision. Its block entity renders a
horizontal plane with vanilla's current end-portal projection shader. This replaces
the old fixed-function end-sky/end-portal texture projection and is not pixel-identical.

Contact performs a natural/external link: the target is the Overworld's shared
spawn, the source entity's yaw/pitch are retained, and no held book or inventory
is consumed. Spawn collision escape scans upward at most 512 positions inside
build-height/world-border bounds; liquid or fully obstructed/out-of-range spawn
fails without clearing blocks. The loader transfer backend moves players and
nonplayer entities exactly, without creating vanilla Nether portals. Forge travel
vetoes are respected by that backend. Successful return clears fall distance and
momentum, then sets upward motion to **+0.2** (the original value is velocity,
not a destination-height offset), applies portal cooldown and plays the original
`linking/link-fissure.ogg` at departure and arrival. Failed attempts are rate-limited
per entity to avoid a per-tick veto-event storm.

Mounted/passenger entities are currently rejected rather than partially moving
a vehicle tree. Original addon `StarFissureLinkEvent` customization and configurable
home dimensions have not been reproduced; home is explicitly the Overworld.
Natural/external behavior is intrinsic to this path and does not import book flags.

`StarFissureSmoke` creates a real fissure Age, verifies minimum-Y geometry and
once-only placement and an alternate-site collision preserving a chest and its
contents, invokes actual player block collision, checks exact home
spawn/orientation/held inventory/+0.2 velocity, transfers a nonplayer item preserving
its stack and verifies an out-of-height spawn rejects. Pure tests check noise
pairing/continuity/determinism and compiler duplicate rejection. Those tests are
provided for centralized execution; this agent did not run Gradle or claim results.

Dynamic Age spawn updates deliberately avoid vanilla `START` tickets. Previously, preparing an Age installed a 529-full-chunk spawn region (2,209 generation-dependency holders observed); a subsequent fissure preflight could wait behind that unnecessary work. Ages now retain only reader/requested/explicitly ticketed chunks. This removes that amplification without disabling the watchdog or relaxing fissure block-entity protection. Candidate search and synchronous cold-chunk generation still have finite work budgets rather than a guaranteed wall-clock completion time; see [server-testing.md](server-testing.md).
