#!/usr/bin/env bash
# Build the macOS universal (x86_64 + arm64) cubiomes JNI bridge from xpple/cubiomes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/natives/macos"
WORK="${CUBIOMES_BUILD_DIR:-/tmp/opencode/cubiomes-macos-build}"
CUBIOMES_COMMIT="${CUBIOMES_COMMIT:-8c37ab26f2b47740839eb0045b34efc5141eb065}"
CUBIOMES_URL="https://codeload.github.com/xpple/cubiomes/tar.gz/${CUBIOMES_COMMIT}"

JAVA_HOME="${JAVA_HOME:-/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home}"
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

cmake -S "$WORK/cubiomes-src" -B "$WORK/cubiomes-src/build" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_OSX_ARCHITECTURES="x86_64;arm64"
cmake --build "$WORK/cubiomes-src/build" --target cubiomes_static -j "$(sysctl -n hw.ncpu)"

# Universal static library + universal JNI dylib; no @rpath dependency.
clang -dynamiclib -O2 -Wall -Wextra \
    -arch x86_64 -arch arm64 \
    -I"$WORK/cubiomes-src" \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
    "$ROOT/natives/src/cubiomes_jni.c" \
    "$WORK/cubiomes-src/build/libcubiomes_static.a" \
    -lm -o "$OUT/libCubiomesJNI.dylib"

echo "built:"
ls -lh "$OUT"
file "$OUT/libCubiomesJNI.dylib" || true
