package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.affix.Eligibility;
import com.diredifficulty.elite.EliteAttributes;
import com.diredifficulty.elite.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * §3.1 #2 — Relentless. {@code FOLLOW_RANGE} x2.5, and target acquisition ignores line of
 * sight: it aggros through walls and it keeps the target.
 *
 * <p><b>The second half is why this is a ticker affix and not just a modifier.</b> Vanilla's
 * {@code NearestAttackableTargetGoal} re-evaluates {@code TargetingConditions} on its
 * continue-check, and those conditions include line of sight — so a mob that loses sight of
 * you drops you within a tick or two no matter how large its follow range is. Re-asserting
 * the target every pass is what makes "keeps the target" true, and asserting it against a
 * player it cannot see is what makes "through walls" true. It is the same shape The Hunt uses
 * for the same reason, and for the same reason it needs no mixin.
 *
 * <p>Bounded by the follow range it just granted, so a Relentless elite is not a
 * server-wide tracker: it reaches 2.5x its own species' aggro range and no further.
 */
public final class RelentlessAffix implements Affix {
	private static final Identifier MODIFIER = EliteAttributes.id("affix_relentless");

	@Override
	public String id() {
		return "relentless";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.UTILITY;
	}

	@Override
	public int cost() {
		return Tuning.COST_RELENTLESS;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.hasTargeting(mob);
	}

	@Override
	public void onApply(final ServerLevel level, final Mob mob, final Rarity rarity) {
		EliteAttributes.multiply(mob, Attributes.FOLLOW_RANGE, MODIFIER,
				Tuning.RELENTLESS_FOLLOW_RANGE_MULTIPLIER);
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		LivingEntity current = mob.getTarget();

		// Still valid and still in reach: re-assert it, which is the whole "keeps the target"
		// half. The goal's own continue-check would have dropped it the moment a wall
		// intervened.
		if (current != null && current.isAlive() && mob.distanceToSqr(current) <= range * range) {
			mob.setTarget(current);
			return;
		}

		// Deliberately the no-line-of-sight lookup: getNearestPlayer does not raycast.
		Player nearest = level.getNearestPlayer(mob, range);

		if (nearest != null && !nearest.isSpectator() && !nearest.isCreative()) {
			mob.setTarget(nearest);
		}
	}
}
