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

## Pull requests

Every PR needs a good description built from its ticket (template: `.github/pull_request_template.md`):

- **Why:** the ticket's Context in one or two sentences.
- **What changed:** plain-language bullets a teammate who doesn't code can follow.
- **Acceptance checks:** every Scope bullet, ticked, with how it was verified.

**If the PR changes how the app looks or how the user moves through it** (new or changed screens, layout, controls, steps, wording):

- The whole description is jargon-free. No class names, library names or code terms outside the "Technical notes" section.
- Every UI change gets its own entry under **UI changes** with:
  - a screenshot for something that looks different, or a short video/GIF (under ~20 s) for something that moves or has steps;
  - before and after, when an existing screen changed;
  - one or two sentences telling the reviewer what to look at and what to judge ("Check the Connect buttons are clear about which accounts are required").
- Media goes on the `pr-media` branch, never on the feature branch (see `implement-ticket` skill, step 7).

## Build and test

**Every action goes through the `Makefile`.** Run `make` to list targets. CI and release workflows call the same targets. When you add a new repeatable action (a script, a Gradle task people need, a tool setup), add a `make` target for it with a `## description`, and use that target in docs and workflows instead of the raw command.

| Action | Command |
|---|---|
| Create your local `.env` for keys (first time, or after new keys are added) | `make setup` |
| Run the app | `make run` |
| Compile, test, lint (what CI runs; must pass before a PR) | `make check` |
| Unit tests only | `make test` |
| Build this OS's installer into `dist/` | `make package VERSION=1.2.3` |
| Release (tag `main`, CI builds installers and publishes the GitHub Release) | `make release VERSION=1.2.3` |
| Worktree for a ticket | `make worktree TICKET=FGC-1 BRANCH=<Linear branch name>` |
| Upload PR screenshots/GIFs | `make pr-media TICKET=FGC-1` |

- Versions are `x.y.z` with `x` ≥ 1 (macOS installers require it). The Gradle build reads the version from `-Papp.version`.
- Build-time keys: `TWITCH_CLIENT_ID`, `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`. Locally they live in `.env` (git-ignored, created by `make setup` from `.env.example`); the Makefile exports them to every command, so Gradle reads them from the environment. Never commit them. When you add a key, add it to `.env.example` with a comment on where to get it. The build and tests must work without them (features needing them show "not configured"). Release builds get them from GitHub Actions secrets.
- `main` is protected: changes land only through a PR with green CI.

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
