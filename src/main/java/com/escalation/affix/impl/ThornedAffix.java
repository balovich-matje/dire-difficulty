package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #17 — Thorned. Reflects 25% of melee damage taken back at the attacker. Does not
 * reflect projectile or magic damage.
 *
 * <p><b>The reflect must not reflect itself.</b> Thorns damage carries an attacking entity
 * and is not tagged as a projectile, so without the explicit {@code minecraft:thorns} guard
 * two Thorned elites hitting each other — or one Thorned elite and a player wearing Thorns
 * armour — would bounce a diminishing but real ping-pong between them for as many frames as
 * the stack allows. The guard is one line and the bug it prevents is a stack overflow inside
 * the damage funnel.
 *
 * <p>Melee only is the whole point of the affix: it punishes occupying the same space, which
 * means it must leave the ranged out completely open. §4.3's X7 bars it with
 * {@code searing} for the same reason from the other direction — melee punished twice for
 * standing in one place closes that out rather than taxing it.
 */
public final class ThornedAffix implements Affix {
	@Override
	public String id() {
		return "thorned";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_THORNED;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onDamageTaken(final ServerLevel level, final Mob mob, final DamageSource source,
			final float taken) {
		if (taken <= 0.0F || source.is(DamageTypes.THORNS)) {
			return;
		}

		if (source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.IS_EXPLOSION)
				|| source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
			return;
		}

		Entity attacker = source.getDirectEntity();

		// The DIRECT entity, not the causing one: an arrow's cause is its shooter, and
		// reflecting a bow shot back at the archer is precisely what "does not reflect
		// projectile damage" rules out.
		if (!(attacker instanceof LivingEntity living) || attacker == mob) {
			return;
		}

		living.hurtServer(level, level.damageSources().thorns(mob), taken * Tuning.THORNED_REFLECT_FRACTION);
	}
}
