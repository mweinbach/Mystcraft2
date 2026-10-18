# Contributing

Target Minecraft 1.19.2, Forge 43.5.0, and Java 17. Keep each port in
`mods/<mod-id>` and put its dependencies in that project's `build.gradle`.
Change `gradle/forge-mod.gradle` only for behavior shared by all ports.

Before opening a pull request:

```sh
python3 scripts/ports.py check
python3 -m unittest discover -s scripts/tests -v
./scripts/gradle.sh build
```

For gameplay changes, also run the affected mod's client and dedicated server.
Describe what you tried and any known failures. Include regression tests for
ported logic where they exercise behavior; use Forge GameTests when world state
matters. The starter contains no gameplay tests.

Use commit scopes such as `feat(mystcraft): ...` or `build(workspace): ...`.
Record the upstream URL and imported revision, preserve license notices, and
update the port README and `port_status` as implementation progresses.
Upstream projects may require their own contributor agreements for patches sent
back to them. The archived Mystcraft CLA automation does not run for this collection.
