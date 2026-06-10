package net.sojeong.rescuecraft.animal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.sojeong.rescuecraft.mixin.MobGoalAccessor;

/**
 * Adjusts the vanilla AI of the rescued animal species so the rescue gameplay
 * behaves well:
 *  - removes {@link TemptGoal} so the animals do NOT swarm a player holding food
 *    (right-clicking any individual still feeds the species representative), and
 *  - removes {@link BreedGoal} / {@link FollowParentGoal} so they cannot be bred
 *    by accident while being fed.
 *
 * Goals are stripped each time a supported animal is loaded into the world.
 */
public final class AnimalBehaviorTweaks {

    private AnimalBehaviorTweaks() {}

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof Mob mob && AnimalSpecies.isSupportedType(entity.getType())) {
                GoalSelector goalSelector = ((MobGoalAccessor) mob).rescuecraft$getGoalSelector();
                goalSelector.removeAllGoals(goal ->
                        goal instanceof TemptGoal
                                || goal instanceof BreedGoal
                                || goal instanceof FollowParentGoal);
            }
        });
    }
}
