package net.sojeong.rescuecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.animal.AnimalCompanion;
import net.sojeong.rescuecraft.pig.PigCompanion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HungryAnimalParticles {
    private static final String NEED_ITEM_DISPLAY_TAG = "rescuecraft_need_item_display";

    private static final Map<UUID, UUID> DISPLAY_BY_ANIMAL = new HashMap<>();

    private static int tickCounter = 0;

    private HungryAnimalParticles() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            // 0.1초마다 위치/상태 갱신
            if (tickCounter < 2) {
                return;
            }

            tickCounter = 0;

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = (ServerLevel) player.level();

                AABB searchArea = player.getBoundingBox().inflate(32);

                List<Animal> animals = level.getEntitiesOfClass(
                        Animal.class,
                        searchArea,
                        Animal::isAlive
                );

                for (Animal animal : animals) {
                    updateItemDisplay(level, animal);
                }
            }
        });
    }

    private static void updateItemDisplay(ServerLevel level, Animal animal) {
        Item bubbleItem = getBubbleItemForAnimal(animal);

        // 표시할 말풍선이 없으면 기존 display 제거
        if (bubbleItem == null) {
            removeDisplay(level, animal);
            return;
        }

        Display.ItemDisplay display = getOrCreateDisplay(level, animal);

        if (display == null) {
            return;
        }

        display.setItemStack(new ItemStack(bubbleItem));
        display.setItemTransform(ItemDisplayContext.GUI);

        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setCustomNameVisible(false);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);

        double x = animal.getX();
        double y = animal.getY() + animal.getBbHeight() + 0.85;
        double z = animal.getZ();

        display.teleportTo(x, y, z);
    }

    private static Item getBubbleItemForAnimal(Animal animal) {
        // 범용 구조 동물(소/닭/토끼/말/아홀로틀/거북/고양이):
        // 무리에게 줄 먹이/물이 충분해질 때까지 말풍선 표시
        AnimalCompanion rescued = AnimalCompanion.get(animal.getUUID());
        if (rescued != null) {
            boolean needFood = rescued.stillNeedsFood();
            boolean needWater = rescued.stillNeedsWater();
            if (needFood && needWater) {
                return Items.PAPER;   // foodwater_bubble
            }
            if (needFood) {
                return Items.STICK;   // food_bubble
            }
            if (needWater) {
                return Items.STRING;  // water_bubble
            }
            return null;
        }

        PigCompanion companion = PigCompanion.getActive();

        // 아직 Bori가 등록되지 않았으면 아무 말풍선도 표시하지 않음
        if (companion == null) {
            return null;
        }

        // Bori가 아닌 동물은 표시하지 않음
        if (!animal.getUUID().equals(companion.getPigUuid())) {
            return null;
        }

        boolean needFood = !companion.isFed();
        boolean needWater = !companion.isWatered();

        // 음식과 물 둘 다 필요
        if (needFood && needWater) {
            return Items.PAPER;   // foodwater_bubble
        }

        // 음식만 필요
        if (needFood) {
            return Items.STICK;   // food_bubble
        }

        // 물만 필요
        if (needWater) {
            return Items.STRING;  // water_bubble
        }

        // 둘 다 받았으면 말풍선 제거
        return null;
    }

    private static Display.ItemDisplay getOrCreateDisplay(ServerLevel level, Animal animal) {
        UUID animalUuid = animal.getUUID();
        UUID displayUuid = DISPLAY_BY_ANIMAL.get(animalUuid);

        if (displayUuid != null) {
            var existing = level.getEntity(displayUuid);

            if (existing instanceof Display.ItemDisplay itemDisplay && existing.isAlive()) {
                return itemDisplay;
            }

            DISPLAY_BY_ANIMAL.remove(animalUuid);
        }

        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);

        display.addTag(NEED_ITEM_DISPLAY_TAG);
        display.setNoGravity(true);
        display.setInvulnerable(true);
        display.setCustomNameVisible(false);
        display.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        display.setItemTransform(ItemDisplayContext.GUI);

        double x = animal.getX();
        double y = animal.getY() + animal.getBbHeight() + 0.85;
        double z = animal.getZ();

        display.setPos(x, y, z);

        level.addFreshEntity(display);

        DISPLAY_BY_ANIMAL.put(animalUuid, display.getUUID());

        return display;
    }

    private static void removeDisplay(ServerLevel level, Animal animal) {
        UUID displayUuid = DISPLAY_BY_ANIMAL.remove(animal.getUUID());

        if (displayUuid == null) {
            return;
        }

        var entity = level.getEntity(displayUuid);

        if (entity != null) {
            entity.discard();
        }
    }
}