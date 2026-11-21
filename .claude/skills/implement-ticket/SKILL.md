---
name: implement-ticket
description: Use when asked to implement, build, pick up, or work on a Linear ticket (FGC-<n>) in the fgc-vod-helper repo.
argument-hint: FGC-<n>
---

# Implement a ticket

Takes one Linear ticket from Backlog to an open, green, self-reviewed PR. A human merges.

## Who does what

| Work | Who |
|---|---|
| Reading the ticket, planning (steps 1–3), reviewing each task, self-review, PR description, CI fixes that need judgment (steps 6–9) | **You** (the main session) |
| Building each plan task (step 4) | A **Sonnet** subagent per task |
| Purely mechanical tasks (renames, moving files, doc wording from an exact outline) | A **Haiku** subagent |

The plan carries the decisions, so building from it is rote work for a cheaper model. If the plan is too vague for a cheaper model to follow, fix the plan; don't do the task yourself.

Input: the ticket ID (e.g. `FGC-4`). If none was given, ask for it.

## 1. Read and check

1. `get_issue` the ticket and `list_comments` on it. Read Context, Scope, Agent notes, and any comments.
2. Read the design sections the ticket touches (`docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`) and `CLAUDE.md`.
3. Check every "blocked by" ticket is **Done**. If one isn't, stop and report which, unless the user said to go ahead anyway.
4. If Scope is unclear in a way that would change what gets built, or conflicts with the design: stop, post the question as a Linear comment on the ticket, and ask the user. Don't guess.
5. Set the ticket to **In Progress**, assignee `me`.

## 2. Workspace

1. Create the worktree with `make worktree TICKET=FGC-<n> BRANCH=<gitBranchName from get_issue>` (branches from `origin/main` into `.worktrees/FGC-<n>`), then work inside it.
2. If the design spec isn't on `main` yet (first ticket only), commit it as the first commit on the branch.

## 3. Spec the ticket

Write `docs/plans/FGC-<n>-<slug>.md` with `superpowers:writing-plans`. A cheaper model will build each task from it, so every task names its exact files, signatures, test names and values. It must contain, in order:

1. **Ticket:** ID, title, link.
2. **Acceptance checks:** each Scope bullet rewritten as a check, and how it's verified (test name, or manual step for UI-only).
3. **Approach:** the design decisions for this ticket, and anything taken from Agent notes or decided differently (with why).
4. **Steps:** small test-first steps with the files they touch.
5. **Out of scope:** copied from the ticket's "Not in this ticket", plus anything found while planning.

Commit the plan on its own (`FGC-<n>: plan`).

## 4. Build

Each task in the plan goes to its own subagent, one after another (they share the worktree, so never in parallel).

### 4a. Dispatch a task

Use the Agent tool with `subagent_type: "general-purpose"` and `model: "sonnet"` (`"haiku"` for purely mechanical tasks). The prompt is self-contained; the subagent has none of your context. It contains, in order:

1. **Where:** the absolute worktree path. "Work only inside it. Don't touch other worktrees or `main`."
2. **What:** the plan path and the task number. "Read `CLAUDE.md`, the plan's header sections, and Task N. Do only Task N."
3. **Done when:** the task's steps and checks, and that `make check` passes.
4. **How:** test-first (`superpowers:test-driven-development`); follow `CLAUDE.md` code rules; small commits prefixed `FGC-<n>:` ending with the `Co-Authored-By` line for the subagent's own model (e.g. `Claude Sonnet 5.5 <noreply@anthropic.com>`); never push; never edit the plan.
5. **Stop and report instead of guessing** when the plan is ambiguous, contradicts the code, or needs a decision not written down.
6. **Report back:** commits made, files changed, the last `make check` result (pass/fail + failing test names), anything skipped or assumed.

### 4b. Check the task before the next one

1. Read the report and `git diff` for the task's commits. Run `make check` yourself; don't trust the report alone.
2. Matches the plan and the code rules? Go to the next task.
3. Small fix needed? Send it back to the same subagent with `SendMessage` (it keeps its context). Two failed rounds on the same problem → do it yourself and note why in the PR's Technical notes.
4. The subagent stopped on a real decision? Make the decision, write it into the plan (commit `FGC-<n>: plan update`), then continue the same subagent.

### Rules for every task

- If the work grows beyond Scope, stop, note it in the plan's Out of scope, and suggest a follow-up ticket (written with `writing-tickets`) instead of building it.
- Update `CLAUDE.md` / README when a command, convention, or setup step changes.
- New repeatable action (script, tool, Gradle task people run)? Add a `make` target with a `## description` and use it everywhere instead of the raw command.

## 5. Verify locally

Use `superpowers:verification-before-completion`. All must pass, with output seen:

- `make check`
- `make run` for anything the user sees: walk through each UI acceptance check and note the result.
- Every acceptance check in the plan is ticked or explicitly marked as not verified with a reason.

## 6. Self-review

1. Run the `code-review` skill at `medium` on the branch diff against `origin/main`.
2. Fix confirmed findings. Re-run `make check`.
3. Findings left unfixed go in the PR body with the reason.

## 7. Open the PR

### 7a. Capture UI changes (only if the app looks or flows differently)

List each UI change: every new or changed screen, control, step, or piece of wording. For each:

- **Looks different** → screenshot of the app window. Before + after if the screen existed on `main` (build `main` in a second worktree for the "before").
- **Moves or has steps** → short screen recording (under ~20 s), converted to GIF so it plays inline on GitHub.

Capturing (macOS), with the app running via `make run`:

```bash
make screenshot TICKET=FGC-<n> NAME=01-setup-screen      # click the app window when prompted
make record TICKET=FGC-<n> NAME=02-connect-flow SECONDS=15  # records the screen, saves a GIF
```

Name files in the order the reviewer should see them (`01-…`, `02-…`).

If you can't capture (no display, permission denied), say so in the PR and to the user. Never skip silently.

Upload to the `pr-media` branch (orphan branch, keeps media out of the code history):

```bash
make pr-media TICKET=FGC-<n>
```

It prints one Markdown image link per file. Paste them into the PR, replacing the alt text with what each shows. `media/` is git-ignored; never commit it on the feature branch.

### 7b. Write the description and open the PR

Write the body from the ticket, following `.github/pull_request_template.md` and the "Pull requests" rules in `CLAUDE.md`:

- **Ticket:** `Fixes FGC-<n>`
- **Why:** the ticket's Context, one or two sentences
- **What changed:** 2–5 plain-language bullets
- **UI changes:** one entry per change from 7a — media + "What to look at". "None" if nothing user-visible changed
- **Acceptance checks:** the plan's checklist, ticked, with how each was verified
- **Manual checks:** what was run in the app, on which OS
- **Review:** self-review summary and anything left open
- **Technical notes:** the only section allowed to use code terms; link the plan

Before opening, reread every section except Technical notes as a teammate who doesn't code. Replace any jargon.

```bash
git push -u origin HEAD
gh pr create --base main --title "FGC-<n>: <ticket title>" --body-file <tmp body file>
```

## 8. CI

1. `gh pr checks --watch`.
2. If a check fails: read the log (`gh run view --log-failed`), fix with `superpowers:systematic-debugging`, push, watch again. Repeat until green.
3. If a failure is unrelated to the ticket (flaky runner, outage), say so in the PR and to the user; don't hide it with retries.

## 9. Hand off

1. Comment on the Linear ticket: PR link, one-line summary, anything left open.
2. Set the ticket to **In Review**.
3. Tell the user: PR link, CI status, review findings left open, manual checks not done.

Don't merge. Don't delete the worktree until the PR is merged.
