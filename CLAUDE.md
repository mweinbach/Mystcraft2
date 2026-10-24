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
- The cloud environment needs network access to the hosts listed in
  `docs/claude-cloud.md`.
