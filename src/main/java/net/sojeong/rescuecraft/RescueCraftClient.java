package net.sojeong.rescuecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.animal.AnimalSpecies;

import java.util.ArrayList;
import java.util.List;

public class RescueCraftClient implements ClientModInitializer {
    private static boolean introStarted = false;

    /** Short English placement quiz, easy -> hard, used to gauge the player's level. */
    private static final String[] QUIZ = {
            "Question 1 of 5 - Introduce yourself: what is your name, and where are you from?",
            "Question 2 of 5 - Tell me about something you did yesterday.",
            "Question 3 of 5 - What do you enjoy doing in your free time, and why do you like it?",
            "Question 4 of 5 - You find a hungry, frightened animal. Describe, step by step, what you would do to help it.",
            "Question 5 of 5 - Some people believe keeping animals in zoos is wrong. What is your opinion, and what reasons support it?",
    };

    /** -1 = not started; 0..N-1 = waiting for that answer; N = all answered. */
    private static int quizIndex = -1;
    private static boolean evaluating = false;
    private static boolean awaitingRetry = false;
    private static final List<String> answers = new ArrayList<>();

    /** How close the player must be for chat to be routed to a befriended animal. */
    private static final double TALK_RANGE = 7.0;

    /** Debounce so a single right-click is not processed twice. */
    private static long lastInteractMs = 0L;

    @Override
    public void onInitializeClient() {
        try {
            WorldTemplateInstaller.installWorldTemplate();
        } catch (Exception e) {
            System.err.println("[RescueCraft] World template install failed, but game will continue.");
            e.printStackTrace();
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }

            if (!introStarted) {
                introStarted = true;
                quizIndex = 0;

                client.player.sendSystemMessage(Component.literal(
                        "[RescueCraft] Before your journey begins, I will ask you a few short questions in English."));
                client.player.sendSystemMessage(Component.literal(
                        "[RescueCraft] Answer each one in chat. Your answers set how difficult the animals' English will be."));
                client.player.sendSystemMessage(Component.literal("[RescueCraft] " + QUIZ[0]));
            }
        });

        // Right-click an animal to befriend / feed / water it (no commands needed).
        registerRightClickInteraction();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            Minecraft client = Minecraft.getInstance();

            // A previous evaluation failed (e.g. Ollama down); any message retries it.
            if (awaitingRetry && !evaluating) {
                startEvaluation(client);
                return false;
            }

            // Placement quiz in progress: record the answer and ask the next question.
            if (quizIndex >= 0 && quizIndex < QUIZ.length && !evaluating) {
                answers.add(message);
                quizIndex++;
                if (quizIndex < QUIZ.length) {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("[RescueCraft] " + QUIZ[quizIndex]));
                    }
                } else {
                    startEvaluation(client);
                }
                return false;
            }

            // After the intro: if the player is standing next to a befriended animal,
            // route their chat to that animal as a "talk" instead of broadcasting it.
            return routeChatToNearbyAnimal(message);
        });
    }

    /** Sends all collected quiz answers to the local evaluator and saves the CEFR level. */
    private static void startEvaluation(Minecraft client) {
        evaluating = true;
        awaitingRetry = false;

        giveGuidebookAfterAnswer(client);
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "[RescueCraft] Thank you. I am reading all your answers now - open the guidebook while I evaluate your English level."));
        }

        final String transcript = buildTranscript();
        new Thread(() -> {
            String level = OllamaEnglishEvaluator.evaluate(transcript);
            client.execute(() -> {
                evaluating = false;
                if (client.player == null) {
                    return;
                }
                if (level == null) {
                    awaitingRetry = true;
                    client.player.sendSystemMessage(Component.literal(
                            "[RescueCraft] I could not evaluate your answers. Make sure Ollama is running, then type anything to try again."));
                    return;
                }
                PlayerEnglishProfile.saveLevel(level);
                client.player.sendSystemMessage(Component.literal("[RescueCraft] Your English level is: " + level));
            });
        }, "RescueCraft-Ollama-Evaluator").start();
    }

    private static String buildTranscript() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < answers.size(); i++) {
            String q = i < QUIZ.length ? QUIZ[i] : ("Question " + (i + 1));
            sb.append(q).append("\nAnswer: ").append(answers.get(i)).append("\n\n");
        }
        return sb.toString();
    }

    /**
     * Translates right-clicking a rescue animal into the matching command, so the
     * player can befriend / feed / water animals with the mouse instead of typing.
     * The command targets the nearest befriended animal (resolved server-side).
     */
    private static void registerRightClickInteraction() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            if (!AnimalSpecies.isSupportedType(entity.getType())) {
                return InteractionResult.PASS;
            }

            // Some right-clicks fire the callback twice; ignore the immediate repeat.
            long now = System.currentTimeMillis();
            if (now - lastInteractMs < 250L) {
                return InteractionResult.SUCCESS;
            }
            lastInteractMs = now;

            // The server decides what to do based on adoption state and the held item.
            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.sendCommand("rcanimal interact");
            }
            // Consume the interaction so vanilla behaviour (mount, breed, tame) is skipped.
            return InteractionResult.SUCCESS;
        });
    }

    /**
     * If the player is next to a befriended (named) rescue animal, send their chat
     * to it as a talk command and cancel the normal broadcast. A leading "!" forces
     * a normal chat message.
     *
     * @return true to allow the message as normal chat, false if it was routed.
     */
    private static boolean routeChatToNearbyAnimal(String message) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || message == null || message.isBlank()) {
            return true;
        }

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            return true;
        }

        // Escape hatch: a message starting with "!" is left as a normal chat message.
        if (message.startsWith("!")) {
            return true;
        }

        Entity target = findNearestNamedRescueAnimal(client);
        if (target == null) {
            return true;
        }

        connection.sendCommand("rcanimal talk " + message);
        return false;
    }

    private static Entity findNearestNamedRescueAnimal(Minecraft client) {
        var player = client.player;
        if (player == null) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(TALK_RANGE);
        List<Animal> animals = player.level().getEntitiesOfClass(
                Animal.class, box,
                a -> a.isAlive() && a.hasCustomName() && isRescueType(a.getType()));

        Entity best = null;
        double bestDistSqr = Double.MAX_VALUE;
        for (Animal animal : animals) {
            double distSqr = animal.distanceToSqr(player);
            if (distSqr < bestDistSqr) {
                bestDistSqr = distSqr;
                best = animal;
            }
        }
        return best;
    }

    private static boolean isRescueType(EntityType<?> type) {
        return AnimalSpecies.isSupportedType(type);
    }

    private static void giveGuidebookAfterAnswer(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();

        if (server == null || client.player == null) {
            if (client.player != null) {
                client.player.sendSystemMessage(
                        Component.literal("[RescueCraft] Guidebook can only be given automatically in singleplayer for now.")
                );
            }
            return;
        }

        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayer(client.player.getUUID());

            if (serverPlayer != null) {
                RescueCraft.giveFieldJournal(serverPlayer);
            }
        });
    }
}
