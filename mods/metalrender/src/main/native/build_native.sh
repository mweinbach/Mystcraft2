#!/usr/bin/env bash
# Builds the MetalRender native bridge and its tiered shader libraries.
#   shaders_metal3.metallib   -std=metal3.0  macOS 13+  (mesh shaders, ICB, MetalFX)
#   shaders_metal4.metallib   -std=metal4.0  macOS 26+  (Metal 4 command model)
#   shaders_metal4_1.metallib -std=metal4.1  macOS 27+  (Metal 4.1 shading language)
# The runtime picks the newest library the OS and GPU can load.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
MOD_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
REPO_ROOT="$(cd -- "$MOD_ROOT/../.." && pwd)"
SHADER_DIR="$SCRIPT_DIR/shaders"
OUT_DIR="$MOD_ROOT/src/main/resources/natives/macos"
SELFTEST_OUT="$MOD_ROOT/build/native/metalrender_selftest"

mkdir -p "$OUT_DIR" "$(dirname "$SELFTEST_OUT")"

has_jni() { [[ -n "${1:-}" && -f "$1/include/jni.h" ]]; }
if ! has_jni "${JAVA_HOME:-}"; then
    if has_jni "$REPO_ROOT/.local/jdk-17"; then
        JAVA_HOME="$(cd -- "$REPO_ROOT/.local/jdk-17" && pwd -P)"
    else
        JAVA_HOME="$(/usr/libexec/java_home -v 17)"
    fi
fi
has_jni "$JAVA_HOME" || { echo "jni.h not found under JAVA_HOME=$JAVA_HOME" >&2; exit 1; }

TMP_AIR="$(mktemp -d)"
trap 'rm -rf "$TMP_AIR"' EXIT

SHADERS=(terrain cull_icb mesh_terrain temporal)
rm -f "$OUT_DIR/shaders.metallib"

build_lib() {
    local tag="$1" std="$2" minos="$3"
    local airs=()
    for s in "${SHADERS[@]}"; do
        xcrun -sdk macosx metal -std="$std" -mmacosx-version-min="$minos" -Wall -Wextra -Werror \
            -c "$SHADER_DIR/$s.metal" -o "$TMP_AIR/$s.$tag.air"
        airs+=("$TMP_AIR/$s.$tag.air")
    done
    xcrun -sdk macosx metallib "${airs[@]}" -o "$OUT_DIR/shaders_$tag.metallib"
}

build_lib metal3   metal3.0 13.0
build_lib metal4   metal4.0 26.0
build_lib metal4_1 metal4.1 27.0

COMMON_FLAGS=(
    -O2 -std=c++17 -fobjc-arc
    -mmacosx-version-min=13.0
    -Wall -Wextra -Werror -Wunguarded-availability-new
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" -I"$SCRIPT_DIR"
    -framework Foundation -framework Metal -framework MetalFX
    -framework IOSurface -framework CoreVideo -framework OpenGL
)

clang++ "${COMMON_FLAGS[@]}" -dynamiclib -arch arm64 -arch x86_64 \
    "$SCRIPT_DIR/metal_bridge.mm" -o "$OUT_DIR/libmetalrender.dylib"

# Native GPU self-test (host arch only, never packaged).
clang++ "${COMMON_FLAGS[@]}" \
    "$SCRIPT_DIR/metal_bridge.mm" "$SCRIPT_DIR/selftest/metal_selftest.mm" -o "$SELFTEST_OUT"
