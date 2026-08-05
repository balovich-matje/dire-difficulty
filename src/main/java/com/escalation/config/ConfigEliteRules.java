package com.escalation.config;

import com.escalation.affix.AffixClass;
import com.escalation.elite.EliteRules;

/**
 * The config system's implementation of {@link EliteRules} — the elite core's preset plug
 * point, backed by the loaded {@link EscalationConfig}.
 *
 * <p><b>Every accessor reads the live config on every call</b>, deliberately, rather than
 * snapshotting values at install time. The Cloth screen mutates the held instance in place, so
 * a snapshot would leave a player who just changed a preset playing the old one until restart —
 * with no error and nothing to see. Reading live also means there is exactly one copy of these
 * numbers in memory, which is the same "read live, never mirror" rule the compat bridges follow
 * for the sibling mods' values.
 *
 * <p><b>The elite core never reads a preset.</b> {@code docs/DESIGN.md} §8's semantics are that
 * selecting a preset STAMPS individual fields; there is no hidden override layer, and what the
 * config file says is what runs. So this class reads stamped fields and never
 * {@link EscalationConfig#preset} — the preset name is a label, and a label is not an input to
 * the game.
 */
public final class ConfigEliteRules implements EliteRules {
	public static final ConfigEliteRules INSTANCE = new ConfigEliteRules();

	private ConfigEliteRules() {
	}

	/** Install these rules over the built-in Gathering Clouds defaults. Called from common init. */
	public static void register() {
		EliteRules.install(INSTANCE);
	}

	@Override
	public double eliteChanceMax() {
		return ConfigManager.get().eliteChanceMax;
	}

	@Override
	public int maxRarityTier() {
		return ConfigManager.get().maxRarityTier;
	}

	@Override
	public double legendaryWeightFloor() {
		return ConfigManager.get().legendaryWeightFloor;
	}

	@Override
	public int bonusBudget() {
		return ConfigManager.get().bonusAffixBudget;
	}

	@Override
	public int bonusSlots() {
		return ConfigManager.get().bonusAffixSlots;
	}

	@Override
	public int bonusFromTier() {
		return ConfigManager.get().bonusFromRarityTier;
	}

	@Override
	public boolean ignoreProgression() {
		return ConfigManager.get().ignoreProgression;
	}

	@Override
	public boolean allows(final AffixClass affixClass) {
		// The config stores class names as strings, not as this enum: the file has to survive
		// the catalogue growing, and an unknown name must be ignored rather than fail to parse
		// the whole config. The enum name is the contract between the two.
		return ConfigManager.get().affixClassAllowed(affixClass.name());
	}
}
