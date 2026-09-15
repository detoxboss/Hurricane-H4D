package haven.groups;

import haven.BuddyWnd;
import haven.MapWnd;
import haven.Polity;
import haven.Widget;

import java.util.WeakHashMap;

/**
 * Classifies a BuddyWnd.GroupSelector by its ancestor/parent identity so the
 * 40-group extension can target the right windows without touching the
 * shared selector widget itself. See doc/H4D-features/group-permission-extension.md
 * for how each class name below was captured (live ant-run console output,
 * not guessed) and what's still unverified.
 */
public class GroupSelectorClassifier {
    /** Village top-level selector's immediate parent AND the class name
     *  Polity.getClass() resolves to for a Village - confirmed identical,
     *  captured live. */
    private static final String CLASS_VILLAGE = "haven.res.ui.vlg.Village";
    /** Village per-member selector's immediate parent. The "ers" (not "res")
     *  is the resource's own real package name, confirmed from raw
     *  Class.getName() output - not a typo in this code, do not "fix". Not
     *  actually needed for classification below (the Polity ancestor check
     *  already covers this selector), kept only as confirmed-evidence
     *  documentation. */
    private static final String CLASS_VILLAGE_MEMBER = "haven.ers.ui.vmemb.VillageMember";
    /** Personal claim ("Stake") window - confirmed live. Capped at 8: this
     *  fork has no vendored source for it either, so the 8-row cap is
     *  inherited caution from the Nurgling2 port (vendor-verified there via
     *  a third-party reference client), not independently re-verified here. */
    private static final String CLASS_LANDWINDOW = "haven.res.ui.land.Landwindow";
    /** Field Cairn's permission window - confirmed live (this is its real
     *  resource/class name; "Field Cairn" is just the window's title). */
    private static final String CLASS_FIELDCAIRN = "haven.res.ui.sar.Administer";

    private static final WeakHashMap<BuddyWnd.GroupSelector, GroupSelectorCompanion> companions = new WeakHashMap<>();

    private static final class Target {
        final GroupLabels.Scope scope;
        final String owner;
        final int lo, hi;
        final boolean warnAboveNquick;
        Target(GroupLabels.Scope scope, String owner, int lo, int hi, boolean warnAboveNquick) {
            this.scope = scope; this.owner = owner; this.lo = lo; this.hi = hi;
            this.warnAboveNquick = warnAboveNquick;
        }
    }

    public static void attached(BuddyWnd.GroupSelector sel) {
        synchronized(companions) {
            if(companions.containsKey(sel))
                return;
        }
        Target t = classify(sel);
        if(t == null)
            return;
        GroupSelectorCompanion companion = new GroupSelectorCompanion(sel, t.scope, t.owner, t.lo, t.hi, t.warnAboveNquick);
        synchronized(companions) {
            companions.put(sel, companion);
        }
        sel.hide();
        sel.parent.add(companion, sel.c);
    }

    public static void detached(BuddyWnd.GroupSelector sel) {
        GroupSelectorCompanion companion;
        synchronized(companions) {
            companion = companions.remove(sel);
        }
        if(companion != null)
            companion.destroy();
    }

    private static String chrid(Widget w) {
        haven.GameUI gui = w.getparent(haven.GameUI.class);
        return((gui != null) ? gui.chrid : "");
    }

    private static Target classify(BuddyWnd.GroupSelector sel) {
        /* Map-marker colour picker (MapWnd.colsel) - unrelated use of the
         * same widget class, must never be touched. */
        if(sel.getparent(MapWnd.class) != null)
            return(null);

        if(sel.getparent(BuddyWnd.BuddyInfo.class) != null)
            return(new Target(GroupLabels.Scope.KIN, chrid(sel), 0, BuddyWnd.ncolors - 1, false));

        Polity polity = sel.getparent(Polity.class);
        if(polity != null) {
            if(CLASS_VILLAGE.equals(polity.getClass().getName())) {
                /* Root-caused (see doc/H4D-features/group-permission-extension.md,
                 * "Bug B"): Village's compiled VMember reads BuddyWnd.gc[group]
                 * directly with no bounds check of its own - the crash was
                 * never Village's cap, it was BuddyWnd.gc[] itself only being
                 * 8 long. Fixed by sizing gc[] safely (255) in BuddyWnd.java;
                 * full range restored. Warning restored too - a group >= 8
                 * is still real shared state rendered by every viewing
                 * client, including unmodified ones with their own smaller
                 * array bound, which this fork's own fix can't do anything
                 * about for them. */
                return(new Target(GroupLabels.Scope.VILLAGE, polity.name, 0, BuddyWnd.ncolors - 1, true));
            }
            /* Any other Polity subtype: none observed live, no evidence to
             * extend it safely - leave alone. */
            return(null);
        }

        Widget parent = sel.parent;
        if(parent != null) {
            String parentClass = parent.getClass().getName();
            if(CLASS_LANDWINDOW.equals(parentClass))
                return(new Target(GroupLabels.Scope.KIN, chrid(sel), 0, BuddyWnd.nquick - 1, false));
            if(CLASS_FIELDCAIRN.equals(parentClass)) {
                /* Range unverified past 8 - see doc/H4D-features note. Ship
                 * it, warn, get it live-tested. */
                return(new Target(GroupLabels.Scope.KIN, chrid(sel), 0, BuddyWnd.ncolors - 1, true));
            }
        }

        /* Every selector site actually observed in a live session is
         * enumerated above. Unlike the Nurgling2 port (which never got this
         * level of certainty and kept a wide-open "unknown resource" bucket),
         * anything not matching one of the confirmed cases here is left
         * alone rather than guessed into the wide range. */
        return(null);
    }
}
