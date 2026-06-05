package net.sojeong.rescuecraft.animal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Per-species configuration for the RescueCraft conversational animals.
 *
 * Every rescued animal asks the player for ONE specific item that matches the
 * real animal's diet/characteristics. Once the player brings that item, the
 * animal teaches how to grow/obtain more of it ("cultivation tip") and then
 * tells the player how many the whole herd needs ({@code itemsPerAnimal} times
 * the number of nearby animals of the same kind).
 *
 * The pig (Bori) keeps its own dedicated {@code /rcpig} flow; the species here
 * are the representatives for the other animals that were added to the map.
 */
public enum AnimalSpecies {

    COW(
            EntityType.COW,
            "Daisy",
            Items.WHEAT,
            "wheat",
            2,
            "a gentle, slow dairy cow who misses chewing fresh wheat",
            "To grow wheat: use a hoe on grass next to water to make farmland, "
                    + "plant wheat seeds on it, wait until the wheat turns golden, then harvest it.",
            "밀 키우는 법: 물 옆 풀밭을 괭이로 갈아 경작지를 만들고, 밀 씨앗을 심은 뒤 "
                    + "밀이 황금색이 될 때까지 기다렸다가 수확하세요."
    ),

    CHICKEN(
            EntityType.CHICKEN,
            "Coco",
            Items.WHEAT_SEEDS,
            "wheat seeds",
            1,
            "a small, fluffy, easily-startled chicken who loves pecking at seeds",
            "To get more seeds: break tall grass to collect wheat seeds, "
                    + "then plant them on farmland to grow wheat and gather even more seeds.",
            "씨앗 더 얻는 법: 키 큰 풀을 부수면 밀 씨앗이 나와요. "
                    + "그 씨앗을 경작지에 심으면 밀이 자라고 씨앗을 더 많이 모을 수 있어요."
    ),

    RABBIT(
            EntityType.RABBIT,
            "Mochi",
            Items.CARROT,
            "carrots",
            1,
            "a shy, twitchy little rabbit who nibbles crunchy carrots",
            "To grow carrots: plant a carrot on watered farmland, "
                    + "wait until it is fully grown, then harvest several carrots from it.",
            "당근 키우는 법: 물을 댄 경작지에 당근을 심고, 다 자랄 때까지 기다린 다음 "
                    + "수확하면 당근 여러 개를 얻을 수 있어요."
    ),

    HORSE(
            EntityType.HORSE,
            "Comet",
            Items.APPLE,
            "apples",
            3,
            "a proud but tired horse who is comforted by sweet apples",
            "To find apples: chop oak leaves and oak trees, where apples sometimes drop. "
                    + "Plant oak saplings on grass and wait for them to grow into new trees.",
            "사과 얻는 법: 참나무 잎과 나무를 베면 가끔 사과가 떨어져요. "
                    + "풀밭에 참나무 묘목을 심고 새 나무로 자라기를 기다리세요."
    );

    private final EntityType<?> entityType;
    private final String defaultName;
    private final Item requiredItem;
    private final String itemDisplayName;
    private final int itemsPerAnimal;
    private final String personality;
    private final String cultivationTipEnglish;
    private final String cultivationTipKorean;

    AnimalSpecies(EntityType<?> entityType,
                  String defaultName,
                  Item requiredItem,
                  String itemDisplayName,
                  int itemsPerAnimal,
                  String personality,
                  String cultivationTipEnglish,
                  String cultivationTipKorean) {
        this.entityType = entityType;
        this.defaultName = defaultName;
        this.requiredItem = requiredItem;
        this.itemDisplayName = itemDisplayName;
        this.itemsPerAnimal = itemsPerAnimal;
        this.personality = personality;
        this.cultivationTipEnglish = cultivationTipEnglish;
        this.cultivationTipKorean = cultivationTipKorean;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public String defaultName() {
        return defaultName;
    }

    public Item requiredItem() {
        return requiredItem;
    }

    public String itemDisplayName() {
        return itemDisplayName;
    }

    public int itemsPerAnimal() {
        return itemsPerAnimal;
    }

    public String personality() {
        return personality;
    }

    public String cultivationTipEnglish() {
        return cultivationTipEnglish;
    }

    public String cultivationTipKorean() {
        return cultivationTipKorean;
    }

    /** Returns the configured species for the given entity, or null if it is not a supported animal. */
    public static AnimalSpecies forEntity(Entity entity) {
        if (entity == null) {
            return null;
        }
        EntityType<?> type = entity.getType();
        for (AnimalSpecies species : values()) {
            if (species.entityType == type) {
                return species;
            }
        }
        return null;
    }
}
