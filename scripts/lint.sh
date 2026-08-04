#!/usr/bin/env bash
#
# lint.sh: every linter, both platforms.
#
#   ./scripts/lint.sh          check only (what CI runs)
#   ./scripts/lint.sh --fix    autofix what can be autofixed, then check
#
# Runs every linter even if an earlier one fails, so one pass surfaces every
# problem instead of making you re-run after each fix.

set -uo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# Resolve Xcode without needing `sudo xcode-select`: see scripts/xcode-env.sh.
# shellcheck source=scripts/xcode-env.sh
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/xcode-env.sh"

FIX=0
[[ "${1:-}" == "--fix" ]] && FIX=1

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
  skip "ktlint / detekt / Android Lint" "Android SDK not installed"
else
  if (( FIX )); then
    (cd android && ./gradlew ktlintFormat --quiet) \
      && pass "ktlintFormat" || fail "ktlintFormat"
  fi

  (cd android && ./gradlew ktlintCheck --quiet) && pass "ktlintCheck" || fail "ktlintCheck"
  (cd android && ./gradlew detekt --quiet) && pass "detekt" || fail "detekt"
  # `lintDebug` without a project prefix runs it for every module, which is the point:
  # a library module can violate lint independently of :app.
  (cd android && ./gradlew lintDebug --quiet) && pass "Android Lint (all modules)" || fail "Android Lint"
fi

# ── iOS ─────────────────────────────────────────────────────────────────────
section "iOS"

if command -v swiftformat >/dev/null 2>&1; then
  if (( FIX )); then
    (cd ios && swiftformat . --quiet) && pass "swiftformat (applied)" || fail "swiftformat"
  else
    (cd ios && swiftformat --lint . --quiet) && pass "swiftformat --lint" || fail "swiftformat --lint"
  fi
else
  skip "swiftformat" "not installed"
fi

if ! command -v swiftlint >/dev/null 2>&1; then
  skip "swiftlint" "not installed"
elif [[ "${DEVELOPER_DIR:-$(xcode-select -p 2>/dev/null)}" != *"Xcode.app"* ]]; then
  # SwiftLint links against Xcode's SourceKit; Command Line Tools alone is not enough.
  skip "swiftlint" "requires full Xcode, not just Command Line Tools"
else
  if (( FIX )); then
    (cd ios && swiftlint --fix --quiet) || true
  fi
  (cd ios && swiftlint --strict --quiet) && pass "swiftlint --strict" || fail "swiftlint"
fi

# ── Shell scripts ───────────────────────────────────────────────────────────
section "Shell"

if command -v shellcheck >/dev/null 2>&1; then
  shellcheck scripts/*.sh && pass "shellcheck" || fail "shellcheck"
else
  skip "shellcheck" "not installed (brew install shellcheck)"
fi

# ── Summary ─────────────────────────────────────────────────────────────────
printf '\n'
(( ${#skipped[@]} )) && printf '%sSkipped: %s%s\n' "$YELLOW" "${skipped[*]}" "$RESET"

if (( ${#failures[@]} == 0 )); then
  printf '%sAll linters passed.%s\n' "$GREEN" "$RESET"
  exit 0
fi

printf '%sFailed: %s%s\n' "$RED" "${failures[*]}" "$RESET"
printf 'Try %s./scripts/lint.sh --fix%s for the autofixable ones.\n' "$BOLD" "$RESET"
exit 1
