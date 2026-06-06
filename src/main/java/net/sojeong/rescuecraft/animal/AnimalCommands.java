package net.sojeong.rescuecraft.animal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.pig.PigConfusionDetector;

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

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerInternal(dispatcher));
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
            befriend(player, animal, species);
            return 1;
        }
        // Already befriended: act on whatever the player is holding.
        AnimalCompanion.getOrCreate(animal.getUUID(), species, companion.getName()); // refocus
        ItemStack held = player.getMainHandItem();
        if (species.accepts(held)) {
            giveFood(player, companion);
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
        if (companion.getHerdNeed() > 0) {
            sendInfo(player, "  herd:    " + companion.getHerdSize() + " animals");
            sendInfo(player, "  food:    " + companion.getFoodGiven() + " / " + companion.getHerdNeed()
                    + " given (" + companion.getFoodRemaining() + " to go)");
        } else {
            sendInfo(player, "  food:    none given yet");
        }
        showInstruction(player, companion);
        return 1;
    }

    // ===================== actions =====================

    private static void giveFood(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        ItemStack stack = findAcceptedFood(player, species);
        if (stack == null) {
            String how = species.needsWater()
                    ? "Find it in the village chests, or grow your own."
                    : "Catch it with a fishing rod, or look in the chests.";
            sendError(player, companion.getName() + " only wants " + species.foodDisplayName() + ". " + how);
            return;
        }
        stack.shrink(1);

        if (companion.getHerdNeed() == 0) {
            companion.establishHerdNeed(countHerd(player, companion));
        }
        boolean firstFood = companion.getFoodGiven() == 0;

        TrustState before = companion.recordFood();
        if (before != companion.getTrust()) {
            sendTrustUpdate(player, companion.getName(), before.name(), companion.getTrust().name());
        }

        speak(player, companion,
                "(The player just gave you some " + species.foodDisplayName() + ". You are grateful.)", true);

        if (firstFood && !companion.isTaughtTip()) {
            teachTip(player, companion);
            announceHerdNeed(player, companion);
            companion.markTaughtTip();
        }
        if (companion.isHerdSatisfied()) {
            sendHerdSaved(player, companion);
        } else if (!firstFood) {
            sendProgress(player, companion);
        }
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
            sendHerdSaved(player, companion);
        } else {
            sendInfo(player, companion.getName() + "'s herd now has water. " + foodNeedSummary(companion));
        }
    }

    // ===================== quest messaging =====================

    /**
     * Short quest reminder shown on every right-click greeting and on adopt:
     * WHAT food, HOW MANY for the herd, and WHERE to get it. Always shown in both
     * English and Korean, regardless of the player's English level.
     */
    private static void showInstruction(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies s = companion.getSpecies();
        int herd = Math.max(1, companion.getHerdSize());
        int per = s.itemsPerAnimal();
        int total = companion.getHerdNeed() > 0 ? companion.getHerdNeed() : herd * per;

        String whereEn = s.needsWater() ? "in the village chests (or grow your own)" : "by fishing with a fishing rod";
        String whereKo = s.needsWater() ? "마을 상자에서 찾거나 직접 길러서" : "낚싯대로 낚시해서";
        String waterEn = s.needsWater() ? " We also need water - bring a water bucket." : "";
        String waterKo = s.needsWater() ? " 그리고 물도 필요해요 - 물 양동이를 가져와요." : "";
        String progressEn = companion.getFoodGiven() > 0
                ? " So far " + companion.getFoodGiven() + "/" + total + "; " + companion.getFoodRemaining() + " more to go." : "";
        String progressKo = companion.getFoodGiven() > 0
                ? " 지금까지 " + companion.getFoodGiven() + "/" + total + ", " + companion.getFoodRemaining() + "개 더 필요해요." : "";

        player.sendSystemMessage(Component.literal("[Quest] " + companion.getName() + " needs "
                + s.foodDisplayName() + ". There are " + herd + " of us, so the herd needs " + total
                + " in total (" + per + " each). Find it " + whereEn + "." + waterEn + progressEn)
                .withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.literal("[안내] " + companion.getName() + "에게는 "
                + s.foodDisplayKorean() + "이(가) 필요해요. 무리가 " + herd + "마리라 모두 " + total
                + "개가 필요해요(" + per + "개씩). " + whereKo + " 구할 수 있어요." + waterKo + progressKo)
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
        int per = species.itemsPerAnimal();
        int total = companion.getHerdNeed();
        String band = AnimalPrompt.currentBand();

        String waterEn = species.needsWater() ? " We also need fresh water." : "";
        String english = "There are " + herd + " of us here. Each of us needs " + per + " "
                + species.foodDisplayName() + ". Please bring " + herd + " x " + per + " = "
                + total + " for our whole herd." + waterEn;
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (!"ADVANCED".equals(band)) {
            String waterKo = species.needsWater() ? " 그리고 물도 필요해." : "";
            String korean = "우리는 여기 " + herd + "마리야. 한 마리당 " + per + "개씩 필요해. "
                    + "무리 전체를 위해 " + herd + " x " + per + " = " + total + "개를 가져다 줘." + waterKo;
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendProgress(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        String band = AnimalPrompt.currentBand();
        String waterEn = companion.stillNeedsWater() ? " We still need water too." : "";
        String english = "Thank you! " + companion.getFoodGiven() + " of " + companion.getHerdNeed()
                + " so far. We still need " + companion.getFoodRemaining() + " more "
                + species.foodDisplayName() + "." + waterEn;
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!"ADVANCED".equals(band)) {
            String waterKo = companion.stillNeedsWater() ? " 물도 아직 필요해." : "";
            String korean = "고마워! 지금까지 " + companion.getFoodGiven() + " / " + companion.getHerdNeed()
                    + "개야. " + companion.getFoodRemaining() + "개 더 필요해." + waterKo;
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendHerdSaved(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        String band = AnimalPrompt.currentBand();
        String english = "Our whole herd has enough " + species.foodDisplayName()
                + " now. We are safe because of you. Thank you, friend!";
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.GREEN));
        if (!"ADVANCED".equals(band)) {
            String korean = "이제 우리 무리 모두가 충분히 먹을 수 있어. 네 덕분에 우리는 안전해. 고마워, 친구!";
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
        sendInfo(player, "Quest complete: you saved " + companion.getName() + "'s herd!");
    }

    private static String foodNeedSummary(AnimalCompanion companion) {
        if (companion.getHerdNeed() <= 0) {
            return "Now bring " + companion.getSpecies().foodDisplayName() + " for the herd.";
        }
        return "Still need " + companion.getFoodRemaining() + " more "
                + companion.getSpecies().foodDisplayName() + ".";
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

    private static ItemStack findAcceptedFood(ServerPlayer player, AnimalSpecies species) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (species.accepts(s)) {
                return s;
            }
        }
        return null;
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
