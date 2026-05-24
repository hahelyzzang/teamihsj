package net.sojeong.rescuecraft.pig;

/**
 * Structured response returned by the local LLM for a pig dialogue turn.
 * `english` is what the pig says; `korean` is the Korean restatement of the
 * same line. Both should be short and stay in character.
 */
public record PigDialogueResponse(String english, String korean) {
    public static PigDialogueResponse fallback() {
        return new PigDialogueResponse(
                "*The pig trembles and squeaks softly.*",
                "*돼지가 떨면서 작게 꿀꿀거린다.*"
        );
    }

    public boolean isUsable() {
        return english != null && !english.isBlank();
    }
}
