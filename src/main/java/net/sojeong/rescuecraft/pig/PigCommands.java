package net.sojeong.rescuecraft.pig;
import net.sojeong.rescuecraft.HungryAnimalParticles;

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
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;

/**
 * Registers and implements the /rcpig command tree used by the pig NPC prototype.
 *
 * Subcommands:
 *   /rcpig adopt              - mark the nearest pig as Bori, the RescueCraft companion
 *   /rcpig talk <message...>  - send a chat line to Bori (LLM-driven response)
 *   /rcpig feed               - feed a carrot / potato / beetroot from your inventory
 *   /rcpig water              - give water from a water bucket in your main hand
 *   /rcpig habitat            - declare that you have built a safe barn pen for Bori
 *   /rcpig release            - lead Bori to her habitat (final companion milestone)
 *   /rcpig status             - print Bori's current trust state and needs
 */
public final class PigCommands {

    private static final String PIG_PREFIX = "<Bori> ";
    private static final String PIG_PREFIX_KO = "<Bori 한국어> ";

    private PigCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerInternal(dispatcher);
        });
    }

    private static void registerInternal(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rcpig")
                        .then(Commands.literal("adopt").executes(PigCommands::cmdAdopt))
                        .then(Commands.literal("talk")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                        .executes(PigCommands::cmdTalk)))
                        .then(Commands.literal("feed").executes(PigCommands::cmdFeed))
                        .then(Commands.literal("water").executes(PigCommands::cmdWater))
                        .then(Commands.literal("habitat").executes(PigCommands::cmdHabitat))
                        .then(Commands.literal("release").executes(PigCommands::cmdRelease))
                        .then(Commands.literal("status").executes(PigCommands::cmdStatus))
        );
    }

    // ----- adopt -----

    private static int cmdAdopt(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        Optional<Pig> nearest = findNearestPig(player, 6.0);
        if (nearest.isEmpty()) {
            sendError(player, "No pig within 6 blocks. Stand next to one and try again.");
            return 0;
        }
        Pig pig = nearest.get();
        pig.setCustomName(Component.literal("Bori"));
        pig.setCustomNameVisible(true);
        pig.setPersistenceRequired();

        PigCompanion companion = PigCompanion.getOrCreate(pig.getUUID());

        sendInfo(player, "You named this pig Bori. She is your RescueCraft companion now.");
        // Trigger first-encounter dialogue automatically.
        if (!companion.isFirstEncounterDone()) {
            speak(player, companion, PigPrompt.firstEncounterPlayerMessage(), true);
        }
        return 1;
    }

    // ----- talk -----

    private static int cmdTalk(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        String message = StringArgumentType.getString(ctx, "message");
        speak(player, companion, message, false);
        return 1;
    }

    // ----- feed -----

    private static int cmdFeed(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        ItemStack food = findFoodInInventory(player);
        if (food == null) {
            sendError(player, "Bori only eats carrots, potatoes, or beetroots. None found in your inventory.");
            return 0;
        }
        food.shrink(1);

        boolean trustChanged = companion.recordFed();
        if (trustChanged) {
            sendTrustUpdate(player, "SCARED", "TRUSTING");
        }

        speak(player, companion, "(The player just gave you food. You are no longer hungry.)", true);
        return 1;
    }

    // ----- water -----

    private static int cmdWater(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.is(Items.WATER_BUCKET)) {
            sendError(player, "Hold a water bucket in your main hand to give Bori water.");
            return 0;
        }
        // Replace water bucket with empty bucket.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));

        boolean trustChanged = companion.recordWatered();
        if (trustChanged) {
            sendTrustUpdate(player, "SCARED", "TRUSTING");
        }
        speak(player, companion, "(The player just gave you fresh water.)", true);
        return 1;
    }

    // ----- habitat -----

    private static int cmdHabitat(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        PigTrustState before = companion.getTrust();
        boolean trustChanged = companion.recordHabitatBuilt();
        if (trustChanged) {
            sendTrustUpdate(player, before.name(), companion.getTrust().name());
        }
        speak(player, companion, "(The player just finished building a small safe barn pen for you.)", true);
        return 1;
    }

    // ----- release -----

    private static int cmdRelease(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = requireCompanion(player);
        if (companion == null) return 0;

        if (!companion.isHabitatBuilt()) {
            sendError(player, "Bori has nowhere to go yet. Build a safe habitat first (/rcpig habitat).");
            return 0;
        }
        speak(player, companion, "(The player gently guides you into your new safe barn. You are home now.)", true);
        return 1;
    }

    // ----- status -----

    private static int cmdStatus(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = playerOrNull(ctx);
        if (player == null) return 0;

        PigCompanion companion = PigCompanion.getActive();
        if (companion == null) {
            sendInfo(player, "No pig adopted yet. Use /rcpig adopt next to a pig.");
            return 0;
        }
        sendInfo(player, "Bori status:");
        sendInfo(player, "  trust:   " + companion.getTrust().name());
        sendInfo(player, "  fed:     " + companion.isFed());
        sendInfo(player, "  watered: " + companion.isWatered());
        sendInfo(player, "  habitat: " + companion.isHabitatBuilt());
        return 1;
    }

    // ===================== helpers =====================

    private static ServerPlayer playerOrNull(CommandContext<CommandSourceStack> ctx) {
        try {
            return ctx.getSource().getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return null;
        }
    }

    private static PigCompanion requireCompanion(ServerPlayer player) {
        PigCompanion companion = PigCompanion.getActive();
        if (companion == null) {
            sendError(player, "You have not adopted a pig yet. Stand next to one and use /rcpig adopt.");
            return null;
        }
        return companion;
    }

    private static Optional<Pig> findNearestPig(ServerPlayer player, double radius) {
        ServerLevel level = (ServerLevel) player.level();
        AABB box = player.getBoundingBox().inflate(radius);
        List<Pig> pigs = level.getEntitiesOfClass(Pig.class, box);
        return pigs.stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
    }

    private static ItemStack findFoodInInventory(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Generate and broadcast the pig's next line.
     *
     * @param playerMessage   message from the player, or an "(action event)" wrapper when triggered by feed/water/etc.
     * @param isInternalEvent true when triggered by a player action (not chat) - skips confusion shortcut
     */
    private static void speak(ServerPlayer player, PigCompanion companion, String playerMessage, boolean isInternalEvent) {
        final MinecraftServer server = player.level().getServer();

        // Confusion shortcut: just re-show the previous Korean line. Does NOT call the LLM.
        if (!isInternalEvent
                && PigConfusionDetector.isConfusion(playerMessage)
                && !companion.getLastPigEnglish().isBlank()) {
            sendPigEnglish(player, companion.getLastPigEnglish());
            if (!companion.getLastPigKorean().isBlank()) {
                sendPigKorean(player, companion.getLastPigKorean());
            }
            return;
        }

        new Thread(() -> {
            PigDialogueResponse response = PigDialogueService.generate(companion, playerMessage);

            server.execute(() -> {
                companion.rememberLastLine(response.english(), response.korean());

                sendPigEnglish(player, response.english());

                String band = PigPrompt.currentBand();
                boolean showKorean = isInternalEvent
                        ? "BEGINNER".equals(band)
                        : PigConfusionDetector.shouldShowKorean(band, playerMessage);

                if (showKorean && response.korean() != null && !response.korean().isBlank()) {
                    sendPigKorean(player, response.korean());
                }

                if (!companion.isFirstEncounterDone()) {
                    companion.markFirstEncounterDone();
                }
            });
        }, "RescueCraft-Pig-Dialogue").start();
    }

    private static void sendPigEnglish(ServerPlayer player, String text) {
        MutableComponent c = Component.literal(PIG_PREFIX + text).withStyle(ChatFormatting.LIGHT_PURPLE);
        player.sendSystemMessage(c);
    }

    private static void sendPigKorean(ServerPlayer player, String text) {
        MutableComponent c = Component.literal(PIG_PREFIX_KO + text).withStyle(ChatFormatting.GRAY);
        player.sendSystemMessage(c);
    }

    private static void sendInfo(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[RescueCraft] " + text).withStyle(ChatFormatting.AQUA));
    }

    private static void sendError(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[RescueCraft] " + text).withStyle(ChatFormatting.RED));
    }

    private static void sendTrustUpdate(ServerPlayer player, String from, String to) {
        player.sendSystemMessage(
                Component.literal("[RescueCraft] Bori's trust: " + from + " -> " + to)
                        .withStyle(ChatFormatting.GREEN)
        );
    }
}
