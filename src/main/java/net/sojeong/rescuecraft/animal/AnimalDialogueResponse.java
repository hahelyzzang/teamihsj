package net.sojeong.rescuecraft.animal;

/**
 * Structured response returned by the local LLM for one animal dialogue turn.
 * {@code english} is what the animal says; {@code korean} is a Korean restatement
 * of the same line. Both should stay short and in character.
 */
public record AnimalDialogueResponse(String english, String korean) {
    public static AnimalDialogueResponse fallback() {
        return new AnimalDialogueResponse(
                "*The animal looks at you nervously and stays quiet.*",
                "*동물이 불안한 듯 당신을 바라보며 조용히 있는다.*"
        );
    }

    public boolean isUsable() {
        return english != null && !english.isBlank();
    }
}
