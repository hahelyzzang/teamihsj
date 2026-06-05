package net.sojeong.rescuecraft.animal;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-animal state for a rescued conversational companion (cow, chicken,
 * rabbit, horse, ...). One instance per "adopted" entity, identified by its
 * Minecraft entity UUID. State is held in memory for the prototype.
 *
 * The pig (Bori) intentionally keeps its own dedicated state class so the
 * finished pig flow is untouched.
 */
public class AnimalCompanion {

    private static final Map<UUID, AnimalCompanion> COMPANIONS = new ConcurrentHashMap<>();

    /** The most recently adopted animal. Convenience for the single-animal-at-a-time flow. */
    private static volatile UUID activeUuid = null;

    private final UUID animalUuid;
    private final AnimalSpecies species;
    private final String name;

    private TrustState trust = TrustState.SCARED;
    private boolean firstEncounterDone = false;

    /** How many of the required item the player has handed over so far. */
    private int itemsGiven = 0;
    /** Total the herd needs (herd size * itemsPerAnimal). 0 until first counted. */
    private int herdNeed = 0;
    /** Number of same-species animals counted as the herd (including this one). */
    private int herdSize = 0;
    /** Whether we have already taught the cultivation tip. */
    private boolean taughtCultivation = false;

    private String lastEnglish = "";
    private String lastKorean = "";

    private AnimalCompanion(UUID animalUuid, AnimalSpecies species, String name) {
        this.animalUuid = animalUuid;
        this.species = species;
        this.name = name;
    }

    public static AnimalCompanion getOrCreate(UUID animalUuid, AnimalSpecies species, String name) {
        AnimalCompanion companion =
                COMPANIONS.computeIfAbsent(animalUuid, uuid -> new AnimalCompanion(uuid, species, name));
        activeUuid = animalUuid;
        return companion;
    }

    public static AnimalCompanion get(UUID animalUuid) {
        return COMPANIONS.get(animalUuid);
    }

    public static AnimalCompanion getActive() {
        UUID uuid = activeUuid;
        return uuid == null ? null : COMPANIONS.get(uuid);
    }

    public UUID getAnimalUuid() {
        return animalUuid;
    }

    public AnimalSpecies getSpecies() {
        return species;
    }

    public String getName() {
        return name;
    }

    public TrustState getTrust() {
        return trust;
    }

    public boolean isFirstEncounterDone() {
        return firstEncounterDone;
    }

    public void markFirstEncounterDone() {
        this.firstEncounterDone = true;
    }

    public int getItemsGiven() {
        return itemsGiven;
    }

    public int getHerdNeed() {
        return herdNeed;
    }

    public int getHerdSize() {
        return herdSize;
    }

    public boolean isTaughtCultivation() {
        return taughtCultivation;
    }

    public void markTaughtCultivation() {
        this.taughtCultivation = true;
    }

    /** True once the player has handed over enough items for the whole herd. */
    public boolean isHerdSatisfied() {
        return herdNeed > 0 && itemsGiven >= herdNeed;
    }

    public int getItemsRemaining() {
        return Math.max(0, herdNeed - itemsGiven);
    }

    /**
     * Lock in the herd need the first time we are able to count it. Called when
     * the player hands over the first item. {@code countedHerdSize} should be the
     * number of same-species animals nearby (at least 1).
     */
    public void establishHerdNeed(int countedHerdSize) {
        if (herdNeed > 0) {
            return;
        }
        this.herdSize = Math.max(1, countedHerdSize);
        this.herdNeed = this.herdSize * species.itemsPerAnimal();
    }

    public String getLastEnglish() {
        return lastEnglish;
    }

    public String getLastKorean() {
        return lastKorean;
    }

    public void rememberLastLine(String english, String korean) {
        this.lastEnglish = english == null ? "" : english;
        this.lastKorean = korean == null ? "" : korean;
    }

    /**
     * Record that the player handed over one required item. The first item nudges
     * trust SCARED -> TRUSTING; reaching the full herd need pushes trust to
     * COMPANION. Returns the trust state BEFORE this call so the caller can report
     * a transition.
     */
    public TrustState recordItemGiven() {
        TrustState before = trust;
        itemsGiven++;
        if (trust == TrustState.SCARED) {
            trust = TrustState.TRUSTING;
        }
        if (isHerdSatisfied()) {
            trust = TrustState.COMPANION;
        }
        return before;
    }
}
