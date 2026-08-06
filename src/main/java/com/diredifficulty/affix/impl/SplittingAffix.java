package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.affix.Eligibility;
import com.diredifficulty.elite.EliteMob;
import com.diredifficulty.elite.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #19 — Splitting. On death spawns 2 non-elite copies at 40% max health.
 * <b>One generation only.</b>
 *
 * <p>The generation counter is persistent, and the reasoning is the same as
 * {@code escapist}'s used-flag: a transient counter is dropped by a chunk unload, so a copy
 * that survived one would split again, and its copies would too. Two copies per split with no
 * depth bound is 2^n, and the n is "how many chunk boundaries did this fight cross".
 *
 * <p>Copies are also stamped never-promote, so a split can never produce an elite — the same
 * flag {@code summoner} uses, for the same reason. Two independent bounds on the same
 * runaway, because the affix that generates bodies is the one worth over-constraining.
 *
 * <p>§4.3 bars two pairings: X9 with {@code summoner}, an unbounded add fight, and X10 with
 * {@code volatile}, an explosion cascade. The size predicate lives in {@link Eligibility};
 * the design's "excluded on anything already Rooted" is an exclusion pair rather than an
 * eligibility clause, because an eligibility clause depends on draw order and an exclusion
 * does not.
 */
public final class SplittingAffix implements Affix {
	@Override
	public String id() {
		return "splitting";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEATH;
	}

	@Override
	public int cost() {
		return Tuning.COST_SPLITTING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.splittable(mob);
	}

	@Override
	public void onDeath(final ServerLevel level, final Mob mob, final Rarity rarity,
			final DamageSource source) {
		if (EliteMob.splitGeneration(mob) >= Tuning.SPLITTING_MAX_GENERATION) {
			return;
		}

		RandomSource random = level.getRandom();

		for (int i = 0; i < Tuning.SPLITTING_COPIES; i++) {
			spawnCopy(level, mob, random);
		}
	}

	private static void spawnCopy(final ServerLevel level, final Mob parent, final RandomSource random) {
		Mob copy = (Mob) parent.getType().create(level, EntitySpawnReason.MOB_SUMMONED);

		if (copy == null) {
			return;
		}

		double x = parent.getX() + (random.nextDouble() - 0.5) * 2.0;
		double z = parent.getZ() + (random.nextDouble() - 0.5) * 2.0;
		copy.snapTo(x, parent.getY(), z, random.nextFloat() * 360.0F, 0.0F);

		// Stamped BEFORE the entity joins the world, so the promotion roll at the tail of
		// finalizeSpawn already sees both flags.
		EliteMob.suppress(copy);
		EliteMob.setSplitGeneration(copy, EliteMob.splitGeneration(parent) + 1);
		copy.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, parent.getY(), z)),
				EntitySpawnReason.MOB_SUMMONED, null);

		// Health is set AFTER finalizeSpawn, which is where vanilla applies difficulty-scaled
		// equipment and can itself move max health.
		copy.setHealth(copy.getMaxHealth() * Tuning.SPLITTING_HEALTH_FRACTION);

		LivingEntity inheritedTarget = parent.getTarget();

		if (inheritedTarget != null) {
			copy.setTarget(inheritedTarget);
		}

		level.addFreshEntity(copy);
	}
}
