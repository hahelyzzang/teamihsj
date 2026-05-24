package net.sojeong.rescuecraft.pig;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-pig state for the RescueCraft AI companion. One instance per "adopted" pig
 * entity (identified by its Minecraft entity UUID). State is held in memory for
 * the prototype - persistence to disk can be added later by serializing the
 * COMPANIONS map.
 */
public class PigCompanion {
    private static final Map<UUID, PigCompanion> COMPANIONS = new ConcurrentHashMap<>();

    /** The most recently adopted pig. Convenience for single-pig prototype. */
    private static volatile UUID activePigUuid = null;

    private final UUID pigUuid;
    private PigTrustState trust = PigTrustState.SCARED;
    private boolean firstEncounterDone = false;
    private boolean fed = false;
    private boolean watered = false;
    private boolean habitatBuilt = false;

    private String lastPigEnglish = "";
    private String lastPigKorean = "";

    private PigCompanion(UUID pigUuid) {
        this.pigUuid = pigUuid;
    }

    public static PigCompanion getOrCreate(UUID pigUuid) {
        PigCompanion companion = COMPANIONS.computeIfAbsent(pigUuid, PigCompanion::new);
        activePigUuid = pigUuid;
        return companion;
    }

    public static PigCompanion get(UUID pigUuid) {
        return COMPANIONS.get(pigUuid);
    }

    public static PigCompanion getActive() {
        UUID uuid = activePigUuid;
        return uuid == null ? null : COMPANIONS.get(uuid);
    }

    public UUID getPigUuid() {
        return pigUuid;
    }

    public PigTrustState getTrust() {
        return trust;
    }

    public boolean isFirstEncounterDone() {
        return firstEncounterDone;
    }

    public void markFirstEncounterDone() {
        this.firstEncounterDone = true;
    }

    public boolean isFed() {
        return fed;
    }

    public boolean isWatered() {
        return watered;
    }

    public boolean isHabitatBuilt() {
        return habitatBuilt;
    }

    public String getLastPigEnglish() {
        return lastPigEnglish;
    }

    public String getLastPigKorean() {
        return lastPigKorean;
    }

    public void rememberLastLine(String english, String korean) {
        this.lastPigEnglish = english == null ? "" : english;
        this.lastPigKorean = korean == null ? "" : korean;
    }

    /**
     * Record that the pig was fed. The first feeding nudges trust SCARED -> TRUSTING.
     * Returns true if the trust state actually changed.
     */
    public boolean recordFed() {
        boolean firstTime = !this.fed;
        this.fed = true;
        return advanceTrustIfReady(firstTime);
    }

    public boolean recordWatered() {
        boolean firstTime = !this.watered;
        this.watered = true;
        return advanceTrustIfReady(firstTime);
    }

    /**
     * Record that the player built a safe habitat for the pig. This is the final
     * milestone and pushes trust to COMPANION (assuming the pig has already been
     * fed and watered, otherwise it still advances one step).
     */
    public boolean recordHabitatBuilt() {
        boolean firstTime = !this.habitatBuilt;
        this.habitatBuilt = true;
        if (!firstTime) {
            return false;
        }
        PigTrustState before = trust;
        // Habitat is the strongest trust signal: go straight to COMPANION if we
        // already had any prior care, otherwise nudge by one step.
        if (fed || watered) {
            trust = PigTrustState.COMPANION;
        } else {
            trust = trust.advance();
        }
        return trust != before;
    }

    private boolean advanceTrustIfReady(boolean firstTimeForThisNeed) {
        if (!firstTimeForThisNeed) {
            return false;
        }
        PigTrustState before = trust;
        if (trust == PigTrustState.SCARED) {
            trust = PigTrustState.TRUSTING;
        }
        return trust != before;
    }
}
