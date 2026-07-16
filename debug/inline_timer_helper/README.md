# Inline Timer helper dex

Optional Java helper for `inline_timer.plugin`.

The Python plugin works without this file. The helper moves fragile
`ChatMessageCell` / `BotButton` reflection into Java and caches reflected fields
and methods.

## Why `android.jar` is needed

`android.jar` is only a compile-time stub from Android SDK. It contains Android
API declarations such as `Canvas`, `Paint`, `Drawable`, and `ValueAnimator` so
`javac` can compile the helper. It is not uploaded to GitHub and is not loaded by
the plugin; Android already provides these classes at runtime.

## Build locally

```bash
cd inline_timer_helper

ANDROID_HOME=/home/alina/Android/Sdk
ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
D8="$ANDROID_HOME/build-tools/34.0.0/d8"

rm -rf build
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
sha256sum inline_timer_helper.dex
```

Publish `inline_timer_helper.dex` somewhere direct-downloadable, for example a
GitHub Release asset or a raw file, then put the URL into plugin setting
`Helper dex URL`. Put the printed hash into `Helper dex SHA-256`.

For local testing, push/copy `inline_timer_helper.dex` to app-accessible storage
and set `Helper dex path` instead. If loading fails, the plugin falls back to
Python reflection.
