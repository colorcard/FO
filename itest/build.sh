#!/usr/bin/env bash
# 构建 26.3 集成测试 mod（不随正式 FO jar 发布）。
#
# 前置：先构建正式 FO jar，使 fo/build/classes/java/main 存在：
#   cd fo && ./gradlew build
# 用法：
#   ./itest/build.sh
# 产物：
#   itest/build/libs/fo-it.jar
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FO="$ROOT/fo"
IT="$ROOT/itest"

if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/javac" ]]; then
    JAVAC="$JAVA_HOME/bin/javac"
elif [[ -x /home/colorcard/.local/jdks/jdk-25.0.5.0.3/bin/javac ]]; then
    JAVAC=/home/colorcard/.local/jdks/jdk-25.0.5.0.3/bin/javac
else
    JAVAC="$(command -v javac || true)"
fi
if [[ -z "${JAVAC:-}" || ! -x "$JAVAC" ]]; then
    echo "找不到 javac；请设置 JAVA_HOME 指向 JDK 25" >&2
    exit 1
fi

mkdir -p "$IT/build/classes" "$IT/build/libs"
cd "$FO"
./gradlew -q printCompileClasspath "$@" > "$IT/build/compile-classpath.txt"
CP="$(cat "$IT/build/compile-classpath.txt"):$FO/build/classes/java/main"

"$JAVAC" --release 25 -cp "$CP" -d "$IT/build/classes" \
    "$IT/src/fo/it/FoIntegrationTest.java"

cp "$IT/fabric.mod.json" "$IT/build/classes/"
rm -f "$IT/build/libs/fo-it.jar"
jar cf "$IT/build/libs/fo-it.jar" -C "$IT/build/classes" .

echo "built: $IT/build/libs/fo-it.jar"
