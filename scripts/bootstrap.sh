#!/usr/bin/env bash
#
# bootstrap.sh: prepare a fresh clone for development.
#
# Idempotent: safe to re-run at any time. It never overwrites an existing
# secrets file, so re-running cannot destroy your API key.
#
#   ./scripts/bootstrap.sh

set -euo pipefail

readonly REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if [[ -t 1 ]]; then
  readonly BOLD=$'\033[1m' GREEN=$'\033[0;32m' YELLOW=$'\033[0;33m' RESET=$'\033[0m'
else
  readonly BOLD='' GREEN='' YELLOW='' RESET=''
fi

step() { printf '\n%s▸ %s%s\n' "$BOLD" "$1" "$RESET"; }
note() { printf '  %s\n' "$1"; }
warn() { printf '  %s! %s%s\n' "$YELLOW" "$1" "$RESET"; }
done_() { printf '  %s✓ %s%s\n' "$GREEN" "$1" "$RESET"; }

# ── 1. Secrets templates ────────────────────────────────────────────────────
step "Configuration files"

if [[ -f android/local.properties ]]; then
  done_ "android/local.properties already exists, left untouched"
else
  android_sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
  {
    printf 'sdk.dir=%s\n' "$android_sdk"
    printf 'OPEN_WEATHER_API_KEY=\n'
  } > android/local.properties
  done_ "created android/local.properties (sdk.dir=$android_sdk)"
  warn "add your OpenWeather key to android/local.properties"
fi

if [[ -f ios/Config/Secrets.xcconfig ]]; then
  done_ "ios/Config/Secrets.xcconfig already exists, left untouched"
else
  cp ios/Config/Secrets.xcconfig.example ios/Config/Secrets.xcconfig
  done_ "created ios/Config/Secrets.xcconfig from the template"
  warn "add your OpenWeather key to ios/Config/Secrets.xcconfig"
fi

# ── 2. Xcode project ────────────────────────────────────────────────────────
step "Xcode project"

if command -v xcodegen >/dev/null 2>&1; then
  (cd ios && xcodegen generate >/dev/null)
  done_ "regenerated ios/SkyCast.xcodeproj from project.yml"
else
  warn "xcodegen not installed, skipping (brew install xcodegen)"
fi

# ── 3. Git hooks ────────────────────────────────────────────────────────────
step "Git hooks"

mkdir -p .git/hooks
cat > .git/hooks/pre-commit <<'HOOK'
#!/usr/bin/env bash
#
# Managed by scripts/bootstrap.sh: re-run it to restore this hook.
#
# Fast checks only. The full suite runs via scripts/test.sh; a slow hook just gets bypassed
# with --no-verify, which is worse than no hook at all.

set -uo pipefail

fail=0

staged() { git diff --cached --name-only --diff-filter=ACM; }

# 1. Never let a secrets file be committed. This is the one check worth being
#    absolutely certain about.
if staged | grep -qE '(^|/)(local\.properties|Secrets\.xcconfig|\.env)$'; then
  echo "pre-commit: refusing to commit a secrets file."
  echo "  Unstage it:  git restore --staged <file>"
  fail=1
fi

# 2. An OpenWeather key is 32 hex characters; nothing that shape belongs in source.
if staged | grep -qE '\.(kt|kts|swift)$'; then
  if staged | grep -E '\.(kt|kts|swift)$' | xargs -r grep -lE '"[0-9a-f]{32}"' 2>/dev/null; then
    echo "pre-commit: a hardcoded API key appears in the files above."
    fail=1
  fi
fi

# 3. Format Kotlin and Swift, if the tools are available.
if staged | grep -qE '\.(kt|kts)$' && command -v ktlint >/dev/null 2>&1; then
  ktlint --format --relative $(staged | grep -E '\.(kt|kts)$' | tr '\n' ' ') >/dev/null 2>&1 || true
  staged | grep -E '\.(kt|kts)$' | xargs -r git add
fi

if staged | grep -qE '\.swift$' && command -v swiftformat >/dev/null 2>&1; then
  swiftformat $(staged | grep -E '\.swift$' | tr '\n' ' ') >/dev/null 2>&1 || true
  staged | grep -E '\.swift$' | xargs -r git add
fi

exit "$fail"
HOOK
chmod +x .git/hooks/pre-commit
done_ "installed .git/hooks/pre-commit (secret scan + auto-format)"

# ── 4. Gradle wrapper sanity ────────────────────────────────────────────────
step "Gradle wrapper"

if [[ -x android/gradlew ]]; then
  done_ "android/gradlew is executable"
else
  chmod +x android/gradlew
  done_ "made android/gradlew executable"
fi

# ── 5. Report ───────────────────────────────────────────────────────────────
step "Environment"
"$REPO_ROOT/scripts/doctor.sh" || true

printf '\n%sNext steps%s\n' "$BOLD" "$RESET"
note "1. Add your OpenWeather key to both config files (see warnings above)."
note "2. Android:  cd android && ./gradlew assembleDebug"
note "3. iOS:      open ios/SkyCast.xcodeproj   (needs Xcode)"
note "4. Read README.md for the architecture and the run instructions."
