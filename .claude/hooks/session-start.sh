#!/usr/bin/env bash
# SessionStart hook for Claude Code on the web: installs the workspace JDK and
# Gradle proxy trust through scripts/setup.sh, then exports the toolchain so
# plain ./gradlew works too. Local sessions keep their own JAVA_HOME.
set -uo pipefail
[[ "${CLAUDE_CODE_REMOTE:-}" == true ]] || exit 0

repo_root="${CLAUDE_PROJECT_DIR:-$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)}"
cd "$repo_root"
mkdir -p .local
log=.local/claude-session-setup.log

if ! ./scripts/setup.sh >"$log" 2>&1; then
    echo "Workspace setup failed (scripts/setup.sh). Last lines of $log:"
    tail -n 25 "$log"
    echo 'Fix the cause, then rerun ./scripts/setup.sh before building.'
    exit 0
fi

# setup.sh ran env.sh in a subshell; source it here to learn the values to persist.
# shellcheck source=../../scripts/env.sh
source scripts/env.sh >>"$log" 2>&1
if [[ -n "${CLAUDE_ENV_FILE:-}" ]]; then
    {
        printf 'export JAVA_HOME=%q\n' "$JAVA_HOME"
        printf 'export GRADLE_USER_HOME=%q\n' "$GRADLE_USER_HOME"
        printf 'export PATH=%q:"$PATH"\n' "$JAVA_HOME/bin"
    } >>"$CLAUDE_ENV_FILE"
fi

java_version="$("$JAVA_HOME/bin/java" -version 2>&1 | head -n 1)"
echo "Workspace ready: $java_version at $JAVA_HOME; Gradle caches in $GRADLE_USER_HOME."
echo 'Build through ./scripts/gradle.sh as AGENTS.md describes; the first build of a port downloads Minecraft and loader dependencies.'
