package net.sojeong.rescuecraft.pig;

public enum PigTrustState {
    SCARED,
    TRUSTING,
    COMPANION;

    public PigTrustState advance() {
        return switch (this) {
            case SCARED -> TRUSTING;
            case TRUSTING -> COMPANION;
            case COMPANION -> COMPANION;
        };
    }

    public String describeForPrompt() {
        return switch (this) {
            case SCARED -> "scared, trembling, just met the player, does not yet trust them";
            case TRUSTING -> "starting to trust the player after being fed or given water, still cautious";
            case COMPANION -> "feels safe with the player, calm and grateful, considers them a friend";
        };
    }
}
