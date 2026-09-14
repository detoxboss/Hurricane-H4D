# Pulling in a new NightDawg release

Remote already configured (checked via `git remote -v`):

```
upstream  https://github.com/Nightdawg/Hurricane.git
origin    https://github.com/detoxboss/Hurricane-H4D.git   (this fork)
```

## When told "NightDawg pushed an update, let's merge"

```
git fetch upstream
git log HEAD..upstream/master --oneline     # see what actually changed
git merge upstream/master                   # or: git rebase upstream/master
```

Resolve conflicts, run a build (`ant jar` at minimum to catch compile breaks — see below),
commit, push to `origin`.

Prefer `merge` over `rebase` unless the user asks for a linear history — this fork's custom
commits are not meant to be published upstream, so rewriting their hashes on every release has
no benefit and makes it harder to compare "what did I add" over time.

## Why conflicts are basically unavoidable in a few hot files

NightDawg's own architecture puts most custom-feature wiring inline in a handful of large
files — `MenuGrid.java`, `GameUI.java`, `OptWnd.java` — rather than behind extension points.
Any custom addition that hooks into the menu grid, options window, or GameUI's per-frame tick
necessarily edits those same files NightDawg also edits upstream. This is not something to
"fix" by refactoring the fork's core architecture — that would create much bigger, harder
conflicts later. Accept it and minimize *surface area* instead:

### House rules to keep future conflicts small

1. **New behavior goes in a new file** under `src/haven/automated/` (or a new top-level class)
   whenever possible. Only the wiring (a field, a `makeLocal` line, an `else if` branch) touches
   a shared file — the actual logic doesn't.
2. **Always append, never insert alphabetically/logically.** Add new `makeLocal(...)` calls,
   `GameUI` fields, and `useCustom()` branches at the *end* of their existing block/chain, not
   sorted into the middle. Git's line-based diff/merge only conflicts when both sides touch the
   *same lines*; appending after NightDawg's last line almost never collides with whatever he
   appended on his end.
3. **Don't reformat or reindent code you're not changing.** This fork already mixes tabs/spaces
   inconsistently in places (NightDawg's own commits do this too) — resist the urge to clean it
   up in the same commit as a feature change; it turns a 3-line diff into a 300-line one and
   guarantees a conflict on every future merge of that file.
4. Custom, fork-only resource files live under their own leaf paths
   (`res/customclient/menugrid/...`) that NightDawg's upstream doesn't touch, so `.res` file
   additions essentially never conflict.

## Validating after a merge

There's no CI in this repo. Minimum smoke check after resolving conflicts:

```
ant jar
```

(Uses Apache Ant + JDK 21, both present in this dev environment — see build.xml.) A clean
`BUILD SUCCESSFUL` catches compile-level breakage from the merge; it does not catch runtime/UI
regressions, so a manual in-game check of anything touched by the merge is still worthwhile
before calling it done.
