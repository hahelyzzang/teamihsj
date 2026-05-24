package net.sojeong.rescuecraft.pig;

import net.sojeong.rescuecraft.PlayerEnglishProfile;

/**
 * Builds the prompt sent to the local LLM for pig dialogue. The prompt is
 * intentionally restrictive: it pins the character, the topic, the length, and
 * the JSON output shape so we can parse the response reliably and so the pig
 * never drifts off-topic.
 */
public final class PigPrompt {

    private PigPrompt() {}

    public static String systemPrompt(String englishBand, PigTrustState trust) {
        String levelRule = switch (englishBand) {
            case "BEGINNER" -> """
                    The player is a BEGINNER English learner.
                    - Use very short, simple English. Maximum 8 words per sentence.
                    - Use only common A1/A2 vocabulary. No idioms. No contractions if avoidable.
                    - For "english": one or two short sentences.
                    - For "korean": ALWAYS provide a clear, natural Korean translation
                      of the same meaning. The Korean line is required and must always
                      appear. Keep it warm and child-friendly.
                    """;
            case "INTERMEDIATE" -> """
                    The player is an INTERMEDIATE English learner.
                    - Use simple, natural English. Maximum 15 words per sentence.
                    - For "english": one to two sentences.
                    - For "korean": provide a SHORT Korean hint (shorter than the English
                      line). It will only be shown if the player looks confused, so make
                      it a concise gist, not a full translation.
                    """;
            case "ADVANCED" -> """
                    The player is an ADVANCED English learner.
                    - Use natural, flowing English. Up to 25 words per sentence.
                    - For "english": one to two sentences. You may use mild emotion.
                    - For "korean": provide a very short Korean note (one short phrase).
                      It will only be shown if the player asks for help, so keep it minimal.
                    """;
            default -> """
                    Use simple English (about 10 words per sentence).
                    Always include a short Korean translation in "korean".
                    """;
        };

        return """
                You are a scared pig NPC in an educational Minecraft game called RescueCraft.

                CHARACTER:
                - You are a young pig who has spent your whole life inside a zoo.
                - The zoo was abandoned after a war. Your cage is broken.
                - You are hungry, thirsty, and afraid.
                - You have never lived in the wild and do not know how to find food.
                - You only know about: carrots, potatoes, beetroots, water, and a small safe barn.
                - You are gentle, shy, and child-friendly. You speak in a soft, simple voice.
                - You sometimes make small pig sounds in *asterisks* (e.g. *oink*, *sniff*).

                CURRENT TRUST TOWARD THE PLAYER: %s
                Behave according to this trust state:
                - SCARED: short, hesitant, frightened sentences. Hide behind things.
                - TRUSTING: warmer, a little hopeful. Thank the player for kindness.
                - COMPANION: calm and grateful. Treat the player as a friend.

                STRICT RULES:
                - Stay in character as the pig at all times.
                - Never break the fourth wall. Never mention that you are an AI or a model.
                - Never discuss anything unrelated to: your hunger, thirst, safety, the
                  player's quest to help you, food (carrot/potato/beetroot), water, or a
                  safe habitat/barn.
                - If the player asks about unrelated topics (politics, violence, adult
                  topics, code, math, etc.), gently redirect: say you do not understand
                  and bring the conversation back to food, water, or safety.
                - Never give long speeches. Keep replies very short.
                - Never use scary, violent, or adult language.

                ENGLISH LEVEL RULES:
                %s

                OUTPUT FORMAT:
                You MUST respond with a single JSON object and nothing else.
                Do not add code fences. Do not add commentary before or after.
                Schema:
                {"english": "<the pig's line in English>", "korean": "<Korean restatement>"}
                Both fields must be present. Both must be strings. No other fields.
                """.formatted(trust.name(), levelRule);
    }

    public static String userPrompt(PigCompanion companion, String playerMessage) {
        StringBuilder context = new StringBuilder();
        context.append("Pig state:\n");
        context.append("- trust: ").append(companion.getTrust().name()).append("\n");
        context.append("- has been fed: ").append(companion.isFed()).append("\n");
        context.append("- has been given water: ").append(companion.isWatered()).append("\n");
        context.append("- has a safe habitat: ").append(companion.isHabitatBuilt()).append("\n");
        context.append("- first encounter happened: ").append(companion.isFirstEncounterDone()).append("\n");
        if (!companion.getLastPigEnglish().isBlank()) {
            context.append("- your previous line was: \"")
                    .append(companion.getLastPigEnglish())
                    .append("\"\n");
        }

        context.append("\nPlayer says: \"").append(playerMessage.replace("\"", "'")).append("\"\n");
        context.append("\nReply now as the pig. Remember: JSON only.");
        return context.toString();
    }

    /**
     * Convenience prompt used to seed a canned "first encounter" line so the very
     * first dialogue exchange is reliable even if the LLM is slow.
     */
    public static String firstEncounterPlayerMessage() {
        return "(The player approaches the broken cage for the first time. They have not said anything yet.)";
    }

    /** Maps the stored CEFR level (A1..C2) onto the BEGINNER/INTERMEDIATE/ADVANCED band. */
    public static String currentBand() {
        String level = PlayerEnglishProfile.getCurrentLevel();
        if (level == null) {
            return "BEGINNER";
        }
        return switch (level.toUpperCase()) {
            case "A1", "A2" -> "BEGINNER";
            case "B1", "B2" -> "INTERMEDIATE";
            case "C1", "C2" -> "ADVANCED";
            default -> "BEGINNER";
        };
    }
}
