package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.elite.EliteAttributes;
import com.escalation.elite.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** §3.1 #1 — Swift. {@code MOVEMENT_SPEED} x1.35 via one permanent modifier. */
public final class SwiftAffix implements Affix {
	private static final Identifier MODIFIER = EliteAttributes.id("affix_swift");

	@Override
	public String id() {
		return "swift";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.MOBILITY;
	}

	@Override
	public int cost() {
		return Tuning.COST_SWIFT;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onApply(final ServerLevel level, final Mob mob, final Rarity rarity) {
		EliteAttributes.multiply(mob, Attributes.MOVEMENT_SPEED, MODIFIER, Tuning.SWIFT_SPEED_MULTIPLIER);
	}
}
