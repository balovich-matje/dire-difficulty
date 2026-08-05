package com.escalation.affix.impl;

import java.util.List;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * §3.1 #5 — Chilling Aura. Slowness I on players within 5 blocks, refreshed each second.
 *
 * <p>Refreshed rather than extended: the effect duration is short and re-applied while the
 * player stands in it, so walking out of the radius ends it in under two seconds. A long
 * duration reapplied on contact would be a slow that follows you across the map, which is a
 * different — and much worse — affix.
 *
 * <p>Slowness I and not II. §4.3's X14 already bars this from stacking with {@code frostbound}
 * because two stacking slows reach effective immobilisation, and §3.2's line is that nothing
 * in the catalogue disables player movement outright.
 */
public final class ChillingAuraAffix implements Affix {
	@Override
	public String id() {
		return "chilling";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.AURA;
	}

	@Override
	public int cost() {
		return Tuning.COST_CHILLING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		List<Player> victims = level.getEntitiesOfClass(Player.class,
				mob.getBoundingBox().inflate(Tuning.CHILLING_RADIUS), AuraTargets::isAffectable);

		for (final Player victim : victims) {
			victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
					Tuning.CHILLING_DURATION_TICKS, Tuning.CHILLING_AMPLIFIER, false, true), mob);
		}
	}
}
