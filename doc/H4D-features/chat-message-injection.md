# Sending a chat message from code without touching input focus

Need: a bot/script/alert must "say" something in chat, without stealing keyboard focus, moving
the mouse, or interrupting whatever the player is currently doing (moving, fighting, clicking).

## The mechanism

`src/haven/ChatUI.java`, class `EntryChannel` (~line 793), method `send`:

```java
public void send(String text) {
    history.add(text);
    wdgmsg("msg", text);
}
```

This is **exactly** what happens when the player types a line and presses Enter in a chat tab
— `TextEntry.activate()` calls this same `send()`. It is a plain widget-message send to the
server; it does not touch the text-entry widget's focus, does not simulate keystrokes, and does
not move the cursor. Calling it directly from a background thread has zero UI side effects
beyond the message actually appearing in that chat channel (and being added to that channel's
local recall history, same as if typed normally).

## How to call it

The currently-selected chat tab is `GameUI.chat.sel` (public field, `ChatUI.Channel` type). Not
every tab is an `EntryChannel` (e.g. a pure log tab isn't), so guard with `instanceof`:

```java
ChatUI.Channel sel = gui.chat.sel;
if (sel instanceof ChatUI.EntryChannel) {
    ((ChatUI.EntryChannel) sel).send("your message");
}
```

If no `EntryChannel` tab is currently selected, this is a no-op — there's no "default" channel
to force it into without changing what the player is looking at, so callers should just skip
that cycle rather than trying to switch tabs for them.

## Why this is safe to call from a background `Thread`

This codebase already calls `wdgmsg(...)` directly from background bot threads in several
places (e.g. `src/haven/automated/CloverScript.java` calls `clover.wdgmsg("take", ...)` and
`gui.map.wdgmsg("itemact", ...)` from inside `Runnable.run()`). There is no requirement to
marshal these calls onto a UI/render thread in this codebase.

## Used by

[Yapper Bot](yapper-bot.md) (`src/haven/automated/YapperBot.java`) — the reference
implementation of this pattern.
