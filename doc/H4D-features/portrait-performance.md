# Portrait / big-party performance

## Why portraits are expensive

Every `Avaview` ([Avaview.java](../../src/haven/Avaview.java)) extends `PView`, i.e. it is a full
extra 3D scene render each frame (own `RenderTree`, own `Composited` skeleton + pose ticking, own
lights/outlines, sized by the render-scale setting). There is no cap. Creators:

| Where | Count |
|---|---|
| `Partyview.MemberView` | one per party member (the big one: 50-person party = ~49 scenes/frame) |
| `GameUI.portrait` | 1 (own HUD portrait) |
| `Fightview` Mainrel / Relbox | 1 + up to 5 list rows |
| `BuddyWnd`, `Equipory` | only while the window is open |
| `Charlist` | login screen only |

The Party widget is added as a direct child of `GameUI` (`place == "party"`), not inside a
`Hidepanel`, so folding panels / the hide-UI toggle do not hide it.

## What this fork changed (perf/portrait-lag-fixes)

- **Speed text cache.** Hurricane's "show u/s on portraits" (`Avaview.draw`) used to build a stroked
  text image + new `TexI` *every frame for every moving portrait*. Now cached per portrait and
  rebuilt only when `gobSpeed` changes. Do not revert to per-frame `PUtils.strokeTex(...)`.
- **Options** (Advanced Settings -> Interface Settings, left column):
  - "Disable 3D Portraits (Performance)" -> `Avaview.disable3DPortraits` (pref `disable3DPortraits`).
    Draws the flat `missing` placeholder, skips `updcomp()`, `super.draw()` and `comp.tick()`.
    Applies to every `Avaview`, including the Equipment window doll.
  - "Hide Speed on Portraits" -> `Avaview.hidePortraitSpeed` (pref `hidePortraitSpeed`).
  - Both are read from static booleans (not `OptWnd` checkbox fields) because `Avaview` also draws
    on the login screen, before `OptWnd` exists.
- **Party overlay refresh.** `Partyview` used to call `oc.gobAction(Gob::updatePartyCircleOverlay)` /
  `...HighlightOverlay` (a sweep of *every* loaded gob under the OCache lock) on every member-map
  change, leader change, and dispose, plus a `updateLeaderPingArrow` sweep on every `list`. Now
  `refreshPartyOverlays(prev, cur)` touches only gobs in `prev ∪ cur`; gobs that load later set
  themselves up in `Gob` init. The `list` sweep only runs when the party is empty/size 1 (the only
  case `updateLeaderPingArrow` acts).

## Merge notes

The pieces most likely to conflict on an upstream sync: `Avaview.draw`/`tick`, `Partyview.update`/
`uimsg`/`dispose`, and the new checkboxes in `OptWnd.InterfaceSettingsPanel`.
