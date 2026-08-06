package com.diredifficulty.affix.impl;

import java.util.List;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.Rarity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * §3.1 #6 — Searing Aura. 1.0 magic damage per second to players within 4 blocks.
 *
 * <p><b>No knockback on the tick, and that is enforced structurally rather than hoped for.</b>
 * The design calls this a pressure aura, not a shove: an aura that pushed would combine with
 * the four-block radius to make melee range unreachable, closing the "trade in melee" out on
 * its own. {@code damageSources().magic()} is the source that cannot shove — {@code
 * minecraft:magic} is a member of the {@code no_knockback} damage-type tag in vanilla data
 * (verified in {@code DamageTypeTagsProvider}), which is the tag {@code hurtServer} consults
 * before applying any knockback at all, and it also carries no attacking entity for the
 * knockback vector to be computed from. Two independent reasons, either sufficient.
 *
 * <p>The cost of that choice is honest and worth stating: with no entity on the source, the
 * death message is generic and the aura earns no kill credit. An entity-carrying source would
 * fix both and reintroduce the shove, and the shove is the thing the design rules out.
 *
 * <p>Damage is scaled by the ticker interval rather than assuming one call per second, so
 * retuning {@code ELITE_TICK_INTERVAL} cannot silently change this affix's DPS.
 */
public final class SearingAuraAffix implements Affix {
	@Override
	public String id() {
		return "searing";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.AURA;
	}

	@Override
	public int cost() {
		return Tuning.COST_SEARING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		float damage = Tuning.SEARING_DAMAGE_PER_SECOND * interval / 20.0F;
		List<Player> victims = level.getEntitiesOfClass(Player.class,
				mob.getBoundingBox().inflate(Tuning.SEARING_RADIUS), AuraTargets::isAffectable);

		for (final Player victim : victims) {
			victim.hurtServer(level, level.damageSources().magic(), damage);
		}

		level.sendParticles(ParticleTypes.SMALL_FLAME, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
				mob.getZ(), 6, Tuning.SEARING_RADIUS * 0.4, 0.4, Tuning.SEARING_RADIUS * 0.4, 0.0);
	}
}
