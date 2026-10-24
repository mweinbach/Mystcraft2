# ProjectE 1.19.2 port: notices

Each JAR carries this file as `META-INF/ProjectE-NOTICE.md`, [UPSTREAM.md](UPSTREAM.md) as
`META-INF/ProjectE-UPSTREAM.md`, upstream's [LICENSE](LICENSE) at its root and the files in [licenses/](licenses/)
under `META-INF/licenses/projecte/`.

## ProjectE

This is an unofficial port of [ProjectE](https://github.com/sinkillerj/ProjectE) 1.1.0 for Minecraft 1.19.2 (branch
`mc1.19.x`, tag `PE1.1.0`). It is not made, endorsed or supported by the ProjectE team; report problems with this
port to [mweinbach/Mystcraft2](https://github.com/mweinbach/Mystcraft2/issues), not upstream.

ProjectE's code, assets and data are used under the MIT License:

> Copyright (c) 2020 Sin Tachikawa
>
> Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
> documentation files (the "Software"), to deal in the Software without restriction, ... (full text in
> [LICENSE](LICENSE)).

ProjectE is a rewrite of Equivalent Exchange 2 by x3n0ph0b3. Upstream credits, kept here as upstream lists them:

- **Current team:** SinKillerJ (main project and community lead), pupnewfster (lead of new version ports),
  MaPePeR / Blubberbub (lead EMC mapper developer), Lilylicious (assistant developer).
- **Emeritus:** MozeIntel (original lead developer), williewillus (maintainer, 1.8.x to 1.13.x).
- **Former team members:** Magic Banana (texture artist), Kolatra (collaborator).
- **Equivalent Exchange 2:** x3n0ph0b3 (creator); MidnightLightning (EE2 GUI textures).

The mod id stays `projecte` and the item, block, tag, config and data names are upstream's, so pack configs, KubeJS
scripts, data packs and other mods' ProjectE integrations keep working.

## Port code

The Forge API mirror (`src/fabric/java/ports/projecte/lib`), the Fabric adapters, mixins, GameTests and build tooling
were written for this repository and are MIT licensed ([licenses/PORT-LICENSE.txt](licenses/PORT-LICENSE.txt)).
The Forge API mirror was adapted from the Mekanism 1.19.2 port in this repository (`mods/mekanism`, also MIT).
No code was taken from Minecraft Forge, Porting Lib, Fabricated Exchange or any other ProjectE remake.

## Third-party material

| Material | Where | Terms |
| --- | --- | --- |
| Apache Commons Math 3.6.1 (the EMC mapper's fractions) | Relocated into `moze_intel.projecte.shaded` in the Forge jar, as upstream ships it; nested unmodified in the Fabric jar | Apache License 2.0, [licenses/commons-math3-LICENSE.txt](licenses/commons-math3-LICENSE.txt) and [licenses/commons-math3-NOTICE.txt](licenses/commons-math3-NOTICE.txt) |
| Night Config 3.6.6, maven-artifact 3.8.5, fabric-permissions-api 0.3.1 | Nested unmodified in the Fabric jar (Forge ships the first two itself) | Their own licenses, inside each nested jar |
| Minecraft Forge's own `forge:` tag values | Merged into the Fabric jar's tag bridges (`src/fabric/resources/data/forge/tags`, from `scripts/forge-tags.json`) | LGPL 2.1, [licenses/Forge-LICENSE.txt](licenses/Forge-LICENSE.txt); Fabric jar only |
