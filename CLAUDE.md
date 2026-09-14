# Hurricane-H4D

Personal fork of NightDawg's custom Haven & Hearth client
(`upstream` remote = https://github.com/Nightdawg/Hurricane.git). Adding custom features on top,
pulling in NightDawg's releases over time.

## Project docs

Feature/system knowledge built up while working in this repo lives in
**[doc/H4D-features/](doc/H4D-features/README.md)** as small, single-topic files (not one big
doc) so only the relevant piece gets read for a given task. Check that index whenever the task
touches:

- Pulling in a new NightDawg release / merging upstream
- Cutting a release of this fork for players
- The bottom-right "Custom Client Extras" grid menu (toggles, bots, scripts)
- Hand-building/editing a `.res` resource file
- Sending a chat message from bot/script code

When a task uncovers a new non-obvious system (traced through the codebase, not just "read the
code"), add a new small file there rather than re-deriving it next time.

## Build

```
ant jar     # compile + package build/hafen.jar — quickest compile-error check
ant bin     # full bin/ output (jogl/lwjgl/steamworks libs, res/, AlarmSounds/, etc.)
```

Requires Apache Ant and JDK 21.
