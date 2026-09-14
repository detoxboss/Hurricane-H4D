# Bottom-right grid menu ("Custom Client Extras")

The bottom-right grid widget in the game UI is `MenuGrid` (`src/haven/MenuGrid.java`). It's
the same widget the vanilla server uses for the real per-character action/pagina buttons
("scm" = server command menu). NightDawg repurposed it to also host purely client-side
custom buttons alongside the real server ones.

## Category tree

Everything under the "Custom Client Extras" root icon is a tree of resources:

```
CustomClientExtras
├── Toggles
├── Bots
├── OtherScriptsAndTools   ("Other Scripts & Tools")
├── QuickSwitchFromBelt
└── CombatDecks
```

Each node (category and leaf button) is one `.res` resource file under
`res/customclient/menugrid/...`, mirroring the category path, e.g.
`res/customclient/menugrid/Bots/OceanScoutBot.res`.

## How a button gets loaded

`MenuGrid.loadCustomActionButtons()` (called once from the `MenuGrid()` constructor) calls
`makeLocal("customclient/menugrid/<Category>/<ButtonName>")` for every custom button. This:
loads the `.res` file via `Resource.local()`, wraps it in a `Pagina`, and adds it to the grid's
`paginae` set so it renders as a button under its parent category (parent is declared inside
the `.res` file itself — see [res-file-binary-format.md](res-file-binary-format.md)).

**To add a new button:** append one `makeLocal(...)` line at the *end* of the relevant category
block in `loadCustomActionButtons()` (append, don't insert alphabetically — see
[upstream-merge-workflow.md](upstream-merge-workflow.md) for why).

## How a click gets wired to Java code

Real server pagina buttons have `ad[0]` set by the server. Custom buttons instead set
`ad[0] = "@"` inside their `.res` file — this is the marker this fork uses to recognize "this
is a client-side fake button, not a real server action." `MenuGrid.use()` checks for that `"@"`
and routes to `MenuGrid.useCustom(String[] ad)`.

`useCustom` is one big if/else chain: outer branch on `ad[1]` (category name, e.g. `"Bots"`),
inner branch on `ad[2]` (button id, e.g. `"YapperBot"`). **To wire a new button**, add one more
`else if (ad[2].equals("YourButtonId"))` branch at the *end* of the relevant category's chain.

The standard pattern for a toggleable bot/tool (see any existing entry in the `"Bots"` branch):

```java
if (gui.xBot == null && gui.xBotThread == null) {
    gui.xBot = new XBot(gui);
    gui.xBotThread = new Thread(gui.xBot, "XBot");
    gui.xBotThread.start();
} else {
    if (gui.xBot != null) {
        gui.xBot.stop();
        gui.xBotThread = null;  // (interrupt() too, if the loop sleeps a long time)
        gui.xBot = null;
    }
}
```

The `gui.xBot` / `gui.xBotThread` fields live on `GameUI` (`src/haven/GameUI.java`), grouped
under a `// Bot Threads` comment block. Append new fields at the end of that block.

## Hotbar / hotkey support

Every `MenuGrid` button (custom or real) is drag-and-droppable onto the action belt/hotbar for
free — `MenuGrid.mouseup()` already calls `DropTarget.dropthing(...)` when a button is dragged.
No extra code is needed to make a new custom button draggable onto the hotbar; once it's a
registered `Pagina`/`PagButton`, it behaves like any other action icon.

## Files touched by a typical new button

1. `res/customclient/menugrid/<Category>/<Name>.res` — the resource (icon + action + tooltip).
   See [res-file-binary-format.md](res-file-binary-format.md) for how to build one without the
   official resource editor (not present in this repo).
2. `src/haven/MenuGrid.java` — one `makeLocal(...)` line, one `else if` branch in `useCustom()`.
3. `src/haven/GameUI.java` — field(s) to hold the bot/window/thread instance, if any.
4. Optionally a new class under `src/haven/automated/` implementing the actual behavior.

Worked example: [yapper-bot.md](yapper-bot.md).
