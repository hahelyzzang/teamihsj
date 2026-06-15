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
import net.sojeong.rescuecraft.animal.AnimalSpecies;
import net.sojeong.rescuecraft.pig.PigCompanion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HungryAnimalParticles {
    private static final String NEED_ITEM_DISPLAY_TAG = "rescuecraft_need_item_display";
    private static final Map<UUID, UUID> DISPLAY_BY_ANIMAL = new HashMap<>();
    private static int tickCounter = 0;

    private HungryAnimalParticles() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tickCounter < 2) return;
            tickCounter = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = (ServerLevel) player.level();
                AABB searchArea = player.getBoundingBox().inflate(32);
                List<Animal> animals = level.getEntitiesOfClass(Animal.class, searchArea, Animal::isAlive);
                for (Animal animal : animals) {
                    updateItemDisplay(level, animal);
                }
            }
        });
    }

    // =================== bubble item selection ===================

    /**
     * Returns the item to show as a speech bubble, or null if no bubble needed.
     *
     * Convention (placeholder items — replace with custom textures later):
     *   food+water bubble : species-specific CHESTPLATE variant
     *   food-only bubble  : species-specific SWORD/AXE variant
     *   water-only bubble : STRING (shared across all species)
     */
    private static Item getBubbleItemForAnimal(Animal animal) {

        // ---- generic rescued animals ----
        AnimalCompanion rescued = AnimalCompanion.get(animal.getUUID());
        if (rescued != null) {
            boolean needFood  = rescued.stillNeedsFood();
            boolean needWater = rescued.stillNeedsWater();
            if (!needFood && !needWater) return null;

            AnimalSpecies species = rescued.getSpecies();
            if (needFood && needWater) return foodWaterBubble(species);
            if (needFood)              return foodBubble(species);
            return Items.BLAZE_ROD; // water only (shared)
        }

        // ---- Bori (pig prototype) ----
        PigCompanion companion = PigCompanion.getActive();
        if (companion == null) return null;
        if (!animal.getUUID().equals(companion.getPigUuid())) return null;

        boolean needFood  = !companion.isFed();
        boolean needWater = !companion.isWatered();
        if (!needFood && !needWater) return null;
        if (needFood && needWater) return foodWaterBubble(AnimalSpecies.PIG);
        if (needFood)              return foodBubble(AnimalSpecies.PIG);
        return Items.BLAZE_ROD; // water only
    }

    /** Food-only bubble item per species (sword/axe family — never normally used). */
    private static Item foodBubble(AnimalSpecies species) {
        return switch (species) {
            case PIG     -> Items.IRON_SWORD;
            case COW     -> Items.GOLDEN_SWORD;
            case CHICKEN -> Items.WOODEN_SWORD;
            case RABBIT  -> Items.STONE_SWORD;
            case HORSE   -> Items.DIAMOND_SWORD;
            case AXOLOTL -> Items.IRON_AXE;
            case TURTLE  -> Items.GOLDEN_AXE;
            case CAT     -> Items.STONE_AXE;
        };
    }

    /** Food+water bubble item per species (chestplate/helmet family — never normally used). */
    private static Item foodWaterBubble(AnimalSpecies species) {
        return switch (species) {
            case PIG     -> Items.IRON_CHESTPLATE;
            case COW     -> Items.GOLDEN_CHESTPLATE;
            case CHICKEN -> Items.LEATHER_CHESTPLATE;
            case RABBIT  -> Items.CHAINMAIL_CHESTPLATE;
            case HORSE   -> Items.DIAMOND_CHESTPLATE;
            case AXOLOTL -> Items.NETHERITE_CHESTPLATE;
            case TURTLE  -> Items.IRON_HELMET;
            case CAT     -> Items.GOLDEN_HELMET;
        };
    }

    // =================== display entity management ===================

    private static void updateItemDisplay(ServerLevel level, Animal animal) {
        Item bubbleItem = getBubbleItemForAnimal(animal);
        if (bubbleItem == null) {
            removeDisplay(level, animal);
            return;
        }
        Display.ItemDisplay display = getOrCreateDisplay(level, animal);
        if (display == null) return;

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
        if (displayUuid == null) return;
        var entity = level.getEntity(displayUuid);
        if (entity != null) entity.discard();
    }
}