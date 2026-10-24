# Claude Code on the web

The repository is set up so a Claude Code cloud session can build and test the
ports without manual steps.

## What runs automatically

`.claude/settings.json` registers `.claude/hooks/session-start.sh` as a
`SessionStart` hook. In cloud sessions (`CLAUDE_CODE_REMOTE=true`) it:

1. runs `./scripts/setup.sh`, which installs a checksum-verified Temurin 17 JDK
   into `.local/jdk-17` unless a JDK 17 is already on `JAVA_HOME`, writes the
   proxy and CA trust settings into `.local/gradle/gradle.properties`, installs
   `tomli` for Python older than 3.11, validates port metadata and downloads the
   Gradle 8.8 wrapper distribution;
2. exports `JAVA_HOME`, `GRADLE_USER_HOME` and `PATH` through
   `CLAUDE_ENV_FILE`, so later shell commands see the workspace JDK;
3. tells Claude whether setup succeeded. The full output is in
   `.local/claude-session-setup.log`.

Local sessions skip the hook. `CLAUDE.md` imports `AGENTS.md`, so Claude reads
the same workspace rules as other agents.

## Environment settings

Configure these once in the cloud environment at claude.ai/code:

- **Setup script**: optional. To cache the JDK and Gradle distribution in the
  environment snapshot instead of downloading them each session, set it to
  `./scripts/setup.sh || true`. It runs as root before the environment's
  variables are set, a non-zero exit blocks the session, and only scripts that
  finish in about five minutes are snapshotted, so leave dependency downloads
  to the builds. The hook still runs and finds the JDK already installed.
- **Network access**: the default **Trusted** level reaches GitHub, Gradle,
  Maven Central and PyPI, but not Adoptium, Mojang, Forge, Fabric or the mod
  mavens, so builds fail there. Choose **Full**, or **Custom** with "Also
  include default list of common package managers" ticked and these hosts:

  | Purpose | Hosts |
  | --- | --- |
  | JDK | `api.adoptium.net`, `github.com`, `objects.githubusercontent.com` |
  | Gradle and Maven Central | `services.gradle.org`, `downloads.gradle.org`, `plugins.gradle.org`, `plugins-artifacts.gradle.org`, `repo.maven.apache.org`, `repo1.maven.org` |
  | Minecraft | `piston-meta.mojang.com`, `piston-data.mojang.com`, `launchermeta.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net` |
  | Loaders | `maven.minecraftforge.net`, `files.minecraftforge.net`, `maven.fabricmc.net`, `maven.parchmentmc.org` |
  | Mod dependencies | `api.modrinth.com`, `cursemaven.com`, `edge.forgecdn.net`, `mediafilez.forgecdn.net`, `maven.blamejared.com`, `maven.terraformersmc.com`, `maven.shedaniel.me`, `maven.architectury.dev`, `maven.ladysnake.org`, `maven.theillusivec4.top`, `maven.squiddev.cc`, `squiddev.cc`, `modmaven.dev`, `dvs1.progwml6.com`, `oss.sonatype.org` |
  | Python | `pypi.org`, `files.pythonhosted.org` |

  When a build fails to resolve a dependency, add the host from the error to
  this list and to the table.

## Notes

- The cloud image ships OpenJDK 21, which these ports cannot use, so setup
  installs JDK 17 the first time.
- Traffic goes through the environment's HTTP(S) proxy. `scripts/java_network.py`
  points Gradle at that proxy and at `/etc/ssl/certs/java/cacerts`. If Gradle
  reports TLS or PKIX errors, check `env | grep -i proxy` and that the trust
  store exists.

- The first build of a port downloads Minecraft, the loader toolchain and mod
  dependencies into `.local/gradle`; later builds in the same session reuse
  them.
- GameTest servers and full builds can take several minutes. Run them in the
  background.
- Do not accept the Minecraft EULA on the user's behalf.
