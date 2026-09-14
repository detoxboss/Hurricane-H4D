package haven.automated;

import haven.ChatUI;
import haven.Client;
import haven.GameUI;
import haven.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class YapperBot implements Runnable {
    private static final String PHRASES_FILE = "Yapper_Bot_Phrases.txt";
    private static final double DEFAULT_INTERVAL_SECONDS = 1.0;
    private static final double MIN_INTERVAL_SECONDS = 0.1;

    private final GameUI gui;
    public boolean stop = false;

    public YapperBot(GameUI gui) {
        this.gui = gui;
    }

    @Override
    public void run() {
        List<String> phrases = loadPhrases();
        if (phrases.isEmpty()) {
            gui.error("Yapper Bot: No phrases found in \"" + PHRASES_FILE + "\".");
            return;
        }
        while (!stop) {
            speak(phrases.get(ThreadLocalRandom.current().nextInt(phrases.size())));
            try {
                Thread.sleep(intervalMillis());
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /* ND: Read fresh every cycle so the "Yapper Bot Interval" setting in
     * Options -> Advanced Settings -> Chat Settings takes effect immediately,
     * without needing to toggle the bot off and back on. Clamped to a small
     * positive floor since Thread.sleep() rejects <= 0 and a 0/negative
     * interval would otherwise flood the chat as fast as the network allows. */
    private long intervalMillis() {
        double seconds = Math.max(MIN_INTERVAL_SECONDS, Utils.getprefd("yapperBotInterval", DEFAULT_INTERVAL_SECONDS));
        return Math.round(seconds * 1000);
    }

    /* ND: Sends straight through the currently selected chat channel's own
     * network message, the same call EntryChannel.send() makes when the
     * player presses Enter. This never touches keyboard focus, the text
     * entry widget, or the mouse, so it can't interrupt whatever the player
     * is doing (moving, fighting, clicking) at the moment it fires. */
    private void speak(String line) {
        ChatUI chat = gui.chat;
        if (chat == null)
            return;
        ChatUI.Channel sel = chat.sel;
        if (sel instanceof ChatUI.EntryChannel)
            ((ChatUI.EntryChannel) sel).send(line);
    }

    private List<String> loadPhrases() {
        List<String> lines = new ArrayList<>();
        File file = new File(Client.gameDir + PHRASES_FILE);
        try (BufferedReader in = new BufferedReader(new FileReader(file))) {
            String ln;
            while ((ln = in.readLine()) != null) {
                ln = ln.trim();
                if (!ln.isEmpty())
                    lines.add(ln);
            }
        } catch (Exception e) {
            gui.error("Yapper Bot: Could not read \"" + PHRASES_FILE + "\".");
        }
        return lines;
    }

    public void stop() {
        stop = true;
    }
}
