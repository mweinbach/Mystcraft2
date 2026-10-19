# Crystal portals

The source reference is Mystcraft-Legacy `PortalUtils`, `BlockCrystal`,
`BlockBookReceptacle`, `BlockLinkPortal`, and `TileEntityBookReceptacle` at the
revision pinned in `NOTICE.md`. The original crystal, receptacle, and animated
portal textures are retained byte for byte; models are adapted to modern resource
locations. The receptacle recipe uses eight crystal blocks around an empty center.

A receptacle mounts on a crystal face, except the underside. Inserting one linking
or descriptive book activates the connected field; using an empty hand retrieves
the book without requiring sneak. The source book remains in the receptacle while
players link through the field, and its destination and unknown NBT persist.
Breaking the receptacle drops its book once and invalidates its field.

This is not the vanilla Nether portal rectangle algorithm. Classic growth fills
air with at least two adjacent crystal/portal neighbors. It then prunes cells that
lack two opposite-neighbor axis pairs. The resulting field can be horizontal,
vertical, nonrectangular, or volumetric. Directional source pointers on active
crystals and field cells lead to the book receptacle; loops and missing sources
are rejected. Book removal and broken boundaries invalidate dependent fields.
The initial growth seeds are the same eighteen face/edge neighbors of the crystal
supporting the receptacle used by the legacy implementation.

The port adds explicit **per-operation** safety limits absent from that source:
4,096 graph/path nodes and 131,072 growth/invalidation queue steps. Oversized
activation rolls back newly created portal cells and retains the source book.
These are not an aggregate per-server-tick scheduler or a rectangle-size rule.
Only loaded chunks participate; activation and source validation do not force-load
remote chunks. A frame crossing unloaded chunks may fail or invalidate and can be
reactivated by reinserting its book after the relevant chunks load.

Portal collisions are server authoritative and use the shared linking service and
its destination safety checks. A vanilla entity portal cooldown throttles both
successful and refused links. Current collision travel supports server players;
legacy arbitrary entity transport, destination-derived tint, label configuration,
and every classic portal modifier/event integration remain separate parity work.

`PortalSmoke.run` is compiled only into the runtime test harness. It exercises real
level blocks, source loops, persistence, frame invalidation and budget rollback;
it restores fixture blocks and empties its temporary receptacles before cleanup
so it does not create duplicate book drops.
