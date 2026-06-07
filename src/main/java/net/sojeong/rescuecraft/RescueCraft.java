package net.sojeong.rescuecraft;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.sojeong.rescuecraft.animal.AnimalCommands;
import net.sojeong.rescuecraft.pig.PigCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RescueCraft implements ModInitializer {
    public static final String MOD_ID = "rescuecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Set<UUID> playersGivenJournal = new HashSet<>();

    @Override
    public void onInitialize() {
        LOGGER.info("RescueCraft initialized!");
        // Register the /rcpig command tree (pig NPC dialogue prototype).
        PigCommands.register();
        // Register the /rcanimal command tree (cow, chicken, rabbit, horse NPCs).
        AnimalCommands.register();
        HungryAnimalParticles.register();
        // Speed up crop growth to ~5 minutes near players.
        CropGrowthAccelerator.register();
    }

    public static void giveFieldJournal(ServerPlayer player) {
        if (player == null) {
            return;
        }

        if (playersGivenJournal.contains(player.getUUID())) {
            return;
        }

        playersGivenJournal.add(player.getUUID());

        ItemStack journal = createFieldJournal();

        boolean added = player.getInventory().add(journal);

        if (!added) {
            player.drop(journal, false);
        }

        player.sendSystemMessage(
                Component.literal("[RescueCraft] You received the RescueCraft Field Journal.")
        );
    }

    private static ItemStack createFieldJournal() {
        ItemStack journal = new ItemStack(Items.WRITTEN_BOOK);

        WrittenBookContent content = new WrittenBookContent(
                Filterable.passThrough("RescueCraft Field Journal"),
                "RescueCraft",
                0,
                List.of(
                        Filterable.passThrough(Component.literal("""
                                20XX.09.08
                                
                                The world as I knew it is gone.
                                WWIII struck without warning, and everyone I knew... is gone.
                                """)),

                        Filterable.passThrough(Component.literal("""
                                20XX.09.30
                                
                                It was not just the people I knew.
                                It was everyone.
                                Weeks have passed, and I have not seen a single living soul.
                                I truly believe I may be the last person alive.
                                """)),

                        Filterable.passThrough(Component.literal("""
                                20XX.10.05
                                
                                Today, I found something impossible.
                                While climbing a mountain to search the horizon, I spotted what looked like a zoo in the distance. Using my binoculars, I swear I saw movement inside.
                                """)),

                        Filterable.passThrough(Component.literal("""
                                Animals… maybe even survivors.
                                I do not know what is waiting for me there, but for the first time in weeks, I feel hope.
                                """)),


                        Filterable.passThrough(Component.literal("""
                                How to help an animal

                                RIGHT-CLICK an animal to befriend it.
                                Then RIGHT-CLICK it again while holding:
                                 - its food  -> you feed it
                                 - a water bucket -> you give water

                                To TALK, just type in chat while standing
                                next to it. (Type ! first to chat normally.)
                                """)),

                        Filterable.passThrough(Component.literal("""
                                What each animal wants

                                Land animals need WATER + crops:
                                 - Bori (pig): carrots/potatoes/beetroots
                                 - Cow: wheat / beetroot
                                 - Chicken: wheat & beetroot seeds
                                 - Rabbit: carrots / dandelions
                                 - Horse: apples / wheat / sugar

                                Water animals & the cat need FISH you catch
                                with a fishing rod:
                                 - Axolotl, Turtle, Cat
                                """)),

                        Filterable.passThrough(Component.literal("""
                                The plan

                                Find seeds, crops and buckets in the village
                                chests. Bring an animal its food and it will
                                teach you how to grow or catch more - then ask
                                for enough for its WHOLE herd. (Crops here grow
                                fast - about 5 minutes.)

                                Right-click an animal any time to hear again
                                what it needs, how many, and where to find it.
                                """)),

                        Filterable.passThrough(Component.literal("""
                                Setting them free

                                Once a herd has all its food and water, care for
                                them for 3 days while they recover. Then they will
                                ask you to break the iron bars and set them free,
                                so they can restore the wildlife.

                                Only ONE of each animal becomes your friend - the
                                rest of the herd follows that one.
                                """)),

                        Filterable.passThrough(Component.literal("""
                                Backup commands (if needed)

                                Works for every animal, including Bori:
                                  /rcanimal interact
                                  /rcanimal talk <message>
                                  /rcanimal give | water | status
                                """))
                ),
                true
        );

        journal.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        return journal;
    }
}
