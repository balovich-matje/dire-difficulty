package com.diredifficulty.elite;

import java.util.concurrent.atomic.AtomicReference;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.AffixClass;

/**
 * Everything the elite roll needs to know that a PRESET decides — the second plug point of
 * the elite core, alongside {@code progression/ScoreSource}.
 *
 * <p>{@code docs/DESIGN.md} §8 gives each of the four presets a row of knobs, and §8's own
 * semantics rule is that selecting a preset <b>stamps</b> individual config fields: there is
 * no hidden override layer, and what the config file says is what runs. So the elite core
 * must never read a preset — it reads the stamped values, and this interface is the shape
 * of the subset it reads. The config system implements it over its loaded config instance
 * and calls {@link #install(EliteRules)} from common init.
 *
 * <p>The numbers behind {@link #GATHERING_CLOUDS} are the §8.2 preset's, read live from
 * {@link Tuning} rather than copied, so this file carries no balance number of its own. That
 * preset is the designed default, so a build with only the elite half plays the game the
 * design intends rather than an arbitrary one.
 *
 * <p><b>Guarantees A and B are not on this interface, on purpose.</b> The damage ceiling and
 * the EHP audit are not preset knobs (§8.4), and the only way to keep that true structurally
 * is for no preset-facing type to be able to name them.
 */
public interface EliteRules {
	/** The §8.2 "Gathering Clouds" defaults — the intended default preset. */
	EliteRules GATHERING_CLOUDS = new EliteRules() {
		@Override
		public double eliteChanceMax() {
			return Tuning.PRESET_CLOUDS_ELITE_CHANCE_MAX;
		}

		@Override
		public int maxRarityTier() {
			return Tuning.PRESET_CLOUDS_MAX_RARITY_TIER;
		}

		@Override
		public double legendaryWeightFloor() {
			return Tuning.PRESET_CLOUDS_LEGENDARY_WEIGHT_FLOOR;
		}

		@Override
		public int bonusBudget() {
			return Tuning.PRESET_CLOUDS_BONUS_BUDGET;
		}

		@Override
		public int bonusSlots() {
			return Tuning.PRESET_CLOUDS_BONUS_SLOTS;
		}

		@Override
		public int bonusFromTier() {
			return Tuning.PRESET_CLOUDS_BONUS_FROM_TIER;
		}

		@Override
		public boolean ignoreProgression() {
			return Tuning.PRESET_CLOUDS_IGNORE_PROGRESSION;
		}

		@Override
		public boolean allows(final AffixClass affixClass) {
			return true;
		}
	};

	/** Ceiling for the elite-chance curve. Fed to {@code Tuning.eliteChance(score, max)}. */
	double eliteChanceMax();

	/**
	 * Highest rarity tier that may be drawn, 1..4 (1 = Uncommon). Weights above it are
	 * zeroed before the draw rather than the draw being rejected and retried, so a locked
	 * Legendary raises everything below it instead of producing fewer elites.
	 */
	int maxRarityTier();

	/** Floor under the Legendary draw weight. §8.4 uses it; every other preset leaves it 0. */
	double legendaryWeightFloor();

	/** Extra affix points granted at and above {@link #bonusFromTier}. */
	int bonusBudget();

	/** Extra affix slots granted at and above {@link #bonusFromTier}. */
	int bonusSlots();

	/** Lowest rarity tier, 1..4, that receives {@link #bonusBudget} and {@link #bonusSlots}. */
	int bonusFromTier();

	/**
	 * True when the score is pinned to 1.0 and progression is not consulted at all — §8.4's
	 * "the world is already at maximum".
	 */
	boolean ignoreProgression();

	/**
	 * Whether this affix class may be drawn at all. §8.1 allows only MOBILITY, UTILITY and
	 * SUPPORT; every other preset allows all eight.
	 */
	boolean allows(AffixClass affixClass);

	/** The rules in force. Never null. */
	static EliteRules active() {
		return Holder.ACTIVE.get();
	}

	/** Installs the config-backed rules, replacing {@link #GATHERING_CLOUDS}. */
	static void install(final EliteRules rules) {
		Holder.ACTIVE.set(rules);
	}

	/** See {@code ScoreSource.Holder} — same shape, same reason. */
	final class Holder {
		static final AtomicReference<EliteRules> ACTIVE = new AtomicReference<>(GATHERING_CLOUDS);

		private Holder() {
		}
	}
}
