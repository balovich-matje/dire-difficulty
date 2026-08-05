package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;
import com.escalation.elite.EliteMob;
import com.escalation.elite.EliteTimers;
import com.escalation.elite.Rarity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * §3.1 #15 — Blinkstrike. Every ~6s, when more than 6 blocks from its target and with line of
 * sight, blinks to just behind it — ender-pearl style, <b>no damage on arrival</b>.
 *
 * <p>The three conditions are all load-bearing. The range floor stops it teleporting on top of
 * something it is already fighting, which would read as a stutter rather than as a gap-close.
 * The line-of-sight requirement is what keeps terrain working as counterplay — a Blinkstrike
 * elite cannot come through a wall, so breaking line of sight is still an out. And no damage
 * on arrival is what stops it being an unavoidable opener: the blink closes distance, the
 * mob's ordinary attack does the work, and the player gets the interval between the two.
 *
 * <p>Landing behind the target rather than in front is not flavour: it puts the elite where
 * the player is not looking, which costs the player a turn and is the actual cost of the
 * affix.
 *
 * <p>Three exclusions in §4.3, all about the same thing — X2 with {@code warded} and X11 with
 * {@code volley} both leave no approach window, and X4 with {@code swift} is two gap-closers
 * on one body. C5 bars the last of those independently; it is listed anyway so the intent
 * survives a cap edit.
 */
public final class BlinkstrikeAffix implements Affix {
	@Override
	public String id() {
		return "blinkstrike";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.MOBILITY;
	}

	@Override
	public int cost() {
		return Tuning.COST_BLINKSTRIKE;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.hasTargeting(mob);
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		long now = level.getGameTime();
		EliteTimers timers = EliteMob.timers(mob);

		if (now < timers.blinkReadyAt()) {
			return;
		}

		LivingEntity target = mob.getTarget();

		if (target == null || !target.isAlive()) {
			return;
		}

		double minRangeSq = Tuning.BLINKSTRIKE_MIN_RANGE * Tuning.BLINKSTRIKE_MIN_RANGE;

		if (mob.distanceToSqr(target) < minRangeSq || !mob.hasLineOfSight(target)) {
			return;
		}

		// "Behind" is the target's own facing, taken from its look vector and stepped
		// backwards, so a player who turns to face the elite denies it the flank.
		Vec3 look = target.getLookAngle();
		double x = target.getX() - look.x * Tuning.BLINKSTRIKE_BEHIND_OFFSET;
		double z = target.getZ() - look.z * Tuning.BLINKSTRIKE_BEHIND_OFFSET;

		if (!mob.randomTeleport(x, target.getY(), z, false)) {
			return;
		}

		level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
				mob.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT,
				SoundSource.HOSTILE, 0.8F, 1.2F);
		EliteMob.setTimers(mob, timers.withBlinkReadyAt(now + Tuning.BLINKSTRIKE_INTERVAL_TICKS));
	}
}
