#!/usr/bin/env bash
# Runs the packaged app with --check-tools to prove its bundled video tools are found and run.
# Used by `make verify-package` (after `make package`), locally and in CI.
set -euo pipefail

base="app/build/compose/binaries/main/app"

case "$(uname -s)" in
  Darwin) launcher="$base/FGC VOD Helper.app/Contents/MacOS/FGC VOD Helper" ;;
  MINGW* | MSYS* | CYGWIN*) launcher="$base/FGC VOD Helper/FGC VOD Helper.exe" ;;
  *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac

if [ ! -f "$launcher" ]; then
  echo "Packaged app not found: $launcher" >&2
  echo "Run make package first." >&2
  exit 1
fi

echo "Running packaged app: $launcher --check-tools"
exec "$launcher" --check-tools
