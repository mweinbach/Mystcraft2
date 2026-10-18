#!/usr/bin/env bash
# Source this file to use the workspace JDK in an interactive shell.
ports_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -z "${JAVA_HOME:-}" ]] || [[ ! -x "$JAVA_HOME/bin/javac" ]] ||
   ! "$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | grep -q '"17\.'; then
    if [[ -x "$ports_root/.local/jdk-17/bin/javac" ]]; then
        export JAVA_HOME="$ports_root/.local/jdk-17"
    else
        echo 'A Java 17 JDK is required. Run ./scripts/setup.sh or set JAVA_HOME to JDK 17.' >&2
        return 1
    fi
fi
export PATH="$JAVA_HOME/bin:$PATH"
# Keep caches and machine-specific proxy configuration outside tracked files.
export GRADLE_USER_HOME="$ports_root/.local/gradle"
python3 "$ports_root/scripts/java_network.py" || return 1
