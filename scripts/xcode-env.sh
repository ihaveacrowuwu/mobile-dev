#!/usr/bin/env bash
#
# xcode-env.sh: make Xcode reachable without `sudo xcode-select`.
#
# Source this, do not execute it:
#     source "$(dirname "${BASH_SOURCE[0]}")/xcode-env.sh"
#
# `xcode-select -s` needs sudo and a TTY, which makes it awkward in scripts, CI and any
# non-interactive shell. `DEVELOPER_DIR` overrides it per-process with no privileges at
# all, and every Xcode tool (xcodebuild, xcrun, simctl, swiftlint) honours it.
#
# If DEVELOPER_DIR is already set, it is respected: so a machine that HAS run
# xcode-select, or a CI runner that sets it explicitly, is unaffected.

if [[ -z "${DEVELOPER_DIR:-}" ]]; then
  # Prefer whatever xcode-select already points at, if it is a real Xcode.
  _selected="$(xcode-select -p 2>/dev/null || true)"
  if [[ "$_selected" == *"Xcode.app"* ]]; then
    export DEVELOPER_DIR="$_selected"
  else
    # Otherwise take the first Xcode in /Applications. Sorted so Xcode.app wins over
    # versioned copies like Xcode_16.app.
    for _candidate in /Applications/Xcode.app /Applications/Xcode*.app; do
      if [[ -d "$_candidate/Contents/Developer" ]]; then
        export DEVELOPER_DIR="$_candidate/Contents/Developer"
        break
      fi
    done
  fi
  unset _selected _candidate
fi
