# Mystcraft for 1.19.2

Status: **scaffold**. This project contains a Forge loader entry point. Books,
Ages, dimension generation, and linking are not implemented.

Mystcraft is XCompWiz's dimension-travel mod inspired by the Myst games.
This repository began with the documentation-only Mystcraft2 revision recorded
in `port.properties`. [UPSTREAM.md](UPSTREAM.md) preserves that README and its
architecture notes. The original [license](LICENSE) and
[CLA workflow](upstream/cla.yml) are retained for reference.

```sh
./scripts/gradle.sh :mystcraft:build
./scripts/gradle.sh :mystcraft:runClient
```

Run these commands from the repository root. New implementation should follow
the upstream `net.mystcraft.core`, `net.mystcraft.integration`, and
`net.mystcraft.minecraft` separation where useful.
