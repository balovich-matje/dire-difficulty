package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.EliteMob;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #9 — Escapist. <b>Once per lifetime</b>, at 25% HP or below: teleports 12–20 blocks
 * away and heals 40% of max health.
 *
 * <p><b>The flag is persistent, and that is the whole affix.</b> Design §4.4 B2 names this
 * explicitly: a transient flag would be dropped by a chunk unload, so a player who chased a
 * fleeing elite past a chunk boundary would face a full-strength escape again, and again. The
 * once-per-lifetime promise would be once per load. {@code EliteData.escapistUsed} lives in
 * the persistent record for exactly this reason and for no other.
 *
 * <p>Fires from {@code onDamageTaken} rather than from the damage modifier, so the escape is
 * a consequence of a hit that actually landed — it cannot be triggered by a blocked or
 * cancelled hit, and it cannot pre-empt a killing blow that was going to land anyway.
 *
 * <p>§4.3 bars two pairings, each closing a different out: X5 with {@code regenerating},
 * because heal-over-time plus heal-and-flee makes a kill attempt un-finishable, and X12 with
 * {@code stalker}, because something that always knows where you are and can leave when hurt
 * makes a fight neither party can end.
 */
public final class EscapistAffix implements Affix {
	@Override
	public String id() {
		return "escapist";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_ESCAPIST;
	}

	@Override
	public double ehpFactor() {
		return Tuning.ESCAPIST_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onDamageTaken(final ServerLevel level, final Mob mob, final DamageSource source,
			final float taken) {
		if (EliteMob.escapistUsed(mob) || !mob.isAlive()) {
			return;
		}

		if (mob.getHealth() > mob.getMaxHealth() * Tuning.ESCAPIST_HEALTH_TRIGGER) {
			return;
		}

		if (!teleport(level, mob)) {
			// No landing spot found: the charge is NOT spent. Burning the once-per-lifetime
			// flag on a teleport that did not happen would turn a cramped arena into a silent
			// removal of the affix.
			return;
		}

		mob.heal(mob.getMaxHealth() * (float) Tuning.ESCAPIST_HEAL_FRACTION);
		EliteMob.markEscapistUsed(mob);
	}

	/**
	 * Vanilla's own {@code randomTeleport} does the safety checks — solid ground, no
	 * suffocation, no liquid — and reports whether it found somewhere to land. Candidates are
	 * drawn in an annulus so the escape is always a real disengage rather than a shuffle.
	 */
	private static boolean teleport(final ServerLevel level, final Mob mob) {
		RandomSource random = level.getRandom();
		double span = Tuning.ESCAPIST_MAX_DISTANCE - Tuning.ESCAPIST_MIN_DISTANCE;

		for (int attempt = 0; attempt < Tuning.ESCAPIST_TELEPORT_ATTEMPTS; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double distance = Tuning.ESCAPIST_MIN_DISTANCE + random.nextDouble() * span;
			double x = mob.getX() + Math.cos(angle) * distance;
			double z = mob.getZ() + Math.sin(angle) * distance;
			double y = mob.getY() + (random.nextDouble() - 0.5) * 8.0;

			if (mob.randomTeleport(x, y, z, false)) {
				level.sendParticles(ParticleTypes.PORTAL, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
						mob.getZ(), 32, 0.5, 0.8, 0.5, 0.1);
				level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT,
						SoundSource.HOSTILE, 1.0F, 1.0F);
				return true;
			}
		}

		return false;
	}
}
