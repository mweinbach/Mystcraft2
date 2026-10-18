# Actual Tekxit integration test mod

Dedicated source set/module: `icbm_port_tekxittest`.
Entrypoint: `dev.icbm.port.tekxittest.TekxitCompatibilityGameTests`.
The root build supplies a separate remapped test jar; neither these classes nor
metadata belong in the production ICBM jar. There are no fake provider items,
fixture tags, resource reloads, explosions or placed test machines.

Six actual-pack GameTests cover:

1. Tech Reborn, Industrial Revolution and Ad Astra steel/plate inputs; Create
   iron sheets and Tech Reborn saltpeter in the port's private material unions.
2. Actual surviving pack recipes consuming ICBM steel/plates, including Almost
   Unified duplicate removal and the untagged Mythic Metals nugget exception;
   unique Industrial Revolution, Create and Ad Astra recipe patterns.
3. Real dust alternatives selecting gunpowder conditions, nuclear selection
   agreeing with actual loaded tags, and unchanged gas water-bucket remainders.
4. Actual Almost Unified recipe output preference: Tech Reborn steel/steel plate
   and Create iron sheet. The configured common-wire ingredient rewrite is checked
   as an expected pack policy, distinct from source-alone direct-item fidelity.
5. Actual Tech Reborn red-cell battery and Industrial Revolution battery charging,
   transaction rollback and conserved transfer into the port's energy buffer.
6. Full-pack recipe-manager matching for mixed cluster manifests and foreign-item
   cargo NBT, including exactly one empty carrier remainder.

The tests use registry IDs and the public Fabric/Reborn Energy APIs, with no
compile-time dependency on technology-mod classes. They require actual runtime
mod IDs `techreborn`, `indrev`, `create`, `ad_astra`, `almostunified`; each test
fails clearly if a required mod, item or recipe is absent. Output assertions target
the audited official Tekxit 4 client 20.0.4/server 20.0.0 priorities,
not arbitrary customized packs. Duplicate removal or changed pack recipes can
produce actionable failures rather than being silently skipped.

Build and run these checks with the commands in [TEKXIT.md](../../docs/TEKXIT.md).
Use the integrated-server route there for the official pack: BCLib and Distant
Horizons prevent its dedicated GameTest harness from starting. The ordinary client
world runs all official mods without those test-harness workarounds.
