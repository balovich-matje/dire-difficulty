package com.diredifficulty.config;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.diredifficulty.Tuning;

/**
 * The four difficulty bundles, on a weather ladder so the ramp is legible from the names
 * alone, plus {@link #CUSTOM} ({@code docs/DESIGN.md} §8).
 *
 * <p><b>This enum holds the SHAPE; {@link Tuning} holds every number.</b> Same split as the
 * loot table's: {@code stamp} says which fields a preset governs and in what order, and each
 * value it writes is a {@code Tuning.PRESET_*} constant. There is no numeric literal below.
 *
 * <p><b>The semantics, and they matter for the UX.</b> Selecting a preset STAMPS the individual
 * fields. Editing any stamped field flips the config's preset to {@link #CUSTOM}, in
 * {@link DireDifficultyConfig#sanitize()}. There is no hidden override layer and no "preset wins
 * over your edit" surprise — what the file says is what runs, and the preset name is a label
 * for a set of values rather than a set of values that outranks them.
 *
 * <p><b>Guarantees A and B are not preset knobs.</b> Nothing this enum writes can reach the
 * one-hit damage ceiling or the effective-HP audit. Hell on Earth is faster, denser and
 * relentless — never a coin-flip death from full health, and never a mob that cannot be killed
 * ({@code docs/DESIGN.md} §4.4).
 */
public enum Preset {
	/** Gentler than vanilla. Elites exist as a rare treat, never as a threat. */
	SUNNY_DAY,

	/** The intended default. Vanilla, plus a real elite layer that grows with you. */
	GATHERING_CLOUDS,

	/** For a party with an Archetypes build online. Elites are the content. */
	STORM_FRONT,

	/** Progression is ignored; the world is already at maximum. */
	HELL_ON_EARTH,

	/**
	 * Not a bundle — the label a config wears once any governed field diverges from the
	 * preset it named. {@link #stamp} is a no-op here by design: there is nothing to stamp,
	 * and stamping anything would be exactly the override layer §8 forbids.
	 */
	CUSTOM;

	/** What a fresh install runs. */
	public static final Preset DEFAULT = GATHERING_CLOUDS;

	/**
	 * The fields {@link #stamp} writes, by their exact name in {@link DireDifficultyConfig} —
	 * which is also their key in the JSON file.
	 *
	 * <p>This list is the single source of truth for "is this field preset-governed?". It is
	 * read by {@link #matches} to decide whether a config still matches its label, and by
	 * {@link ConfigManager} to tell a file that merely NAMES a preset from one that also
	 * spells out values. Adding a governed field means adding it here and in {@link #stamp};
	 * the static initialiser below turns a name that no longer resolves into a loud boot
	 * failure instead of a knob that silently stops being governed.
	 */
	public static final List<String> GOVERNED_FIELDS = List.of(
			"ignoreProgression",
			"eliteChanceMax",
			"maxRarityTier",
			"legendaryWeightFloor",
			"bonusAffixBudget",
			"bonusAffixSlots",
			"bonusFromRarityTier",
			"allowedAffixClasses",
			"daySpawning",
			"daySpawnBurnImmunity",
			"spawnFrequencyMultiplier",
			"mobCapMultiplier",
			"despawnDistanceMultiplier",
			"theHunt");

	private static final Field[] GOVERNED = resolveGoverned();

	private static Field[] resolveGoverned() {
		Field[] fields = new Field[GOVERNED_FIELDS.size()];

		for (int i = 0; i < fields.length; i++) {
			String name = GOVERNED_FIELDS.get(i);
			try {
				fields[i] = DireDifficultyConfig.class.getDeclaredField(name);
			} catch (NoSuchFieldException e) {
				// Deliberately fatal, and deliberately at class-init rather than at first use.
				// A governed field that was renamed away would otherwise stop being governed
				// silently: the preset would keep its label while that knob drifted, which is
				// the class of bug this whole file exists to make impossible.
				throw new IllegalStateException(
						"Preset.GOVERNED_FIELDS names '" + name + "', which is not a field of DireDifficultyConfig", e);
			}
		}

		return fields;
	}

	/** The lang key for this preset's display name in the Cloth screen. */
	public String translationKey() {
		return "config.dire_difficulty.preset." + this.name().toLowerCase(Locale.ROOT);
	}

	/**
	 * Write this preset's values over every field it governs. Nothing outside
	 * {@link #GOVERNED_FIELDS} is touched — the score-source toggles, the normalisation
	 * targets and the combine mode are statements about the player's install, not about how
	 * hard they want the game, so a preset never moves them.
	 *
	 * <p><b>Does not sanitize, on purpose.</b> {@link DireDifficultyConfig#sanitize()} calls
	 * {@link #matches}, which stamps a scratch instance — a stamp that sanitized would recurse
	 * without a base case. Callers sanitize instead, and every path that stamps goes on to
	 * {@link ConfigManager#save()}, which does.
	 */
	public void stamp(final DireDifficultyConfig config) {
		switch (this) {
			case SUNNY_DAY -> {
				config.ignoreProgression = Tuning.PRESET_SUNNY_IGNORE_PROGRESSION;
				config.eliteChanceMax = Tuning.PRESET_SUNNY_ELITE_CHANCE_MAX;
				config.maxRarityTier = Tuning.PRESET_SUNNY_MAX_RARITY_TIER;
				config.legendaryWeightFloor = Tuning.PRESET_SUNNY_LEGENDARY_WEIGHT_FLOOR;
				config.bonusAffixBudget = Tuning.PRESET_SUNNY_BONUS_BUDGET;
				config.bonusAffixSlots = Tuning.PRESET_SUNNY_BONUS_SLOTS;
				config.bonusFromRarityTier = Tuning.PRESET_SUNNY_BONUS_FROM_TIER;
				// The one preset that restricts the catalogue: no auras, no on-death effects,
				// no on-hit offence. What is left cannot pressure a player who is standing
				// still, which is what "never a threat" means mechanically.
				config.allowedAffixClasses = List.of("MOBILITY", "UTILITY", "SUPPORT");
				config.daySpawning = Tuning.PRESET_SUNNY_DAY_SPAWNING;
				config.daySpawnBurnImmunity = Tuning.PRESET_SUNNY_DAY_SPAWN_BURN_IMMUNITY;
				config.spawnFrequencyMultiplier = Tuning.PRESET_SUNNY_SPAWN_FREQUENCY;
				config.mobCapMultiplier = Tuning.PRESET_SUNNY_MOB_CAP;
				config.despawnDistanceMultiplier = Tuning.PRESET_SUNNY_DESPAWN_DISTANCE;
				config.theHunt = Tuning.PRESET_SUNNY_THE_HUNT;
			}
			case GATHERING_CLOUDS -> {
				config.ignoreProgression = Tuning.PRESET_CLOUDS_IGNORE_PROGRESSION;
				config.eliteChanceMax = Tuning.PRESET_CLOUDS_ELITE_CHANCE_MAX;
				config.maxRarityTier = Tuning.PRESET_CLOUDS_MAX_RARITY_TIER;
				config.legendaryWeightFloor = Tuning.PRESET_CLOUDS_LEGENDARY_WEIGHT_FLOOR;
				config.bonusAffixBudget = Tuning.PRESET_CLOUDS_BONUS_BUDGET;
				config.bonusAffixSlots = Tuning.PRESET_CLOUDS_BONUS_SLOTS;
				config.bonusFromRarityTier = Tuning.PRESET_CLOUDS_BONUS_FROM_TIER;
				config.allowedAffixClasses = List.of();
				config.daySpawning = Tuning.PRESET_CLOUDS_DAY_SPAWNING;
				config.daySpawnBurnImmunity = Tuning.PRESET_CLOUDS_DAY_SPAWN_BURN_IMMUNITY;
				config.spawnFrequencyMultiplier = Tuning.PRESET_CLOUDS_SPAWN_FREQUENCY;
				config.mobCapMultiplier = Tuning.PRESET_CLOUDS_MOB_CAP;
				config.despawnDistanceMultiplier = Tuning.PRESET_CLOUDS_DESPAWN_DISTANCE;
				config.theHunt = Tuning.PRESET_CLOUDS_THE_HUNT;
			}
			case STORM_FRONT -> {
				config.ignoreProgression = Tuning.PRESET_STORM_IGNORE_PROGRESSION;
				config.eliteChanceMax = Tuning.PRESET_STORM_ELITE_CHANCE_MAX;
				config.maxRarityTier = Tuning.PRESET_STORM_MAX_RARITY_TIER;
				config.legendaryWeightFloor = Tuning.PRESET_STORM_LEGENDARY_WEIGHT_FLOOR;
				config.bonusAffixBudget = Tuning.PRESET_STORM_BONUS_BUDGET;
				config.bonusAffixSlots = Tuning.PRESET_STORM_BONUS_SLOTS;
				config.bonusFromRarityTier = Tuning.PRESET_STORM_BONUS_FROM_TIER;
				config.allowedAffixClasses = List.of();
				config.daySpawning = Tuning.PRESET_STORM_DAY_SPAWNING;
				config.daySpawnBurnImmunity = Tuning.PRESET_STORM_DAY_SPAWN_BURN_IMMUNITY;
				config.spawnFrequencyMultiplier = Tuning.PRESET_STORM_SPAWN_FREQUENCY;
				config.mobCapMultiplier = Tuning.PRESET_STORM_MOB_CAP;
				config.despawnDistanceMultiplier = Tuning.PRESET_STORM_DESPAWN_DISTANCE;
				config.theHunt = Tuning.PRESET_STORM_THE_HUNT;
			}
			case HELL_ON_EARTH -> {
				config.ignoreProgression = Tuning.PRESET_HELL_IGNORE_PROGRESSION;
				config.eliteChanceMax = Tuning.PRESET_HELL_ELITE_CHANCE_MAX;
				config.maxRarityTier = Tuning.PRESET_HELL_MAX_RARITY_TIER;
				config.legendaryWeightFloor = Tuning.PRESET_HELL_LEGENDARY_WEIGHT_FLOOR;
				config.bonusAffixBudget = Tuning.PRESET_HELL_BONUS_BUDGET;
				config.bonusAffixSlots = Tuning.PRESET_HELL_BONUS_SLOTS;
				config.bonusFromRarityTier = Tuning.PRESET_HELL_BONUS_FROM_TIER;
				config.allowedAffixClasses = List.of();
				config.daySpawning = Tuning.PRESET_HELL_DAY_SPAWNING;
				config.daySpawnBurnImmunity = Tuning.PRESET_HELL_DAY_SPAWN_BURN_IMMUNITY;
				config.spawnFrequencyMultiplier = Tuning.PRESET_HELL_SPAWN_FREQUENCY;
				config.mobCapMultiplier = Tuning.PRESET_HELL_MOB_CAP;
				config.despawnDistanceMultiplier = Tuning.PRESET_HELL_DESPAWN_DISTANCE;
				config.theHunt = Tuning.PRESET_HELL_THE_HUNT;
			}
			case CUSTOM -> {
				// Nothing. See the constant's javadoc.
			}
			default -> throw new IllegalStateException("Unstampable preset " + this);
		}
	}

	/**
	 * True when {@code config}'s governed fields still hold exactly what this preset stamps.
	 * {@link #CUSTOM} matches everything — it is the absence of a claim, so there is nothing
	 * it can disagree with.
	 *
	 * <p>Implemented by stamping a scratch instance and comparing reflectively over
	 * {@link #GOVERNED_FIELDS} rather than by a hand-written field-by-field comparison. A
	 * second hand-written mirror of the field list is exactly the thing that drifts.
	 */
	public boolean matches(final DireDifficultyConfig config) {
		if (this == CUSTOM) {
			return true;
		}

		DireDifficultyConfig probe = new DireDifficultyConfig();
		this.stamp(probe);

		try {
			for (Field field : GOVERNED) {
				if (!valueEquals(field.get(config), field.get(probe))) {
					return false;
				}
			}
		} catch (IllegalAccessException e) {
			// Unreachable: every governed field is public and this class is in the same
			// unnamed module. Treating it as "diverged" is the safe direction — the worst
			// outcome is a config labelled Custom that happened to match a preset.
			return false;
		}

		return true;
	}

	/**
	 * Field equality for the governed set. Lists compare as SETS: reordering the affix-class
	 * whitelist is not a difficulty change, so it must not flip a preset to Custom.
	 */
	private static boolean valueEquals(final Object a, final Object b) {
		if (a instanceof Collection<?> ca && b instanceof Collection<?> cb) {
			return ca.size() == cb.size() && ca.containsAll(cb) && cb.containsAll(ca);
		}

		return Objects.equals(a, b);
	}
}
