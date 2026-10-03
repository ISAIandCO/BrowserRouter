#!/usr/bin/env bash
set -euo pipefail
collect_screenshots() {
  mkdir -p screenshots
  adb pull /sdcard/Pictures/BrowserRouterTests screenshots/ || true
}
trap collect_screenshots EXIT
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=phone
adb shell settings put system font_scale 1.6
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.browserrouter.UiSmokeTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=large-font --rerun-tasks
adb shell settings put system font_scale 1.0
adb shell wm size 1600x1200
adb shell wm density 160
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.browserrouter.UiSmokeTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=wide --rerun-tasks
