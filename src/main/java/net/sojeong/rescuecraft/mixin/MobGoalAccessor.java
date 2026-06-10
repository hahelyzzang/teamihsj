package net.sojeong.rescuecraft.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@link Mob}'s protected {@code goalSelector} so we can remove specific
 * AI goals (e.g. tempt-following and breeding) from rescued animals at runtime.
 */
@Mixin(Mob.class)
public interface MobGoalAccessor {
    @Accessor("goalSelector")
    GoalSelector rescuecraft$getGoalSelector();
}
