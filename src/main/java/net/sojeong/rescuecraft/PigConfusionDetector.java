package net.sojeong.rescuecraft.pig;

import java.util.List;
import java.util.Locale;

/**
 * Detects when the player is signalling that they didn't understand the pig's
 * previous line. When this returns true the caller should re-send the last
 * Korean translation instead of generating a new LLM response.
 *
 * For ADVANCED players we additionally treat explicit help requests
 * ("can you help me", "한국어로 부탁해" etc.) as confusion, since the prompt
 * rules say Korean only appears on demand at that level.
 */
public final class PigConfusionDetector {

    private static final List<String> ENGLISH_CONFUSION = List.of(
            "i don't understand",
            "i do not understand",
            "i dont understand",
            "what?",
            "what did you say",
            "say that again",
            "can you say that again",
            "say it again",
            "repeat please",
            "what do you mean",
            "huh?",
            "i'm confused",
            "im confused"
    );

    private static final List<String> KOREAN_CONFUSION = List.of(
            "한국어로",
            "한국말로",
            "무슨 뜻",
            "무슨뜻",
            "이해 못",
            "이해못",
            "모르겠어",
            "다시 말",
            "다시말",
            "뭐라고"
    );

    private static final List<String> EXPLICIT_HELP_REQUEST = List.of(
            "translate",
            "in korean",
            "to korean",
            "한국어로 말해",
            "한국말로 말해",
            "번역"
    );

    private PigConfusionDetector() {}

    public static boolean isConfusion(String playerMessage) {
        if (playerMessage == null) return false;
        String normalized = playerMessage.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty()) return false;

        for (String p : ENGLISH_CONFUSION) {
            if (normalized.contains(p)) return true;
        }
        // Korean phrases are case-insensitive already; compare against the original
        // (lowercase shouldn't matter for Hangul).
        for (String p : KOREAN_CONFUSION) {
            if (playerMessage.contains(p)) return true;
        }
        return false;
    }

    public static boolean isExplicitHelpRequest(String playerMessage) {
        if (playerMessage == null) return false;
        String normalized = playerMessage.toLowerCase(Locale.ROOT);
        for (String p : EXPLICIT_HELP_REQUEST) {
            if (normalized.contains(p) || playerMessage.contains(p)) return true;
        }
        return false;
    }

    /**
     * Decides whether the Korean line should be SHOWN to the player on this turn,
     * given the player's level band and the message they just sent.
     *
     * BEGINNER       -> always.
     * INTERMEDIATE   -> only when confused.
     * ADVANCED       -> only on confusion or explicit help request.
     */
    public static boolean shouldShowKorean(String band, String playerMessage) {
        return switch (band) {
            case "BEGINNER" -> true;
            case "INTERMEDIATE" -> isConfusion(playerMessage);
            case "ADVANCED" -> isConfusion(playerMessage) || isExplicitHelpRequest(playerMessage);
            default -> true;
        };
    }
}
