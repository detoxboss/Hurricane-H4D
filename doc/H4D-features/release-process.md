# Cutting a release

## What actually needs to ship

`ant bin` already produces exactly the payload players need, in `bin/`: `hafen.jar` +
every dependency/native jar, `res/`, `AlarmSounds/`, `midiFiles/`, `MapIconsPresets/`,
`Play.bat` / `Play_Linux.sh` / `Play_WithSteam.bat`, `Yapper_Bot_Phrases.txt`, and the small
config/manifest files. No source, no `doc/`, no `.git`, no build tooling. Verified end-to-end
with `ant clean && ant bin` from a fully fresh state (no cached `lib/ext`) — it re-downloads
everything it needs and produces a complete `bin/`.

**`Yapper_Bot_Phrases.txt` is copied into `bin/` by `build.xml`'s `bin` target** (added
alongside `hafen.hl`/`launcher.hl`) specifically so it lands at the root of the package next to
`Play.bat`, editable by players without digging into subfolders.

## The old `Release/` folder has been removed

The repo used to also carry `Release/` at the root: a **manually-maintained, committed-to-git
snapshot** of some past `bin/` output. Nothing in `build.xml` ever wrote to it, and nothing
regenerated it automatically, so it silently drifted out of sync with `src/`/`res/` every time a
PR merged without someone remembering to hand-refresh it.

This caused real, confirmed confusion: after merging the Yapper Bot PR, `Release/hafen.jar` and
`Release/res/` still held the pre-PR build. Verified directly — downloaded the actual GitHub
"Source code (zip)" asset for the `1.0.0` tag via `gh release download` and inspected
`Release/hafen.jar` inside it: 4066 class entries, zero containing `Yapper`, and
`haven/MenuGrid.class` timestamped months before the feature existed. Anyone who downloaded that
release got a genuinely stale client, full stop — not a caching or Steam-path issue.

It's been deleted from the repo (see the `chore/release-automation` PR). If it ever reappears
(e.g. someone manually re-adds a prebuilt copy for convenience), treat it exactly as before:
never trust it as a source of truth for what's shipped, since nothing keeps it in sync.

## The actual release path: `.github/workflows/release.yml`

Manual (`workflow_dispatch`) GitHub Action, run from the repo's Actions tab. Steps:

1. Checkout, set up JDK 21 + Ant.
2. `ant bin` — full clean build.
3. Zip **the contents of `bin/`** (not `bin/` itself) so extracting the release puts `Play.bat`,
   `hafen.jar`, `Yapper_Bot_Phrases.txt`, etc. directly at the top level — no wrapper folder to
   navigate into.
4. `gh release create <version> <zip> --target <commit>` — creates the git tag and the GitHub
   Release together, with the zip as the only asset, in one step.

**To cut a release:** merge your PR(s) into `master`, then go to Actions → "Create Release" →
Run workflow → enter a version string (e.g. `1.0.1`) → Run. That's the whole process; no local
build or manual zip/upload needed.

This runs on `ubuntu-latest` even though the output is cross-platform (native libs for
Windows/Linux/macOS are all bundled together already) — building on Linux is fine and just
needs outbound internet access to `www.havenandhearth.com` (where `ant bin` fetches
`builtin-res.jar`/`hafen-res.jar` and the jogl/lwjgl/steamworks native libs from — a pre-existing
dependency of this build, not something the workflow introduces).
