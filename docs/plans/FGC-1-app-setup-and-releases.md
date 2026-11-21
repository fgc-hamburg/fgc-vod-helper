# FGC-1: Set up the app and publish installers on GitHub Releases — Plan

> For agentic workers: executed natively via `implement-ticket` (superpowers:executing-plans style). Steps use checkboxes.

**Ticket:** FGC-1 — Set up the app and publish installers on GitHub Releases — https://linear.app/xunfosechunfos/issue/FGC-1

**Goal:** An empty Compose Desktop app that opens on macOS and Windows showing its name and version, with tested CI on every PR and tag-driven installers on GitHub Releases.

**Spec:** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md` (sections "Releases", "Constraints", "Parts → Packaging").

**Tech stack:** Gradle 9.3.0 (wrapper, version catalog), Kotlin 2.4.21 (JVM), Compose Multiplatform 1.12.2, JDK 21, JUnit via `kotlin("test")`, Compose desktop UI tests.

## Acceptance checks

| # | Scope bullet | Verified by |
|---|---|---|
| 1 | App window opens on macOS and Windows, showing name and version | `AppScreenTest` (renders name + "Version x.y.z") in CI on both OSes; manual `make run` on macOS with screenshot |
| 2 | Version tag builds Mac + Windows installers and attaches them to a GitHub Release | CI `package` job builds both installers on every PR (proves packaging); the full tag → Release run is done after merge with `make release VERSION=1.0.0` (needs `main`), noted in PR |
| 3 | Version set in one place and shown in the app | `app.version` in `gradle.properties` only; `BuildConfigTest` asserts generated version equals the Gradle property; `-Papp.version` overrides it |
| 4 | Installer file names predictable from version | `make package VERSION=1.0.0` locally → `dist/fgc-vod-helper-1.0.0.dmg`; CI package job uploads `fgc-vod-helper-<v>.{dmg,msi}` |
| 5 | README explains running locally and releasing | `README.md` review |
| 6 | Every PR runs tests on Mac and Windows, result on PR | `make check` runs real tests now (guard removed); required checks green on this PR |

## Approach

- **Two modules** so logic never depends on UI (CLAUDE.md rule):
  - `core` — plain Kotlin/JVM, no Compose. Holds `AppInfo` (name, version, key status). Later tickets add timeline, project store, etc. here.
  - `app` — Compose Desktop: window, screens, packaging. Depends on `core`.
  - KMP with a single `jvm("desktop")` target isn't needed yet; plain `kotlin("jvm")` keeps the build simple. Compose Multiplatform works with the JVM plugin for desktop. Switch to KMP only if a non-JVM target appears.
- **Single version source:** `gradle.properties` → `app.version=1.0.0`. Overridable with `-Papp.version=x.y.z` (used by `make package` / release workflow). Validated in the build: must match `^[1-9]\d*\.\d+\.\d+$` (macOS installer rule), build fails otherwise.
- **Generated `BuildConfig`** (small Gradle task in `core`, no plugin): `VERSION`, `TWITCH_CLIENT_ID`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, read from Gradle property or environment variable, defaulting to `""`. Generated into `build/`, never committed. Build passes without keys.
- **Packaging:** `nativeDistributions { targetFormats(Dmg, Msi) }`, `packageName = "FGC VOD Helper"`, `packageVersion = app.version`, macOS `bundleID = "de.fgchamburg.vodhelper"`, Windows fixed `upgradeUuid`, `perUserInstall = true` (no admin rights; keeps a future self-updater simple), `menuGroup = "FGC Hamburg"`. Makefile renames output to `fgc-vod-helper-<v>.<ext>`.
- **CI:** existing `check` job unchanged (now runs tests). New non-required `package` job on PRs and `main` runs `make package VERSION=<app.version>` on macOS + Windows and uploads the installers as build artifacts — proves packaging before any tag.
- **Makefile:** remove the "no Gradle project yet" guard from `check` and `clean`. `test` → `./gradlew test`. Add `make version-current` is not needed; `make package` default VERSION falls back to `gradle.properties` when not given.

## Steps

### Task 1: Gradle skeleton + `core` with `AppInfo`

Files: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, wrapper files, `core/build.gradle.kts`, `core/src/main/kotlin/de/fgchamburg/vodhelper/core/AppInfo.kt`, `core/src/test/kotlin/.../AppInfoTest.kt`, `core/src/test/kotlin/.../BuildConfigTest.kt`.

Interfaces (produced):
- `object BuildConfig { const val VERSION: String; const val TWITCH_CLIENT_ID: String; const val GOOGLE_CLIENT_ID: String; const val GOOGLE_CLIENT_SECRET: String }` (generated, package `de.fgchamburg.vodhelper.core`)
- `data class AppInfo(val name: String, val version: String, val twitchConfigured: Boolean, val youtubeConfigured: Boolean) { val versionLabel: String /* "Version $version" */; companion object { const val NAME = "FGC VOD Helper"; fun fromBuildConfig(): AppInfo } }`

- [ ] Generate wrapper (Gradle 9.3.0); write version catalog and settings.
- [ ] Failing tests: `AppInfoTest.versionLabel shows the version` (`AppInfo("FGC VOD Helper","1.2.3",false,false).versionLabel == "Version 1.2.3"`), `AppInfoTest.keys are configured only when non-blank` (blank twitch id → `twitchConfigured == false`; youtube needs both Google values), `BuildConfigTest.version matches the Gradle property` (test reads system property `app.version` set by Gradle for the test task; equals `BuildConfig.VERSION`).
- [ ] Implement generation task + `AppInfo`. Version validation fails the build for `0.1.0` and `1.0`.
- [ ] `make test` passes; `make package VERSION=0.1.0` fails early with a clear message. Commit.

### Task 2: `app` module — window showing name and version

Files: `app/build.gradle.kts`, `app/src/main/kotlin/de/fgchamburg/vodhelper/app/Main.kt`, `.../app/AppScreen.kt`, `app/src/test/kotlin/.../AppScreenTest.kt`.

Interfaces: `@Composable fun AppScreen(info: AppInfo)`; `fun main()` opens `Window(title = "${info.name} ${info.version}")` with `AppScreen(AppInfo.fromBuildConfig())`.

- [ ] Failing UI test `AppScreenTest.shows name and version`: `runComposeUiTest { setContent { AppScreen(AppInfo("FGC VOD Helper","1.2.3",false,false)) }; onNodeWithText("FGC VOD Helper").assertExists(); onNodeWithText("Version 1.2.3").assertExists() }`.
- [ ] Implement screen: centered name (headline) + version label. Material 3, light/dark from system. Plain text only; no images/art.
- [ ] `make check` passes; `make run` opens the window. Commit.

### Task 3: Packaging + Makefile + CI package job

Files: `app/build.gradle.kts` (nativeDistributions), `Makefile`, `.github/workflows/ci.yml`.

- [ ] Configure nativeDistributions as in Approach.
- [ ] Makefile: remove guards; `test` → `$(GRADLE) test`; `VERSION ?=` default read from `gradle.properties` for `package`.
- [ ] `make package VERSION=1.0.0` on macOS → `dist/fgc-vod-helper-1.0.0.dmg` exists; mount check optional.
- [ ] Add `package` job to `ci.yml` (matrix macOS/Windows, `make package`, upload `dist/*` as artifact `installer-<os>`). Commit.

### Task 4: Docs

Files: `README.md`, `CLAUDE.md` (Build and test).

- [ ] README: what the app is, requirements (JDK 21), `make setup` / `make run` / `make check`, how to release (`make release VERSION=x.y.z` from up-to-date `main`; where installers appear; unsigned installer warning on first open for Mac and Windows).
- [ ] CLAUDE.md: module layout (`core` vs `app`), package `de.fgchamburg.vodhelper`, version source, real commands. Commit.

## Out of scope

- Bundling ffmpeg, yt-dlp and VLC's engine (FGC-2).
- Code signing and self-updating (later).
- The first real tag → GitHub Release run: needs this PR merged into `main`; do it right after merge with `make release VERSION=1.0.0`.
- App icon: needs a non-AI icon from the team; default icon for now.

## Review focus

- Building without any keys or `.env` → must compile, tests pass, app opens (`AppInfoTest` covers configured flags; CI has no keys on PRs).
- Version override with an invalid value → build fails early with a clear message, not a jpackage error.
- Windows: Makefile/ci use bash; paths with spaces in `packageName` ("FGC VOD Helper") must survive the `find`/`cp` in `make package`.
- Window on a machine in dark mode → text stays readable (Material theme follows system).
- Re-running `make package` with an existing `dist/` → overwrites cleanly.
