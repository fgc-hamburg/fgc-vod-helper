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

## Twitch sign-in

The app signs in to Twitch with a Client ID. It has no secret. Set it up once for the whole team:

1. Go to [dev.twitch.tv/console](https://dev.twitch.tv/console) and click Register Your Application.
2. Set the OAuth Redirect URL to `http://localhost`. Set the category to Application Integration.
3. Set Client Type to **Public**. You cannot change this later.
4. Create the app, then copy the Client ID. The account that owns the app needs two-factor sign-in turned on.

Put the Client ID in two places:

- Your local `.env`: run `make setup`, then fill in `TWITCH_CLIENT_ID`.
- Releases: add a GitHub Actions secret named `TWITCH_CLIENT_ID`.

To sign in, the app shows a short code. It opens twitch.tv in your browser, where you type the code and approve the app. The app asks only for `user:read:broadcast`, a read-only permission. Twitch requires at least one, and reading past broadcasts needs none, so this is the smallest one available.

If the build has no Client ID, the Twitch row in Setup says "Not available in this build of the app". The rest of the app still works.

The full step-by-step guide is in Linear: [Setup: Register the Twitch app](https://linear.app/xunfosechunfos/document/setup-register-the-twitch-app-37eb7504fd7e).

## Bundled video tools

The app ships with the video tools it needs, so nobody has to install them:

- ffmpeg and ffprobe 8.1.2
- yt-dlp 2026.10.26 (folder build, so it starts fast)
- VLC engine 3.0.24 (libvlc, for the video player)

The build downloads them once (cached in the Gradle user home) and checks their checksums. The versions, URLs and checksums are pinned in `buildSrc/src/main/kotlin/de/fgchamburg/vodhelper/buildlogic/VideoToolPins.kt`. `make check` downloads the tools once (cached) and runs them in a test; `make test` stays offline and never downloads. Installers bundle the tools for the OS and CPU of the machine that builds them.

To bump a tool: change its version, URL and sha256 in `VideoToolPins.kt`, update `THIRD_PARTY_NOTICES.md`, then run `make check` and `make package && make verify-package`. `make verify-package` builds the app folder that goes into the installer and runs it with `--check-tools` and prints whether each tool is ready (no window opens). CI runs it for every installer.

On macOS, the first check after installing can take about 25 seconds while the system checks the new files.

## Release

1. Merge everything you want to release into `main`.
2. Set the new version in `gradle.properties` (`app.version=1.2.3`) through a pull request, and merge it.
3. On an up-to-date `main`, run `make release` (it uses `app.version`; or pass `VERSION=1.2.3`).
4. GitHub builds the Mac and Windows installers and publishes them on the Releases page as `fgc-vod-helper-<version>.dmg` and `.msi`. Follow it under the repo's Actions tab.

Versions look like `1.2.3`, and the first number must be 1 or higher (Mac installers require it).

## How we work

Tickets live in Linear (team FGC HH, project VOD Helper v1). The design is in `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`. Contributor and agent rules are in `CLAUDE.md`.
