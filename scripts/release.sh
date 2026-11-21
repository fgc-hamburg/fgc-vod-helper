#!/usr/bin/env bash
# Tags the current main as v<version> and pushes the tag.
# The Release workflow (.github/workflows/release.yml) then builds the
# installers and publishes the GitHub Release.
set -euo pipefail

v="${1:-}"
fail() { echo "release: $*" >&2; exit 1; }

[ -n "$v" ] || fail "usage: make release VERSION=1.2.3"
# macOS installers require the first number to be 1 or higher.
[[ "$v" =~ ^[1-9][0-9]*\.[0-9]+\.[0-9]+$ ]] || fail "version must look like 1.2.3 (first number 1 or higher)"
[ "$(git rev-parse --abbrev-ref HEAD)" = "main" ] || fail "switch to main first"
[ -z "$(git status --porcelain --untracked-files=no)" ] || fail "commit or stash your changes first"

git fetch --quiet origin main --tags
[ "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)" ] || fail "main is not the same as origin/main; pull first"
! git rev-parse -q --verify "refs/tags/v$v" >/dev/null || fail "tag v$v already exists"

git tag -a "v$v" -m "Release v$v"
git push origin "v$v"
echo "Pushed v$v. Follow the build: $(gh repo view --json url -q .url)/actions/workflows/release.yml"
