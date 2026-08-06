package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #10 — Warded. Projectile damage reduced 50%. Melee unaffected.
 *
 * <p>Keyed on the {@code is_projectile} damage-type tag rather than on the projectile entity
 * class, so it covers arrows, tridents, fireballs and anything a mod adds under that tag, and
 * covers none of the melee, magic or environmental damage that shares an attacker.
 *
 * <p><b>This is the most heavily excluded affix in the catalogue, and each pairing closes a
 * different player out.</b> §4.3 bars it three times: X1 with {@code rooted}, because
 * projectile-resistant AND unpushable leaves neither ranged nor knockback working; X2 with
 * {@code blinkstrike}, because halved ranged damage on something that closes the gap at will
 * means kiting stops existing; and X3 with {@code bulwark}, because flat armour plus
 * projectile resistance means no damage type gets through at a normal rate.
 */
public final class WardedAffix implements Affix {
	@Override
	public String id() {
		return "warded";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_WARDED;
	}

	@Override
	public double ehpFactor() {
		return Tuning.WARDED_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public float modifyIncoming(final ServerLevel level, final Mob mob, final DamageSource source,
			final float damage) {
		if (!source.is(DamageTypeTags.IS_PROJECTILE)) {
			return damage;
		}

		return damage * (1.0F - Tuning.WARDED_PROJECTILE_REDUCTION);
	}
}
