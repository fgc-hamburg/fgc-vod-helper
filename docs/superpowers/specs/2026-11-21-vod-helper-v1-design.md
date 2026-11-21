# FGC VOD Helper — Version 1 Design

Date: 2026-11-21
Status: Approved

## Why

We stream fighting game tournaments on Twitch. One event often leaves several VODs, because the stream sometimes has to restart. Turning those into a clean YouTube video means downloading, joining, cutting out the downtime between matches, and writing chapter timestamps by hand. This app makes that a short, repeatable job.

"Deterministic" matters to us: the same VODs and the same match marks must always produce the same video and the same description.

## Goals (version 1)

1. First-time setup that connects the accounts the app needs: Twitch and start.gg (required), YouTube (only if the user wants uploads).
2. List our channel's past broadcasts on Twitch.
3. Pick the VODs from one event and download them at the best available quality.
4. Link the project to a start.gg event to load its matches: round, players, characters.
5. Show the picked VODs as one continuous video with a single time bar.
6. Let the user mark each match's start and end by scrubbing and playing, attach the match details, and fix or remove marks.
7. Save all of this as a project file, so work can be closed and resumed.
8. Export one video that contains only the matches, in order, plus a YouTube description with a chapter timestamp per match.
9. At export, the user picks: save the video and description locally, or upload them to YouTube directly.
10. Installers for macOS and Windows are built automatically and published to GitHub Releases, so a self-updater can be added later without rework.

## Not in version 1

- Suggested match marks from Twitch stream markers (next version).
- Suggested match marks from start.gg match start/end times (later; version 1 only uses start.gg for match details).
- Suggested match marks from image matching on the video (later).
- Exporting each match as its own video.
- Thumbnails (later; game and character specific).
- Self-updating and code signing (later; see Releases).

## Must never happen

- Any generative AI art, anywhere in the app or its output.
- Two exports of the same project producing different cut points or descriptions.
- Losing the user's match marks (crash, closed window, failed download).
- Publishing a video publicly without the user explicitly choosing to.

## Constraints

- Runs on macOS and Windows. Built with Kotlin Multiplatform, Compose for Desktop.
- Twitch deletes past broadcasts after 7–60 days. Downloading is time-sensitive.
- VODs are large: a 1080p60 stream is roughly 3 GB per hour. An 8-hour event needs ~25 GB for downloads plus the same again for the export.
- Twitch video has a keyframe roughly every 2 seconds. Cuts without re-encoding can only land on keyframes.
- YouTube upload API:
  - Needs a Google Cloud project with OAuth set up for the `youtube.upload` scope.
  - Until that project passes Google's API audit, uploaded videos are locked to **private**.
  - Default quota allows about 6 uploads per day. Fine for us.
- start.gg API: needs a personal API token, created by the user in their start.gg developer settings. Characters are only there if the organizers reported them; the user must be able to fill or fix them by hand.
- YouTube only shows chapters when the description has at least 3 timestamps, the first is `0:00`, and each chapter is at least 10 seconds.

## Approaches considered

### Video player inside the app

- **A. Bundle VLC's engine (vlcj)** — smooth playback with sound, precise seeking, same behavior on both OSes. Adds about 100 MB. **Chosen.**
- B. Each OS's built-in player — lighter, but seeking accuracy differs between Mac and Windows, which works against determinism.
- C. No playback, only still frames and a thumbnail strip — simplest, but finding match ends without sound or motion is slow.

### Joining the VODs

- **Virtual join. Chosen.** The app treats the VODs as one timeline but never writes a combined file. Export cuts directly from the downloaded VODs. This saves disk space and time.
- Physical join (write one big file first): simpler to reason about, but doubles disk use and adds a long step.

### Cutting

- **Copy without re-encoding, snapped to keyframes. Chosen.** No quality loss, export takes minutes. Match marks snap to the nearest keyframe so the time bar shows exactly where cuts land.
- Re-encode everything: frame-exact cuts, but hours per export and some quality loss before YouTube re-encodes it again.
- Fallback inside the chosen approach: if two VODs have different video settings (e.g. the stream restarted at another resolution), only the affected matches are re-encoded to match the others, with fixed settings.

## How it works

### First-time setup

Shown on first launch, and reachable later from settings. Each step shows whether it is connected and lets the user reconnect or sign out.

1. **Twitch (required)** — device code sign-in: the app shows a code, the user approves it in the browser.
2. **start.gg (required)** — the user pastes a personal API token. The app links to the page where it is created and checks the token works.
3. **YouTube (optional)** — browser sign-in, only needed for direct upload. Can be skipped and done later, or at the moment the user first picks "Upload" at export.

Tokens are stored in the OS keychain / credential store, never in the project file.

### Main flow

1. **New project** → user names it and picks a folder.
2. **Pick VODs** → list of past broadcasts with title, date, length, and thumbnail. The user ticks the ones from one event. Order is by broadcast start time.
3. **Download** → each VOD downloads in the background with progress, resume after interruption, and a clear error if a VOD has expired. Downloads go into the project folder.
4. **Link start.gg event** → the user pastes the event link. The app loads the event's matches (sets): game, round, both players, and characters when reported. This can be refreshed later if the organizers fix results.
5. **Mark matches** → the editor screen:
   - Video player on top, one time bar below covering all VODs, with visible boundaries between VODs.
   - Keys and buttons to set "match start" and "match end" at the current position, step frame by frame, and jump ±5 s / ±30 s.
   - A list of matches beside the player: name, start, end, length. Click to jump; drag edges on the time bar to adjust.
   - Each marked match is given its details by picking a start.gg match from a searchable list (by player name or round). The user can also type or correct any field by hand. Fields: game, round, player 1, player 2, characters for each player.
   - The match name shown in the app and in the description is built from the fields, e.g. `Winners Final – Alice (Ryu) vs Bob (Ken)`.
   - Marks snap to keyframes. The snapped position is what is shown and saved.
   - A match may not cross a VOD boundary in version 1 (it would need re-encoding). If a match does span a restart, the user marks it as two matches. *(Builder's call; revisit if it happens often.)*
6. **Export** → user reviews the title and description, then picks one:
   - **Save locally** → picks a folder; the app writes `<title>.mp4` (matches in order, nothing in between) and `<title>-description.txt`.
   - **Upload to YouTube** → the app builds the same video, then uploads it with title and description, visibility default **private**. Progress is shown and uploads resume after interruption. The video and description are also kept locally. If YouTube isn't connected yet, the app asks to connect first.

### Description format

```
<free text header the user can edit, saved in the project>

0:00 Match 1 name
12:34 Match 2 name
1:02:10 Match 3 name
```

Timestamps are each match's start in the exported video. Hours appear only when the video is at least an hour long. The app warns if there are fewer than 3 matches or any match is under 10 seconds, since YouTube would then not show chapters.

### Project file

One folder per project with a human-readable `project.json`:

- picked VODs: Twitch video ID, title, broadcast start, length, local file path, download state
- linked start.gg event and a copy of its loaded matches
- matches: start and end as (VOD ID, time in that VOD), in order; game, round, players, characters; the start.gg match ID if picked from the list
- description header, export title
- versions of the bundled tools used for the last export

Saved automatically after every change, written to a temporary file then swapped in, so a crash never leaves a half-written file.

### Determinism

- Same `project.json` + same downloaded files + same tool versions → same cut points, same description, and the same video bytes. ffmpeg runs with fixed flags and no timestamps or metadata that vary between runs.
- The app bundles pinned versions of ffmpeg and yt-dlp and records them in the project at export.
- yt-dlp breaks when Twitch changes its site. The app lets the user update yt-dlp from settings. That only affects downloading, not export.

### Parts

- **Setup & accounts:** first-time setup screen, settings, token storage.
- **Twitch:** sign-in, list past broadcasts, VOD metadata.
- **start.gg:** token check, load an event's matches with players and characters.
- **Downloader:** wraps yt-dlp; progress, resume, cancel.
- **Project store:** reads/writes `project.json`, autosave.
- **Timeline:** maps positions on the single time bar ↔ (VOD, time); keyframe lists per VOD (read once via ffprobe, cached in the project folder); snapping.
- **Editor UI:** player (vlcj), time bar, match list, keyboard controls.
- **Exporter:** builds ffmpeg commands from the project; runs them; verifies output length.
- **Description builder:** pure function from project → text.
- **YouTube:** sign-in (browser login returning to the app), resumable upload.
- **Packaging:** installers for macOS and Windows with bundled VLC engine, ffmpeg, yt-dlp.

### Releases

- Pushing a version tag (e.g. `v1.0.0`) builds the macOS and Windows installers in CI and attaches them to a GitHub Release.
- The app shows its version in settings. The version comes from one place in the build.
- Installers are unsigned in version 1. Users click through the OS warning on install.
- Path to self-updating later: the app checks the latest GitHub Release, compares versions, and offers the new installer. Nothing in version 1 should block that (stable app ID, stable install location, release assets with predictable names).

### Auto-update decision

Considered Conveyor (hands-off installers and updates; free only for open-source repos) and a custom updater on GitHub Releases. Decided: no self-updating in version 1, only GitHub Releases, keeping the custom-updater path open.

## Left to the builder

- Module layout, libraries (HTTP client, JSON, logging) and how tokens are stored (should use the OS keychain / credential store).
- Which tool builds the installers (e.g. Compose Desktop packaging).
- The Twitch app registration and Google Cloud project are set up by us; the builder documents what is needed.
- Exact keyboard shortcuts.
