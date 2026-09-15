package haven.groups;

import haven.Utils;
import org.json.JSONObject;

/**
 * Persists free-text labels for permission-group numbers, keyed by
 * (scope, owner, group). Uses Utils.getpref/setpref (this fork's only
 * gameplay-preference store - there is no NConfig-style structured config
 * here) with a single JSON blob per scope, since Utils has no map-shaped
 * pref accessor.
 *
 * Kin labels are keyed by the owning character's chrid (GameUI.chrid) -
 * confirmed as this fork's own existing per-character scoping convention
 * (already used for "mapfile/"+chrid and "actionBar*_"+chrid in GameUI.java),
 * not invented for this feature. Village labels are keyed by the village's
 * own name - there's no durable numeric village id exposed to the client
 * here either, so renaming a village orphans its labels and two
 * identically-named villages collide; accepted as a low-stakes tradeoff
 * since this is client-side presentation only, same call made in the
 * Nurgling2 port this was ported from.
 */
public class GroupLabels {
    public enum Scope {KIN, VILLAGE}

    private static final Object lock = new Object();

    public static String get(Scope scope, String owner, int group) {
        synchronized(lock) {
            JSONObject byOwner = load(scope).optJSONObject(owner);
            return((byOwner == null) ? "" : byOwner.optString(Integer.toString(group), ""));
        }
    }

    public static void set(Scope scope, String owner, int group, String label) {
        synchronized(lock) {
            JSONObject root = load(scope);
            JSONObject byOwner = root.optJSONObject(owner);
            String trimmed = (label == null) ? "" : label.trim();
            String id = Integer.toString(group);
            if(trimmed.isEmpty()) {
                if((byOwner == null) || !byOwner.has(id))
                    return;
                byOwner.remove(id);
                if(byOwner.length() == 0)
                    root.remove(owner);
            } else {
                if(trimmed.equals((byOwner == null) ? null : byOwner.optString(id, null)))
                    return;
                if(byOwner == null) {
                    byOwner = new JSONObject();
                    root.put(owner, byOwner);
                }
                byOwner.put(id, trimmed);
            }
            save(scope, root);
        }
    }

    private static String key(Scope scope) {
        return((scope == Scope.VILLAGE) ? "villageGroupLabels" : "kinGroupLabels");
    }

    private static JSONObject load(Scope scope) {
        String raw = Utils.getpref(key(scope), null);
        if(raw == null)
            return(new JSONObject());
        try {
            return(new JSONObject(raw));
        } catch(Exception e) {
            return(new JSONObject());
        }
    }

    private static void save(Scope scope, JSONObject root) {
        Utils.setpref(key(scope), root.toString());
    }
}
