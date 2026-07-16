#!/usr/bin/env bash
set -euo pipefail

ANDROID_HOME="${ANDROID_HOME:-/home/alina/Android/Sdk}"
ANDROID_PLATFORM="${ANDROID_PLATFORM:-android-35}"
BUILD_TOOLS="${BUILD_TOOLS:-34.0.0}"

ANDROID_JAR="$ANDROID_HOME/platforms/$ANDROID_PLATFORM/android.jar"
D8="$ANDROID_HOME/build-tools/$BUILD_TOOLS/d8"

rm -rf build inline_timer_helper.dex inline_timer_helper.dex.sha256
mkdir -p build/classes build/dex

javac \
  -source 8 \
  -target 8 \
  -bootclasspath "$ANDROID_JAR" \
  -d build/classes \
  src/org/exteragram/plugins/inlinetimer/InlineTimerHelper.java

"$D8" \
  --min-api 23 \
  --output build/dex \
  build/classes/org/exteragram/plugins/inlinetimer/InlineTimerHelper.class

cp build/dex/classes.dex inline_timer_helper.dex
sha256sum inline_timer_helper.dex | tee inline_timer_helper.dex.sha256
