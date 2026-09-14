# Yapper Bot

Toggleable button under **Custom Client Extras → Bots → Yapper Bot** (icon reused from Custom
Alarm Manager's red alarm icon). While on, it picks a random line from a phrase list and sends
it as a chat message on a repeating interval, via
[chat-message-injection.md](chat-message-injection.md) — so it never steals keyboard focus or
interrupts movement/combat/clicking.

## Files

| File | Role |
|---|---|
| `src/haven/automated/YapperBot.java` | The bot: `Runnable`, interval loop, loads phrases, sends via `ChatUI.EntryChannel`. |
| `src/haven/GameUI.java` | `yapperBot` / `yapperBotThread` fields (end of the `// Bot Threads` block). |
| `src/haven/MenuGrid.java` | `makeLocal("customclient/menugrid/Bots/YapperBot")` registration; `useCustom()` toggle branch in the `"Bots"` chain. |
| `res/customclient/menugrid/Bots/YapperBot.res` | The button resource (icon + label + tooltip). Built with [tools/New-MenuGridButton.ps1](tools/New-MenuGridButton.ps1); see command in that file's history/regeneration notes below. |
| `Yapper_Bot_Phrases.txt` (repo root) | One phrase per line, source list for the bot. Blank lines are skipped. Editable without recompiling. |
| `src/haven/OptWnd.java` | "Yapper Bot Interval" setting, in `ChatSettingsPanel` (Options → Advanced Settings → Chat Settings). Field: `yapperBotIntervalTextEntry`. |

## The interval setting

Options → Advanced Settings → Chat Settings → **Yapper Bot Interval (seconds)**. Free-text
field; must parse as a number (e.g. `1.0`, `0.3`, `5.0`) — invalid text is silently ignored and
the last valid value is kept (same tolerant-parse convention as
`src/haven/automated/CombatDistanceTool.java`'s distance field). Stored via
`Utils.setprefd("yapperBotInterval", ...)`, rounded to one decimal place on save.

Default is **1.0** seconds (`YapperBot.DEFAULT_INTERVAL_SECONDS`) if the pref was never set.
`YapperBot` re-reads the pref at the top of every cycle (`intervalMillis()`), so changing the
setting takes effect on the bot's very next message — no need to toggle it off/on.

Clamped to a floor of **0.1** seconds (`YapperBot.MIN_INTERVAL_SECONDS`) no matter what's typed
in the box. This isn't a UI restriction — it's a code-level safety clamp because
`Thread.sleep()` throws on a `<= 0` value, and an unclamped 0/negative interval would otherwise
flood the chat as fast as the network allows.

## Behavior notes

- Toggle on: starts a thread immediately, sends the first line right away (so the player gets
  instant confirmation the toggle worked), then waits one interval between lines.
- Toggle off: sets `stop = true` and interrupts the thread, so it stops promptly rather than
  finishing out its current sleep (some other bots in this fork only set the flag and rely on
  the loop to notice next iteration — Yapper Bot interrupts so a long interval setting doesn't
  make toggling off feel laggy).
- If the phrase file is missing/unreadable, or no `EntryChannel` chat tab is selected, it fails
  silently/logs via `gui.error(...)` rather than throwing.
- Phrases are read fresh from disk each time the bot is toggled on (not cached across toggles),
  so editing `Yapper_Bot_Phrases.txt` takes effect on the next toggle without a client restart.
- Draggable onto the hotbar for free — see [menugrid-system.md](menugrid-system.md#hotbar--hotkey-support).

## Regenerating the `.res` file

If the icon, name, or tooltip text ever need to change:

```powershell
./doc/H4D-features/tools/New-MenuGridButton.ps1 `
  -IconDonorRes res/customclient/menugrid/OtherScriptsAndTools/CustomAlarmManager.res `
  -CategoryDonorRes res/customclient/menugrid/Bots/OceanScoutBot.res `
  -Category Bots -ButtonId YapperBot -ActionName "Spitsburgen Yapper Bot" `
  -PaginaText "<tooltip body text>" `
  -OutDir res/customclient/menugrid/Bots
```
