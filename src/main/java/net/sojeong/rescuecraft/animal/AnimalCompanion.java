package net.sojeong.rescuecraft.animal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-animal state for a rescued conversational companion (cow, chicken,
 * rabbit, horse, axolotl, turtle, cat). One instance per "adopted" entity,
 * identified by its Minecraft entity UUID. State is held in memory for the
 * prototype.
 *
 * Land animals need water (a single shared source for the herd) plus enough
 * food for the whole herd; aquatic animals and the cat only need fish.
 *
 * The pig (Bori) intentionally keeps its own dedicated state class so the
 * finished pig flow is untouched.
 */
public class AnimalCompanion {

    private static final Map<UUID, AnimalCompanion> COMPANIONS = new ConcurrentHashMap<>();

    /** The most recently adopted animal. Used as a fallback target. */
    private static volatile UUID activeUuid = null;

    /** Length of the recovery / care period before the herd can be freed (3 Minecraft days). */
    public static final long CARE_TICKS = 3L * 24000L;

    private final UUID animalUuid;
    private final AnimalSpecies species;
    private final String name;

    private TrustState trust = TrustState.SCARED;
    private boolean firstEncounterDone = false;

    /** How many of EACH accepted food the player has handed over (per item type). */
    private final Map<Item, Integer> given = new HashMap<>();
    /** How many of EACH accepted food the herd needs (herd size * itemsPerAnimal). 0 until counted. */
    private int perFoodNeed = 0;
    /** Number of same-species animals counted as the herd (including this one). */
    private int herdSize = 0;
    /** Land animals: whether the player has provided the herd's water. */
    private boolean watered = false;
    /** Whether we have already taught the cultivation/fishing tip. */
    private boolean taughtTip = false;

    /** Game time (total ticks) when food + water were first fully provided; -1 until then. */
    private long satisfiedAtGameTime = -1L;
    /** Whether the herd has been told they are free (story finale, delivered once). */
    private boolean liberated = false;

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

    public static Collection<AnimalCompanion> all() {
        return COMPANIONS.values();
    }

    /**
     * Returns the single companion already adopted for this species, or null if
     * none. Used to keep ONE representative per species (e.g. only one Bori).
     */
    public static AnimalCompanion getBySpecies(AnimalSpecies species) {
        for (AnimalCompanion companion : COMPANIONS.values()) {
            if (companion.species == species) {
                return companion;
            }
        }
        return null;
    }

    public static AnimalCompanion getActive() {
        UUID uuid = activeUuid;
        return uuid == null ? null : COMPANIONS.get(uuid);
    }

    /**
     * Finds the adopted companion whose entity is nearest to the player within
     * {@code range} blocks, so right-clicking / talking next to a specific animal
     * targets that one even when several have been rescued. Returns null if none
     * are in range.
     */
    public static AnimalCompanion findNearestAdopted(ServerPlayer player, ServerLevel level, double range) {
        AnimalCompanion best = null;
        double bestDistSqr = range * range;
        for (AnimalCompanion companion : COMPANIONS.values()) {
            Entity entity = level.getEntity(companion.animalUuid);
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            double distSqr = entity.distanceToSqr(player);
            if (distSqr <= bestDistSqr) {
                bestDistSqr = distSqr;
                best = companion;
            }
        }
        return best;
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

    public int getHerdSize() {
        return herdSize;
    }

    /** How many of EACH accepted food the herd needs (0 until counted). */
    public int getPerFoodNeed() {
        return perFoodNeed;
    }

    public boolean isPerFoodNeedSet() {
        return perFoodNeed > 0;
    }

    /** How many of the given food item have been handed over so far. */
    public int getGiven(Item food) {
        return given.getOrDefault(food, 0);
    }

    /** How many more of the given food item the herd still needs. */
    public int getRemaining(Item food) {
        return Math.max(0, perFoodNeed - getGiven(food));
    }

    public int getTotalGiven() {
        int sum = 0;
        for (int v : given.values()) sum += v;
        return sum;
    }

    /** Total across every accepted food (perFoodNeed * number of food types). */
    public int getTotalNeed() {
        return perFoodNeed * species.acceptedFoods().size();
    }

    public int getTotalRemaining() {
        int sum = 0;
        for (Item food : species.acceptedFoods()) {
            sum += getRemaining(food);
        }
        return sum;
    }

    public boolean isWatered() {
        return watered;
    }

    public boolean isTaughtTip() {
        return taughtTip;
    }

    public void markTaughtTip() {
        this.taughtTip = true;
    }

    /** True once EVERY accepted food has reached its per-food need (AND, not OR). */
    public boolean isFoodComplete() {
        if (perFoodNeed <= 0) {
            return false;
        }
        for (Item food : species.acceptedFoods()) {
            if (getGiven(food) < perFoodNeed) {
                return false;
            }
        }
        return true;
    }

    /** True once every food is complete AND (for land animals) water is provided. */
    public boolean isHerdSatisfied() {
        boolean waterOk = !species.needsWater() || watered;
        return isFoodComplete() && waterOk;
    }

    public boolean stillNeedsFood() {
        return !isFoodComplete();
    }

    public boolean stillNeedsWater() {
        return species.needsWater() && !watered;
    }

    // ---- recovery / liberation (3-day care) ----

    /** Records the moment food + water were first fully provided (recovery begins). */
    public void markSatisfied(long gameTime) {
        if (satisfiedAtGameTime < 0) {
            satisfiedAtGameTime = gameTime;
        }
    }

    public boolean isSatisfiedRecorded() {
        return satisfiedAtGameTime >= 0;
    }

    /** Ticks left in the recovery period; CARE_TICKS until supplies are complete. */
    public long careTicksRemaining(long now) {
        if (satisfiedAtGameTime < 0) {
            return CARE_TICKS;
        }
        return Math.max(0L, CARE_TICKS - (now - satisfiedAtGameTime));
    }

    /** Whole days left in the recovery period (rounded up, min 0). */
    public int careDaysRemaining(long now) {
        return (int) Math.ceil(careTicksRemaining(now) / 24000.0);
    }

    /** True once supplies are complete and the 3-day recovery has fully passed. */
    public boolean isCareComplete(long now) {
        return satisfiedAtGameTime >= 0 && (now - satisfiedAtGameTime) >= CARE_TICKS;
    }

    public boolean isLiberated() {
        return liberated;
    }

    public void markLiberated() {
        this.liberated = true;
    }

    /**
     * Lock in the per-food need the first time we can count it. {@code countedHerdSize}
     * is the number of same-species animals nearby (at least 1). Each accepted food
     * then needs herdSize * itemsPerAnimal.
     */
    public void establishHerdNeed(int countedHerdSize) {
        if (perFoodNeed > 0) {
            return;
        }
        this.herdSize = Math.max(1, countedHerdSize);
        this.perFoodNeed = this.herdSize * species.itemsPerAnimal();
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
     * Record that the player handed over one unit of the given accepted food. The
     * first item nudges trust SCARED -> TRUSTING; satisfying the whole herd pushes
     * trust to COMPANION. Returns the trust state BEFORE this call.
     */
    public TrustState recordFood(Item food) {
        TrustState before = trust;
        given.merge(food, 1, Integer::sum);
        if (trust == TrustState.SCARED) {
            trust = TrustState.TRUSTING;
        }
        if (isHerdSatisfied()) {
            trust = TrustState.COMPANION;
        }
        return before;
    }

    /**
     * Record that the player provided water for the herd (land animals only).
     * Returns the trust state BEFORE this call.
     */
    public TrustState recordWater() {
        TrustState before = trust;
        watered = true;
        if (trust == TrustState.SCARED) {
            trust = TrustState.TRUSTING;
        }
        if (isHerdSatisfied()) {
            trust = TrustState.COMPANION;
        }
        return before;
    }
}
