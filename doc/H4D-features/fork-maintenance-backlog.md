# Fork maintenance backlog

Verified, deferred issues only. Each should become a focused PR. Remove an item when fixed
(history lives in git and [upstream-sync-history/](upstream-sync-history/)).

## Release / Panama backend not packaged — fix before the next public release

`.github/workflows/release.yml` installs JDK 21 and runs `ant bin`. `ant bin` depends on `jar`, not
`opt/panama`, and `opt/panama` requires JDK 22+ (`has-panama`). So a fresh GitHub runner never builds
`hafen-panama.jar`, and `bin/`/the release zip omit it (the manifest `Class-Path` entry is simply
unresolved). All upstream Panama source (PulseAudio, Win32/Linux init, FFI changes) **is present and
compiles** on JDK 23 (`ant opt/panama`, 60 sources, 0 warnings) — it is the packaging that is
missing. Decide whether to ship the backend; if so, use JDK 22+ in the workflow and build
`opt/panama` before `bin`. Beware: a locally built `build/hafen-panama.jar` gets copied into `bin/`
and hides the gap.

## Inherited `notify-docs.yml`

`.github/workflows/notify-docs.yml` fires on `release: published` and dispatches to
`Nightdawg/HurricaneDocs` using `secrets.DOCS_DISPATCH_TOKEN`. That secret/repo is NightDawg's, so
this is unconfigured and inappropriate for H4D releases (it will fail on publish). Disable, remove,
or adapt it before the next release.

## Tracked runtime churn in `hitboxes.db` / `static_data.db`

Both are tracked SQLite files the client rewrites at runtime. PR #4's commit (`0bc1b901c`) captured
incidental runtime state (`hitboxes.db` 12 KB → 245 KB). They are not feature data and re-dirty on
every local run. Decide later whether to restore the merge-base content and/or stop tracking them.
Not touched by any upstream sync so far.

## Update checker points at upstream

`src/haven/LoginScreen.java` calls `GitHubVersionFetcher.fetchLatestVersion("Nightdawg", "Hurricane", …)`
and compares against `Config.clientVersion`. H4D users are therefore compared against NightDawg's
release tags, not `detoxboss/Hurricane-H4D`. Adapt so upstream tags are not presented as H4D updates.
