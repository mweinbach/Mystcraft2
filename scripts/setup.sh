#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"
for required in python3 curl tar; do
    command -v "$required" >/dev/null || { echo "Missing prerequisite: $required" >&2; exit 1; }
done

if [[ -n "${JAVA_HOME:-}" ]] && [[ -x "$JAVA_HOME/bin/javac" ]] &&
   "$JAVA_HOME/bin/java" -version 2>&1 | head -n 1 | grep -q '"17\.'; then
    echo 'Using the supplied Java 17 JDK.'
elif [[ -x .local/jdk-17/bin/javac ]]; then
    echo 'Using the workspace Java 17 JDK.'
else
    [[ "$(uname -s)" == Linux ]] || { echo 'Install JDK 17 and set JAVA_HOME on this platform.' >&2; exit 1; }
    case "$(uname -m)" in
        x86_64) jdk_arch=x64 ;;
        aarch64|arm64) jdk_arch=aarch64 ;;
        *) echo 'Unsupported JDK architecture.' >&2; exit 1 ;;
    esac
    mkdir -p .local/downloads
    curl --fail --location --retry 3 --silent --show-error \
        "https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=$jdk_arch&image_type=jdk&os=linux&vendor=eclipse" \
        -o .local/downloads/jdk.json
    python3 - "$repo_root" <<'PY'
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1])
package = json.loads((root / '.local/downloads/jdk.json').read_text())[0]['binary']['package']
archive = root / '.local/downloads/jdk.tar.gz'
subprocess.run(['curl', '--fail', '--location', '--retry', '3', '--silent', '--show-error',
                package['link'], '-o', str(archive)], check=True)
digest = hashlib.sha256()
with archive.open('rb') as stream:
    for block in iter(lambda: stream.read(1024 * 1024), b''):
        digest.update(block)
if digest.hexdigest() != package['checksum']:
    archive.unlink()
    raise SystemExit('JDK SHA-256 verification failed.')
with tempfile.TemporaryDirectory(dir=root / '.local') as staging:
    subprocess.run(['tar', '-xzf', str(archive), '--strip-components=1', '-C', staging], check=True)
    Path(staging).rename(root / '.local/jdk-17')
print('Installed checksum-verified Eclipse Temurin 17.')
PY
fi

source "$repo_root/scripts/env.sh"
"$JAVA_HOME/bin/javac" -version
python3 scripts/ports.py check
./scripts/gradle.sh --version
