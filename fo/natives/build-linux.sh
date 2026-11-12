#!/usr/bin/env bash
# Build the Linux x86_64 cubiomes JNI bridge.
#
# Requires: gcc, cmake, make, curl, JDK 25 (for jni.h).
# Pinned cubiomes fork: xpple/cubiomes (supports MC 26.3), commit below.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/natives/linux-x86_64"
WORK="${CUBIOMES_BUILD_DIR:-/tmp/opencode/cubiomes-linux-build}"
CUBIOMES_COMMIT="${CUBIOMES_COMMIT:-8c37ab26f2b47740839eb0045b34efc5141eb065}"
CUBIOMES_URL="https://codeload.github.com/xpple/cubiomes/tar.gz/${CUBIOMES_COMMIT}"

JAVA_HOME="${JAVA_HOME:-/home/colorcard/.local/jdks/jdk-25.0.5.0.3}"
if [[ ! -f "$JAVA_HOME/include/jni.h" ]]; then
    echo "JAVA_HOME 必须指向 JDK 25（找不到 $JAVA_HOME/include/jni.h）" >&2
    exit 1
fi

mkdir -p "$WORK" "$OUT"
if [[ ! -d "$WORK/cubiomes-src" ]]; then
    curl -L --fail --max-time 180 -o "$WORK/cubiomes.tar.gz" "$CUBIOMES_URL"
    mkdir -p "$WORK/cubiomes-src"
    tar xzf "$WORK/cubiomes.tar.gz" -C "$WORK/cubiomes-src" --strip-components=1
fi

cmake -S "$WORK/cubiomes-src" -B "$WORK/cubiomes-src/build" -DCMAKE_BUILD_TYPE=Release
cmake --build "$WORK/cubiomes-src/build" -j"$(nproc)"

gcc -shared -fPIC -O2 -Wall -Wextra \
    -I"$WORK/cubiomes-src" \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    "$ROOT/natives/src/cubiomes_jni.c" \
    -L"$WORK/cubiomes-src/build" -lcubiomes \
    -Wl,-rpath,'$ORIGIN' -Wl,-soname,libCubiomesJNI.so \
    -o "$OUT/libCubiomesJNI.so"

cp "$WORK/cubiomes-src/build/libcubiomes.so" "$OUT/libcubiomes.so"
chmod 755 "$OUT/libCubiomesJNI.so" "$OUT/libcubiomes.so"

echo "built:"
ls -lh "$OUT"
readelf -d "$OUT/libCubiomesJNI.so" | grep -E 'NEEDED|RUNPATH' || true
