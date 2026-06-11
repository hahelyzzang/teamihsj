package net.sojeong.rescuecraft.animal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.pig.PigConfusionDetector;
import net.sojeong.rescuecraft.TodoSync;
import net.sojeong.rescuecraft.MissionSync;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Registers and implements the /rcanimal command tree for the rescued
 * conversational animals (pig, cow, chicken, rabbit, horse, axolotl, turtle, cat).
 *
 * Players normally never type these commands: right-clicking an animal sends
 * {@code /rcanimal interact} and typing in chat next to it sends
 * {@code /rcanimal talk} (see RescueCraftClient). The commands stay available as
 * a documented backup.
 *
 * Subcommands:
 *   /rcanimal interact          - befriend, or feed/water with the held item, or greet
 *   /rcanimal adopt             - befriend the nearest supported animal
 *   /rcanimal talk <message...> - speak to the nearest befriended animal
 *   /rcanimal give              - give one accepted food item (crops or fish)
 *   /rcanimal water             - give water to a land animal (hold a water bucket)
 *   /rcanimal status            - check the animal's trust state and herd need
 */
public final class AnimalCommands {

    /** How close the player must stand to adopt / interact with an animal. */
    private static final double INTERACT_RADIUS = 6.0;
    /** How close a befriended animal must be for talk/give/water/status to target it. */
    private static final double TARGET_RADIUS = 8.0;
    /** Radius used to count an animal's herd (same species nearby). */
    private static final double HERD_RADIUS = 48.0;

    private AnimalCommands() {}

    /** Ticks between recovery checks. */
    private static int recoveryTick = 0;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerInternal(dispatcher));
        // Each second, check whether any herd has finished its 3-day recovery.
        ServerTickEvents.END_SERVER_TICK.register(AnimalCommands::tickRecovery);
    }

    private static void registerInternal(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rcanimal")
                        .then(Commands.literal("interact").executes(AnimalCommands::cmdInteract))
                        .then(Commands.literal("adopt").executes(AnimalCommands::cmdAdopt))
                        .then(Commands.literal("talk")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(AnimalCommands::cmdTalk)))
                        .then(Commands.literal("give").executes(AnimalCommands::cmdGive))
                        .then(Commands.literal("water").executes(AnimalCommands::cmdWater))
                        .then(Commands.literal("status").executes(AnimalCommands::cmdStatus))
                        .then(Commands.literal("skipcare").executes(AnimalCommands::cmdSkipCare))
        );
    }

    // ----- interact (right-click) -----

    private static int cmdInteract(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        Optional<Animal> nearest = findNearestSupportedAnimal(player, INTERACT_RADIUS);
        if (nearest.isEmpty()) {
            sendError(player, "Stand closer to an animal to help it.");
            return 0;
        }
        Animal animal = nearest.get();
        AnimalSpecies species = AnimalSpecies.forEntity(animal);
        if (species == null) {
            return 0;
        }

        AnimalCompanion companion = AnimalCompanion.get(animal.getUUID());
        if (companion == null) {
            // Only ONE representative per species. If this species already has a
            // friend (e.g. Bori), act on that one - even though a DIFFERENT individual
            // was clicked. This fixes the "other pigs crowd around the food so I can't
            // click Bori" problem: feeding any pig in the herd feeds the representative.
            AnimalCompanion existingForSpecies = AnimalCompanion.getBySpecies(species);
            if (existingForSpecies != null) {
                companion = existingForSpecies;
            } else {
                befriend(player, animal, species);
                return 1;
            }
        }
        // Act on the representative companion with whatever the player is holding.
        ItemStack held = player.getMainHandItem();
        if (species.accepts(held)) {
            giveFoodItem(player, companion, held);
            return 1;
        }
        if (species.needsWater() && held.is(Items.WATER_BUCKET)) {
            giveWater(player, companion);
            return 1;
        }
        // Empty hand / other item: greet, and always remind what is needed.
        speak(player, companion, "(The player gently greets you and asks how they can help.)", true);
        showInstruction(player, companion);
        return 1;
    }

    // ----- adopt -----

    private static int cmdAdopt(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        Optional<Animal> nearest = findNearestSupportedAnimal(player, INTERACT_RADIUS);
        if (nearest.isEmpty()) {
            sendError(player, "No rescuable animal within " + (int) INTERACT_RADIUS
                    + " blocks. Stand next to a pig, cow, chicken, rabbit, horse, axolotl, turtle, or cat.");
            return 0;
        }
        Animal animal = nearest.get();
        AnimalSpecies species = AnimalSpecies.forEntity(animal);
        if (species == null) {
            sendError(player, "That animal cannot be rescued yet.");
            return 0;
        }
        AnimalCompanion existing = AnimalCompanion.get(animal.getUUID());
        if (existing != null) {
            AnimalCompanion.getOrCreate(animal.getUUID(), species, existing.getName());
            sendInfo(player, existing.getName() + " is already your friend.");
            showInstruction(player, existing);
            return 1;
        }
        // Keep one representative per species.
        AnimalCompanion existingForSpecies = AnimalCompanion.getBySpecies(species);
        if (existingForSpecies != null) {
            sendInfo(player, existingForSpecies.getName() + " the " + species.name().toLowerCase()
                    + " is already your friend - help " + existingForSpecies.getName() + " instead.");
            showInstruction(player, existingForSpecies);
            return 1;
        }
        befriend(player, animal, species);
        return 1;
    }

    /** Names the animal, creates its companion, counts its herd, greets, and shows the quest. */
    private static void befriend(ServerPlayer player, Animal animal, AnimalSpecies species) {
        String name = species.defaultName();
        animal.setCustomName(Component.literal(name));
        animal.setCustomNameVisible(true);
        animal.setPersistenceRequired();

        AnimalCompanion companion = AnimalCompanion.getOrCreate(animal.getUUID(), species, name);
        companion.establishHerdNeed(countHerd(player, companion));

        sendInfo(player, "You befriended " + name + " the " + species.name().toLowerCase()
                + ". " + name + " is your RescueCraft companion now.");

        if (!companion.isFirstEncounterDone()) {
            speak(player, companion, AnimalPrompt.firstEncounterPlayerMessage(species), true);
        }
        showInstruction(player, companion);
        TodoSync.syncToPlayer(player);
        MissionSync.syncToPlayer(player);
    }

    // ----- talk -----

    private static int cmdTalk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        AnimalCompanion companion = targetCompanion(player);
        if (companion == null) return 0;

        String message = StringArgumentType.getString(ctx, "message");
        speak(player, companion, message, false);
        return 1;
    }

    // ----- give / water / status -----

    private static int cmdGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        AnimalCompanion companion = targetCompanion(player);
        if (companion == null) return 0;
        giveFood(player, companion);
        return 1;
    }

    private static int cmdWater(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        AnimalCompanion companion = targetCompanion(player);
        if (companion == null) return 0;
        giveWater(player, companion);
        return 1;
    }

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        AnimalCompanion companion = targetCompanion(player);
        if (companion == null) return 0;

        AnimalSpecies species = companion.getSpecies();
        sendInfo(player, companion.getName() + " (" + species.name().toLowerCase() + ") status:");
        sendInfo(player, "  trust:   " + companion.getTrust().name());
        sendInfo(player, "  wants:   " + species.foodDisplayName());
        if (species.needsWater()) {
            sendInfo(player, "  water:   " + (companion.isWatered() ? "given" : "needed (hold a water bucket)"));
        }
        if (companion.isPerFoodNeedSet()) {
            sendInfo(player, "  herd:    " + companion.getHerdSize() + " animals");
            sendInfo(player, "  needs:   " + companion.getPerFoodNeed() + " of EACH food");
            sendInfo(player, "  food:    " + foodBreakdown(companion));
        } else {
            sendInfo(player, "  food:    none given yet");
        }
        showInstruction(player, companion);
        return 1;
    }

    // ----- skipcare (testing: free the herd immediately) -----

    private static int cmdSkipCare(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;
        AnimalCompanion companion = targetCompanion(player);
        if (companion == null) return 0;
        if (companion.isLiberated()) {
            sendInfo(player, companion.getName() + "'s herd is already free.");
            return 1;
        }
        ServerLevel level = (ServerLevel) player.level();
        companion.markSatisfied(level.getGameTime());
        deliverLiberation(level, companion);
        Entity self = level.getEntity(companion.getAnimalUuid());
        NatureRestoration.restoreAround(level, self != null ? self.blockPosition() : player.blockPosition());
        companion.markLiberated();
        sendInfo(player, "[debug] Skipped the 3-day care timer for " + companion.getName() + ".");
        return 1;
    }

    // ===================== actions =====================

    /** Give a specific item (from right-click held item). */
    private static void giveFoodItem(ServerPlayer player, AnimalCompanion companion, ItemStack held) {
        AnimalSpecies species = companion.getSpecies();
        if (!companion.isPerFoodNeedSet()) {
            companion.establishHerdNeed(countHerd(player, companion));
        }
        Item item = held.getItem();
        if (companion.getGiven(item) >= companion.getPerFoodNeed() && companion.isPerFoodNeedSet()) {
            sendInfo(player, companion.getName() + " already has enough " + foodLabel(item) + ". Still need: "
                    + stillNeededSummary(companion));
            return;
        }
        held.shrink(1);
        boolean firstFood = companion.getTotalGiven() == 0;
        TrustState before = companion.recordFood(item);
        if (before != companion.getTrust()) {
            sendTrustUpdate(player, companion.getName(), before.name(), companion.getTrust().name());
        }
        speak(player, companion,
                "(The player just gave you a " + foodLabel(item) + ". You are grateful.)", true);
        if (firstFood && !companion.isTaughtTip()) {
            teachTip(player, companion);
            announceHerdNeed(player, companion);
            companion.markTaughtTip();
        }
        if (companion.isHerdSatisfied()) {
            onSuppliesComplete(player, companion);
        } else {
            sendProgress(player, companion);
        }
        TodoSync.syncToPlayer(player);
        MissionSync.syncToPlayer(player);
    }

    private static void giveFood(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        if (!companion.isPerFoodNeedSet()) {
            companion.establishHerdNeed(countHerd(player, companion));
        }

        // The herd needs EVERY accepted food (AND), so prefer a food still missing.
        ItemStack stack = findNeededFood(player, companion);
        if (stack == null) {
            if (hasAnyAcceptedFood(player, species)) {
                sendInfo(player, companion.getName() + " already has enough of that one. Still need: "
                        + stillNeededSummary(companion));
            } else {
                String how = species.needsWater()
                        ? "Find them in the village chests/fields, or grow your own."
                        : "Catch them with a fishing rod.";
                sendError(player, companion.getName() + " needs ALL of: " + species.foodDisplayName()
                        + " (each kind). " + how);
            }
            return;
        }
        Item item = stack.getItem();
        stack.shrink(1);

        boolean firstFood = companion.getTotalGiven() == 0;
        TrustState before = companion.recordFood(item);
        if (before != companion.getTrust()) {
            sendTrustUpdate(player, companion.getName(), before.name(), companion.getTrust().name());
        }

        speak(player, companion,
                "(The player just gave you a " + foodLabel(item) + ". You are grateful.)", true);

        if (firstFood && !companion.isTaughtTip()) {
            teachTip(player, companion);
            announceHerdNeed(player, companion);
            companion.markTaughtTip();
        }
        if (companion.isHerdSatisfied()) {
            onSuppliesComplete(player, companion);
        } else {
            sendProgress(player, companion);
        }
        TodoSync.syncToPlayer(player);
        MissionSync.syncToPlayer(player);
    }

    private static void giveWater(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        if (!species.needsWater()) {
            sendInfo(player, companion.getName() + " lives in water and does not need a bucket. Bring fish instead.");
            return;
        }
        if (companion.isWatered()) {
            sendInfo(player, companion.getName() + "'s herd already has water.");
            return;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(Items.WATER_BUCKET)) {
            sendError(player, "Hold a water bucket in your main hand to give " + companion.getName() + " water.");
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));

        TrustState before = companion.recordWater();
        if (before != companion.getTrust()) {
            sendTrustUpdate(player, companion.getName(), before.name(), companion.getTrust().name());
        }
        speak(player, companion, "(The player just gave your herd fresh water.)", true);

        if (companion.isHerdSatisfied()) {
            onSuppliesComplete(player, companion);
        } else {
            sendInfo(player, companion.getName() + "'s herd now has water. " + foodNeedSummary(companion));
        }
        TodoSync.syncToPlayer(player);
        MissionSync.syncToPlayer(player);
    }

    // ===================== quest messaging =====================

    /**
     * Short quest reminder shown on every right-click greeting and on adopt:
     * WHAT food, HOW MANY for the herd, and WHERE to get it. Always shown in both
     * English and Korean, regardless of the player's English level.
     */
    private static void showInstruction(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies s = companion.getSpecies();

        // Finale: the herd has been told they are free.
        if (companion.isLiberated()) {
            player.sendSystemMessage(Component.literal("[Quest] " + companion.getName()
                            + "'s herd is free now. Thank you for restoring the wildlife!")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal("[안내] " + companion.getName()
                            + "의 무리는 이제 자유예요. 야생을 되살려줘서 고마워요!")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        // Recovery phase: supplies are complete, waiting out the 3-day care period.
        if (companion.isHerdSatisfied()) {
            int daysLeft = companion.careDaysRemaining(player.level().getGameTime());
            player.sendSystemMessage(Component.literal("[Quest] " + companion.getName()
                            + " has enough food and water. We are recovering - about " + daysLeft
                            + " day(s) until we are well. Stay near and keep us safe!")
                    .withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.literal("[안내] " + companion.getName()
                            + "은(는) 먹이와 물이 충분해요. 회복 중이에요 - 약 " + daysLeft
                            + "일 뒤면 다 나아요. 곁에서 지켜줘요!")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        int herd = Math.max(1, companion.getHerdSize());
        int perEach = companion.isPerFoodNeedSet() ? companion.getPerFoodNeed() : herd * s.itemsPerAnimal();
        int total = perEach * s.acceptedFoods().size();

        String whereEn = s.needsWater() ? "in the village chests/fields (or grow your own)" : "by fishing with a fishing rod";
        String whereKo = s.needsWater() ? "마을 상자/들판에서 찾거나 직접 길러서" : "낚싯대로 낚시해서";
        String waterEn = s.needsWater() ? " We also need water - bring a water bucket." : "";
        String waterKo = s.needsWater() ? " 그리고 물도 필요해요 - 물 양동이를 가져와요." : "";
        String progressEn = companion.getTotalGiven() > 0 ? " Progress: " + foodBreakdown(companion) + "." : "";
        String progressKo = companion.getTotalGiven() > 0 ? " 진행: " + foodBreakdown(companion) + "." : "";

        player.sendSystemMessage(Component.literal("[Quest] " + companion.getName() + " needs ALL of: "
                        + s.foodDisplayName() + " - " + perEach + " of EACH (herd of " + herd + "), so " + total
                        + " in total. Find them " + whereEn + "." + waterEn + progressEn)
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("[안내] " + companion.getName() + "에게는 "
                        + s.foodDisplayKorean() + " 전부가 필요해요. 무리가 " + herd + "마리라 각각 " + perEach
                        + "개씩, 총 " + total + "개예요. " + whereKo + " 구할 수 있어요." + waterKo + progressKo)
                .withStyle(ChatFormatting.YELLOW));
    }

    private static void teachTip(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        sendInfo(player, companion.getName() + " teaches you how to get more "
                + species.foodDisplayName() + ":");
        player.sendSystemMessage(Component.literal("  " + species.tipEnglish())
                .withStyle(ChatFormatting.YELLOW));
        player.sendSystemMessage(Component.literal("  " + species.tipKorean())
                .withStyle(ChatFormatting.GRAY));
    }

    private static void announceHerdNeed(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        int herd = companion.getHerdSize();
        int perEach = companion.getPerFoodNeed();
        int total = companion.getTotalNeed();
        String band = AnimalPrompt.currentBand();

        String waterEn = species.needsWater() ? " We also need fresh water." : "";
        String english = "There are " + herd + " of us here. We need ALL of " + species.foodDisplayName()
                + " - " + perEach + " of EACH (" + total + " in total) for the whole herd." + waterEn;
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (!"ADVANCED".equals(band)) {
            String waterKo = species.needsWater() ? " 그리고 물도 필요해." : "";
            String korean = "우리는 여기 " + herd + "마리야. " + species.foodDisplayKorean()
                    + " 전부 필요해 - 각각 " + perEach + "개씩, 총 " + total + "개야." + waterKo;
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendProgress(ServerPlayer player, AnimalCompanion companion) {
        String band = AnimalPrompt.currentBand();
        String waterEn = companion.stillNeedsWater() ? " We still need water too." : "";
        String english = "Thank you! " + foodBreakdown(companion) + "." + waterEn;
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!"ADVANCED".equals(band)) {
            String waterKo = companion.stillNeedsWater() ? " 물도 아직 필요해." : "";
            String korean = "고마워! 지금 상황: " + foodBreakdown(companion) + "." + waterKo;
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /**
     * Supplies (food + water) are complete: the 3-day recovery begins. The herd is
     * not freed yet - that happens automatically once the care period has passed
     * (see {@link #tickRecovery}).
     */
    private static void onSuppliesComplete(ServerPlayer player, AnimalCompanion companion) {
        long now = player.level().getGameTime();
        boolean firstTime = !companion.isSatisfiedRecorded();
        companion.markSatisfied(now);
        int daysLeft = companion.careDaysRemaining(now);
        AnimalSpecies species = companion.getSpecies();

        String waterEn = species.needsWater() ? " and water" : "";
        String english = "We have enough " + species.foodDisplayName() + waterEn
                + " now! Please care for us for about " + daysLeft + " more day(s) while we recover.";
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.GREEN));
        String korean = "이제 충분히 먹고 마셨어! 회복하는 동안 약 " + daysLeft + "일만 더 곁에서 돌봐줘.";
        player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                .withStyle(ChatFormatting.GRAY));

        if (firstTime) {
            sendInfo(player, "Supplies complete for " + companion.getName()
                    + "'s herd. Care for them for 3 days, then they will be ready to be freed.");
        }
    }

    /** Each second: deliver the liberation finale to any herd that finished recovering. */
    private static void tickRecovery(MinecraftServer server) {
        if (++recoveryTick < 20) {
            return;
        }
        recoveryTick = 0;
        for (AnimalCompanion companion : AnimalCompanion.all()) {
            if (companion.isLiberated() || !companion.isSatisfiedRecorded()) {
                continue;
            }
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntity(companion.getAnimalUuid());
                if (entity == null) {
                    continue;
                }
                if (companion.isCareComplete(level.getGameTime())) {
                    deliverLiberation(level, companion);
                    // The freed herd revives the land around them: flowers and grass grow back.
                    NatureRestoration.restoreAround(level, entity.blockPosition());
                    companion.markLiberated();
                }
                break;
            }
        }
    }

    private static void deliverLiberation(ServerLevel level, AnimalCompanion companion) {
        String english = companion.getName() + ": Thank you for saving us, we are all well now. "
                + "Please, bring down the iron bars and help us be free. We will restore the wildlife.";
        String korean = companion.getName() + " (한국어): 우리를 구해줘서 고마워, 이제 우리 모두 건강해졌어. "
                + "부디 철창을 내리고 우리가 자유로워지게 도와줘. 우리가 야생을 되살릴게.";
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(Component.literal(english).withStyle(ChatFormatting.LIGHT_PURPLE));
            player.sendSystemMessage(Component.literal(korean).withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.literal("[RescueCraft] " + companion.getName()
                            + "'s herd is ready to be freed - break the iron bars to let them out!")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    private static String foodNeedSummary(AnimalCompanion companion) {
        if (!companion.isPerFoodNeedSet()) {
            return "Now bring all of: " + companion.getSpecies().foodDisplayName() + " for the herd.";
        }
        return "Still need: " + stillNeededSummary(companion) + ".";
    }

    // ===================== helpers =====================

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    /** Resolves which befriended animal a command applies to: nearest in range, else the active one. */
    private static AnimalCompanion targetCompanion(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AnimalCompanion near = AnimalCompanion.findNearestAdopted(player, level, TARGET_RADIUS);
        if (near != null) {
            return near;
        }
        AnimalCompanion active = AnimalCompanion.getActive();
        if (active == null) {
            sendError(player, "You have not befriended an animal yet. Right-click one (pig, cow, chicken, rabbit, horse, axolotl, turtle, cat).");
        }
        return active;
    }

    private static Optional<Animal> findNearestSupportedAnimal(ServerPlayer player, double radius) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<Animal> animals = level.getEntitiesOfClass(
                Animal.class, box,
                a -> a.isAlive() && AnimalSpecies.forEntity(a) != null);
        return animals.stream()
                .min(Comparator.comparingDouble(a -> a.distanceToSqr(player)));
    }

    /** Counts how many living animals of the same species are near the companion (its herd). */
    private static int countHerd(ServerPlayer player, AnimalCompanion companion) {
        ServerLevel level = (ServerLevel) player.level();
        AnimalSpecies species = companion.getSpecies();

        Entity self = level.getEntity(companion.getAnimalUuid());
        AABB box = (self != null ? self.getBoundingBox() : player.getBoundingBox()).inflate(HERD_RADIUS);

        List<Animal> sameKind = level.getEntitiesOfClass(
                Animal.class, box,
                a -> a.isAlive() && a.getType() == species.entityType());
        return Math.max(1, sameKind.size());
    }

    /** Finds an accepted food in the inventory that the herd still needs (given < perFoodNeed). */
    private static ItemStack findNeededFood(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (species.accepts(s) && companion.getGiven(s.getItem()) < companion.getPerFoodNeed()) {
                return s;
            }
        }
        return null;
    }

    private static boolean hasAnyAcceptedFood(ServerPlayer player, AnimalSpecies species) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (species.accepts(inv.getItem(i))) {
                return true;
            }
        }
        return false;
    }

    /** Readable name of a food item, e.g. "Carrot". */
    private static String foodLabel(Item item) {
        return new ItemStack(item).getHoverName().getString();
    }

    /** "Carrot 3/10, Potato 0/10, Beetroot 5/10" across all accepted foods. */
    private static String foodBreakdown(AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        int per = companion.getPerFoodNeed();
        StringBuilder sb = new StringBuilder();
        for (Item food : species.acceptedFoods()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(foodLabel(food)).append(" ").append(companion.getGiven(food)).append("/").append(per);
        }
        return sb.toString();
    }

    /** "Carrot x7, Beetroot x10" listing only the foods still missing. */
    private static String stillNeededSummary(AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        StringBuilder sb = new StringBuilder();
        for (Item food : species.acceptedFoods()) {
            int remaining = companion.getRemaining(food);
            if (remaining > 0) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(foodLabel(food)).append(" x").append(remaining);
            }
        }
        return sb.length() == 0 ? "nothing" : sb.toString();
    }

    /**
     * Generate and broadcast the animal's next line via the local LLM.
     *
     * @param isInternalEvent true when triggered by a player action (adopt/give/water) rather than chat
     */
    private static void speak(ServerPlayer player, AnimalCompanion companion, String playerMessage,
                              boolean isInternalEvent) {
        final MinecraftServer server = player.level().getServer();

        if (!isInternalEvent
                && PigConfusionDetector.isConfusion(playerMessage)
                && !companion.getLastEnglish().isBlank()) {
            sendAnimalEnglish(player, companion, companion.getLastEnglish());
            if (!companion.getLastKorean().isBlank()) {
                sendAnimalKorean(player, companion, companion.getLastKorean());
            }
            return;
        }

        new Thread(() -> {
            AnimalDialogueResponse response = AnimalDialogueService.generate(companion, playerMessage);

            server.execute(() -> {
                companion.rememberLastLine(response.english(), response.korean());

                sendAnimalEnglish(player, companion, response.english());

                String band = AnimalPrompt.currentBand();
                boolean showKorean = isInternalEvent
                        ? "BEGINNER".equals(band)
                        : PigConfusionDetector.shouldShowKorean(band, playerMessage);

                if (showKorean && response.korean() != null && !response.korean().isBlank()) {
                    sendAnimalKorean(player, companion, response.korean());
                }

                if (!companion.isFirstEncounterDone()) {
                    companion.markFirstEncounterDone();
                }
            });
        }, "RescueCraft-Animal-Dialogue").start();
    }

    private static void sendAnimalEnglish(ServerPlayer player, AnimalCompanion companion, String text) {
        MutableComponent c = Component.literal("<" + companion.getName() + "> " + text)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        player.sendSystemMessage(c);
    }

    private static void sendAnimalKorean(ServerPlayer player, AnimalCompanion companion, String text) {
        MutableComponent c = Component.literal("<" + companion.getName() + " 한국어> " + text)
                .withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(c);
    }

    private static void sendInfo(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[RescueCraft] " + text).withStyle(ChatFormatting.AQUA));
    }

    private static void sendError(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[RescueCraft] " + text).withStyle(ChatFormatting.RED));
    }

    private static void sendTrustUpdate(ServerPlayer player, String name, String from, String to) {
        player.sendSystemMessage(
                Component.literal("[RescueCraft] " + name + "'s trust: " + from + " -> " + to)
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}