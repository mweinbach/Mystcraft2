#!/usr/bin/env bash
# Setup script for the Claude Code cloud environment (paste the snippet from docs/claude-cloud.md).
# It runs as root before the session starts, and its result is snapshotted when it finishes in
# about five minutes. Everything that needs to survive a fresh clone goes outside the checkout.
# It always exits 0: a failed step leaves the SessionStart hook to report and retry.
set -uo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

# JDK 17 from Ubuntu also brings ca-certificates-java, whose /etc/ssl/certs/java/cacerts trusts the
# environment's proxy CA (scripts/java_network.py points Gradle at it).
if ! ls /usr/lib/jvm/java-17-openjdk-*/bin/javac >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq && apt-get install -y -qq openjdk-17-jdk-headless ca-certificates-java ||
        echo 'Could not install openjdk-17-jdk-headless; scripts/setup.sh will download Temurin 17.' >&2
fi

# The official Tekxit 4 server (650 MB) and the Fabric GameTest API, unpacked into the cache.
python3 "$repo_root/scripts/tekxit4.py" fetch || echo 'Tekxit 4 fetch failed; rerun scripts/tekxit4.py fetch.' >&2
exit 0
