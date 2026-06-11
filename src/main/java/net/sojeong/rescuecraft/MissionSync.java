package net.sojeong.rescuecraft;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.sojeong.rescuecraft.animal.AnimalCompanion;
import net.sojeong.rescuecraft.pig.PigCompanion;

/**
 * Server-side mission stage computation (stages 3+).
 * Stages 0-2 are managed client-side in RescueCraftClient.
 */
public final class MissionSync {

    private static String currentAnimalName = "the animal";

    private MissionSync() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(
                MissionSyncPayload.TYPE,
                MissionSyncPayload.CODEC
        );
    }

    public static void syncToPlayer(ServerPlayer player) {
        int stage = computeStage();
        ServerPlayNetworking.send(player, new MissionSyncPayload(stage, currentAnimalName));
    }

    public static int computeStage() {
        PigCompanion pig = PigCompanion.getActive();

        // Check AnimalCompanion for pig species too (right-click adopt uses AnimalCompanion)
        AnimalCompanion pigAsAnimal = net.sojeong.rescuecraft.animal.AnimalCompanion.getBySpecies(
                net.sojeong.rescuecraft.animal.AnimalSpecies.PIG);

        boolean pigAdopted = pig != null || pigAsAnimal != null;
        if (!pigAdopted) return -1; // client handles 0-3

        // PigCompanion path (via /rcpig adopt)
        if (pig != null) {
            if (pig.isHabitatBuilt()) {
                AnimalCompanion other = findActiveNonPigCompanion();
                if (other != null) {
                    currentAnimalName = other.getName();
                    if (other.isLiberated())     return 9;
                    if (other.isHerdSatisfied()) return 8;
                    return 7;
                }
                return 6;
            }
            if (pig.isFed() && pig.isWatered()) return 5;
            return 4;
        }

        // AnimalCompanion path (via right-click interact)
        if (pigAsAnimal != null) {
            AnimalCompanion other = findActiveNonPigCompanion();
            if (pigAsAnimal.isLiberated()) {
                if (other != null) {
                    currentAnimalName = other.getName();
                    if (other.isLiberated())     return 9;
                    if (other.isHerdSatisfied()) return 8;
                    return 7;
                }
                return 6;
            }
            if (pigAsAnimal.isHerdSatisfied()) return 5;
            return 4;
        }

        return -1;
    }

    private static AnimalCompanion findActiveNonPigCompanion() {
        for (AnimalCompanion c : AnimalCompanion.all()) return c;
        return null;
    }
}