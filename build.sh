#!/usr/bin/env bash
# SPDX-License-Identifier: AGPL-3.0-only
# Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

# 掌上hbu · 手动编译脚本
#
# 路线与掌上唐师一致：aapt2 + javac + d8 + apksigner。
# 工程**零第三方依赖**（不含 androidx / compose），只用 Android framework API，
# 因此不需要 Gradle、不需要联网拉 maven —— 一台装了 JDK 17 和 Android SDK
# （platform 34 + build-tools 34.0.0）的机器就能出 APK。
#
# 用法：
#   bash build.sh              # 用默认路径
#   JDK_HOME=/path/to/jdk ANDROID_SDK_ROOT=/path/to/sdk bash build.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
APP="$ROOT/app/src/main"
OUT="$ROOT/build"

if [ -z "${JDK_HOME:-}" ]; then
  for c in "C:/Android/jdk17" "$HOME/jdk17" "/c/Android/jdk17"; do
    if [ -d "$c" ]; then JDK_HOME="$(ls -d "$c"/* 2>/dev/null | head -1)"; [ -z "$JDK_HOME" ] && JDK_HOME="$c"; break; fi
  done
fi
: "${JDK_HOME:?未找到 JDK 17，请设置 JDK_HOME}"

if [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  for c in "C:/Android/sdk" "/c/Android/sdk" "$HOME/Android/Sdk"; do
    [ -d "$c" ] && ANDROID_SDK_ROOT="$c" && break
  done
fi
: "${ANDROID_SDK_ROOT:?未找到 Android SDK，请设置 ANDROID_SDK_ROOT}"

# Windows 风格路径（C:/...）转成 bash 能用的 /c/...，否则 PATH 里找不到 javac
if command -v cygpath >/dev/null 2>&1; then
  JDK_HOME="$(cygpath -u "$JDK_HOME" 2>/dev/null || echo "$JDK_HOME")"
  ANDROID_SDK_ROOT="$(cygpath -u "$ANDROID_SDK_ROOT" 2>/dev/null || echo "$ANDROID_SDK_ROOT")"
fi
export JAVA_HOME="$JDK_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

BT="$ANDROID_SDK_ROOT/build-tools/34.0.0"
PLATFORM="$ANDROID_SDK_ROOT/platforms/android-34/android.jar"
AAPT2="$BT/aapt2.exe"; [ -x "$AAPT2" ] || AAPT2="$BT/aapt2"
ZIPALIGN="$BT/zipalign.exe"; [ -x "$ZIPALIGN" ] || ZIPALIGN="$BT/zipalign"
# Windows 的 build-tools 只有 d8.bat / apksigner.bat，没有 *nix 脚本：
# 直接调 jar 更稳（不依赖 cmd 的路径转换）
D8_JAR="$BT/lib/d8.jar"
APKSIGNER_JAR="$BT/lib/apksigner.jar"

VP="$ROOT/version.properties"
VERSION_CODE="$(sed -nE 's/^versionCode=([0-9]+).*/\1/p' "$VP" | head -1)"
VERSION_NAME="$(sed -nE 's/^versionName=(.+)$/\1/p' "$VP" | head -1)"
: "${VERSION_CODE:?version.properties 缺 versionCode}"
: "${VERSION_NAME:?version.properties 缺 versionName}"

echo "==> JDK: $JAVA_HOME"
echo "==> SDK: $ANDROID_SDK_ROOT"
echo "==> 版本 $VERSION_NAME ($VERSION_CODE)"

mkdir -p "$OUT" "$OUT/gen" "$OUT/classes" "$OUT/dex"

MANIFEST_TMP="$OUT/AndroidManifest.xml"
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.hbu.zhangshanghbu">#' \
  "$APP/AndroidManifest.xml" > "$MANIFEST_TMP"

echo "==> [1/5] aapt2 compile"
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.flata"

echo "==> [2/5] aapt2 link"
"$AAPT2" link -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$MANIFEST_TMP" \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  "$OUT/res.flata"

echo "==> [3/5] javac"
rm -rf "$OUT/classes"; mkdir -p "$OUT/classes"
javac -nowarn -J-Duser.language=en -J-Duser.country=US -d "$OUT/classes" \
  -cp "$PLATFORM" --release 17 -encoding UTF-8 \
  $(find "$OUT/gen" -name '*.java') \
  $(find "$APP/java" -name '*.java')

echo "==> [4/5] d8"
rm -rf "$OUT/dex"; mkdir -p "$OUT/dex"
rm -f "$OUT/classes.jar"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
"$JAVA_HOME/bin/java" -cp "$D8_JAR" com.android.tools.r8.D8 --lib "$PLATFORM" --output "$OUT/dex" "$OUT/classes.jar"

echo "==> [5/5] 组装 / 对齐 / 签名"
rm -f "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk" "$OUT/zhangshanghbu-debug.apk"
cp "$OUT/base.apk" "$OUT/app-unsigned.apk"
python - "$OUT/app-unsigned.apk" "$OUT/dex" <<'PY'
import sys, os, zipfile
apk, dexdir = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', compression=zipfile.ZIP_STORED) as z:
    for f in sorted(os.listdir(dexdir)):
        if f.endswith('.dex'):
            z.write(os.path.join(dexdir, f), f)
PY
"$ZIPALIGN" -f -p 4 "$OUT/app-unsigned.apk" "$OUT/app-aligned.apk"

KS="$ROOT/debug.keystore"
if [ ! -f "$KS" ]; then
  echo "==> 生成调试签名"
  "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$KS" -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=ZSHED, OU=HBU, O=ZSHED, L=Baoding, S=Hebei, C=CN" >/dev/null 2>&1
fi

"$JAVA_HOME/bin/java" -jar "$APKSIGNER_JAR" sign --ks "$KS" --ks-pass pass:android \
  --key-pass pass:android --ks-key-alias androiddebugkey \
  --out "$OUT/zhangshanghbu-debug.apk" "$OUT/app-aligned.apk"
"$JAVA_HOME/bin/java" -jar "$APKSIGNER_JAR" verify --print-certs "$OUT/zhangshanghbu-debug.apk" | head -4

ls -lh "$OUT/zhangshanghbu-debug.apk"
echo "==> 完成：$OUT/zhangshanghbu-debug.apk"
