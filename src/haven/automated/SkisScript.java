package haven.automated;

import haven.*;

import static haven.OCache.posres;

public class SkisScript implements Runnable {

    private GameUI gui;
    private static final int TIMEOUT = 4000;

    private static final int HAND_DELAY = 8;

    // How long to wait for a mount attempt to be confirmed (player.imOnSkis) before retrying,
    // and how many times to retry. Poll-based instead of a fixed blind delay so it holds up
    // under high ping instead of requiring the player to spam the script button.
    private static final int MOUNT_ACK_TIMEOUT = 1500;
    private static final int MOUNT_ATTEMPTS = 3;


    public SkisScript(GameUI gui) {
        this.gui = gui;
    }

    public void run() {
        Gob player = gui.map.player();
        if (player == null)
            return; //player is null, possibly taking a road, don't bother trying to do all of the below.

        //Get Equipment
        Equipory eq = gui.getequipory();
        if (eq == null)
            return;

        if (player.imOnSkis) { // ND: If you're on skis already, try to pick them up
            //find closest skis and pick it up
            Gob gobSkis = null;
            synchronized (gui.map.glob.oc) {
                for (Gob gob : gui.map.glob.oc) {
                    try {
                        Resource res = gob.getres();
                        if (res != null && (res.name.startsWith("gfx/terobjs/vehicle/skis-wilderness"))) {
                            Coord2d plc = gui.map.player().rc;
                            if ((gobSkis == null || gob.rc.dist(plc) < gobSkis.rc.dist(plc)))
                                gobSkis = gob;
                        }
                    } catch (Loading l) {
                    }
                }
            }

            if (gobSkis == null)
                return;
            if (gobSkis.rc.dist(gui.map.player().rc) < 11*5) {
                    FlowerMenu.setNextSelection("Pick up");
                    gui.map.wdgmsg("click", Coord.z, gobSkis.rc.floor(posres), 3, 0, 0, (int) gobSkis.id, gobSkis.rc.floor(posres), 0, -1);
            }
            if (eq.slots[21] == null) { // ND: Don't need to do anything here, cause "Pick up" already puts it on your cape slot
                return;
            } else {
                int timeout = 0;
                while (gui.hand.isEmpty() || gui.vhand == null) {
                    timeout += HAND_DELAY;
                    if (timeout >= TIMEOUT) {
                        gui.error("Skis Script: Timed out trying to Pick up Skis");
                        return;
                    }
                    try {
                        Thread.sleep(HAND_DELAY);
                    } catch (InterruptedException ex) {
                        return;
                    }
                }

                //Check if there is a 2x3 space in inventory
                Coord freecoord = gui.maininv.isRoom(2,3);
                if (freecoord != null) {
                    gui.maininv.wdgmsg("drop", freecoord);
                } else {
                    gui.error("Skis Script: No free space in Inventory for Skis.");
                }
            }

        } else { // ND: If you're NOT on skis already, run this part instead
            // Find WItem Skis by going through equipment list
            WItem skis = null;

            skis = gui.maininv.getItemPartial("Wilderness Skis");

            for (WItem wi : eq.slots) {
                try {
                    if (wi.item.getname().equals("Wilderness Skis")) {
                        skis = wi;
                    }
                } catch (NullPointerException ex) {
                    //System.out.println("nothing equipped in this slot");
                }
            }


            if (skis != null) { // ND: If I have any Skis in my inventory at all, do the following
                //Get gui item and drop skis
                GItem skisItem = skis.item;
                skisItem.wdgmsg("drop", new Coord(skisItem.sz.x / 2, skisItem.sz.y / 2));
            }

            // ND: give the dropped skis a moment to land as a mountable, non-moving gob instead of
            // a single blind sleep - poll for it so this holds up under high ping.
            Gob gobSkis = findMountableSkisGob();
            int findTimeout = 0;
            while (gobSkis == null && findTimeout < TIMEOUT) {
                try {
                    Thread.sleep(HAND_DELAY);
                } catch (InterruptedException ex) {
                    return;
                }
                findTimeout += HAND_DELAY;
                gobSkis = findMountableSkisGob();
            }

            if (gobSkis == null) {
                if (skis == null)
                    gui.error("Skis Script: No Skis found in Inventory and no mountable Skis found in close proximity.");
                else
                    gui.error("Skis Script: Timed out waiting for mountable Skis.");
                return;
            }
            if (gobSkis.rc.dist(gui.map.player().rc) >= 11 * 6) {
                gui.error("Skis Script: Mountable Skis found, but too far away.");
                return;
            }

            // ND: retry the mount click only if it isn't confirmed within MOUNT_ACK_TIMEOUT, instead
            // of firing 3 blind clicks on a fixed schedule regardless of whether one already landed.
            boolean mounted = false;
            for (int attempt = 0; attempt < MOUNT_ATTEMPTS && !mounted; attempt++) {
                FlowerMenu.setNextSelection("Ski off");
                gui.map.wdgmsg("click", Coord.z, gobSkis.rc.floor(posres), 3, 0, 0, (int) gobSkis.id, gobSkis.rc.floor(posres), 0, -1);

                int waited = 0;
                while (waited < MOUNT_ACK_TIMEOUT) {
                    try {
                        Thread.sleep(HAND_DELAY);
                    } catch (InterruptedException ex) {
                        return;
                    }
                    waited += HAND_DELAY;
                    Gob player2 = gui.map.player();
                    if (player2 != null && player2.imOnSkis) {
                        mounted = true;
                        break;
                    }
                }
            }

            if (!mounted) {
                gui.error("Skis Script: Timed out trying to mount Skis.");
                return;
            }
        }
    }

    private Gob findMountableSkisGob() {
        Gob gobSkis = null;
        synchronized (gui.map.glob.oc) {
            for (Gob gob : gui.map.glob.oc) {
                try {
                    Resource res = gob.getres();
                    if (res != null && (res.name.startsWith("gfx/terobjs/vehicle/skis-wilderness"))) {
                        Coord2d plc = gui.map.player().rc;
                        if ((gobSkis == null || gob.rc.dist(plc) < gobSkis.rc.dist(plc)))
                            gobSkis = gob;
                    }
                } catch (Loading l) {
                }
            }
        }
        if (gobSkis == null)
            return null;

        Integer peekrbuf = null;
        for (GAttrib g : gobSkis.attr.values()) {
            if (g instanceof ResDrawable) {
                ResDrawable resDrawable = gobSkis.getattr(ResDrawable.class);
                peekrbuf = resDrawable.sdt.checkrbuf(0);
            }
        }
        return (peekrbuf != null && peekrbuf == 0) ? gobSkis : null; // ND: peekrbuf 0 means not-moving skis. Not necessarily empty.
    }

    // ND: Speed-4 auto-restore while on skis is handled in GameUI.skiSpeedTick() (runs every tick,
    // regardless of how the player got on skis - not just after this script's own mount).
}
