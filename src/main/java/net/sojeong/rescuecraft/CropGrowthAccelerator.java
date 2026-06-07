package net.sojeong.rescuecraft;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Speeds up crop growth so a class session does not have to wait the full vanilla
 * time. Each second we scan the blocks near every player and give crops a chance
 * to advance one growth stage, tuned so a crop reaches full growth in roughly
 * {@link #TARGET_SECONDS} seconds (~5 minutes) on top of normal growth.
 *
 * Only {@link CropBlock}s (wheat, carrots, potatoes, beetroot) are affected, so
 * unrelated "age" blocks like fire are never touched.
 */
public final class CropGrowthAccelerator {

    /** Roughly how long a crop should take to grow from freshly planted to ripe. */
    private static final int TARGET_SECONDS = 300; // ~5 minutes
    private static final int SCAN_RADIUS = 10;
    private static final int VERTICAL_RANGE = 3;

    private static int tick = 0;

    private CropGrowthAccelerator() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (++tick < 20) {
                return; // run about once per second
            }
            tick = 0;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                accelerateNear((ServerLevel) player.level(), player.blockPosition());
            }
        });
    }

    private static void accelerateNear(ServerLevel level, BlockPos center) {
        RandomSource random = level.getRandom();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                for (int dy = -VERTICAL_RANGE; dy <= VERTICAL_RANGE; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (!(state.getBlock() instanceof CropBlock)) {
                        continue;
                    }
                    IntegerProperty age = ageProperty(state);
                    if (age == null) {
                        continue;
                    }
                    int max = 0;
                    for (int value : age.getPossibleValues()) {
                        max = Math.max(max, value);
                    }
                    int current = state.getValue(age);
                    if (current >= max) {
                        continue;
                    }
                    float chancePerSecond = (float) max / TARGET_SECONDS;
                    if (random.nextFloat() < chancePerSecond) {
                        level.setBlock(pos.immutable(), state.setValue(age, current + 1), 2);
                    }
                }
            }
        }
    }

    private static IntegerProperty ageProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty intProperty && "age".equals(property.getName())) {
                return intProperty;
            }
        }
        return null;
    }
}
