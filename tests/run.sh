#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

# 两套离线测试：
#   tests/js  —— jsdom 跑 app 里真正的 res/raw/page.js（课表 / 成绩 / 登录判定）
#                和 res/raw/urp.js（考试表格 / 空闲教室 / 学籍+头像）
#   tests/jvm —— JDK 跑原生解析层（成绩列映射、拆行、课表聚合）
set -euo pipefail
cd "$(dirname "$0")"

echo "== JS =="
(cd js && { [ -d node_modules ] || npm i --no-audit --no-fund; } && node test.js && node urp-test.js)

echo
echo "== JVM =="

JAVA_HOME="${JAVA_HOME:-/c/Android/jdk17/jdk-17.0.21+9}"
JAVA_BIN="$JAVA_HOME/bin"
mkdir -p jvm/lib
if [ ! -f jvm/lib/json.jar ]; then
  curl -sL -o jvm/lib/json.jar \
    https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar
fi

# Windows 的 javac/java 只认 C:/... 形式
to_win() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi; }
CP="$(to_win "$PWD/jvm/lib/json.jar")"
OUT_W="$(to_win "$PWD/jvm/out")"

cd jvm
rm -rf out && mkdir -p out
"$JAVA_BIN/javac" -nowarn -encoding UTF-8 -d out -cp "$CP" \
  ../../app/src/main/java/com/hbu/zhangshanghbu/data/Table.java \
  ../../app/src/main/java/com/hbu/zhangshanghbu/data/Cal.java \
  ../../app/src/main/java/com/hbu/zhangshanghbu/data/Palette.java \
  ../../app/src/main/java/com/hbu/zhangshanghbu/campus/Parse.java \
  ParseTest.java

"$JAVA_BIN/java" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 \
  -cp "$OUT_W;$CP" ParseTest
