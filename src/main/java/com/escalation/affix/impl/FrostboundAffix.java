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
 * §3.1 #13 — Frostbound. Hits apply Slowness II for 4s and a brief freeze shiver.
 *
 * <p>The shiver is {@code setTicksFrozen}, vanilla's powder-snow counter, which drives the
 * blue vignette and the shaking on the client with no packet and no render code of this mod's
 * — the same "let the engine already own it" reasoning that makes {@code Attributes.SCALE}
 * carry the whole of §2.1's resizing. It is set, not accumulated: the counter decays on its
 * own, so repeated hits refresh rather than compound toward the freeze-damage threshold.
 *
 * <p>Slowness II here against Chilling's Slowness I is deliberate — this one costs a hit to
 * apply and lasts four seconds, that one is free and continuous. §4.3's X14 bars the two from
 * appearing together (as do C3 and C7 independently); the pair is kept explicit in the table
 * so the intent survives a cap edit.
 */
public final class FrostboundAffix implements Affix {
	@Override
	public String id() {
		return "frostbound";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.CONTROL;
	}

	@Override
	public int cost() {
		return Tuning.COST_FROSTBOUND;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.attacker(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Tuning.FROSTBOUND_DURATION_TICKS,
				Tuning.FROSTBOUND_AMPLIFIER, false, true), mob);
		victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), Tuning.FROSTBOUND_FREEZE_TICKS));
	}
}
