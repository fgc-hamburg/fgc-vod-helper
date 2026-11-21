---
name: implement-ticket
description: Use when asked to implement, build, pick up, or work on a Linear ticket (FGC-<n>) in the fgc-vod-helper repo.
argument-hint: FGC-<n>
---

# Implement a ticket

Takes one Linear ticket from Backlog to an open, green, self-reviewed PR. A human merges.

Input: the ticket ID (e.g. `FGC-4`). If none was given, ask for it.

## 1. Read and check

1. `get_issue` the ticket and `list_comments` on it. Read Context, Scope, Agent notes, and any comments.
2. Read the design sections the ticket touches (`docs/superpowers/specs/2026-11-21-vod-helper-v1-design.md`) and `CLAUDE.md`.
3. Check every "blocked by" ticket is **Done**. If one isn't, stop and report which, unless the user said to go ahead anyway.
4. If Scope is unclear in a way that would change what gets built, or conflicts with the design: stop, post the question as a Linear comment on the ticket, and ask the user. Don't guess.
5. Set the ticket to **In Progress**, assignee `me`.

## 2. Workspace

1. `git fetch origin` and create a worktree from `origin/main` on the ticket's `gitBranchName` (from `get_issue`). Use `superpowers:using-git-worktrees`.
2. If the design spec isn't on `main` yet (first ticket only), commit it as the first commit on the branch.

## 3. Spec the ticket

Write `docs/plans/FGC-<n>-<slug>.md` with `superpowers:writing-plans`. It must contain, in order:

1. **Ticket:** ID, title, link.
2. **Acceptance checks:** each Scope bullet rewritten as a check, and how it's verified (test name, or manual step for UI-only).
3. **Approach:** the design decisions for this ticket, and anything taken from Agent notes or decided differently (with why).
4. **Steps:** small test-first steps with the files they touch.
5. **Out of scope:** copied from the ticket's "Not in this ticket", plus anything found while planning.

Commit the plan on its own (`FGC-<n>: plan`).

## 4. Build

- Follow the plan with `superpowers:test-driven-development`: failing test, make it pass, commit. Small commits, each prefixed `FGC-<n>:`.
- Keep `CLAUDE.md` code rules: logic outside the UI, services behind interfaces with fakes, determinism, atomic writes, no secrets in files.
- If the work grows beyond Scope, stop, note it in the plan's Out of scope, and suggest a follow-up ticket (written with `writing-tickets`) instead of building it.
- Update `CLAUDE.md` / README when a command, convention, or setup step changes.

## 5. Verify locally

Use `superpowers:verification-before-completion`. All must pass, with output seen:

- `./gradlew check`
- `./gradlew run` for anything the user sees: walk through each UI acceptance check and note the result.
- Every acceptance check in the plan is ticked or explicitly marked as not verified with a reason.

## 6. Self-review

1. Run the `code-review` skill at `medium` on the branch diff against `origin/main`.
2. Fix confirmed findings. Re-run `./gradlew check`.
3. Findings left unfixed go in the PR body with the reason.

## 7. Open the PR

```bash
git push -u origin HEAD
gh pr create --base main --title "FGC-<n>: <ticket title>" --body-file <tmp body file>
```

Body (follows `.github/pull_request_template.md`):

- **Ticket:** `Fixes FGC-<n>` + link
- **What changed:** 2–5 plain-language bullets
- **Acceptance checks:** the plan's checklist, ticked, with how each was verified
- **Manual checks:** what was run in the app, on which OS
- **Review:** summary of the self-review and anything left open
- **Plan:** path to `docs/plans/FGC-<n>-<slug>.md`

## 8. CI

1. `gh pr checks --watch`.
2. If a check fails: read the log (`gh run view --log-failed`), fix with `superpowers:systematic-debugging`, push, watch again. Repeat until green.
3. If a failure is unrelated to the ticket (flaky runner, outage), say so in the PR and to the user; don't hide it with retries.

## 9. Hand off

1. Comment on the Linear ticket: PR link, one-line summary, anything left open.
2. Set the ticket to **In Review**.
3. Tell the user: PR link, CI status, review findings left open, manual checks not done.

Don't merge. Don't delete the worktree until the PR is merged.
