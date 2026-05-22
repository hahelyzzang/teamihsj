package net.sojeong.rescuecraft;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
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
                                The old city is silent.

                                The streets are broken.
                                The zoo has been abandoned.

                                But some animals are still waiting for help.
                                """)),

						Filterable.passThrough(Component.literal("""
                                You are not here to conquer this world.

                                You are here to listen, learn, and help.

                                The animals may speak to you if you are willing to understand them.
                                """)),

						Filterable.passThrough(Component.literal("""
                                Language is your bridge.

                                Through English, you will ask questions, understand needs, and rebuild trust.

                                Do not worry if your English is not perfect.
                                """)),

						Filterable.passThrough(Component.literal("""
                                Before your journey begins...

                                Who are you?

                                Your answer will help RescueCraft adjust future conversations to your English level.
                                """))
				),
				true
		);

		journal.set(DataComponents.WRITTEN_BOOK_CONTENT, content);

		return journal;
	}
}