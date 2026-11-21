# FGC VOD Helper

Desktop app (macOS + Windows) that turns our Twitch tournament VODs into one YouTube-ready video with match chapters. Kotlin Multiplatform, Compose for Desktop, JVM target.

## Where things are decided

- **Design (source of truth):** `docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`. Read the sections your ticket touches before planning. If a ticket conflicts with the design, stop and ask.
- **Work items:** Linear team **FGC HH** (key `FGC`), project **VOD Helper v1**. Each ticket's "Agent notes" hold technical hints; the Scope bullets are the acceptance criteria.
- **One-time account setup** (Twitch app, Google Cloud project): Linear project documents "Setup: …".

## Working on a ticket

Always use the `implement-ticket` skill (`/implement-ticket FGC-<n>`). It covers: checking blockers, worktree + branch, writing the ticket plan, test-first implementation, local verification, self-review, PR, CI, and updating Linear. Don't implement tickets outside that flow.

- One ticket = one branch = one PR. Use the branch name Linear gives the ticket.
- Never commit to `main` directly. Never merge your own PR; a human merges.
- Ticket plans live in `docs/plans/FGC-<n>-<slug>.md` and are committed with the work.
- Write and update tickets with the `writing-tickets` skill.

## Build and test

- `./gradlew check` — compile, unit tests, lint. Must pass before opening a PR. CI runs the same on macOS and Windows.
- `./gradlew run` — run the app locally.
- Build-time secrets come from Gradle properties or environment: `TWITCH_CLIENT_ID`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Never commit them. The build and tests must work without them (features needing them show "not configured").

(FGC-1 creates the Gradle project; update this section if commands change.)

## Code rules

- **Keep logic out of the UI.** Timeline math, snapping, description text, ffmpeg/yt-dlp command building, and project file handling are plain Kotlin with no Compose imports, so they are unit-tested directly.
- **Outside services behind interfaces.** Twitch, start.gg, YouTube, the file system for downloads, and external processes each sit behind an interface with a fake for tests. Unit tests never touch the network.
- **Determinism is a requirement, not a nice-to-have.**
  - No `System.currentTimeMillis()`, random values, or locale-dependent formatting in anything that affects exports or the description. Pass clocks in.
  - Collections written to `project.json` or used to build commands have a defined order.
  - ffmpeg runs with fixed flags (see design, "Determinism"). Exports of the same project must be byte-identical; there is a test for this.
- **Never lose user work.** `project.json` is written atomically (temp file + rename). Long tasks (download, export, upload) are resumable or cleanly cancellable.
- **No generative AI art** anywhere: icons, thumbnails, placeholders, test fixtures.
- Secrets (sign-in tokens) go to the OS credential store, never to files or logs.
- Prefer small, focused files. Follow existing patterns in the codebase over introducing new ones.

## Testing

- Test-first for logic (`superpowers:test-driven-development`).
- Each Scope bullet in the ticket maps to at least one automated test, or to a manual check listed in the PR when it's UI-only.
- Video fixtures are generated in tests with ffmpeg (`testsrc`/`sine`, a few seconds long), never committed as large binaries.
- UI behavior that can't be automated is verified by running the app (`./gradlew run`) and described in the PR.
