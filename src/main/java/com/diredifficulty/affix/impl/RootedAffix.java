package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.EliteAttributes;
import com.diredifficulty.elite.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * §3.1 #11 — Rooted. {@code KNOCKBACK_RESISTANCE} and {@code EXPLOSION_KNOCKBACK_RESISTANCE}
 * both 1.0: it cannot be pushed, pulled or blasted.
 *
 * <p>Two attributes rather than one, because vanilla separates them and a mod that sets only
 * the first ships an "unpushable" mob that a wind charge still launches. Both are set with
 * {@code ADD_VALUE 1.0} over a base of 0, which is full immunity on the 0..1 range vanilla
 * clamps these to.
 *
 * <p>§4.3 X1 bars this with {@code warded}: unpushable and projectile-resistant together
 * close both the kite and the disengage, and no elite may close more than two of the six
 * player outs. §4.3 also declines to pair it with {@code splitting} — the eligibility
 * predicate there excludes anything oversized, and the design's note that a Rooted splitter is
 * barred is carried by the exclusion table so it survives an eligibility edit.
 */
public final class RootedAffix implements Affix {
	private static final Identifier KNOCKBACK_MODIFIER = EliteAttributes.id("affix_rooted_knockback");

	private static final Identifier EXPLOSION_MODIFIER = EliteAttributes.id("affix_rooted_explosion");

	@Override
	public String id() {
		return "rooted";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_ROOTED;
	}

	@Override
	public double ehpFactor() {
		return Tuning.ROOTED_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onApply(final ServerLevel level, final Mob mob, final Rarity rarity) {
		EliteAttributes.set(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_MODIFIER,
				Tuning.ROOTED_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);
		EliteAttributes.set(mob, Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, EXPLOSION_MODIFIER,
				Tuning.ROOTED_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);
	}
}
