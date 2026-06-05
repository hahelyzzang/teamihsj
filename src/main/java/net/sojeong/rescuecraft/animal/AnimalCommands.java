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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.pig.PigConfusionDetector;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Registers and implements the /rcanimal command tree used by the rescued
 * conversational animals (cow, chicken, rabbit, horse). Each animal asks for one
 * food item that matches its real-life diet, teaches the player how to grow/find
 * it, and then asks for enough food for its whole herd.
 *
 * The pig (Bori) keeps its own dedicated {@code /rcpig} flow.
 *
 * Subcommands:
 *   /rcanimal adopt             - befriend the nearest supported animal
 *   /rcanimal talk <message...> - speak to the adopted animal (LLM-driven)
 *   /rcanimal give              - give one of the food item it is asking for
 *   /rcanimal status            - check the animal's trust state and herd need
 */
public final class AnimalCommands {

    /** How close the player must stand to adopt an animal. */
    private static final double ADOPT_RADIUS = 6.0;
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
                        .then(Commands.literal("adopt").executes(AnimalCommands::cmdAdopt))
                        .then(Commands.literal("talk")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(AnimalCommands::cmdTalk)))
                        .then(Commands.literal("give").executes(AnimalCommands::cmdGive))
                        .then(Commands.literal("status").executes(AnimalCommands::cmdStatus))
        );
    }

    // ----- adopt -----

    private static int cmdAdopt(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        Optional<Animal> nearest = findNearestSupportedAnimal(player, ADOPT_RADIUS);
        if (nearest.isEmpty()) {
            sendError(player, "No rescuable animal within " + (int) ADOPT_RADIUS
                    + " blocks. Stand next to a cow, chicken, rabbit, or horse and try again.");
            return 0;
        }

        Animal animal = nearest.get();
        AnimalSpecies species = AnimalSpecies.forEntity(animal);
        if (species == null) {
            sendError(player, "That animal cannot be rescued yet.");
            return 0;
        }

        String name = species.defaultName();
        animal.setCustomName(Component.literal(name));
        animal.setCustomNameVisible(true);
        animal.setPersistenceRequired();

        AnimalCompanion companion = AnimalCompanion.getOrCreate(animal.getUUID(), species, name);

        sendInfo(player, "You befriended " + name + " the "
                + species.name().toLowerCase() + ". " + name + " is your RescueCraft companion now.");

        if (!companion.isFirstEncounterDone()) {
            speak(player, companion, AnimalPrompt.firstEncounterPlayerMessage(species), true);
        }
        return 1;
    }

    // ----- talk -----

    private static int cmdTalk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        AnimalCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        String message = StringArgumentType.getString(ctx, "message");
        speak(player, companion, message, false);
        return 1;
    }

    // ----- give -----

    private static int cmdGive(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        AnimalCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        AnimalSpecies species = companion.getSpecies();
        Item required = species.requiredItem();

        ItemStack stack = findItemInInventory(player, required);
        if (stack == null) {
            sendError(player, companion.getName() + " wants " + species.itemDisplayName()
                    + ". Look in the village chests, bring some, then try /rcanimal give again.");
            return 0;
        }
        stack.shrink(1);

        boolean firstGive = companion.getItemsGiven() == 0;
        if (firstGive) {
            int herd = countHerd(player, companion);
            companion.establishHerdNeed(herd);
        }

        TrustState before = companion.recordItemGiven();
        if (before != companion.getTrust()) {
            sendTrustUpdate(player, companion.getName(), before.name(), companion.getTrust().name());
        }

        // Grateful in-character line from the animal (LLM).
        speak(player, companion,
                "(The player just gave you one " + species.itemDisplayName() + ". You are grateful.)", true);

        if (firstGive) {
            teachCultivation(player, companion);
            announceHerdNeed(player, companion);
            companion.markTaughtCultivation();
            if (companion.isHerdSatisfied()) {
                sendHerdSaved(player, companion);
            }
        } else if (companion.isHerdSatisfied()) {
            sendHerdSaved(player, companion);
        } else {
            sendProgress(player, companion);
        }
        return 1;
    }

    // ----- status -----

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        AnimalCompanion companion = AnimalCompanion.getActive();
        if (companion == null) {
            sendInfo(player, "No animal befriended yet. Use /rcanimal adopt next to a cow, chicken, rabbit, or horse.");
            return 0;
        }
        AnimalSpecies species = companion.getSpecies();
        sendInfo(player, companion.getName() + " (" + species.name().toLowerCase() + ") status:");
        sendInfo(player, "  trust:   " + companion.getTrust().name());
        sendInfo(player, "  wants:   " + species.itemDisplayName());
        if (companion.getHerdNeed() > 0) {
            sendInfo(player, "  herd:    " + companion.getHerdSize() + " animals");
            sendInfo(player, "  food:    " + companion.getItemsGiven() + " / " + companion.getHerdNeed()
                    + " given (" + companion.getItemsRemaining() + " to go)");
        } else {
            sendInfo(player, "  food:    none given yet");
        }
        return 1;
    }

    // ===================== quest messaging =====================

    private static void teachCultivation(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        String band = AnimalPrompt.currentBand();
        sendInfo(player, companion.getName() + " teaches you how to get more "
                + species.itemDisplayName() + ":");
        player.sendSystemMessage(Component.literal("  " + species.cultivationTipEnglish())
                .withStyle(ChatFormatting.YELLOW));
        if (!"ADVANCED".equals(band)) {
            player.sendSystemMessage(Component.literal("  " + species.cultivationTipKorean())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void announceHerdNeed(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        int herd = companion.getHerdSize();
        int per = species.itemsPerAnimal();
        int total = companion.getHerdNeed();
        String band = AnimalPrompt.currentBand();

        String english = "There are " + herd + " of us here. Each of us needs " + per + " "
                + species.itemDisplayName() + ". Please bring " + herd + " x " + per + " = "
                + total + " " + species.itemDisplayName() + " in total for our herd.";
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (!"ADVANCED".equals(band)) {
            String korean = "우리는 여기 " + herd + "마리야. 한 마리당 " + species.itemDisplayName()
                    + " " + per + "개가 필요해. 무리 전체를 위해 " + herd + " x " + per + " = "
                    + total + "개를 가져다 줘.";
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendProgress(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        String band = AnimalPrompt.currentBand();
        String english = "Thank you! " + companion.getItemsGiven() + " of " + companion.getHerdNeed()
                + " " + species.itemDisplayName() + " so far. We still need "
                + companion.getItemsRemaining() + " more.";
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!"ADVANCED".equals(band)) {
            String korean = "고마워! 지금까지 " + companion.getItemsGiven() + " / " + companion.getHerdNeed()
                    + "개야. " + companion.getItemsRemaining() + "개 더 필요해.";
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private static void sendHerdSaved(ServerPlayer player, AnimalCompanion companion) {
        AnimalSpecies species = companion.getSpecies();
        String band = AnimalPrompt.currentBand();
        String english = "Our whole herd has enough " + species.itemDisplayName()
                + " now. We are safe because of you. Thank you, friend!";
        player.sendSystemMessage(Component.literal(companion.getName() + ": " + english)
                .withStyle(ChatFormatting.GREEN));
        if (!"ADVANCED".equals(band)) {
            String korean = "이제 우리 무리 모두가 충분한 " + species.itemDisplayName()
                    + "을(를) 얻었어. 네 덕분에 우리는 안전해. 고마워, 친구!";
            player.sendSystemMessage(Component.literal(companion.getName() + " (한국어): " + korean)
                    .withStyle(ChatFormatting.GRAY));
        }
        sendInfo(player, "Quest complete: you saved " + companion.getName() + "'s herd!");
    }

    // ===================== helpers =====================

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static AnimalCompanion requireCompanion(ServerPlayer player) {
        AnimalCompanion companion = AnimalCompanion.getActive();
        if (companion == null) {
            sendError(player, "You have not befriended an animal yet. Stand next to one and use /rcanimal adopt.");
            return null;
        }
        return companion;
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

    private static ItemStack findItemInInventory(ServerPlayer player, Item item) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Generate and broadcast the animal's next line via the local LLM.
     *
     * @param isInternalEvent true when triggered by a player action (adopt/give) rather than chat
     */
    private static void speak(ServerPlayer player, AnimalCompanion companion, String playerMessage,
                              boolean isInternalEvent) {
        final MinecraftServer server = player.level().getServer();

        // Confusion shortcut: re-show the previous Korean line without calling the LLM.
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
