# Mekanism loader parity and known gaps

Gameplay, registries, recipes, assets and data are shared in `src/main` and are upstream Mekanism 10.3.9. Forge
runs them on Forge itself. Fabric runs them on the port's Forge API mirror (`src/fabric/java/ports/mekanism/lib`),
injected interfaces on vanilla classes and the mixins in `ports.mekanism.fabric.mixin`. This file lists where the
Fabric variant does not yet match Forge and where the port differs from upstream.

The sections are files in [`parity/`](parity/), one section per file, read in filename order. A new entry is a new file in that folder.
