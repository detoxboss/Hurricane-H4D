# Syncing with NightDawg upstream (timeless procedure)

Remotes: `upstream` = `https://github.com/Nightdawg/Hurricane.git`, `origin` = this fork
(`detoxboss/Hurricane-H4D`). This file holds **no current SHAs or counts** — each sync's
concrete facts go in [upstream-sync-history/](upstream-sync-history/) as a dated entry. Any SHA
below is an obvious placeholder.

Related: [fork-customization-ledger.md](fork-customization-ledger.md) (what must survive),
[fork-maintenance-backlog.md](fork-maintenance-backlog.md) (known deferred issues),
[release-process.md](release-process.md).

## Philosophy

**Current NightDawg upstream is the structural baseline. Preserve only intentional H4D
divergence on top of it.** Code that is in this fork merely because it came from an older
upstream version is not an H4D customization — take upstream's current version. Deviate only
when there is a specific, demonstrated conflict with an entry in the customization ledger.

- **Merge, do not rebase or cherry-pick**, across the upstream boundary. Fork commits are never
  published upstream, so rewriting their hashes buys nothing and destroys "what did I add"
  history.
- **Never silently expand a pinned target.** Pin one upstream SHA in Phase 1; if upstream moves,
  stop and re-analyze.
- **Inspect clean auto-merges semantically.** "No conflict" only means no overlapping lines.
- New H4D behavior goes primarily in fork-owned files/classes (`src/haven/automated/`,
  `src/haven/groups/`, `res/customclient/...` leaves). Shared upstream files get narrow hooks,
  appended at the end of their block, with no unrelated reformatting. Do not refactor working
  features just to make a merge cleaner.

## One-time repo-local Git config

```
git config rerere.enabled true
git config rerere.autoupdate false     # never stage a replayed resolution on trust
git config merge.conflictstyle zdiff3
```

These live in `.git/config` (shared by all worktrees), not in the tree.

## Phase 1 — Analyze (read-only; no real merge)

1. `git fetch origin` and `git fetch upstream`.
2. Inspect **every** worktree (`git worktree list`, `git status` in each). Never disturb a
   worktree with WIP; do sync work in a new dedicated worktree.
3. Verify local `master` is not stale vs `origin/master`; fast-forward only after
   `git merge-base --is-ancestor master origin/master` succeeds and no worktree owns it.
4. Derive the merge base dynamically: `git merge-base origin/master upstream/master`. Record
   ahead/behind counts and the non-merge vs merge commit split.
5. Baseline: clean `master`, `ant jar` on JDK 21 (see Build notes). Record JDK/Ant versions,
   result, warning count. There is no automated test target.
6. **Pin** `TARGET_UPSTREAM_SHA=$(git rev-parse upstream/master)`.
7. Derive the full divergence locally from the merge base, both sides, with
   `git diff --name-status -M <base> <ref>`: path intersection, renames, add/add,
   modify/delete. Git quotes non-ASCII paths (e.g. `h\303\244st.res`) — count with `-z` or
   handle quoting, or counts will be off. Separate source from docs/release/`Release/`.
8. Explain every fork-only and upstream commit; inventory fork customizations from the ledger.
9. Disposable trial merge in a throwaway worktree/branch with
   `git -c rerere.enabled=false merge --no-commit --no-ff $TARGET`; record conflicts, build the
   resolved result, then `git merge --abort` and delete the worktree/branch. Verify the original
   state is restored.
10. Check build-output compatibility (new dirs/jars/natives, `ant bin` coverage, release
    workflow). **Stop and report for review.**

## Phase 2 — Resolve, stage, validate (stop uncommitted)

1. Re-fetch; confirm refs still match the pinned analysis.
2. Safety tag (local first): `pre-upstream-sync-YYYY-MM-DD-<short-premerge-sha>` at the pre-merge
   `master` SHA.
3. New clean worktree + branch `sync/upstream-YYYY-MM-DD` from `master`. Do not use the dirty
   main worktree.
4. `git merge --no-commit --no-ff $TARGET`. No `-X ours/theirs`, no squash.
5. Resolve each conflict on its merits against the ledger. Known standing rule: **root
   `Release/**` stays deleted** (`git rm` the modify/delete conflicts) — upstream changes to that
   stale committed snapshot never justify resurrecting it.
6. Reconcile the staged path set against the upstream endpoint diff (expect upstream paths minus
   deliberately dropped ones). No H4D-owned file, `.db` file or docs should be staged by the
   merge itself.
7. Semantically review the clean auto-merges and confirm every upstream change survived; confirm
   every ledger entry's files are byte-identical or deliberately resolved.
8. Forced source rebuild (below) with `ant jar` and `ant bin`; separate Panama check if
   `opt/panama` changed. `git diff --cached --check`, conflict-marker scan, zero
   unmerged/unstaged/untracked.
9. **Stop with the merge uncommitted** for review.

## Phase 3 — Commit, document, PR (do not merge to master)

1. Re-fetch and re-verify refs and staged state.
2. Normal two-parent merge commit; verify parents are (pre-merge master, pinned upstream) and
   `git merge-base --is-ancestor $TARGET HEAD`.
3. Rebuild the **committed** tree (`build/classes/buildinfo` embeds `git rev-parse HEAD`).
4. Push the safety tag; verify the remote tag SHA.
5. Docs-only commit(s) *after* the merge commit, so the merge stays a pure upstream merge:
   sync-history entry, ledger/backlog updates, workflow edits if the procedure changed.
6. Push branch; open a PR to `master`; do not merge it. Note which CI exists (see below).

## Phase 4 — Runtime verify, finalize, history

1. Smoke test in game (matrix below) from `bin/` built from the PR head.
2. Merge the PR **as a merge commit** (never squash/rebase — that would sever the upstream parent
   and make the next sync re-diff everything).
3. Fix regressions as focused follow-up PRs, one independent fix/feature per branch.
4. Release only after the release-infrastructure items in the backlog are satisfied.
5. Finish the dated history entry (PR number, merge SHA on master, runtime result, release).

## Build notes (Hurricane-specific)

- JDK 21 + Ant. `ant jar` is the quick compile check; `ant bin` is the canonical distributable.
- A genuine source recompile in an existing worktree: delete `build/classes` and
  `build/hafen.jar` (and `bin/` if checking output). **Do not run `ant clean`** — it deletes
  `lib/ext` and forces a re-download of jogl/lwjgl/steamworks and the res jars.
- `ant bin` depends on `jar`, **not** `opt/panama`. `opt/panama` needs JDK 22+ and is only built by
  the default target / by calling it explicitly; `bin` copies `build/hafen-panama.jar` only if it
  already exists. A locally built Panama jar therefore pollutes `bin/` — remove it before judging
  what CI would ship.
- `build/classes/buildinfo` records the Git HEAD; rebuild after committing.
- `release.yml` is manual (`workflow_dispatch`); a passing local build says nothing about it.

## Runtime smoke-test matrix

Launch + login screen version; map render, resize, stats overlay; audio plays; Custom Client
Extras menu opens and every Bots entry shows; Yapper Bot toggle/interval/stop; skis mount and
speed-4 restore (and no restore after deliberate slowdown); Kin/Village/Field-Cairn groups
0–39 with `[N]` tags and the `>= 8` warning, claim limited to 0–7, label persistence.

## Low-conflict habits for new H4D work

Behavior in a new fork-owned class; hook into shared files with the smallest append-only edit;
unique fork-owned resource paths; no copying whole upstream classes to tweak one thing; no
drive-by reformatting; one feature/fix per PR.
