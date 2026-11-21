---
name: release
description: Use when asked to release, publish, ship, cut or tag a new version of the fgc-vod-helper app, or to bump its version.
---

# Release the app

A release is a `v<x.y.z>` tag on `main`. The tag starts `.github/workflows/release.yml`, which builds the Mac (`.dmg`) and Windows (`.msi`) installers and publishes a GitHub Release with generated notes. `make release` creates and pushes the tag; nothing else is needed.

Version rules: `x.y.z`, first number 1–255, second 0–255, third 0–65535 (installer limits). `app.version` in `gradle.properties` is the only place the version lives.

## 1. Get main up to date

From the main checkout (not a ticket worktree):

```bash
git checkout main && git pull --ff-only
```

If the user says "master", they mean `main`. A dirty tree or a local branch ahead of `origin/main` blocks the release; report it, don't stash or reset someone's work.

## 2. Pick the version

```bash
grep app.version gradle.properties   # version that would be released
make version                         # latest release tag
```

- `app.version` not tagged yet → release it as is.
- `app.version` already tagged (or the user asked for a different version) → bump first. `main` is protected, so the bump goes through a PR:
  1. Ask which version, unless the user said it. Suggest: third number +1 for fixes, second +1 for new features.
  2. Branch `chore/release-<x.y.z>` in `.worktrees/release-<x.y.z>`, change only `app.version`, commit `Release <x.y.z>`, push, `gh pr create --base main --title "Release <x.y.z>"`. The body lists what's new (from `git log <last tag>..origin/main --oneline`, in plain language).
  3. Wait for green CI (`gh pr checks --watch`). A human merges; then go back to step 1.

Don't use `make release VERSION=…` to release a version different from `app.version`: the app would show a version that doesn't match the file.

## 3. Release

Confirm with the user before this step if they didn't ask for the release in this conversation; a pushed tag publishes installers publicly.

```bash
make release
```

It refuses (and changes nothing) if you're not on `main`, the tree is dirty, `main` differs from `origin/main`, the version is invalid, or the tag exists. Fix the cause; never delete or move an existing tag.

## 4. Watch and verify

```bash
id=$(gh run list --workflow release.yml --limit 1 --json databaseId -q '.[0].databaseId')
gh run watch "$id" --interval 30 --exit-status
gh release view v<x.y.z> --json url,assets -q '.url, (.assets[].name)'
```

Done when the release has exactly `fgc-vod-helper-<x.y.z>.dmg` and `fgc-vod-helper-<x.y.z>.msi`.

If a job fails: `gh run view "$id" --log-failed`, fix through a normal PR, then ask the user how to proceed (usually a new patch version; re-tagging a published version is a user decision).

## 5. Report

Tell the user: release link, the two installer files with sizes, and anything that failed or was retried. Installers are unsigned, so first open on Mac/Windows shows a warning (README → Install).
