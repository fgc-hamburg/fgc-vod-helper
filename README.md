# FGC VOD Helper

Desktop app for macOS and Windows that turns our Twitch tournament VODs into one YouTube-ready video with match chapters.

## Install

Download the latest installer from the [GitHub Releases page](https://github.com/fgc-hamburg/fgc-vod-helper/releases): `.dmg` for Mac, `.msi` for Windows.

The installers are not signed yet. So the first time you open the app:

- Mac: right-click the app, then click Open, then Open. Or go to System Settings → Privacy & Security → Open Anyway.
- Windows: on the "Windows protected your PC" screen, click More info, then Run anyway.

## Develop

Requirements: JDK 21 and `make`. On Windows, run `make` from Git Bash.

1. `make setup` — creates your local `.env`. Fill in the keys (see the comments in `.env.example`). The app also runs without them.
2. `make run` — runs the app.
3. `make check` — compiles and runs all tests. CI runs the same on every pull request.

Run `make` to see every command.

## Release

1. Merge everything you want to release into `main`.
2. Set the new version in `gradle.properties` (`app.version=1.2.3`) through a pull request, and merge it.
3. On an up-to-date `main`, run `make release` (it uses `app.version`; or pass `VERSION=1.2.3`).
4. GitHub builds the Mac and Windows installers and publishes them on the Releases page as `fgc-vod-helper-<version>.dmg` and `.msi`. Follow it under the repo's Actions tab.

Versions look like `1.2.3`, and the first number must be 1 or higher (Mac installers require it).

## How we work

Tickets live in Linear (team FGC HH, project VOD Helper v1). The design is in `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`. Contributor and agent rules are in `CLAUDE.md`.
