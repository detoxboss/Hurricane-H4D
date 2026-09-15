package haven.groups;

import haven.BuddyWnd;
import haven.Coord;
import haven.GOut;
import haven.GameUI;
import haven.OldDropBox;
import haven.Text;
import haven.UI;
import haven.Widget;

import java.awt.Color;

/**
 * The dropdown + label-edit button glued onto an (untouched) BuddyWnd.GroupSelector.
 * Never replaces or reconstructs the real selector - resource code may hold its own
 * reference to it and call methods on it directly, so `real` must keep existing and
 * keep working. This sits as a sibling at the real selector's own position, with the
 * real selector hidden (hide() skips both draw and pointer dispatch on this fork's
 * Widget - confirmed in Widget.draw()/UI dispatch before relying on it).
 */
public class GroupSelectorCompanion extends Widget {
    private static final int rowh = UI.scale(20);
    private static final int gap = UI.scale(2);
    private static final int editw = rowh;
    private static final int totalw = BuddyWnd.nquick * UI.scale(20);
    private static final int dropw = totalw - gap - editw;

    final BuddyWnd.GroupSelector real;
    final GroupLabels.Scope scope;
    final String owner;
    final int lo, hi;
    final boolean warnAboveNquick;
    private final GroupDropbox dropdown;
    private GroupLabelPopup labelPopup;

    GroupSelectorCompanion(BuddyWnd.GroupSelector real, GroupLabels.Scope scope, String owner, int lo, int hi, boolean warnAboveNquick) {
        super(new Coord(dropw + gap + editw, rowh));
        this.real = real; this.scope = scope; this.owner = owner;
        this.lo = lo; this.hi = hi; this.warnAboveNquick = warnAboveNquick;
        dropdown = add(new GroupDropbox(displayGroup()), 0, 0);
        add(new EditButton(), dropw + gap, 0);
    }

    /* real.group can legitimately be -1 (vanilla's "no square lit" /
     * never-assigned state - GroupSelector.update() already guards for it).
     * -1 isn't a valid dropdown item (the list only spans lo..hi, always
     * starting at 0), so it must never reach the dropdown or a label lookup:
     * displaying it as literal "-1" text was the least of it - saving a
     * label while unassigned keyed it under -1, which then became
     * unreachable forever the instant any real color got picked (real.group
     * permanently leaves -1 at that point). Clamp for DISPLAY only; never
     * write this clamp back into real, and never call real.select() from it
     * - that would silently force a real assignment nobody asked for. */
    private int displayGroup() {
        return(Math.max(0, real.group));
    }

    public void tick(double dt) {
        super.tick(dt);
        /* Resource code can write real.group directly (not through select()),
         * or the server can re-drive it - re-read every tick rather than
         * relying on changed()/update() overrides catching every path. A
         * direct field write, not dropdown.change(...), so this never
         * re-triggers real.select(). */
        int disp = displayGroup();
        if(dropdown.sel == null || dropdown.sel != disp)
            dropdown.sel = disp;
    }

    void setLabelPopup(GroupLabelPopup popup) {labelPopup = popup;}
    void clearLabelPopup(GroupLabelPopup popup) {if(labelPopup == popup) labelPopup = null;}

    void closeLabelPopup() {
        GroupLabelPopup p = labelPopup;
        labelPopup = null;
        if(p != null)
            p.destroy();
    }

    public void destroy() {
        closeLabelPopup();
        super.destroy();
    }

    private void warnIfRisky(int group) {
        if(warnAboveNquick && (group >= BuddyWnd.nquick)) {
            GameUI gui = getparent(GameUI.class);
            if(gui != null)
                gui.error("Groups above " + (BuddyWnd.nquick - 1) + " may not render correctly on other players' unmodified clients.");
        }
    }

    private String labelText(int group) {
        String label = GroupLabels.get(scope, owner, group);
        return(label.isEmpty() ? Integer.toString(group) : (group + " - " + label));
    }

    private class GroupDropbox extends OldDropBox<Integer> {
        GroupDropbox(int group) {
            super(dropw, 10, rowh);
            sel = group;
        }

        protected Integer listitem(int i) {return(lo + i);}
        protected int listitems() {return(hi - lo + 1);}

        protected void drawitem(GOut g, Integer item, int i) {
            int sw = itemh - UI.scale(4);
            g.chcolor(BuddyWnd.gcolor(item));
            g.frect(new Coord(UI.scale(2), (itemh - sw) / 2), new Coord(sw, sw));
            g.chcolor(Color.WHITE);
            g.text(labelText(item), new Coord(sw + UI.scale(6), (itemh - Text.std.height()) / 2));
            g.chcolor();
        }

        /* item is null when a click lands inside the open list but misses
         * every row (normal base-widget behavior, e.g. a click that closes
         * the window while the list is still open) - must not unbox that
         * into real.select(int). Still let super.change() run unconditionally
         * so its own selindex/sel bookkeeping stays consistent; tick()'s
         * mirroring self-heals the visible selection back to real.group
         * every frame regardless. */
        public void change(Integer item) {
            super.change(item);
            if(item != null) {
                real.select(item);
                warnIfRisky(item);
            }
        }
    }

    private class EditButton extends Widget {
        EditButton() {super(new Coord(editw, rowh));}

        public void draw(GOut g) {
            g.chcolor(new Color(60, 60, 60));
            g.frect(Coord.z, sz);
            g.chcolor(Color.WHITE);
            g.atext("...", sz.div(2), 0.5, 0.5);
            g.chcolor();
        }

        public boolean mousedown(MouseDownEvent ev) {
            GroupLabelPopup.open(GroupSelectorCompanion.this, scope, owner, dropdown.sel);
            return(true);
        }
    }
}
