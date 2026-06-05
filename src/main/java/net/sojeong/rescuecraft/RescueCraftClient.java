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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.sojeong.rescuecraft.animal.AnimalSpecies;

import java.util.List;

public class RescueCraftClient implements ClientModInitializer {
    private static boolean introStarted = false;
    private static boolean waitingForIdentityAnswer = false;
    private static boolean evaluatingAnswer = false;

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
                waitingForIdentityAnswer = true;

                client.player.sendSystemMessage(
                        Component.literal("[RescueCraft] Who am I in this story?")
                );

                client.player.sendSystemMessage(
                        Component.literal("[RescueCraft] Please introduce yourself in chat.")
                );
            }
        });

        // Right-click an animal to befriend / feed / water it (no commands needed).
        registerRightClickInteraction();

        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (waitingForIdentityAnswer && !evaluatingAnswer) {
                waitingForIdentityAnswer = false;
                evaluatingAnswer = true;

                Minecraft client = Minecraft.getInstance();

                giveGuidebookAfterAnswer(client);

                if (client.player != null) {
                    client.player.sendSystemMessage(
                            Component.literal("[RescueCraft] I am reading your answer. Please open the guidebook while I evaluate your English level.")
                    );
                }

                new Thread(() -> {
                    String level = OllamaEnglishEvaluator.evaluate(message);

                    client.execute(() -> {
                        evaluatingAnswer = false;

                        if (client.player == null) {
                            return;
                        }

                        if (level == null) {
                            waitingForIdentityAnswer = true;

                            client.player.sendSystemMessage(
                                    Component.literal("[RescueCraft] I could not evaluate your answer. Please make sure Ollama is running, then try again.")
                            );

                            return;
                        }

                        PlayerEnglishProfile.saveLevel(level);

                        client.player.sendSystemMessage(
                                Component.literal("[RescueCraft] Your English level is: " + level)
                        );
                    });
                }, "RescueCraft-Ollama-Evaluator").start();

                return false;
            }

            // After the intro: if the player is standing next to a befriended animal,
            // route their chat to that animal as a "talk" instead of broadcasting it.
            return routeChatToNearbyAnimal(message);
        });
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

            EntityType<?> type = entity.getType();
            boolean isPig = type == EntityType.PIG;
            AnimalSpecies species = AnimalSpecies.forType(type);
            if (!isPig && species == null) {
                return InteractionResult.PASS;
            }

            // Some right-clicks fire the callback twice; ignore the immediate repeat.
            long now = System.currentTimeMillis();
            if (now - lastInteractMs < 250L) {
                return InteractionResult.SUCCESS;
            }
            lastInteractMs = now;

            ItemStack held = player.getItemInHand(hand);
            boolean named = entity.hasCustomName();

            String command;
            if (isPig) {
                if (!named) {
                    command = "rcpig adopt";
                } else if (held.is(Items.WATER_BUCKET)) {
                    command = "rcpig water";
                } else if (held.is(Items.CARROT) || held.is(Items.POTATO) || held.is(Items.BEETROOT)) {
                    command = "rcpig feed";
                } else {
                    command = "rcpig status";
                }
            } else {
                if (!named) {
                    command = "rcanimal adopt";
                } else if (species.needsWater() && held.is(Items.WATER_BUCKET)) {
                    command = "rcanimal water";
                } else if (species.accepts(held)) {
                    command = "rcanimal give";
                } else {
                    command = "rcanimal status";
                }
            }

            ClientPacketListener connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.sendCommand(command);
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

        String prefix = target.getType() == EntityType.PIG ? "rcpig talk " : "rcanimal talk ";
        connection.sendCommand(prefix + message);
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
        return type == EntityType.PIG || AnimalSpecies.isSupportedType(type);
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
