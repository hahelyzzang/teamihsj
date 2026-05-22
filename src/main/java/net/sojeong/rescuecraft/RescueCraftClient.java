package net.sojeong.rescuecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public class RescueCraftClient implements ClientModInitializer {
    private static boolean introStarted = false;
    private static boolean waitingForIdentityAnswer = false;
    private static boolean evaluatingAnswer = false;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.level == null) {
                return;
            }

            if (!introStarted) {
                introStarted = true;
                waitingForIdentityAnswer = true;

                client.player.sendSystemMessage(
                        Component.literal("[RescueCraft] Who are you?")
                );

                client.player.sendSystemMessage(
                        Component.literal("[RescueCraft] Please introduce yourself in chat.")
                );
            }
        });

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

                        client.player.sendSystemMessage(
                                Component.literal("[RescueCraft] Thank you. Your journey begins now.")
                        );
                    });
                }, "RescueCraft-Ollama-Evaluator").start();

                return false;
            }

            return true;
        });
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