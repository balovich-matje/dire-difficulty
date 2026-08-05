package com.escalation.affix.impl;

import java.util.List;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * §3.1 #25 — Quaking. Every 8s, a shockwave in a 4-block radius: 3 damage and a knockback,
 * telegraphed by a 0.5s wind-up particle.
 *
 * <p><b>The telegraph is the affix.</b> Design §3.2 allows burst only when it is shaped or
 * conditional, never flat, and the shape here is the half-second of warning: a player who
 * reads it and steps out takes nothing, and a player who does not takes three and gets
 * pushed. Without the wind-up this is an unavoidable periodic tax, which is a different and
 * much worse mechanic.
 *
 * <p>So the wind-up cannot be skippable by any interleaving, and that is why
 * {@link EliteTimers} carries two deadlines rather than one plus a flag. {@code quakeFireAt}
 * is armed only by the pass that drew the particles, and the pass that fires reads it and
 * clears it — a shockwave with no telegraph is not reachable from any ordering of ticks,
 * chunk loads or reloads.
 *
 * <p>The knockback is applied to velocity directly rather than through the damage source,
 * because the two must both happen: {@code hurtServer} gives the victim invulnerability
 * frames and can reject the damage outright, and a shove that silently vanished on a player
 * who had just been hit by something else would make the affix read as broken rather than as
 * dodged.
 *
 * <p>§4.3's X8 bars this with {@code shieldbreaker}: the shockwave that strips the shield
 * being also the damage removes the block channel entirely rather than punishing it.
 */
public final class QuakingAffix implements Affix {
	@Override
	public String id() {
		return "quaking";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.OFFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_QUAKING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.melee(mob);
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		long now = level.getGameTime();
		EliteTimers timers = EliteMob.timers(mob);

		if (timers.quakeFireAt() > 0L) {
			if (now >= timers.quakeFireAt()) {
				fire(level, mob);
				EliteMob.setTimers(mob, timers.withQuake(now + Tuning.QUAKING_INTERVAL_TICKS, 0L));
			}

			return;
		}

		if (now < timers.quakeReadyAt() || mob.getTarget() == null) {
			return;
		}

		windUp(level, mob);
		EliteMob.setTimers(mob, timers.withQuake(timers.quakeReadyAt(), now + Tuning.QUAKING_WINDUP_TICKS));
	}

	private static void windUp(final ServerLevel level, final Mob mob) {
		level.sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + 0.2, mob.getZ(), 40,
				Tuning.QUAKING_RADIUS * 0.5, 0.1, Tuning.QUAKING_RADIUS * 0.5, 0.02);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.EVOKER_PREPARE_ATTACK,
				SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	private static void fire(final ServerLevel level, final Mob mob) {
		List<Player> victims = level.getEntitiesOfClass(Player.class,
				mob.getBoundingBox().inflate(Tuning.QUAKING_RADIUS), AuraTargets::isAffectable);

		for (final Player victim : victims) {
			victim.hurtServer(level, level.damageSources().mobAttack(mob), Tuning.QUAKING_DAMAGE);

			double dx = victim.getX() - mob.getX();
			double dz = victim.getZ() - mob.getZ();
			double length = Math.sqrt(dx * dx + dz * dz);

			if (length > 1.0E-4) {
				victim.push(dx / length * Tuning.QUAKING_KNOCKBACK, 0.35,
						dz / length * Tuning.QUAKING_KNOCKBACK);
				victim.hurtMarked = true;
			}
		}

		level.sendParticles(ParticleTypes.SWEEP_ATTACK, mob.getX(), mob.getY() + 0.3, mob.getZ(), 12,
				Tuning.QUAKING_RADIUS * 0.6, 0.05, Tuning.QUAKING_RADIUS * 0.6, 0.0);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
				SoundSource.HOSTILE, 0.7F, 1.4F);
	}
}
