package net.sojeong.rescuecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the rescue-related chat history (both player input and animal/system responses).
 * Used by RescueCraftHud to show the recent dialogue box and the full history overlay.
 */
public final class RescueCraftChat {

    public record ChatLine(String text, boolean isPlayer) {}

    private static final int MAX_HISTORY = 200;
    private static final List<ChatLine> history = new ArrayList<>();

    private RescueCraftChat() {}

    public static void addPlayer(String message) {
        history.add(new ChatLine(message, true));
        if (history.size() > MAX_HISTORY) history.remove(0);
    }

    public static void addSystem(String message) {
        history.add(new ChatLine(message, false));
        if (history.size() > MAX_HISTORY) history.remove(0);
    }

    /** Last N lines for the mini box. */
    public static List<ChatLine> recent(int n) {
        int from = Math.max(0, history.size() - n);
        return history.subList(from, history.size());
    }

    public static List<ChatLine> all() {
        return List.copyOf(history);
    }

    /** Returns true if this system message is rescue-related and should be captured. */
    public static boolean isRescueMessage(String text) {
        if (text == null) return false;
        return text.startsWith("<")           // animal dialogue: <Bori>, <Daisy> etc.
                || text.startsWith("[RescueCraft]")
                || text.startsWith("[Quest]")
                || text.startsWith("[안내]")
                || text.startsWith("[RescueCraft]");
    }
}
