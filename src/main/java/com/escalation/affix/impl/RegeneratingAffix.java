package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.elite.EliteMob;
import com.escalation.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #23 — Regenerating. Heals 2% of max health per second.
 *
 * <p><b>Suppressed by the §4.4 B2 sustain lockout, which is the affix's entire safety
 * margin.</b> Two percent per second against a Legendary's 3.4x health pool is a real number,
 * and against a player who has to break off it compounds; the lockout means it only ever
 * refills what nobody is currently taking off. That is Guarantee B's runtime half stated as a
 * property rather than as a hope: a player who is landing hits always nets the bar down.
 *
 * <p>It is also the single largest EHP contributor in the catalogue at 1.45, which is what
 * makes the §4.4 B1 audit bite — a Legendary that draws this AND {@code bulwark} exceeds the
 * index, and the audit drops this one, because it is the highest-cost DEFENSE affix in the
 * set.
 *
 * <p>Healing is scaled by the ticker interval rather than assuming one call per second, so
 * retuning {@code ELITE_TICK_INTERVAL} cannot silently change the regeneration rate.
 */
public final class RegeneratingAffix implements Affix {
	@Override
	public String id() {
		return "regenerating";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_REGENERATING;
	}

	@Override
	public double ehpFactor() {
		return Tuning.REGENERATING_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		if (EliteMob.timers(mob).sustainLocked(level.getGameTime())) {
			return;
		}

		if (mob.getHealth() >= mob.getMaxHealth()) {
			return;
		}

		mob.heal((float) (mob.getMaxHealth() * Tuning.REGENERATING_FRACTION_PER_SECOND * interval / 20.0));
	}
}
