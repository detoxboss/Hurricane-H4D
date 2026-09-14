# `.res` binary format (menu-grid buttons)

There is no resource-editor tool in this repo (no `rescod`/`ResEdit`/similar). NightDawg's
`.res` files were presumably built with an external Haven resource editor. This fork builds
new menu-grid button `.res` files by **byte-splicing an existing one** instead — reusing the
same icon image bytes verbatim and only rewriting the small text fields. This was reverse
engineered from `Resource.java` (loader) plus hex dumps of
`res/customclient/menugrid/OtherScriptsAndTools/CustomAlarmManager.res` and
`res/customclient/menugrid/Bots/OceanScoutBot.res`.

Use [tools/New-MenuGridButton.ps1](tools/New-MenuGridButton.ps1) rather than doing this by hand.

## Container format

Source of truth: `Resource.load(Message in)` in `src/haven/Resource.java` (~line 2039).

```
"Haven Resource 1"        16 ASCII bytes, no terminator
ver                       uint16 LE   (resource format version, e.g. 4)
<layer>*                  repeated until EOF
```

Each `<layer>`:

```
name        null-terminated UTF-8 string  (e.g. "action", "image", "tooltip", "pagina")
len         int32 LE                       (byte length of the data that follows)
data        <len> bytes, format is layer-specific
```

Unknown layer names are just skipped by length — safe to leave present but irrelevant layers
alone when editing.

All strings inside layer data (except `pagina`'s raw text) are **null-terminated**, not
length-prefixed. All integers are **little-endian**. This is `Utils.uint16d`/`int16d`/etc. —
confirmed in `src/haven/Utils.java`.

## `action` layer — `Resource.AButton` (`Resource.java` ~1352)

```
parentPath      string        (e.g. "customclient/menugrid/Bots")
parentVer       uint16 LE     (must equal the target parent .res file's own `ver`, i.e. 4)
name            string        (bold title shown at the top of the tooltip; also the label
                               used elsewhere the game shows a short name for the action)
prereqSkill     string        (empty string for all custom buttons: just a single 0x00 byte)
hk              uint16 LE     (single-char grid quick-key; 0 = none)
adCount         uint16 LE
ad[0..adCount)  string each
```

For every custom (non-server) menu-grid button in this fork, `ad[0]` **must** be the literal
string `"@"` — that's the marker `MenuGrid.use()` checks to route into `MenuGrid.useCustom()`
instead of sending a real server action. `ad[1]` is the category (`"Bots"`, `"Toggles"`, ...),
`ad[2]` is the button's own id, matched in `useCustom()`'s if/else chain. See
[menugrid-system.md](menugrid-system.md).

## `image` layer — `Resource.Image`

Starts with a short binary header (`"H1"` tag, a few flag/size fields, a `scale` sub-block),
then an embedded PNG. **Never hand-edit this** — copy the entire layer's bytes (name + len +
data) verbatim from a donor `.res` file when you want to reuse its icon.

## `tooltip` layer — `Resource.Tooltip` (`Resource.java` ~1231)

```
data = UTF-8 text, no terminator (raw bytes, whole layer is the string)
```

Short caption; in every existing custom button it duplicates the `action` layer's `name`.

## `pagina` layer — `Resource.Pagina` (`Resource.java` ~1341)

```
data = UTF-8 text, no terminator (raw bytes, whole layer is the string)
```

The long tooltip body shown *below* the bold title. Rendered via
`ItemInfo.buildinfo` → `ItemInfo.Pagina` in `MenuGrid.PagButton.info()`. Plain text works;
paragraph breaks are just literal `\n` (use `\n\n` for a blank line between paragraphs, as seen
in every existing button).

## Worked recipe (what the tool script does)

1. Parse a **donor icon** `.res` (e.g. `CustomAlarmManager.res`) into layers; keep its `image`
   layer's raw bytes (name+len+data) untouched.
2. Parse a **donor category** `.res` (e.g. `OceanScoutBot.res`, any existing button already in
   the target category) purely to confirm the correct `parentPath`/`parentVer` pair for that
   category.
3. Build a fresh `action` layer with the new `name`/`ad[1]`/`ad[2]`, reusing the confirmed
   `parentPath`/`parentVer`.
4. Build fresh `tooltip` and `pagina` layers from plain text.
5. Concatenate: header + action + image (from step 1) + tooltip + pagina, write to
   `res/customclient/menugrid/<Category>/<Name>.res`.
6. Re-parse the written file to sanity-check the layer table before trusting it.

No changes are needed to any build script — `res/` is copied wholesale into the built client,
and `MenuGrid.loadCustomActionButtons()` finds new files purely by the explicit `makeLocal(...)`
call you add (there is no manifest/index file to update).
