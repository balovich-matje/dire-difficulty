package com.escalation.spawn;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.escalation.Tuning;
import com.escalation.config.ConfigManager;
import com.escalation.config.EscalationConfig;
import com.escalation.progression.ScoreSource;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;

/**
 * The knob readers the spawn mixins call into ({@code docs/DESIGN.md} §5.2). Four dials — day
 * spawning, spawn frequency, mob cap, despawn distance — each behind one verified 26.2 hook.
 *
 * <p><b>All logic lives here; the mixins are three-line delegations.</b> That is the shape §5a
 * of the multiversion conventions requires and it is why it is worth doing now, before the
 * port: when the seven nodes arrive, only the mixin ANNOTATIONS fork, and the arithmetic in
 * this file stays single-implementation on every one of them.
 *
 * <p><b>Everything is clamped.</b> No combination of hand-edited config values can produce an
 * unbounded spawn loop, a zero mob cap, or a despawn distance that makes mobs immortal — the
 * ranges are {@link Tuning} constants and {@link EscalationConfig#sanitize()} enforces the same
 * ones on the file.
 */
public final class SpawnControl {
	/**
	 * Extra reasons a mob is immune to sunlight burning, beyond the day-spawn dial.
	 *
	 * <p>This exists because {@code Mob.isSunBurnTick} is <b>one hook with two callers</b>:
	 * §5.2(b)'s day-spawn burn immunity and §3.1's {@code sunwalker} affix. Registering a
	 * predicate rather than having this class name the affix system keeps the two features
	 * independent — the affix half can land, change or be cut without touching spawn control,
	 * and neither has to import the other.
	 *
	 * <p>Copy-on-write because it is written once at init and then read on the server thread
	 * for every burning mob, every tick.
	 */
	private static final List<Predicate<Mob>> SUN_IMMUNITY = new CopyOnWriteArrayList<>();

	private SpawnControl() {
	}

	/**
	 * Register a further reason a mob does not burn in sunlight. Called at init; the
	 * {@code sunwalker} affix is the intended caller.
	 */
	public static void addSunImmunity(final Predicate<Mob> predicate) {
		SUN_IMMUNITY.add(predicate);
	}

	// =====================================================================================
	// (a) Hostile spawning during the day.
	//
	// Monster.isDarkEnoughToSpawn is the single light gate: checkMonsterSpawnRules calls it,
	// and checkSurfaceMonstersSpawnRules calls that. Forcing it true opens day spawning for
	// every monster routed through those two predicates. Mobs that use
	// checkAnyLightMonsterSpawnRules are light-agnostic already and are correctly unaffected.
	//
	// checkSurfaceMonstersSpawnRules ADDITIONALLY requires level.canSeeSky(pos), which is
	// untouched — surface monsters still need sky, which is the behaviour we want.
	// =====================================================================================

	/** True when the light gate should be forced open. */
	public static boolean daySpawningEnabled() {
		return ConfigManager.get().daySpawning;
	}

	// =====================================================================================
	// (b) Day-spawn burn immunity.
	//
	// Without this the feature does nothing: day spawns catch fire and die within seconds.
	// =====================================================================================

	/** True when this mob must not take a sunlight burn tick. */
	public static boolean suppressSunBurn(final Mob mob) {
		EscalationConfig config = ConfigManager.get();

		// Only while day spawning is actually on. A server that turns day spawning off must
		// get vanilla burning back in the same edit, without a second knob to remember.
		if (config.daySpawning && config.daySpawnBurnImmunity) {
			return true;
		}

		for (Predicate<Mob> predicate : SUN_IMMUNITY) {
			if (predicate.test(mob)) {
				return true;
			}
		}

		return false;
	}

	// =====================================================================================
	// (c) Spawn frequency.
	// =====================================================================================

	/**
	 * How many times {@code NaturalSpawner.spawnForChunk} should run for this chunk this tick.
	 *
	 * <p>The multiplier is honest and monotone in {@code m}: run {@code floor(m)} times, then
	 * once more with probability {@code frac(m)}. For {@code m < 1} that degenerates to "run
	 * once with probability m" without a special case, and {@code m == 1.0} returns exactly 1,
	 * so the default config is bit-for-bit vanilla behaviour rather than vanilla-plus-noise.
	 *
	 * <p>The progression term is skipped entirely unless the player opted into it
	 * ({@link EscalationConfig#spawnRateProgressionBonus}, 0.0 by default). This runs once per
	 * spawning chunk per tick, so the default path must not touch the score at all.
	 *
	 * <p>When it IS on, the score is read at the chunk's centre column at sea level. That is a
	 * coarse choice and a deliberate one: {@code spawnForChunk} has no single position to speak
	 * of — it picks its own, repeatedly, inside the chunk — and a stable per-chunk reading is
	 * worth more here than a precise one.
	 */
	public static int spawnPasses(final ServerLevel level, final ChunkPos chunkPos) {
		EscalationConfig config = ConfigManager.get();
		double multiplier = config.spawnFrequencyMultiplier;

		if (config.spawnRateProgressionBonus > 0.0) {
			BlockPos probe = new BlockPos(chunkPos.getMiddleBlockX(), level.getSeaLevel(), chunkPos.getMiddleBlockZ());
			double score = ScoreSource.active().scoreAt(level, probe);
			multiplier *= Tuning.spawnRateMultiplier(score, config.spawnRateProgressionBonus);
		}

		multiplier = Tuning.clamp(multiplier, 0.0, Tuning.SPAWN_FREQUENCY_MAX);

		int passes = (int) multiplier;
		double fraction = multiplier - passes;
		if (fraction > 0.0 && level.getRandom().nextDouble() < fraction) {
			passes++;
		}

		return passes;
	}

	// =====================================================================================
	// (d) Mob cap and despawn distance.
	//
	// MONSTER only. Scaling CREATURE would quietly change farm rates and animal density,
	// which is not what any of the four presets promises.
	// =====================================================================================

	/**
	 * The monster cap, scaled.
	 *
	 * <p>One knob, both caps: {@code getMaxInstancesPerChunk} feeds the global limit in
	 * {@code NaturalSpawner$SpawnState.canSpawnForCategoryGlobal} AND the per-chunk limit in
	 * {@code LocalMobCapCalculator$MobCounts.canSpawn}. That is why this getter is the right
	 * target and a {@code SpawnState} mixin is not — the two caps can never disagree.
	 */
	public static int maxInstancesPerChunk(final MobCategory category, final int vanilla) {
		if (category != MobCategory.MONSTER) {
			return vanilla;
		}

		// Never 0: a zero cap stops monster spawning entirely, which is a different feature
		// than "fewer monsters" and is not what any preset asks for.
		return Math.max(1, (int) Math.round(vanilla * ConfigManager.get().mobCapMultiplier));
	}

	/** The distance beyond which a monster may despawn at random, scaled. */
	public static int despawnDistance(final MobCategory category, final int vanilla) {
		return scaleDespawn(category, vanilla);
	}

	/**
	 * The distance within which a monster never despawns, scaled by the same factor as
	 * {@link #despawnDistance}.
	 *
	 * <p>Scaling both by one factor preserves vanilla's 32/128 ratio, so the near-band never
	 * inverts past the far-band however the knob is set.
	 */
	public static int noDespawnDistance(final MobCategory category, final int vanilla) {
		return scaleDespawn(category, vanilla);
	}

	private static int scaleDespawn(final MobCategory category, final int vanilla) {
		if (category != MobCategory.MONSTER) {
			return vanilla;
		}

		return Math.max(1, (int) Math.round(vanilla * ConfigManager.get().despawnDistanceMultiplier));
	}
}
