package haven.groups;

import haven.Button;
import haven.Coord;
import haven.GameUI;
import haven.Label;
import haven.TextEntry;
import haven.UI;
import haven.Widget;
import haven.Window;

/**
 * Small free-floating window (never a child of the companion or the resource
 * window it's opened from) for typing a custom label for one group number.
 * Saves on every close path - titlebar X, Escape, Enter, Save button - through
 * one idempotent save call in destroy().
 */
public class GroupLabelPopup extends Window {
    private final GroupSelectorCompanion owner;
    private final GroupLabels.Scope scope;
    private final String labelOwner;
    private final int group;
    private final TextEntry entry;
    private boolean saved = false;

    public static void open(GroupSelectorCompanion owner, GroupLabels.Scope scope, String labelOwner, int group) {
        owner.closeLabelPopup();
        GroupLabelPopup popup = new GroupLabelPopup(owner, scope, labelOwner, group);
        owner.setLabelPopup(popup);
        GameUI gui = owner.getparent(GameUI.class);
        Widget host = (gui != null) ? gui : owner.ui.root;
        host.add(popup, owner.ui.mc);
        /* Without these two lines the popup opens behind the main window -
         * adding a child to the root/session widget doesn't raise or focus it. */
        popup.raise();
        host.setfocus(popup);
    }

    private GroupLabelPopup(GroupSelectorCompanion owner, GroupLabels.Scope scope, String labelOwner, int group) {
        super(UI.scale(new Coord(220, 90)), title(scope, group));
        this.owner = owner; this.scope = scope; this.labelOwner = labelOwner; this.group = group;

        int margin = UI.scale(10);
        int y = margin;
        add(new Label("Label for this group:"), new Coord(margin, y));
        y += UI.scale(18);

        entry = add(new TextEntry(UI.scale(200), GroupLabels.get(scope, labelOwner, group)) {
            {dshow = true;}
            public void activate(String text) {
                /* Bare destroy() here would resolve to Widget's own
                 * destroy() on this anonymous TextEntry, not the outer
                 * popup's override - the window would never actually close.
                 * Must qualify. */
                GroupLabelPopup.this.destroy();
            }
        }, new Coord(margin, y));
        y += entry.sz.y + UI.scale(8);

        add(new Button(UI.scale(80), "Save") {
            public void click() {
                super.click();
                GroupLabelPopup.this.destroy();
            }
        }, new Coord(margin, y));

        pack();
    }

    private static String title(GroupLabels.Scope scope, int group) {
        return(((scope == GroupLabels.Scope.VILLAGE) ? "Village group " : "Kin group ") + group);
    }

    public void destroy() {
        if(!saved) {
            saved = true;
            GroupLabels.set(scope, labelOwner, group, entry.text());
        }
        owner.clearLabelPopup(this);
        super.destroy();
    }

    public void wdgmsg(Widget sender, String msg, Object... args) {
        if(msg.equals("close"))
            destroy();
        else
            super.wdgmsg(sender, msg, args);
    }
}
