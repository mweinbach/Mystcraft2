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

# Prefer a system JDK 17 (the environment setup script installs one with apt) over downloading one.
if ! "${JAVA_HOME:-/nonexistent}/bin/java" -version 2>&1 | head -n 1 | grep -q '"17\.'; then
    for jdk in /usr/lib/jvm/java-17-openjdk-*; do
        if [[ -x "$jdk/bin/javac" ]]; then
            export JAVA_HOME="$jdk"
            break
        fi
    done
fi

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
tekxit_cache="${TEKXIT4_CACHE:-$HOME/.cache/mystcraft2/tekxit4}"
if [[ -f "$tekxit_cache/server-20.0.0/.source-sha256" ]]; then
    echo "Tekxit 4 server 20.0.0 is cached in $tekxit_cache; test the ports in it with scripts/tekxit4.py (docs/claude-cloud.md)."
else
    echo 'Tekxit 4 server is not cached; python3 scripts/tekxit4.py fetch downloads it (650 MB).'
fi
echo "Test servers start only when the user's environment sets MINECRAFT_EULA=true. Only the user may accept Minecraft's EULA; never set it yourself."
