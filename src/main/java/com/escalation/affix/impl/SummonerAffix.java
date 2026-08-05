package com.escalation.affix.impl;

import java.util.List;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.elite.EliteMob;
import com.escalation.elite.EliteTimers;
import com.escalation.elite.Rarity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #4 — Summoner. Every 12s spawns 1–2 non-elite mobs of its own type, hard-capped at 4
 * alive, and every summon is flagged so it can never itself roll elite or summon.
 *
 * <p><b>Three independent bounds, because an adds-generator is the affix most able to run
 * away.</b> The cadence bounds the rate, the alive-cap bounds the standing army, and the
 * suppression flag bounds the depth — a summon that could summon is an exponential, not an
 * encounter. §4.3's X9 adds a fourth from outside: a Summoner may never also be Splitting,
 * because two generators on one body is an unbounded add fight however each is individually
 * capped.
 *
 * <p>The suppression flag is <b>persistent</b>, which is the part that is easy to get wrong.
 * A transient flag would be dropped by a chunk unload, and every summon that survived one
 * would come back a legitimate promotion candidate — the farm §2.3 exists to prevent, built
 * out of the mod's own mobs.
 *
 * <p>The alive count is measured, not remembered: a stored counter and a world disagree the
 * first time a summon despawns, burns, or is killed off-camera, and they disagree in the
 * direction that stops the affix working forever.
 */
public final class SummonerAffix implements Affix {
	@Override
	public String id() {
		return "summoner";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.SUPPORT;
	}

	@Override
	public int cost() {
		return Tuning.COST_SUMMONER;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		long now = level.getGameTime();
		EliteTimers timers = EliteMob.timers(mob);

		if (now < timers.summonReadyAt() || mob.getTarget() == null) {
			return;
		}

		int alive = countSummons(level, mob);

		if (alive >= Tuning.SUMMONER_ALIVE_CAP) {
			// Still re-arm, so a summoner at cap does not fire a full batch the instant one
			// of its adds dies.
			EliteMob.setTimers(mob, timers.withSummonReadyAt(now + Tuning.SUMMONER_INTERVAL_TICKS));
			return;
		}

		RandomSource random = level.getRandom();
		int wanted = Tuning.SUMMONER_MIN_PER_PULSE
				+ random.nextInt(Tuning.SUMMONER_MAX_PER_PULSE - Tuning.SUMMONER_MIN_PER_PULSE + 1);
		int room = Tuning.SUMMONER_ALIVE_CAP - alive;
		int count = Math.min(wanted, room);

		for (int i = 0; i < count; i++) {
			spawnOne(level, mob, random);
		}

		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK,
				SoundSource.HOSTILE, 1.0F, 0.9F + random.nextFloat() * 0.2F);
		EliteMob.setTimers(mob, timers.withSummonReadyAt(now + Tuning.SUMMONER_INTERVAL_TICKS));
	}

	private static void spawnOne(final ServerLevel level, final Mob summoner, final RandomSource random) {
		Mob summon = (Mob) summoner.getType().create(level, EntitySpawnReason.MOB_SUMMONED);

		if (summon == null) {
			return;
		}

		double x = summoner.getX() + (random.nextDouble() - 0.5) * 4.0;
		double z = summoner.getZ() + (random.nextDouble() - 0.5) * 4.0;
		summon.snapTo(x, summoner.getY(), z, random.nextFloat() * 360.0F, 0.0F);

		// The flag goes on BEFORE the entity joins the world, so the promotion roll that runs
		// at the tail of finalizeSpawn already sees it. MOB_SUMMONED is not one of the two
		// reasons the roller accepts either — belt and braces, because only one of the two is
		// load-bearing on a re-entrant path and it is not obvious which.
		EliteMob.suppress(summon);
		summon.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, summoner.getY(), z)),
				EntitySpawnReason.MOB_SUMMONED, null);
		summon.setTarget(summoner.getTarget());
		level.addFreshEntity(summon);
	}

	/**
	 * Live summons of this summoner's own species inside the leash radius.
	 *
	 * <p>Counted by species and radius rather than by an owner reference on purpose: an owner
	 * link is another persistent field to migrate and to get wrong, and the failure mode of
	 * this approximation is that two summoners standing on each other share a cap — which is
	 * the conservative direction.
	 */
	private static int countSummons(final ServerLevel level, final Mob summoner) {
		List<Mob> summons = level.getEntitiesOfClass(Mob.class,
				summoner.getBoundingBox().inflate(Tuning.SUMMONER_LEASH_RADIUS),
				candidate -> candidate != summoner
						&& candidate.isAlive()
						&& candidate.getType() == summoner.getType()
						&& EliteMob.suppressed(candidate));
		return summons.size();
	}
}
