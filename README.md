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

## Bundled video tools

The app ships with the video tools it needs, so nobody has to install them:

- ffmpeg and ffprobe 8.1.2
- yt-dlp 2026.10.26 (folder build, so it starts fast)
- VLC engine 3.0.24 (libvlc, for the video player)

The build downloads them once (cached in the Gradle user home) and checks their checksums. The versions, URLs and checksums are pinned in `buildSrc/src/main/kotlin/de/fgchamburg/vodhelper/buildlogic/VideoToolPins.kt`. `make check` downloads the tools once (cached) and runs them in a test; `make test` stays offline and never downloads. Installers bundle the tools for the OS and CPU of the machine that builds them.

To bump a tool: change its version, URL and sha256 in `VideoToolPins.kt`, update `THIRD_PARTY_NOTICES.md`, then run `make check` and `make package && make verify-package`. `make verify-package` runs the packaged app with `--check-tools` and prints whether each tool is ready (no window opens). CI runs it for every installer.

On macOS, the first check after installing can take about 25 seconds while the system checks the new files.

## Release

1. Merge everything you want to release into `main`.
2. Set the new version in `gradle.properties` (`app.version=1.2.3`) through a pull request, and merge it.
3. On an up-to-date `main`, run `make release` (it uses `app.version`; or pass `VERSION=1.2.3`).
4. GitHub builds the Mac and Windows installers and publishes them on the Releases page as `fgc-vod-helper-<version>.dmg` and `.msi`. Follow it under the repo's Actions tab.

Versions look like `1.2.3`, and the first number must be 1 or higher (Mac installers require it).

## How we work

Tickets live in Linear (team FGC HH, project VOD Helper v1). The design is in `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`. Contributor and agent rules are in `CLAUDE.md`.
