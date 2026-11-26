#!/usr/bin/env sh
#
# Builds the example plugin into hello-world.plugin.
#
# Requirements:
#   * JDK 17+ (javac)
#   * Android SDK with build-tools (d8) and at least one platform (android.jar)
#   * an assembled project, so the app classes can be used as a compile classpath:
#       ./gradlew :TMessagesProj:assembleDebug
#
# Usage:
#   export ANDROID_HOME=/path/to/android-sdk
#   ./build.sh
#
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../../.." && pwd)
OUT="$HERE/build"

if [ -z "${ANDROID_HOME:-}" ]; then
    echo "ANDROID_HOME is not set" >&2
    exit 1
fi

D8=$(find "$ANDROID_HOME/build-tools" -maxdepth 2 -name d8 -type f | sort -V | tail -1)
ANDROID_JAR=$(find "$ANDROID_HOME/platforms" -maxdepth 2 -name android.jar -type f | sort -V | tail -1)
if [ -z "$D8" ] || [ -z "$ANDROID_JAR" ]; then
    echo "d8 or android.jar not found under $ANDROID_HOME" >&2
    exit 1
fi

APP_CLASSES=$(find "$ROOT/TMessagesProj/build" -name classes.jar -type f 2>/dev/null | sort | tail -1)
if [ -z "$APP_CLASSES" ]; then
    echo "classes.jar of the app was not found, run ./gradlew :TMessagesProj:assembleDebug first" >&2
    exit 1
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "javac classpath: $ANDROID_JAR:$APP_CLASSES"
javac \
    -classpath "$ANDROID_JAR:$APP_CLASSES" \
    -d "$OUT/classes" \
    $(find "$HERE/src" -name '*.java')

# Dex the plugin classes together with the app classes jar? No: the app classes are provided by
# the parent classloader at runtime, so only the plugin's own classes are dexed.
"$D8" --lib "$ANDROID_JAR" --min-api 23 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

cp "$HERE/plugin.json" "$OUT/plugin.json"
cp "$OUT/dex/classes.dex" "$OUT/classes.dex"

(cd "$OUT" && zip -q hello-world.plugin plugin.json classes.dex)

echo "built: $OUT/hello-world.plugin"
echo "install it via Nekogram settings -> Plugins -> Install from file"
