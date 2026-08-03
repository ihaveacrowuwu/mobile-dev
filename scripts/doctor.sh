#!/usr/bin/env bash
#
# doctor.sh: report what is installed and what is missing.
#
# Run this first when anything behaves oddly. It prints versions rather than
# guessing, so a "works on my machine" difference shows up immediately.
#
#   ./scripts/doctor.sh

set -uo pipefail

# NOT `set -e`: this keeps going and reports every
# problem in one pass rather than stopping at the first missing tool.

readonly REQUIRED_JDK_MAJOR=21
readonly ANDROID_SDK_DEFAULT="$HOME/Library/Android/sdk"

if [[ -t 1 ]]; then
  readonly GREEN=$'\033[0;32m' RED=$'\033[0;31m' YELLOW=$'\033[0;33m'
  readonly BOLD=$'\033[1m' RESET=$'\033[0m'
else
  readonly GREEN='' RED='' YELLOW='' BOLD='' RESET=''
fi

problems=0
warnings=0

section() { printf '\n%s── %s ──%s\n' "$BOLD" "$1" "$RESET"; }
ok()      { printf '  %s✓%s %-22s %s\n' "$GREEN" "$RESET" "$1" "${2:-}"; }
bad()     { printf '  %s✗%s %-22s %s\n' "$RED" "$RESET" "$1" "${2:-}"; problems=$((problems + 1)); }
warn()    { printf '  %s!%s %-22s %s\n' "$YELLOW" "$RESET" "$1" "${2:-}"; warnings=$((warnings + 1)); }

# Reports a tool as present (with its version) or missing.
check_tool() {
  local name="$1" version_cmd="$2" required="${3:-required}"
  if command -v "$name" >/dev/null 2>&1; then
    ok "$name" "$(eval "$version_cmd" 2>&1 | head -1)"
  elif [[ "$required" == "required" ]]; then
    bad "$name" "MISSING"
  else
    warn "$name" "missing (optional)"
  fi
}

printf '%sSkyCast environment check%s\n' "$BOLD" "$RESET"

# ── Shared ──────────────────────────────────────────────────────────────────
section "Shared"
check_tool git "git --version"
check_tool gh "gh --version" optional

# ── iOS ─────────────────────────────────────────────────────────────────────
section "iOS"

developer_dir="$(xcode-select -p 2>/dev/null || true)"
if [[ "$developer_dir" == *"Xcode.app"* ]]; then
  ok "Xcode" "$(xcodebuild -version 2>/dev/null | head -1)"

  if xcrun simctl list runtimes 2>/dev/null | grep -q "iOS"; then
    ok "iOS runtime" "$(xcrun simctl list runtimes 2>/dev/null | grep 'iOS' | tail -1 | sed 's/ - .*//')"
  else
    bad "iOS runtime" "no simulator runtime installed (Xcode ▸ Settings ▸ Components)"
  fi
elif [[ -n "$developer_dir" ]]; then
  bad "Xcode" "only Command Line Tools at $developer_dir"
  printf '      Install Xcode from the App Store, then run:\n'
  printf '        sudo xcode-select -s /Applications/Xcode.app/Contents/Developer\n'
else
  bad "Xcode" "not installed"
fi

check_tool xcodegen "xcodegen --version"
# SwiftLint links against Xcode's SourceKit, so it cannot run on Command Line
# Tools alone even though the binary is present.
check_tool swiftlint "swiftlint --version"
check_tool swiftformat "swiftformat --version"
check_tool xcbeautify "xcbeautify --version" optional

if [[ -f ios/Config/Secrets.xcconfig ]]; then
  ok "Secrets.xcconfig" "present"
else
  warn "Secrets.xcconfig" "missing, app will show 'API key not configured'"
  printf '      cp ios/Config/Secrets.xcconfig.example ios/Config/Secrets.xcconfig\n'
fi

# ── Android ─────────────────────────────────────────────────────────────────
section "Android"

java_bin=""
if [[ -x /opt/homebrew/opt/openjdk@${REQUIRED_JDK_MAJOR}/bin/java ]]; then
  java_bin="/opt/homebrew/opt/openjdk@${REQUIRED_JDK_MAJOR}/bin/java"
elif command -v java >/dev/null 2>&1; then
  java_bin="$(command -v java)"
fi

if [[ -n "$java_bin" ]]; then
  java_version="$("$java_bin" -version 2>&1 | head -1)"
  if [[ "$java_version" == *"\"${REQUIRED_JDK_MAJOR}."* ]]; then
    ok "JDK ${REQUIRED_JDK_MAJOR}" "$java_version"
  else
    warn "JDK" "$java_version (AGP expects ${REQUIRED_JDK_MAJOR})"
    printf '      export JAVA_HOME=/opt/homebrew/opt/openjdk@%s\n' "$REQUIRED_JDK_MAJOR"
  fi
else
  bad "JDK" "no Java runtime found"
  printf '      brew install openjdk@%s\n' "$REQUIRED_JDK_MAJOR"
fi

android_home="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$ANDROID_SDK_DEFAULT}}"
if [[ -d "$android_home/platforms" ]]; then
  ok "Android SDK" "$android_home"
  platforms="$(ls "$android_home/platforms" 2>/dev/null | tr '\n' ' ')"
  ok "  platforms" "${platforms:-none}"
  if [[ -z "${ANDROID_HOME:-}" ]]; then
    warn "ANDROID_HOME" "not exported (Gradle falls back to local.properties)"
  fi
else
  bad "Android SDK" "not found at $android_home"
fi

if [[ -x "$android_home/platform-tools/adb" ]]; then
  ok "adb" "$("$android_home/platform-tools/adb" version 2>/dev/null | head -1)"
else
  bad "adb" "MISSING (sdkmanager --install platform-tools)"
fi

if [[ -x "$android_home/emulator/emulator" ]]; then
  avds="$("$android_home/emulator/emulator" -list-avds 2>/dev/null | tr '\n' ' ')"
  if [[ -n "$avds" ]]; then
    ok "AVDs" "$avds"
  else
    warn "AVDs" "none created, connectedAndroidTest will fail"
  fi
else
  warn "emulator" "not installed (a physical device works too)"
fi

check_tool ktlint "ktlint --version" optional

if [[ -f android/local.properties ]]; then
  if grep -q '^OPEN_WEATHER_API_KEY=.\+' android/local.properties 2>/dev/null; then
    ok "local.properties" "present, API key set"
  else
    warn "local.properties" "present, API key blank"
  fi
else
  bad "local.properties" "missing"
  printf '      cp android/local.properties.example android/local.properties\n'
fi

# ── Disk ────────────────────────────────────────────────────────────────────
section "Disk"
available_gb="$(df -g / 2>/dev/null | awk 'NR==2 {print $4}')"
if [[ -n "$available_gb" ]]; then
  if (( available_gb < 25 )); then
    bad "free space" "${available_gb} GB, Xcode plus simulators needs roughly 40 GB"
  elif (( available_gb < 60 )); then
    warn "free space" "${available_gb} GB, tight if Xcode is not yet installed"
  else
    ok "free space" "${available_gb} GB"
  fi
fi

# ── Summary ─────────────────────────────────────────────────────────────────
printf '\n'
if (( problems == 0 && warnings == 0 )); then
  printf '%sEverything is ready.%s\n' "$GREEN" "$RESET"
elif (( problems == 0 )); then
  printf '%s%d warning(s), nothing blocking.%s\n' "$YELLOW" "$warnings" "$RESET"
else
  printf '%s%d problem(s) and %d warning(s). See README.md.%s\n' \
    "$RED" "$problems" "$warnings" "$RESET"
fi

# Exit non-zero only for real problems, so CI can gate on this.
(( problems == 0 ))
