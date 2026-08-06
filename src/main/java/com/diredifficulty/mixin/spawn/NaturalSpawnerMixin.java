package com.diredifficulty.mixin.spawn;

import java.util.List;

import com.diredifficulty.spawn.SpawnControl;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Spawn frequency — {@code docs/DESIGN.md} §5.2(c).
 *
 * <p>{@code NaturalSpawner.spawnForChunk} runs once per spawning chunk per tick, from
 * {@code ServerChunkCache.tickSpawningChunk}. The multiplier is applied by running it more (or
 * less) often, which is honest and monotone: {@code floor(m)} passes plus one more with
 * probability {@code frac(m)}, so {@code m == 1.0} is exactly one pass and the default config
 * behaves bit-for-bit like vanilla.
 *
 * <p><b>{@code @WrapMethod} rather than an injected re-invocation, and that is forced.</b>
 * Replaying the method's body from a {@code TAIL} injection would need
 * {@code SpawnState.canSpawnForCategoryLocal}, {@code canSpawn} and {@code afterSpawn} — all
 * three are <b>private</b> in 26.2, so a replay would mean three accessors and a hand-copy of
 * vanilla's loop that could silently drift from it. Wrapping calls the real thing.
 *
 * <p>Verified against the 26.2 sources: {@code NaturalSpawner.java:116}, {@code public static},
 * and the three {@code SpawnState} members above at 478/492/527.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
	@WrapMethod(
			method = "spawnForChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;"
					+ "Lnet/minecraft/world/level/NaturalSpawner$SpawnState;Ljava/util/List;)V")
	private static void direDifficulty$spawnFrequency(
			final ServerLevel level,
			final LevelChunk chunk,
			final NaturalSpawner.SpawnState state,
			final List<MobCategory> spawningCategories,
			final Operation<Void> original) {
		int passes = SpawnControl.spawnPasses(level, chunk.getPos());

		for (int pass = 0; pass < passes; pass++) {
			original.call(level, chunk, state, spawningCategories);
		}
	}
}
