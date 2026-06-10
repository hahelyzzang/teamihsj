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
            "carrots, potatoes, and beetroots",
            "당근, 감자, 비트",
            2,
            "a timid little pig who spent her whole life in a cage and is very hungry",
            "1) Craft a hoe (2 sticks + 2 planks). 2) Right-click grass within 4 blocks of water "
                    + "to turn it into farmland. 3) Plant a carrot, potato, or beetroot on it. "
                    + "4) Wait for it to grow (or use bone meal to speed it up). "
                    + "5) Break it to collect several, and replant one to keep the farm going.",
            "1) 괭이를 만들어요(막대 2 + 판자 2). 2) 물에서 4칸 안의 풀밭을 우클릭해 경작지로 만들어요. "
                    + "3) 당근/감자/비트를 심어요. 4) 다 자랄 때까지 기다리거나 뼛가루로 빠르게 키워요. "
                    + "5) 부수면 여러 개가 나와요. 하나는 다시 심어 농사를 이어가요."
    ),

    COW(
            EntityType.COW,
            "Daisy",
            true,
            List.of(Items.WHEAT, Items.BEETROOT),
            "wheat and beetroot",
            "밀과 비트",
            2,
            "a gentle, slow dairy cow who misses chewing fresh wheat",
            "1) Craft a hoe. 2) Right-click grass next to water to make farmland. "
                    + "3) Plant wheat seeds or beetroot seeds on it. 4) Give it light and time, "
                    + "or use bone meal. 5) Harvest when the wheat is golden or the beetroot is red, "
                    + "then replant the seeds you get back.",
            "1) 괭이를 만들어요. 2) 물 옆 풀밭을 우클릭해 경작지로 만들어요. 3) 밀 씨앗이나 비트 씨앗을 심어요. "
                    + "4) 빛과 시간을 주거나 뼛가루를 써요. 5) 밀이 황금색, 비트가 빨개지면 수확하고 "
                    + "나온 씨앗을 다시 심어요."
    ),

    CHICKEN(
            EntityType.CHICKEN,
            "Coco",
            true,
            List.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS),
            "wheat seeds and beetroot seeds",
            "밀 씨앗과 비트 씨앗",
            1,
            "a small, fluffy, easily-startled chicken who loves pecking at seeds",
            "1) Punch tall grass to get wheat seeds (and harvest beetroot for beetroot seeds). "
                    + "2) Craft a hoe and till grass next to water into farmland. 3) Plant the seeds. "
                    + "4) Wait or use bone meal until they ripen. 5) Harvest to get even more seeds, "
                    + "and replant some.",
            "1) 키 큰 풀을 쳐서 밀 씨앗을 얻어요(비트를 수확하면 비트 씨앗). 2) 괭이로 물 옆 풀을 갈아 "
                    + "경작지를 만들어요. 3) 씨앗을 심어요. 4) 기다리거나 뼛가루로 키워요. "
                    + "5) 수확하면 씨앗이 더 많이 나와요. 일부는 다시 심어요."
    ),

    RABBIT(
            EntityType.RABBIT,
            "Mochi",
            true,
            List.of(Items.CARROT, Items.DANDELION),
            "carrots and dandelions",
            "당근과 민들레",
            1,
            "a shy, twitchy little rabbit who nibbles carrots and dandelions",
            "Carrots: 1) make a hoe, 2) till grass next to water, 3) plant a carrot, "
                    + "4) wait or use bone meal, 5) harvest several and replant one. "
                    + "Dandelions: pick the yellow flowers in grassy fields, or use bone meal on grass.",
            "당근: 1) 괭이를 만들어요, 2) 물 옆 풀을 갈아요, 3) 당근을 심어요, "
                    + "4) 기다리거나 뼛가루를 써요, 5) 여러 개를 수확하고 하나는 다시 심어요. "
                    + "민들레: 들판의 노란 꽃을 따거나, 풀밭에 뼛가루를 쓰면 더 자라요."
    ),

    HORSE(
            EntityType.HORSE,
            "Comet",
            true,
            List.of(Items.APPLE, Items.WHEAT),
            "apples and wheat",
            "사과와 밀",
            3,
            "a proud but tired horse who is comforted by sweet apples and wheat",
            "Apples: chop oak leaves and oak trees - apples drop sometimes; plant saplings for more. "
                    + "Wheat: till grass by water with a hoe, plant wheat seeds, let it grow golden, then harvest.",
            "사과: 참나무 잎과 나무를 베면 가끔 떨어져요. 묘목을 심어 나무를 늘려요. "
                    + "밀: 괭이로 물가 풀을 갈아 밀 씨앗을 심고, 황금색이 되면 수확해요."
    ),

    // ----- Aquatic animals & the cat: fish caught by fishing -----

    AXOLOTL(
            EntityType.AXOLOTL,
            "Bubbles",
            false,
            List.of(Items.COD, Items.SALMON),
            "cod and salmon",
            "대구와 연어",
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
            List.of(Items.COD, Items.SALMON),
            "cod and salmon",
            "대구와 연어",
            1,
            "a slow sea turtle who longs for the fish of the sea",
            "To get fish: craft a fishing rod (3 sticks + 2 string), stand by water, "
                    + "right-click to cast, and reel in when the bobber dips.",
            "물고기 잡는 법: 낚싯대(막대 3 + 실 2)를 만들어 물가에서 우클릭으로 던지고, 찌가 잠기면 낚아채세요."
    ),

    CAT(
            EntityType.CAT,
            "Whiskers",
            false,
            List.of(Items.COD, Items.SALMON),
            "cod and salmon",
            "대구와 연어",
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
