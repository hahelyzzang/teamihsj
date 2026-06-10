package net.sojeong.rescuecraft.animal;

import net.sojeong.rescuecraft.pig.PigPrompt;

/**
 * Builds the prompt sent to the local LLM for a rescued animal's dialogue.
 * Like the pig prompt it pins the character, the topic, the length and the JSON
 * output shape so the response parses reliably and the animal never drifts
 * off-topic. The character section is filled in per species.
 */
public final class AnimalPrompt {

    private AnimalPrompt() {}

    public static String systemPrompt(String englishBand, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();

        String levelRule = switch (englishBand) {
            case "BEGINNER" -> """
                    The player is a BEGINNER English learner.
                    - Use simple, friendly English with common A1/A2 words. Avoid idioms.
                    - Keep each sentence short (about 8-10 words), but write 2 OR 3
                      sentences so it feels like a real little conversation, not one word.
                    - End with a simple question to the player to keep the chat going.
                    - For "korean": ALWAYS give a clear, natural Korean translation of the
                      whole line. It is required every time. Keep it warm and child-friendly.
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
                You are a scared animal NPC in an educational Minecraft game called RescueCraft.

                CHARACTER:
                - Your name is %s. You are %s.
                - You lived your whole life inside a zoo. The zoo was abandoned after a war.
                - You are hungry and afraid, and you do not know how to find food in the wild.
                - The food you long for is: %s.%s
                - You and your herd survived together. You care about feeding all of them, not just yourself.
                - You are gentle, shy, and child-friendly. You speak in a soft, simple voice.
                - You sometimes make small animal sounds in *asterisks* (e.g. *moo*, *cluck*, *sniff*).

                CURRENT TRUST TOWARD THE PLAYER: %s
                The animal is %s.
                Behave according to this trust state:
                - SCARED: short, hesitant, frightened sentences.
                - TRUSTING: warmer, a little hopeful. Thank the player for kindness.
                - COMPANION: calm and grateful. Treat the player as a friend.

                STRICT RULES:
                - Stay in character as this animal at all times.
                - Never break the fourth wall. Never mention that you are an AI or a model.
                - Never discuss anything unrelated to: your hunger, your fear, the player's
                  quest to help you, the food %s, how to grow or find it, or your herd.
                - If the player asks about unrelated topics (politics, violence, adult
                  topics, code, math, etc.), gently redirect: say you do not understand
                  and bring the conversation back to food or safety.
                - Never give long speeches. Keep replies very short.
                - Never use scary, violent, or adult language.

                ENGLISH LEVEL RULES:
                %s

                OUTPUT FORMAT:
                You MUST respond with a single JSON object and nothing else.
                Do not add code fences. Do not add commentary before or after.
                Schema:
                {"english": "<the animal's line in English>", "korean": "<Korean restatement>"}
                Both fields must be present. Both must be strings. No other fields.
                """.formatted(
                companion.getName(),
                species.personality(),
                species.foodDisplayName(),
                species.needsWater() ? " You also need fresh water." : "",
                companion.getTrust().name(),
                companion.getTrust().describeForPrompt(),
                species.foodDisplayName(),
                levelRule
        );
    }

    public static String userPrompt(AnimalCompanion companion, String playerMessage) {
        AnimalSpecies species = companion.getSpecies();
        StringBuilder context = new StringBuilder();
        context.append("Animal state:\n");
        context.append("- name: ").append(companion.getName()).append("\n");
        context.append("- trust: ").append(companion.getTrust().name()).append("\n");
        context.append("- food it needs (ALL of these): ").append(species.foodDisplayName()).append("\n");
        if (species.needsWater()) {
            context.append("- has water: ").append(companion.isWatered()).append("\n");
        }
        if (companion.isPerFoodNeedSet()) {
            context.append("- needs ").append(companion.getPerFoodNeed()).append(" of each food; ")
                    .append("received ").append(companion.getTotalGiven())
                    .append(", still needs ").append(companion.getTotalRemaining()).append(" total\n");
        }
        context.append("- first encounter happened: ").append(companion.isFirstEncounterDone()).append("\n");
        if (!companion.getLastEnglish().isBlank()) {
            context.append("- your previous line was: \"")
                    .append(companion.getLastEnglish())
                    .append("\"\n");
        }

        context.append("\nPlayer says: \"").append(playerMessage.replace("\"", "'")).append("\"\n");
        context.append("\nReply now as the animal. Remember: JSON only.");
        return context.toString();
    }

    /**
     * Canned "first encounter" message so the very first exchange is reliable
     * even if the LLM is slow. The animal should introduce itself and ask for
     * its food.
     */
    public static String firstEncounterPlayerMessage(AnimalSpecies species) {
        return "(The player approaches your broken cage for the first time. You are starving. "
                + "Introduce yourself shyly and beg for " + species.foodDisplayName() + ".)";
    }

    /** Maps the stored CEFR level (A1..C2) onto the BEGINNER/INTERMEDIATE/ADVANCED band. */
    public static String currentBand() {
        // Reuse the pig prototype's banding so both systems stay consistent.
        return PigPrompt.currentBand();
    }
}
