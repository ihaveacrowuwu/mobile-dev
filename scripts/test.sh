#!/usr/bin/env bash
#
# test.sh: every test suite, both platforms.
#
#   ./scripts/test.sh              unit tests only (no device needed)
#   ./scripts/test.sh --all        unit + UI tests (needs an emulator / simulator)
#
# Unit tests are the default because they are fast and need no device, which
# makes them the ones you actually run before every commit.

set -uo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

readonly SIMULATOR_NAME="${SIMULATOR_NAME:-iPhone 17}"

# Resolve Xcode without needing `sudo xcode-select`: see scripts/xcode-env.sh.
# shellcheck source=scripts/xcode-env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/xcode-env.sh"

RUN_UI=0
[[ "${1:-}" == "--all" ]] && RUN_UI=1

if [[ -t 1 ]]; then
  readonly BOLD=$'\033[1m' GREEN=$'\033[0;32m' RED=$'\033[0;31m' YELLOW=$'\033[0;33m' RESET=$'\033[0m'
else
  readonly BOLD='' GREEN='' RED='' YELLOW='' RESET=''
fi

failures=()
skipped=()

section() { printf '\n%s── %s ──%s\n' "$BOLD" "$1" "$RESET"; }
pass()    { printf '%s✓ %s%s\n' "$GREEN" "$1" "$RESET"; }
fail()    { printf '%s✗ %s%s\n' "$RED" "$1" "$RESET"; failures+=("$1"); }
skip()    { printf '%s%s (skipped: %s)%s\n' "$YELLOW" "$1" "$2" "$RESET"; skipped+=("$1"); }

# ── Android ─────────────────────────────────────────────────────────────────
section "Android"

if [[ -z "${JAVA_HOME:-}" && -d /opt/homebrew/opt/openjdk@21 ]]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@21
fi
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

if [[ ! -d "$ANDROID_HOME/platforms" ]]; then
  skip "Android tests" "SDK not installed"
else
  (cd android && ./gradlew testDebugUnitTest) \
    && pass "Android unit tests" || fail "Android unit tests"

  if (( RUN_UI )); then
    if "$ANDROID_HOME/platform-tools/adb" devices 2>/dev/null | grep -qE '\sdevice$'; then
      (cd android && ./gradlew connectedDebugAndroidTest) \
        && pass "Android instrumented tests" || fail "Android instrumented tests"
    else
      skip "Android instrumented tests" "no device or emulator connected"
      printf '    Start one:  %s/emulator/emulator -avd SkyCast_API36 &\n' "$ANDROID_HOME"
    fi
  fi
fi

# ── iOS ─────────────────────────────────────────────────────────────────────
section "iOS"

if [[ "${DEVELOPER_DIR:-$(xcode-select -p 2>/dev/null)}" != *"Xcode.app"* ]]; then
  skip "iOS tests" "Xcode not installed (only Command Line Tools)"
else
  # Regenerate first so a newly added Swift file is definitely in the target.
  command -v xcodegen >/dev/null 2>&1 && (cd ios && xcodegen generate >/dev/null)

  # xcbeautify makes xcodebuild output readable; fall back to `cat` without it.
  formatter="cat"
  command -v xcbeautify >/dev/null 2>&1 && formatter="xcbeautify"

  destination="platform=iOS Simulator,name=$SIMULATOR_NAME"

  # `-only-testing` restricts to unit tests so the default run needs no UI automation.
  args=(
    -project ios/SkyCast.xcodeproj
    -scheme SkyCast
    -destination "$destination"
    -quiet
    test
  )
  (( RUN_UI )) || args+=(-only-testing:SkyCastTests)
  # ScreenshotUITests writes PNGs into docs/screenshots/ and takes a couple of minutes. It is a
  # deliberate, separately-invoked utility (scripts/screenshots-ios.sh), never part of a test run.
  args+=(-skip-testing:SkyCastUITests/ScreenshotUITests)

  if set -o pipefail && xcodebuild "${args[@]}" 2>&1 | "$formatter"; then
    pass "iOS tests ($SIMULATOR_NAME)"
  else
    fail "iOS tests"
    printf '    Available simulators:  xcrun simctl list devices available\n'
    printf '    Override the device:   SIMULATOR_NAME="iPhone 16" ./scripts/test.sh\n'
  fi
fi

# ── Summary ─────────────────────────────────────────────────────────────────
printf '\n'
(( ${#skipped[@]} )) && printf '%sSkipped: %s%s\n' "$YELLOW" "${skipped[*]}" "$RESET"

if (( ${#failures[@]} == 0 )); then
  printf '%sAll tests passed.%s\n' "$GREEN" "$RESET"
  exit 0
fi

printf '%sFailed: %s%s\n' "$RED" "${failures[*]}" "$RESET"
exit 1
