package net.sojeong.rescuecraft;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.sojeong.rescuecraft.animal.AnimalCompanion;
import net.sojeong.rescuecraft.animal.AnimalSpecies;
import net.sojeong.rescuecraft.pig.PigCompanion;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

public final class TodoSync {

    private TodoSync() {}

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(
                TodoSyncPayload.TYPE,
                TodoSyncPayload.CODEC
        );
    }

    public static void syncToPlayer(ServerPlayer player) {
        List<TodoEntry> entries = buildEntries();
        ServerPlayNetworking.send(player, new TodoSyncPayload(entries));
    }

    private static List<TodoEntry> buildEntries() {
        List<TodoEntry> list = new ArrayList<>();

        for (AnimalCompanion companion : AnimalCompanion.all()) {
            if (companion.isLiberated()) continue;
            AnimalSpecies species = companion.getSpecies();
            String name = companion.getName();

            if (companion.isPerFoodNeedSet()) {
                for (Item food : species.acceptedFoods()) {
                    list.add(new TodoEntry(name, food, companion.getGiven(food), companion.getPerFoodNeed()));
                }
            }
            if (species.needsWater() && !companion.isWatered()) {
                list.add(new TodoEntry(name));
            }
        }

        PigCompanion pig = PigCompanion.getActive();
        if (pig != null && AnimalCompanion.getBySpecies(AnimalSpecies.PIG) == null) {
            if (!pig.isFed()) {
                for (Item food : AnimalSpecies.PIG.acceptedFoods()) {
                    list.add(new TodoEntry("Bori", food, 0, 1));
                }
            }
            if (!pig.isWatered()) {
                list.add(new TodoEntry("Bori"));
            }
        }

        return list;
    }
}