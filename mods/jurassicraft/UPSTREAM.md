# Upstream attribution

Source: [JurassiCraftTeam/JurassiCraft](https://github.com/JurassiCraftTeam/JurassiCraft)

Imported baseline: `master`, revision `f7b194e6053b3d289a80f301924f9430c70366ac`,
targeting Minecraft 1.12.2 and Forge. The tracked upstream files are preserved
under [upstream/](upstream/), separate from the 1.19.2 port's source and
resources. The 1.19.2 builds do not compile or package this snapshot. Both the
Forge and Fabric JARs adapt upstream behavior and use selected textures,
models, sounds, and other resources from it; each JAR carries this file and the
historical license as `META-INF/JurassiCraft-UPSTREAM.md` and
`META-INF/UPSTREAM-LICENSE-HISTORICAL.md`.

## License provenance

The imported revision has no license file; its commit removed `LICENSE.md`.
JurassiCraft is developed and published openly by the JurassiCraft Team, and
this port proceeds on that open-source basis while crediting the team as the
original authors. The missing file is recorded here rather than resolved.

For historical reference, the LGPL 2.1 text from the immediately preceding
revision `ecc413cadb40afb50e3a725b965f7402780f03f7` is preserved as
[UPSTREAM-LICENSE-HISTORICAL.md](UPSTREAM-LICENSE-HISTORICAL.md). That file and
the workspace's LGPL-3.0-only license do not by themselves establish which
terms apply to the imported revision or its assets. Mod metadata therefore
points to this file instead of naming a license.

## Port history

The 1.19.2 work began as two separate ports, a Forge port in this directory and
a Fabric adaptation in `mods/jurassicraft_fabric`. They were merged into one
shared gameplay core with Forge and Fabric adapters; [PARITY.md](PARITY.md)
records how each difference was reconciled.
