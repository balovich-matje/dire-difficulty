package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #14 — Enfeebling. Hits apply Weakness I for 6s.
 *
 * <p>The cheapest CONTROL affix at one point, and the one that shapes a fight rather than
 * winning it: Weakness reduces the victim's melee output without touching their bow, their
 * movement or their block, so it pressures one out and leaves five open.
 */
public final class EnfeeblingAffix implements Affix {
	@Override
	public String id() {
		return "enfeebling";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.CONTROL;
	}

	@Override
	public int cost() {
		return Tuning.COST_ENFEEBLING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.melee(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, Tuning.ENFEEBLING_DURATION_TICKS,
				Tuning.ENFEEBLING_AMPLIFIER, false, true), mob);
	}
}
