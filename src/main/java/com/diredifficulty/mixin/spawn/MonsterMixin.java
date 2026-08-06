package com.diredifficulty.mixin.spawn;

import com.diredifficulty.spawn.SpawnControl;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hostile spawning during the day — {@code docs/DESIGN.md} §5.2(a).
 *
 * <p>{@code Monster.isDarkEnoughToSpawn} is <b>the single light gate</b>:
 * {@code checkMonsterSpawnRules} calls it, and {@code checkSurfaceMonstersSpawnRules} calls
 * that. Forcing it true opens day spawning for every monster routed through those two
 * predicates in one hook.
 *
 * <p>Two things are deliberately NOT touched, and both are correct:
 *
 * <ul>
 * <li>Mobs using {@code checkAnyLightMonsterSpawnRules} never consult light in the first
 *     place, so they are unaffected — there is nothing to open for them.</li>
 * <li>{@code checkSurfaceMonstersSpawnRules} additionally requires {@code level.canSeeSky(pos)}.
 *     That stays, so surface monsters still need sky. Day spawning means "the sun no longer
 *     stops them", not "they spawn in your lit base".</li>
 * </ul>
 *
 * <p>Verified against the 26.2 sources: {@code Monster.java:85}, {@code public static}.
 */
@Mixin(Monster.class)
public abstract class MonsterMixin {
	@Inject(
			method = "isDarkEnoughToSpawn(Lnet/minecraft/world/level/ServerLevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)Z",
			at = @At("HEAD"),
			cancellable = true)
	private static void direDifficulty$daySpawning(
			final ServerLevelAccessor level,
			final BlockPos pos,
			final RandomSource random,
			final CallbackInfoReturnable<Boolean> cir) {
		if (SpawnControl.daySpawningEnabled()) {
			cir.setReturnValue(true);
		}
	}
}
