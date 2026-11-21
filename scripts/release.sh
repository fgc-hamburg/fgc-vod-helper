#!/usr/bin/env bash
# Tags the current main as v<version> and pushes the tag.
# The Release workflow (.github/workflows/release.yml) then builds the
# installers and publishes the GitHub Release.
set -euo pipefail

v="${1:-}"
fail() { echo "release: $*" >&2; exit 1; }

[ -n "$v" ] || fail "usage: make release VERSION=1.2.3"
# Installer limits: macOS needs the first number >= 1; Windows needs x, y <= 255 and z <= 65535.
[[ "$v" =~ ^([0-9]{1,5})\.([0-9]{1,5})\.([0-9]{1,5})$ ]] || fail "version must look like 1.2.3"
(( 10#${BASH_REMATCH[1]} >= 1 && 10#${BASH_REMATCH[1]} <= 255 && 10#${BASH_REMATCH[2]} <= 255 && 10#${BASH_REMATCH[3]} <= 65535 )) \
  || fail "version $v is outside installer limits: first number 1-255, second 0-255, third 0-65535"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || fail "switch to main first"
[ -z "$(git status --porcelain --untracked-files=no)" ] || fail "commit or stash your changes first"

git fetch --quiet origin main --tags
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || fail "main is not the same as origin/main; pull first"
! git rev-parse -q --verify "refs/tags/v$v" >/dev/null || fail "tag v$v already exists"

git tag -a "v$v" -m "Release v$v"
git push origin "v$v"
echo "Pushed v$v. Follow the build: $(gh repo view --json url -q .url)/actions/workflows/release.yml"
