package net.sojeong.rescuecraft.animal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.sojeong.rescuecraft.mixin.MobGoalAccessor;

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

                // Prevent chickens from laying eggs
                if (entity instanceof Chicken chicken) {
                    chicken.eggTime = Integer.MAX_VALUE;
                }
            }
        });
    }
}