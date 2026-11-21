package com.diredifficulty.mixin.accessor;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to a mob's two goal selectors, for Rufus's AI. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("goalSelector")
	GoalSelector direDifficulty$goalSelector();

	@Accessor("targetSelector")
	GoalSelector direDifficulty$targetSelector();
}
