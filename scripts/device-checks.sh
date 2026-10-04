#!/usr/bin/env bash
set -euo pipefail
collect_screenshots() {
  adb logcat -d -s UiSmoke TestRunner || true
  mkdir -p screenshots
  adb pull /sdcard/Pictures/BrowserRouterTests screenshots/ || true
}
trap collect_screenshots EXIT
save_reports() {
  mkdir -p "device-reports/$1"
  cp -r app/build/reports/androidTests app/build/outputs/androidTest-results "device-reports/$1/"
}
./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=phone
save_reports phone
adb shell settings put system font_scale 1.6
./gradlew --no-daemon connectedDebugAndroidTest --rerun -Pandroid.testInstrumentationRunnerArguments.class=app.browserrouter.UiSmokeTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=large-font
save_reports large-font
adb shell settings put system font_scale 1.0
adb shell wm size 1600x1200
adb shell wm density 160
./gradlew --no-daemon connectedDebugAndroidTest --rerun -Pandroid.testInstrumentationRunnerArguments.class=app.browserrouter.UiSmokeTest -Pandroid.testInstrumentationRunnerArguments.visualVariant=wide
save_reports wide
