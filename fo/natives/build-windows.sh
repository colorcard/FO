#!/usr/bin/env bash
# Build the Windows x86_64 cubiomes JNI bridge from xpple/cubiomes.
# Run inside MSYS2 MinGW64 (GitHub Actions: msys2/setup-msys2).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/src/main/resources/natives/windows"
WORK="${CUBIOMES_BUILD_DIR:-/tmp/opencode/cubiomes-windows-build}"
CUBIOMES_COMMIT="${CUBIOMES_COMMIT:-8c37ab26f2b47740839eb0045b34efc5141eb065}"
CUBIOMES_URL="https://codeload.github.com/xpple/cubiomes/tar.gz/${CUBIOMES_COMMIT}"
CC="${CC:-x86_64-w64-mingw32-gcc}"

# setup-java gives a Windows path (C:\...); gcc/cmake want a POSIX path.
JAVA_HOME_POSIX="$JAVA_HOME"
if command -v cygpath >/dev/null 2>&1; then
    JAVA_HOME_POSIX="$(cygpath -u "$JAVA_HOME")"
fi
if [[ ! -f "$JAVA_HOME_POSIX/include/jni.h" ]]; then
    echo "JAVA_HOME 必须指向 JDK 25（找不到 $JAVA_HOME_POSIX/include/jni.h）" >&2
    exit 1
fi

mkdir -p "$WORK" "$OUT"
if [[ ! -d "$WORK/cubiomes-src" ]]; then
    curl -L --fail --max-time 180 -o "$WORK/cubiomes.tar.gz" "$CUBIOMES_URL"
    mkdir -p "$WORK/cubiomes-src"
    tar xzf "$WORK/cubiomes.tar.gz" -C "$WORK/cubiomes-src" --strip-components=1
fi

cmake -G "MinGW Makefiles" -S "$WORK/cubiomes-src" -B "$WORK/cubiomes-src/build" \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_COMPILER="$CC"
cmake --build "$WORK/cubiomes-src/build" --target cubiomes_static -j 4

# Static-link cubiomes into the JNI DLL, so only one DLL is needed at runtime.
"$CC" -shared -O2 -Wall -Wextra \
    -I"$WORK/cubiomes-src" \
    -I"$JAVA_HOME_POSIX/include" -I"$JAVA_HOME_POSIX/include/win32" \
    "$ROOT/natives/src/cubiomes_jni.c" \
    "$WORK/cubiomes-src/build/libcubiomes_static.a" \
    -static-libgcc -lws2_32 -lm \
    -o "$OUT/CubiomesJNI.dll"

# New DLL is self-contained; remove the old dependency if present.
rm -f "$OUT/cubiomes.dll"

echo "built:"
ls -lh "$OUT"
file "$OUT/CubiomesJNI.dll" || true
