package net.sojeong.rescuecraft.animal;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Per-species configuration for the RescueCraft conversational animals.
 *
 * Land animals (including the pig) want WATER plus SEVERAL crops; aquatic
 * animals and the cat are rescued with FISH the player catches by fishing.
 * Once the player brings the right food, the animal teaches how to grow/catch
 * more of it and asks for enough for its whole herd ({@code itemsPerAnimal}
 * times the number of nearby animals of the same kind).
 */
public enum AnimalSpecies {

    // ----- Land animals: water + several crops -----

    PIG(
            EntityType.PIG,
            "Bori",
            true,
            List.of(Items.CARROT, Items.POTATO, Items.BEETROOT),
            "carrots, potatoes, or beetroots",
            "당근, 감자, 비트",
            2,
            "a timid little pig who spent her whole life in a cage and is very hungry",
            "To grow her food: use a hoe on grass next to water to make farmland, "
                    + "plant a carrot, potato, or beetroot, wait until it is grown, then harvest.",
            "먹이 키우는 법: 물 옆 풀밭을 괭이로 갈아 경작지를 만들고, 당근/감자/비트를 심은 뒤 "
                    + "다 자라면 수확하세요."
    ),

    COW(
            EntityType.COW,
            "Daisy",
            true,
            List.of(Items.WHEAT, Items.BEETROOT),
            "wheat or beetroot",
            "밀이나 비트",
            2,
            "a gentle, slow dairy cow who misses chewing fresh wheat",
            "To grow wheat or beetroot: use a hoe on grass next to water to make farmland, "
                    + "plant wheat or beetroot seeds, wait until they are ripe, then harvest.",
            "밀이나 비트 키우는 법: 물 옆 풀밭을 괭이로 갈아 경작지를 만들고, 밀/비트 씨앗을 심은 뒤 "
                    + "다 자라면 수확하세요."
    ),

    CHICKEN(
            EntityType.CHICKEN,
            "Coco",
            true,
            List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS),
            "wheat seeds or beetroot seeds",
            "밀 씨앗이나 비트 씨앗",
            1,
            "a small, fluffy, easily-startled chicken who loves pecking at seeds",
            "To get more seeds: break tall grass for wheat seeds, and harvest beetroot for "
                    + "beetroot seeds. Plant them on farmland to grow even more.",
            "씨앗 더 얻는 법: 키 큰 풀을 부수면 밀 씨앗이, 비트를 수확하면 비트 씨앗이 나와요. "
                    + "경작지에 심으면 더 많이 모을 수 있어요."
    ),

    RABBIT(
            EntityType.RABBIT,
            "Mochi",
            true,
            List.of(Items.CARROT, Items.DANDELION),
            "carrots or dandelions",
            "당근이나 민들레",
            1,
            "a shy, twitchy little rabbit who nibbles carrots and dandelions",
            "To get more: plant a carrot on watered farmland and wait until it is grown, "
                    + "and pick yellow dandelions from grassy fields.",
            "더 얻는 법: 물을 댄 경작지에 당근을 심고 다 자랄 때까지 기다리세요. "
                    + "노란 민들레는 들판에서 뜯으면 돼요."
    ),

    HORSE(
            EntityType.HORSE,
            "Comet",
            true,
            List.of(Items.APPLE, Items.WHEAT, Items.SUGAR),
            "apples, wheat, or sugar",
            "사과, 밀, 설탕",
            3,
            "a proud but tired horse who is comforted by sweet apples and wheat",
            "To get more: chop oak trees for apples, grow wheat on farmland, "
                    + "and make sugar from sugar cane grown beside water.",
            "더 얻는 법: 참나무를 베면 사과가 나오고, 경작지에서 밀을 키울 수 있어요. "
                    + "물가에서 자란 사탕수수로 설탕을 만들 수 있어요."
    ),

    // ----- Aquatic animals & the cat: fish caught by fishing -----

    AXOLOTL(
            EntityType.AXOLOTL,
            "Bubbles",
            false,
            List.of(Items.TROPICAL_FISH, Items.COD, Items.SALMON, Items.PUFFERFISH),
            "fish (cod, salmon, tropical fish, or pufferfish)",
            "물고기(대구, 연어, 열대어, 복어)",
            2,
            "a tiny water creature who can only eat fish and is drying out",
            "To catch fish: craft a fishing rod (3 sticks + 2 string), stand by water, "
                    + "right-click to cast, and reel in when the bobber dips.",
            "물고기 잡는 법: 낚싯대(막대 3 + 실 2)를 만들어 물가에서 우클릭으로 던지고, "
                    + "찌가 잠기면 낚아채세요."
    ),

    TURTLE(
            EntityType.TURTLE,
            "Shelly",
            false,
            List.of(Items.SEAGRASS, Items.COD, Items.SALMON),
            "seagrass or fish",
            "해초나 물고기",
            1,
            "a slow sea turtle who longs for seagrass and the fish of the sea",
            "To get food: catch fish with a fishing rod by the water, "
                    + "or collect seagrass underwater with shears.",
            "먹이 구하는 법: 물가에서 낚싯대로 물고기를 잡거나, 물속에서 가위로 해초를 모으세요."
    ),

    CAT(
            EntityType.CAT,
            "Whiskers",
            false,
            List.of(Items.COD, Items.SALMON),
            "fish (cod or salmon)",
            "물고기(대구나 연어)",
            2,
            "a wary, hungry cat who only trusts those who bring it fish",
            "To catch fish: craft a fishing rod (3 sticks + 2 string), stand by water, "
                    + "right-click to cast, and reel in when the bobber dips.",
            "물고기 잡는 법: 낚싯대(막대 3 + 실 2)를 만들어 물가에서 우클릭으로 던지고, "
                    + "찌가 잠기면 낚아채세요."
    );

    private final EntityType<?> entityType;
    private final String defaultName;
    private final boolean needsWater;
    private final List<Item> acceptedFoods;
    private final String foodDisplayName;
    private final String foodDisplayKorean;
    private final int itemsPerAnimal;
    private final String personality;
    private final String tipEnglish;
    private final String tipKorean;

    AnimalSpecies(EntityType<?> entityType,
                  String defaultName,
                  boolean needsWater,
                  List<Item> acceptedFoods,
                  String foodDisplayName,
                  String foodDisplayKorean,
                  int itemsPerAnimal,
                  String personality,
                  String tipEnglish,
                  String tipKorean) {
        this.entityType = entityType;
        this.defaultName = defaultName;
        this.needsWater = needsWater;
        this.acceptedFoods = acceptedFoods;
        this.foodDisplayName = foodDisplayName;
        this.foodDisplayKorean = foodDisplayKorean;
        this.itemsPerAnimal = itemsPerAnimal;
        this.personality = personality;
        this.tipEnglish = tipEnglish;
        this.tipKorean = tipKorean;
    }

    public EntityType<?> entityType() {
        return entityType;
    }

    public String defaultName() {
        return defaultName;
    }

    /** Land animals need water (one shared water source for the whole herd); aquatic ones do not. */
    public boolean needsWater() {
        return needsWater;
    }

    public List<Item> acceptedFoods() {
        return acceptedFoods;
    }

    public String foodDisplayName() {
        return foodDisplayName;
    }

    public String foodDisplayKorean() {
        return foodDisplayKorean;
    }

    public int itemsPerAnimal() {
        return itemsPerAnimal;
    }

    public String personality() {
        return personality;
    }

    public String tipEnglish() {
        return tipEnglish;
    }

    public String tipKorean() {
        return tipKorean;
    }

    /** True if the given stack is one of the foods this animal will accept. */
    public boolean accepts(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        for (Item food : acceptedFoods) {
            if (stack.is(food)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the configured species for the given entity, or null if it is not supported. */
    public static AnimalSpecies forEntity(Entity entity) {
        return entity == null ? null : forType(entity.getType());
    }

    /** Returns the configured species for the given entity type, or null if it is not supported. */
    public static AnimalSpecies forType(EntityType<?> type) {
        for (AnimalSpecies species : values()) {
            if (species.entityType == type) {
                return species;
            }
        }
        return null;
    }

    public static boolean isSupportedType(EntityType<?> type) {
        return forType(type) != null;
    }
}
