# 40-group permission extension (Kin/Village/Field Cairn)

Extends the assignable range of the shared 8-square permission-group picker
(`BuddyWnd.GroupSelector`, `src/haven/BuddyWnd.java`) from 8 to 40 for Kin,
Village, and Field Cairn, without growing the picker widget itself. Ported
from an equivalent feature in a sibling fork (Nurgling2); adapted to this
fork's own class names and mechanisms, all confirmed against this repo's own
code and a live `ant run` session (see "How the class names were confirmed"
below) rather than assumed to carry over.

## Why the widget itself isn't touched, and why `gc[]` still had to grow

`GroupSelector`'s *physical shape* (square count, footprint width) and
`BuddyWnd.gc[]`'s *length* used to be the same number by accident, and this
port initially treated them as the same thing on purpose — keeping `gc[]` at
8 so the widget's own `gc.length`-driven sizing never changed, and adding a
second, wider array (`gcExt[]`) just for the new dropdown. **That split
turned out to be wrong and caused a real crash (Bug B, see below) — a
compiled server resource (Village's `VMember`) reads `BuddyWnd.gc[group]`
directly, with no bounds check of its own, for whatever group the server
actually assigned, entirely independent of which array backs this port's own
dropdown.** A client whose `gc[]` is only 8 long crashes rendering a
perfectly valid assignment above 7; Nurgling2 never hit this because its
`gc[]` was already 255 long from the start.

The two concerns are now correctly separated by two different constants
instead of one array's accidental length:

- `BuddyWnd.nquick` (8) — the widget's own physical square count. `GroupSelector`
  sizes and populates itself from `nquick`, not from `gc.length`, so the
  picker's footprint is fixed regardless of how large the color table gets
  (growing `gc[]` was tried undecoupled once already and rejected for
  exactly the layout-breaking reason `nquick` now prevents).
- `BuddyWnd.gc[]` (255 entries) — the one safety-sized color table everything
  reads: the widget's own squares (0-7), the companion dropdown, `gcolor()`,
  and any resource code indexing it directly. Entries 0-7 are byte-for-byte
  identical to the original palette (nothing visibly changes for existing
  squares/resources only using that range), 8-39 are the wider dropdown
  palette, 40-254 are filler (index 0's color, same fallback `gcolor()` uses
  out of range) — sized to 255 because at least one resource
  (`res/ui/obj/buddy_n/Named.java`) reads a raw wire `uint8` directly as a
  group index, and 255 is the maximum value a `uint8` can carry.
- `BuddyWnd.ncolors` (40) — how many entries the companion dropdown actually
  lists; unrelated to either array's length.

Everything else about the shared widget is still untouched:
`GroupSelector.attached()`/`dispose()` gained two lines each, calling into
`haven.groups.GroupSelectorClassifier` — nothing else about the class
changed. Everything else lives in the new `haven.groups` package: the
classifier, a companion dropdown+label-edit control that sits as a sibling
of the real (now-hidden) selector at its same position, a label-edit popup,
and a JSON-backed label store.

**Other call sites that assumed `gc.length == 8` for unrelated reasons, found
by grepping the whole repo after widening `gc[]` and fixed alongside it** (none
of these are part of the group-permission feature; they just happened to
read the same array's length for their own purposes):
- `MapWnd.java` / `ProspectingWnd.java` — a random default color for new map
  markers, `BuddyWnd.gc[new Random().nextInt(BuddyWnd.gc.length)]`. Would
  have picked the index-40-254 filler color (a duplicate of white) ~84% of
  the time once `gc[]` grew. Now bounded to `nquick`, unchanged behavior.
- `res/gfx/hud/mmap/plo/Factory.java` — enumerates one minimap player-icon
  variant per color for a filter/legend list, `for(i = 0; i < BuddyWnd.gc.length; i++)`.
  Would have generated 255 mostly-duplicate icon variants instead of 8. Now
  bounded to `nquick`.
- `res/gfx/hud/mmap/plo/Player.java`'s own `grp < BuddyWnd.gc.length` bounds
  check was **not** changed — that one benefits from the widening (a kinned
  player's minimap icon can now show their real color above group 7 instead
  of falling back to white), which is exactly the kind of consistent
  extension this feature is supposed to provide.

## Confirmed class names (captured live, not guessed)

Village/Realm/claim/Field-Cairn permission windows are all server-distributed
resources with no source in this repo. Before hardcoding anything,
`GroupSelector` was given only its two lifecycle hooks plus temporary
deduped logging of `sel.parent.getClass().getName()` and (if present)
`sel.getparent(Polity.class).getClass().getName()`. The user then opened each
window once in a live `ant run` session; the console output was the evidence
below — see `haven.groups.GroupSelectorClassifier` for where each is used:

| Window | Immediate parent class | `Polity` ancestor class |
|---|---|---|
| Village (top-level selector) | `haven.res.ui.vlg.Village` | `haven.res.ui.vlg.Village` |
| Village (per-member selector) | `haven.ers.ui.vmemb.VillageMember` | `haven.res.ui.vlg.Village` |
| Kin (`BuddyInfo`) | `haven.BuddyWnd$BuddyInfo` (first-party) | none |
| Personal claim ("Stake") | `haven.res.ui.land.Landwindow` | none |
| Field Cairn | `haven.res.ui.sar.Administer` | none |

**`haven.ers.ui.vmemb.VillageMember` is spelled "ers", not "res" — confirmed
from raw `Class.getName()` console output, not a transcription error.** It's
a typo in the shipped server resource's own package name, not a bug in this
port; the classifier doesn't even need to match on it (the `Polity` ancestor
check already covers both Village selectors), it's recorded here purely as
confirmed evidence. Do not "correct" it anywhere.

Realm/Kingdom was never opened during the logging session — this fork has no
observed evidence of a distinct Realm-style polity window at all (no
`extends Polity` subclass exists in source, and only `Village` was ever seen
on the wire). Left alone entirely; the classifier's `Polity` branch returns
"leave alone" for any `Polity` subtype other than the confirmed `Village`
class, so if a Realm-equivalent does exist and is ever opened, it fails safe
(no companion attached) rather than being guessed into a range.

Because both this fork and the reference fork (Nurgling2) connect to the same
official live server (`etc/ansgar-config.properties`: `haven.server=game.havenandhearth.com`),
these compiled resource classes are the same bytecode regardless of which
client fork is running — which is why the Landwindow/Village class name
strings guessed in the Nurgling2 port turned out byte-identical here once
captured live.

## Per-window behavior

| Window | Range | Cross-client warning | Notes |
|---|---|---|---|
| Kin | 0-39 | No | Client-local only, never rendered by anyone else. |
| Village | 0-39 (restored) | Yes, `>= 8` | Bug B root-caused and fixed (below) — the cap was never Village's own limit, it was `BuddyWnd.gc[]` only being 8 long. Full range restored now that `gc[]` is safely sized. |
| Personal claim ("Stake") | 0-7 (unchanged) | No | Companion still added (for the label-edit "..." button), but the dropdown only offers 0-7 — this fork has no vendored source for `Landwindow` either, so the 8-cap is *inherited caution from the Nurgling2 port* (there vendor-verified via a third-party reference client's leaked source), not independently re-verified in this repo. |
| Field Cairn | 0-39 | Yes, `>= 8` | Bug A root-caused and fixed (below) — the earlier freeze was never evidence against Field Cairn's own range. |

### Resource versions checked (both forks, same live server)

| Class | Hurricane `Resource.ver` | Nurgling2 `Resource.ver` |
|---|---|---|
| `haven.res.ui.vlg.Village` | 39 | 39 |
| `haven.ers.ui.vmemb.VillageMember` | *(pending)* | 13 |
| `haven.res.ui.sar.Administer` | *(pending)* | 15 |
| `haven.res.ui.land.Landwindow` | *(not checked)* | *(not checked)* |

The theory that Hurricane might be running a stale/cached older version of
`Village` than Nurgling2 (which would explain a real behavioral difference
without either fork's code being at fault) is **disproven, not just
unconfirmed** — identical version 39 on both, same live server. Whatever
Bug A and Bug B turn out to be, they are not a resource-version mismatch.

### Bug A (FIXED): Field Cairn freeze — our own `GroupSelector.update()`, not a resource cap

The Field Cairn window freezing unresponsive after assigning a group above 7
was traced to this fork's own shared widget, not any resource:

```
Caused by: java.lang.ArrayIndexOutOfBoundsException: Index 13 out of bounds for length 8
    at haven.BuddyWnd$GroupSelector.update(BuddyWnd.java:273)
    at haven.res.ui.sar.Administer.uimsg(sar.cjava:80)
```

`GroupSelector.update()` guarded the lower bound (`group >= 0`) on both the
unselect and select paths, but never the upper bound against `groups.length`
(8) — so any `select()`/`update()` call with a group >= 8 threw partway
through `Administer`'s own `uimsg` handling. Throwing mid-`uimsg` is exactly
why the window went unresponsive afterward: the resource's own message
state machine never got to finish processing that message. Fixed by adding
the same bounds check Nurgling2's `update()` already has:

```java
public void update(int group) {
    if(group == this.group) return;
    if((this.group >= 0) && (this.group < groups.length)) groups[this.group].unselect();
    this.group = group;
    if((group >= 0) && (group < groups.length)) groups[group].select();
}
```

**This means the earlier conclusion that "Field Cairn's range should be
trusted less" was wrong** — the freeze was never evidence about Field
Cairn's own storage/render capability at all, it was purely this fork's own
missing bounds check, now fixed. Field Cairn's 0-39 range is back to
"asserted, not verified" (the original, secondhand-developer-claim status),
not "actively suspicious." Worth re-testing now that the actual bug is gone.

### Bug B (FIXED): Village member-list crash — same root cause as Bug A, decisively confirmed

Assigning a Village member group 39 produced a crash in compiled resource
code, not our widget:

```
java.lang.ArrayIndexOutOfBoundsException: Index 39 out of bounds for length 8
    at haven.res.ui.vlg.Village$VMember.draw(vlg.cjava:80)
    at haven.Polity$MemberList$1.draw(Polity.java:132)
```

The resource-version-mismatch theory was checked and disproven (`Village`
identical `ver=39` on both forks, see table above). Two competing
hypotheses were live-tested by reassigning two real members to two different
out-of-range values (39 and 20, using Nurgling2's uncapped picker) and
reopening Hurricane's Village member list: **the crash index followed
exactly — `Index 39` for the untouched member, `Index 20` for the
reassigned one.** This rules out `Member.order`/join-order or any other
unrelated field. `VMember.draw()` is reading each member's real, live group
number directly and indexing an 8-length array with it, unguarded.

That array is `BuddyWnd.gc[]` itself. `VMember` is compiled resource code,
but it runs inside whichever client loads it, so any call it makes back into
`haven.BuddyWnd` — almost certainly `BuddyWnd.gc[group]` for a display color
— resolves against *that client's own* `BuddyWnd` class. This port had left
`gc[]` at its original 8 entries and added a second, wider array (`gcExt[]`)
purely for the new dropdown; Nurgling2 never split it that way — it made the
*original* `gc[]` itself 255 entries long from the start, specifically so
anything indexing into it (including resource code neither fork controls)
handles any server-sent group number safely. `VMember.draw()` calling into
Hurricane's still-8-long `gc[]` for group 39 was exactly this crash; calling
into Nurgling2's 255-long `gc[]` for the same group number just works.

**Fix:** `gc[]` itself is now 255 entries (see "Why the widget itself isn't
touched" above for the full before/after and the `nquick`/`gc[]`/`ncolors`
split that keeps the widget's own footprint from changing). `gcExt[]` is
removed — the companion dropdown now reads the same widened `gc[]`/`gcolor()`
as everything else, so there's only ever one color table to keep in sync.
Village's range is restored to the full 0-39 now that the actual cause is
fixed, not worked around.

The `Polity.MemberList` item-draw `try/catch(Throwable)` guard added while
this was under investigation is being **kept as a permanent safety net**,
not removed now that the specific cause is fixed — it costs nothing on the
happy path and protects against any future resource code with its own
unguarded array, which is now known to be a real, recurring pattern in this
codebase rather than a one-off.

## Cross-client risk (Village, Field Cairn)

Assigning a Village member or a Field Cairn permission a group above 7 can
still crash or misrender on other players' unmodified clients even though
this fork's own client no longer crashes on either (Bug A/B fixes): any
ground/boundary permission-color overlay is shared world state rendered by
every viewing client, including bystanders who never opened the permission
window — and an unmodified client's own compiled rendering code has its own
array bound (historically 8, and now confirmed to be exactly `BuddyWnd.gc[]`'s
old length doing this same kind of unguarded indexing). This is not fixable
from a client fork alone. As a mitigation (not a fix), `GameUI.error(...)`
fires a one-line warning whenever a group `>= 8` is picked in Village or
Field Cairn.

## Village member `[N]` tag — real bug found (parsememb() override), fixed

`Polity.Member` has a `group` field (default `-1`) and `Polity.MemberList`'s
item wrapper draws a `[N]` tag when `group >= 0`. Getting this populated for
Village took three rounds of live testing, and the middle round's conclusion
was wrong - recorded here so the mistake itself is legible, not just the
final answer.

**Round 1** (during the Bug B investigation): two Village members logged
`group=-1` from this field. Concluded "channel unknown."

**Round 2**: logged every raw wire message `Polity.uimsg()` received. Watching
a single member get reassigned five times (1 → 39 → 32 → 38 → 37 → 36)
showed `args[1]` on `"add"` tracking the real group exactly, matching what
`Polity.parsememb()` already read into `m.group`. **Concluded "the code was
already correct, just needed confirming."** This was wrong, and the mistake
was conflating "the raw wire data contains the right value where we can see
it" with "our code that reads it actually runs."

**Round 3** (this fix): rebuilt with that "confirmed-working" code, then
scrolled through an entire real Village member list and logged `item.group`
for every row actually drawn. **Every single one read `-1`**, including
members with visibly non-default colors on screen - a clean data-side
negative, not a rendering/position issue. That directly contradicted round
2's conclusion and forced re-examining *where* the group-set line lived:
inside `Polity.parsememb()` - a `protected`, overridable method. `Village`
must override it (it has to, to construct its own `VMember` subclass
instead of a plain `Member`), and does not call our logic - so the line that
reads `args[1]` into `m.group` was simply never executing for Village,
despite `Polity.uimsg()` itself (a *different*, non-overridden method)
definitely receiving that same `args[1]` value, which is exactly what round
2's diagnostic was placed inside and exactly why it looked like confirmation
when it wasn't.

**Fix:** moved the `args[1]` → `group` assignment out of `parsememb()` and
into `Polity.uimsg()`'s `"add"` branch, applied to the `Member` object
*after* `parsememb()` returns - regardless of which override (ours or
Village's) actually constructed it. This is the same method round 2's
logging proved always executes for Village, so the fix lands somewhere
already known to run rather than somewhere merely assumed to.

**Lesson for anything similar in this codebase going forward:** confirming a
value exists on the wire, at the point closest to the socket, is necessary
but not sufficient - it says nothing about whether an overridable method
downstream of that point (`parsememb()` here) is the implementation that
actually runs for a given resource subclass. When a fix touches an
overridable method, verify by reading the *field's actual runtime value* at
the point it's used (as round 3 finally did), not by re-confirming the wire
data one layer removed from where the override could intercept it.

Kin's own `[N]` tag is unaffected by any of this - separate code entirely.
`BuddyWnd.Buddy` already had a populated `group` field with no override in
the way, it just wasn't rendered in the list. `Buddy.grouptag()` (same
caching pattern as `Polity.Member.grouptag()`) is drawn inline right after
the name in `BuddyList`'s item draw, matching the reference client's
"Name [N]" style, and was confirmed working immediately.

## Companion dropdown default — fixed, was showing/saving against `-1`

`real.group` can legitimately be `-1` (vanilla's "no square lit" / never-
explicitly-assigned state, which `GroupSelector.update()` already guards
for — e.g. a Village member nobody has put in a group yet). The companion
originally mirrored that raw value straight into the dropdown, which only
lists `lo..hi` (always starting at 0) — so `-1` displayed as literal text,
and clicking "..." while unassigned saved a label keyed under `-1`, which
became permanently unreachable through the UI the instant any real color got
picked (since that moves `real.group` off `-1` for good, and the dropdown
never has a `-1` entry to select back into). Matches default vanilla-client
behavior (no explicit assignment shows as a plain, editable state, not a
literal "-1") by clamping the *displayed* value to a floor of 0 in
`GroupSelectorCompanion.displayGroup()` — used for both the initial dropdown
seed and the `tick()` mirror. This is display-only: it never writes back into
`real.group` and never calls `real.select()`, so nothing gets force-assigned
just because the dropdown is being shown.

## Kin label scoping

Kin/claim/Field-Cairn labels are keyed by `GameUI.chrid` — this fork's own
existing per-character pref-scoping convention (already used for
`"mapfile/"+chrid` and `"actionBar*_"+chrid` in `GameUI.java`), not invented
for this feature. Village labels are keyed by the village's own `Polity.name`
— there's no durable numeric village id exposed to the client here either,
so renaming a village orphans its labels and two identically-named villages
collide; accepted as a low-stakes tradeoff since it's client-side
presentation only (same call made in the Nurgling2 port).

Labels persist via `Utils.getpref`/`setpref` (this fork's only
gameplay-preference store — there's no `NConfig`-style structured config
here) as one JSON blob per scope (`kinGroupLabels`, `villageGroupLabels`),
using the vendored `org.json` already present in this tree.

## What's genuinely uncertain, and what to live-test

1. **Bug A and Bug B are both fixed** — same root cause (`BuddyWnd.gc[]` too
   short for what resource code independently indexes into it with),
   decisively confirmed live for Bug B by watching the crash index follow a
   reassignment from 39 to 20 exactly. Full ranges restored for both Village
   and Field Cairn, both confirmed live by the user (no crashes, bugs, or
   console errors) working with the full 40-group range in Village, Field
   Cairn, and Kin.
2. **Village and Kin `[N]` tags** — Kin confirmed working live. Village went
   through a wrong "confirmed working" conclusion (see the tag section above
   for the full account) before a third round of live testing caught the
   real bug: the `args[1]` → `group` assignment lived inside `Polity.parsememb()`,
   which `Village` must override to build its own `VMember`, so it never ran.
   Fixed by moving the assignment into `Polity.uimsg()`'s `"add"` branch
   instead, applied after `parsememb()` returns regardless of which override
   built the object. **Not yet re-confirmed live after this specific fix** —
   next thing to check: reopen Village, confirm `[N]` tags now show for real
   members (including the one used for the 39/32/38/37/36 test, expect `[36]`).
3. **Realm/Kingdom** — not yet checked at all; still the next thing to look
   at now that Bugs A and B are resolved. No evidence so far that this fork
   has a distinct polity subtype for it (no `extends Polity` in source, only
   `Village` ever seen on the wire) — if that holds up, it fails safe (no
   companion attached, existing 8-square behavior unchanged) with nothing
   further to do.
4. **Cross-client rendering risk for Village/Field Cairn groups >= 7** is a
   known, accepted, unfixable-from-a-fork limitation (confirmed to be the
   exact same unguarded-array pattern this port just fixed on its own side,
   just unfixable on someone else's unmodified client), mitigated only with
   an in-client warning — not something to "fix" further.
5. **The two members used for the Bug B test (reassigned to 39 and 20 via
   Nurgling2's picker)** should render correctly now in Hurricane's Village
   member list instead of being skipped by the crash guard — worth
   confirming directly as part of the retest.
