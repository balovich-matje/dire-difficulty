package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.affix.Eligibility;
import com.diredifficulty.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #8 — Sunwalker. Immune to fire and to sunlight burning.
 *
 * <p><b>Implemented without a mixin, deliberately.</b> Design §6.4 tables a {@code MobSunMixin}
 * on {@code Mob.isSunBurnTick()} serving this affix and §5.2(b)'s day-spawn burn immunity from
 * one hook. That hook belongs to the spawn-control system, which owns the config flag half of
 * it; this affix reaches the same behaviour through two facts already in the engine, so the
 * two systems do not have to share an injection point and neither blocks the other:
 *
 * <ul>
 * <li>{@code hurtServer} returns false immediately for a source in {@code is_fire} when the
 *     victim has Fire Resistance — the third guard in the method, before anything else runs.
 *     That is the fire immunity, in full, including lava and every modded fire type.</li>
 * <li>{@code Mob.burnUndead} does not damage anything itself: it calls
 *     {@code igniteForSeconds(8)}. Clearing the fire ticks on the ticker pass therefore
 *     removes the visible burning as well as the damage, and it is the only thing that
 *     needed removing.</li>
 * </ul>
 *
 * <p>The effect is infinite, invisible and iconless — {@code ambient=false, visible=false,
 * showIcon=false} — so it neither draws swirls on the mob nor reads as a temporary buff a
 * player could wait out.
 */
public final class SunwalkerAffix implements Affix {
	/** Effectively permanent. {@code Integer.MAX_VALUE} is how vanilla spells "infinite". */
	private static final int INFINITE = Integer.MAX_VALUE;

	@Override
	public String id() {
		return "sunwalker";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_SUNWALKER;
	}

	@Override
	public double ehpFactor() {
		return Tuning.SUNWALKER_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.undead(mob);
	}

	@Override
	public void onApply(final ServerLevel level, final Mob mob, final Rarity rarity) {
		mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, INFINITE, 0, false, false, false));
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		if (mob.getRemainingFireTicks() > 0) {
			mob.setRemainingFireTicks(0);
		}
	}
}
