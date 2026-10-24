@AGENTS.md

## Claude Code

- In Claude Code on the web, `.claude/hooks/session-start.sh` runs
  `./scripts/setup.sh` when a session starts, then exports `JAVA_HOME`,
  `GRADLE_USER_HOME` and `PATH` for later commands. If its message says setup
  failed, read `.local/claude-session-setup.log` and rerun `./scripts/setup.sh`.
  Local sessions skip the hook and use the machine's own JDK 17.
- Run Gradle through `./scripts/gradle.sh` (or `./scripts/gradle.sh -p <dir>`
  for a Fabric root), never a system `gradle`. Run long builds and GameTest
  servers in the background.
- Boot the Fabric ports in the official Tekxit 4 server with
  `python3 scripts/tekxit4.py build|prepare|run` (see `docs/claude-cloud.md`).
  Its servers start only when the user has set `MINECRAFT_EULA=true` in the
  environment. Never set it, write `eula=true` or otherwise accept the EULA
  yourself; report that it is missing instead.
- The cloud environment needs network access to the hosts listed in
  `docs/claude-cloud.md`.
