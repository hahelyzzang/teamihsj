package net.sojeong.rescuecraft;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
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
        HungryAnimalParticles.register();
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
                                When you meet an animal:
                                  /rcpig adopt     - befriend the pig in front of you
                                  /rcpig talk ...  - speak to the pig
                                  /rcpig feed      - feed a carrot / potato / beetroot
                                  /rcpig water     - give water (hold a water bucket)
                                  /rcpig habitat   - declare a safe barn pen is built
                                  /rcpig release   - lead the pig to her new home
                                  /rcpig status    - check the pig's trust state
                                """))
                ),
                true
        );

        journal.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

        return journal;
    }
}
