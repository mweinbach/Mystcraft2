# Claude Code on the web

The repository is set up so a Claude Code cloud session can build the ports,
run their GameTests on plain Minecraft 1.19.2, and boot them in the official
Tekxit 4 server pack.

## Cloud environment settings

Enter these once in the environment's settings at claude.ai/code.

**Network access**: **Custom**, with "Also include default list of common
package managers" ticked and these hosts. **Full** also works. The default
**Trusted** level cannot reach Adoptium, Mojang, Forge, Fabric, the mod mavens
or tekxit.lol.

```text
tekxit.lol
api.adoptium.net
github.com
objects.githubusercontent.com
archive.ubuntu.com
security.ubuntu.com
services.gradle.org
downloads.gradle.org
plugins.gradle.org
plugins-artifacts.gradle.org
repo.maven.apache.org
repo1.maven.org
piston-meta.mojang.com
piston-data.mojang.com
launchermeta.mojang.com
libraries.minecraft.net
resources.download.minecraft.net
maven.minecraftforge.net
files.minecraftforge.net
maven.fabricmc.net
maven.parchmentmc.org
api.modrinth.com
cdn.modrinth.com
cursemaven.com
edge.forgecdn.net
mediafilez.forgecdn.net
maven.blamejared.com
maven.terraformersmc.com
maven.shedaniel.me
maven.architectury.dev
maven.ladysnake.org
maven.theillusivec4.top
maven.squiddev.cc
squiddev.cc
modmaven.dev
dvs1.progwml6.com
oss.sonatype.org
pypi.org
files.pythonhosted.org
```

When a build fails to resolve a dependency, add the host from the error here
and in the environment.

**Environment variables**:

```text
MINECRAFT_EULA=true
```

Set this only if you accept the
[Minecraft EULA](https://aka.ms/MinecraftEULA). It is your acceptance, and it
is the only way the test servers start: `scripts/tekxit4.py` writes
`eula=true` only when it sees this value, and resets the `eula=true` that the
Tekxit archive ships with. Claude must never set it.

**Setup script**:

```bash
#!/bin/bash
repo="$(git rev-parse --show-toplevel 2>/dev/null)"
if [ ! -f "$repo/scripts/cloud-setup.sh" ]; then
  found="$(find / -xdev -maxdepth 6 -path /proc -prune -o -path '*/scripts/cloud-setup.sh' -print -quit 2>/dev/null)"
  repo="$(dirname "$(dirname "$found")")"
fi
bash "$repo/scripts/cloud-setup.sh" || true
exit 0
```

[`scripts/cloud-setup.sh`](../scripts/cloud-setup.sh) installs Ubuntu's
OpenJDK 17 with `ca-certificates-java`, then runs `scripts/tekxit4.py fetch`.
That downloads the official Tekxit 4 server 20.0.0 (650 MB), checks its
SHA-256 and unpacks it into `~/.cache/mystcraft2/tekxit4`, outside the
checkout. It also downloads the Fabric GameTest API jar that the pack lacks.
Both steps take a minute or two, so the environment snapshot keeps them, and
later sessions start with JDK 17 and the pack already there. The script always
exits 0. If a step fails, the session hook reports it and the step can be
rerun by hand.

## What runs in each session

`.claude/settings.json` registers `.claude/hooks/session-start.sh` as a
`SessionStart` hook. In cloud sessions (`CLAUDE_CODE_REMOTE=true`) it:

1. uses the system JDK 17 if one is installed, then runs `./scripts/setup.sh`.
   That script falls back to downloading Temurin 17 into `.local/jdk-17`. It
   also writes the proxy and CA trust settings into
   `.local/gradle/gradle.properties`, installs `tomli` for Python older than
   3.11, validates port metadata and downloads the Gradle 8.8 wrapper;
2. exports `JAVA_HOME`, `GRADLE_USER_HOME` and `PATH` through
   `CLAUDE_ENV_FILE`, so later shell commands see JDK 17;
3. tells Claude whether setup succeeded and whether the Tekxit server is
   cached. The full output is in `.local/claude-session-setup.log`.

Local sessions skip the hook. `CLAUDE.md` imports `AGENTS.md`, so Claude reads
the same workspace rules as other agents.

## Testing

**Plain Minecraft 1.19.2.** Each port's GameTests run on Forge 43.5.0 and on
Fabric through Gradle, as `AGENTS.md` lists. Gradle downloads Minecraft and the
loaders on first use.

**Tekxit 4.** The headless server route from
[tekxit4-testing.md](tekxit4-testing.md). The graphical client stages need a
display and a Minecraft account, so they are not run in the cloud.

```bash
python3 scripts/tekxit4.py build            # every Fabric port, ICBM's Tekxit tests, the probe
python3 scripts/tekxit4.py prepare          # fresh .local/tekxit4/server-ports with the jars
python3 scripts/tekxit4.py run              # boot, probe, GameTests, stop; exits 1 on failure
```

- `build` and `prepare` take `--ports mystcraft,mekanism` to limit the set.
  `prepare --no-item-alchemy` leaves Tekxit's optional Item Alchemy add-on out.
  By default the add-on is enabled, every port's EMC values are merged into its
  config with `scripts/emc.py merge-itemalchemy`, and they are copied to
  `tekxit-probe/emc/` for the probe to compare.
- `prepare` recreates the server directory each time and refuses to delete one
  it did not make. The server binds `127.0.0.1:25567` in offline mode with
  seed 12345 and view distance 3. `installed-jars.json` records the SHA-256 of
  every added jar.
- `run` starts the pack's own launcher with a 6 GB heap (`--heap`) and runs
  `TekxitProbeGameTests`, plus ICBM's `TekxitCompatibilityGameTests` when its
  test jar is installed (`--tests` overrides). It stops the server after the
  results. Distant Horizons can keep the JVM alive after every dimension has
  saved. `run` then waits 60 seconds and terminates it, and the exit status
  143 is not a failure. The verdict comes from the probe's
  `tekxit-probe/server-report-*.json` and `gametests.json`.
- The cloud VM has about 4 vCPUs and 16 GB of RAM. Background commands are
  stopped after about 30 minutes, which is enough for `run`. Builds of all
  ports and the Tekxit boot take several minutes each, so run them in the
  background.

## Notes

- The cloud image's own JDK is OpenJDK 21, which these ports cannot use.
- Traffic goes through the environment's HTTP(S) proxy.
  `scripts/java_network.py` points Gradle at that proxy and at
  `/etc/ssl/certs/java/cacerts`, which `ca-certificates-java` provides. If
  Gradle reports TLS or PKIX errors, check `env | grep -i proxy` and that the
  trust store exists.
- Gradle caches live in `.local/gradle` inside the checkout. Each new session
  downloads the build dependencies again.
- Set `TEKXIT4_CACHE` to keep the pack somewhere other than
  `~/.cache/mystcraft2/tekxit4`. The setup script cannot see environment
  variables, so it always uses the default.
