#!/usr/bin/env bash
# Uploads media/<TICKET>/ (screenshots, GIFs) to the orphan `pr-media` branch,
# so PR descriptions can show them without adding images to the code history.
# Prints one Markdown image link per file.
set -euo pipefail

ticket="${1:?usage: make pr-media TICKET=FGC-<n>}"
src="media/$ticket"
[ -d "$src" ] || { echo "pr-media: no $src folder" >&2; exit 1; }

main_root="$(cd "$(git rev-parse --git-common-dir)/.." && pwd)"
wt="$main_root/.worktrees/pr-media"
url="$(gh repo view --json url -q .url)"

git fetch --quiet origin pr-media 2>/dev/null || true
if [ ! -d "$wt" ]; then
  if git show-ref --verify --quiet refs/remotes/origin/pr-media; then
    git worktree add --quiet -B pr-media "$wt" origin/pr-media
  else
    git worktree add --quiet --detach "$wt"
    git -C "$wt" switch --quiet --orphan pr-media
  fi
elif git show-ref --verify --quiet refs/remotes/origin/pr-media; then
  git -C "$wt" merge --quiet --ff-only origin/pr-media
fi

mkdir -p "$wt/$ticket"
cp -R "$src"/. "$wt/$ticket/"
git -C "$wt" add "$ticket"
git -C "$wt" diff --cached --quiet || git -C "$wt" commit --quiet -m "$ticket: PR media"
git -C "$wt" push --quiet -u origin pr-media

for f in "$wt/$ticket"/*; do
  name="$(basename "$f")"
  echo "![${name%.*}]($url/blob/pr-media/$ticket/$name?raw=true)"
done
