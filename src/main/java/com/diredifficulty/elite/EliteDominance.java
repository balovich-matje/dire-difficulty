package com.diredifficulty.elite;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.diredifficulty.Tuning;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Legendary and Mythical elites occasionally pick a fight with a nearby monster of another
 * kind — a show of strength for a player watching from a distance (user idea 2026-11-21).
 *
 * <p>Only while the elite has no target of its own, only within a short radius, only now and
 * then, and with a long pause after each pick, so it reads as a display rather than a purge.
 * A player who comes close takes over: vanilla's own player-targeting goal replaces the target.
 * Never against another elite, a boss or the Warden. Runs from {@code EliteTicker}, which only
 * looks near players.
 */
public final class EliteDominance {
	private static final Map<Mob, Long> NEXT_PICK = new WeakHashMap<>();

	private EliteDominance() {
	}

	static void tick(final ServerLevel level, final Mob elite, final Rarity rarity) {
		if (rarity.tier() < Tuning.DOMINANCE_MIN_TIER) {
			return;
		}

		LivingEntity current = elite.getTarget();

		if (current != null && current.isAlive()) {
			return;
		}

		long now = level.getGameTime();
		Long next = NEXT_PICK.get(elite);

		if (next != null && now < next) {
			return;
		}

		// Roll at most once per DOMINANCE_CHECK_TICKS, so the chance is per check, not per tick.
		NEXT_PICK.put(elite, now + Tuning.DOMINANCE_CHECK_TICKS);

		if (level.getRandom().nextDouble() >= Tuning.DOMINANCE_CHANCE) {
			return;
		}

		List<Mob> candidates = level.getEntitiesOfClass(Mob.class,
				elite.getBoundingBox().inflate(Tuning.DOMINANCE_RADIUS),
				other -> isVictim(elite, other));
		Mob victim = null;
		double best = Double.MAX_VALUE;

		for (Mob other : candidates) {
			double dist = other.distanceToSqr(elite);

			if (dist < best && elite.hasLineOfSight(other)) {
				best = dist;
				victim = other;
			}
		}

		if (victim == null) {
			return;
		}

		elite.setTarget(victim);

		if (elite.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) {
			elite.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, victim);
		}

		NEXT_PICK.put(elite, now + Tuning.DOMINANCE_COOLDOWN_TICKS);
	}

	private static boolean isVictim(final Mob elite, final Mob other) {
		if (other == elite || !other.isAlive() || !(other instanceof Enemy)) {
			return false;
		}

		EntityType<?> type = other.getType();

		if (type == elite.getType() || EliteMob.isElite(other)) {
			return false;
		}

		return type != EntityTypes.WARDEN && type != EntityTypes.WITHER && type != EntityTypes.ENDER_DRAGON
				&& type != EntityTypes.ELDER_GUARDIAN;
	}
}
