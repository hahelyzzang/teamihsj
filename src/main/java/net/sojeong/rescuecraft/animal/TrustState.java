package net.sojeong.rescuecraft.animal;

/**
 * Generic trust progression shared by every rescued animal companion.
 * Mirrors the pig prototype's three-step arc so the dialogue can change tone
 * as the player helps the animal.
 */
public enum TrustState {
    SCARED,
    TRUSTING,
    COMPANION;

    public TrustState advance() {
        return switch (this) {
            case SCARED -> TRUSTING;
            case TRUSTING -> COMPANION;
            case COMPANION -> COMPANION;
        };
    }

    public String describeForPrompt() {
        return switch (this) {
            case SCARED -> "scared and trembling; the animal just met the player and does not trust them yet";
            case TRUSTING -> "starting to trust the player after being given food, but still cautious";
            case COMPANION -> "feels safe with the player, calm and grateful, and treats them as a friend";
        };
    }
}
