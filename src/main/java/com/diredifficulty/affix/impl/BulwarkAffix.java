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
 * §3.1 #24 — Bulwark. +6 {@code ARMOR}, +4 {@code ARMOR_TOUGHNESS}.
 *
 * <p>Both attributes, not just armour. Armour alone is defeated by a big enough single hit —
 * that is what toughness exists to change — and an affix that a critical greatsword swing
 * ignores is not a defensive affix, it is a tax on chip damage.
 *
 * <p>Flat rather than multiplicative on purpose: vanilla's armour curve already has
 * diminishing returns built into it, so a flat +6 is a large upgrade on a bare zombie and a
 * modest one on an armoured skeleton. That is the right shape — it lifts the floor without
 * making the already-tanky unkillable, which is the failure mode §4.4 B1's audit exists to
 * catch.
 *
 * <p>§4.3's X3 bars this with {@code warded}: flat armour plus halved projectile damage means
 * no damage type gets through at a normal rate, which closes the burst-it-down out entirely.
 */
public final class BulwarkAffix implements Affix {
	private static final Identifier ARMOR_MODIFIER = EliteAttributes.id("affix_bulwark_armor");

	private static final Identifier TOUGHNESS_MODIFIER = EliteAttributes.id("affix_bulwark_toughness");

	@Override
	public String id() {
		return "bulwark";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_BULWARK;
	}

	@Override
	public double ehpFactor() {
		return Tuning.BULWARK_EHP_FACTOR;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onApply(final ServerLevel level, final Mob mob, final Rarity rarity) {
		EliteAttributes.set(mob, Attributes.ARMOR, ARMOR_MODIFIER, Tuning.BULWARK_ARMOR,
				AttributeModifier.Operation.ADD_VALUE);
		EliteAttributes.set(mob, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_MODIFIER,
				Tuning.BULWARK_ARMOR_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
	}
}
