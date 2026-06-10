package net.sojeong.rescuecraft.animal;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * When a rescued herd is finally freed, the land around them starts to recover:
 * grass blocks sprout short grass and wild flowers (including dandelions). This is
 * the "nature restores a little" reward the team wanted, and it also tops up the
 * supply of wild dandelions for the rabbits.
 *
 * Implemented by triggering vanilla bone-meal growth on nearby grass blocks, so it
 * needs no world editing.
 */
public final class NatureRestoration {

    private static final int RADIUS = 6;
    private static final int MAX_SPOTS = 14;
    private static final int MAX_ATTEMPTS = 80;

    private NatureRestoration() {}

    public static void restoreAround(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        int spots = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS && spots < MAX_SPOTS; attempt++) {
            int dx = random.nextInt(RADIUS * 2 + 1) - RADIUS;
            int dz = random.nextInt(RADIUS * 2 + 1) - RADIUS;
            int dy = random.nextInt(5) - 2;
            BlockPos pos = center.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.GRASS_BLOCK) && state.getBlock() instanceof BonemealableBlock bonemealable) {
                // Spreads short grass and flowers (dandelions, poppies, ...) above the grass.
                bonemealable.performBonemeal(level, random, pos, state);
                spots++;
            }
        }
        level.levelEvent(2005, center, 0); // bone-meal sparkle particles
    }
}
