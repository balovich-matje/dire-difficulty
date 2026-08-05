package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;
import com.escalation.elite.EliteMob;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #12 — Vampiric. Heals for 35% of the melee damage it deals.
 *
 * <p>Melee only: the tag test excludes the elite's own projectiles, its aura ticks and any
 * indirect damage it caused, so a Vampiric archer with a Volley cannot heal itself from
 * across the arena.
 *
 * <p><b>Suppressed by the §4.4 B2 sustain lockout.</b> For 80 ticks after a player damages
 * the elite, this heals nothing. That is the runtime half of Guarantee B — a player who is
 * landing hits always nets the health bar down — and it is checked here rather than assumed,
 * because §4.3's X6 only stops this stacking with {@code regenerating}; it does not stop
 * either one alone from out-healing a slow fight.
 */
public final class VampiricAffix implements Affix {
	@Override
	public String id() {
		return "vampiric";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.OFFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_VAMPIRIC;
	}

	@Override
	public double ehpFactor() {
		return Tuning.VAMPIRIC_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.melee(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		if (source.is(DamageTypeTags.IS_PROJECTILE) || dealt <= 0.0F) {
			return;
		}

		if (EliteMob.timers(mob).sustainLocked(level.getGameTime())) {
			return;
		}

		mob.heal(dealt * Tuning.VAMPIRIC_LIFESTEAL);
	}
}
