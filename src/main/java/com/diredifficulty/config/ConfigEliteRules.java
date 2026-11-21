package com.diredifficulty.config;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.EliteRules;

/**
 * The installed elite rules. Since presets were retired (2026-11-21) these are fixed numbers
 * from {@code Tuning}: the progression stage alone moves elite chance and rarity, and the
 * world difficulty caps the stage.
 */
public final class ConfigEliteRules implements EliteRules {
	public static final ConfigEliteRules INSTANCE = new ConfigEliteRules();

	private ConfigEliteRules() {
	}

	public static void register() {
		EliteRules.install(INSTANCE);
	}

	@Override
	public double eliteChanceMax() {
		return Tuning.ELITE_CHANCE_MAX;
	}

	@Override
	public int maxRarityTier() {
		return Tuning.MAX_RARITY_TIER;
	}

	@Override
	public double legendaryWeightFloor() {
		return 0.0;
	}

	@Override
	public int bonusBudget() {
		return 0;
	}

	@Override
	public int bonusSlots() {
		return 0;
	}

	@Override
	public int bonusFromTier() {
		return 1;
	}

	@Override
	public boolean ignoreProgression() {
		return false;
	}

	@Override
	public boolean allows(final AffixClass affixClass) {
		return true;
	}
}
