package com.escalation.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.escalation.Tuning;

/**
 * Player-editable knobs, persisted to {@code config/advanced-difficulty-settings.json} and
 * surfaced through the Mod Menu / Cloth Config screen.
 *
 * <p>Plain public fields with a no-arg constructor: this is (de)serialized directly by the
 * Gson bundled with Minecraft, so a missing field in an older file just keeps its default and
 * the config works whether or not Mod Menu and Cloth Config are installed.
 *
 * <p><b>No numeric literal lives in this file.</b> Every default is a {@link Tuning} constant.
 * That is the house rule taken literally rather than approximately: a retune is a one-line
 * edit in {@code Tuning.java} that moves {@code Tuning.class} and nothing else, and it moves
 * the shipped default with it.
 *
 * <p><b>Preset semantics</b> ({@code docs/DESIGN.md} §8), because they are the part that has
 * to be right rather than the widgets:
 *
 * <ul>
 * <li>Selecting a preset <b>stamps</b> the individual fields it governs.</li>
 * <li>Editing any stamped field flips {@link #preset} to {@link Preset#CUSTOM} — detected in
 *     {@link #sanitize()}, which runs on every save.</li>
 * <li>There is no hidden override layer. What the file says is what runs; the preset name is
 *     a LABEL for a set of values, never a set of values that wins over them.</li>
 * </ul>
 *
 * <p><b>This whole class is server-authoritative and nothing here is ever synced.</b> On a
 * dedicated server the server's own copy decides elite rates, spawn control and the Hunt; a
 * connected client's file changes nothing. The client copy exists so singleplayer (where the
 * integrated server reads the same file) and the Cloth screen both work.
 */
public final class EscalationConfig {
	// =====================================================================================
	// Preset
	// =====================================================================================

	/**
	 * The preset this configuration currently matches, or {@link Preset#CUSTOM} once any
	 * governed field has been edited away from it. Never null after {@link #sanitize()}.
	 */
	public Preset preset = Preset.DEFAULT;

	// =====================================================================================
	// Progression score — docs/DESIGN.md §5.1. NOT preset-governed: which mods you want
	// counted is a statement about your install, not about how hard you want the game.
	// =====================================================================================

	/** Count completed non-recipe advancements as a progression source. */
	public boolean scoreAdvancements = true;

	/** Count the player's Archetypes level as a progression source. Absent without that mod. */
	public boolean scoreArchetypes = true;

	/** Count the player's summed Skill Proficiencies levels. Absent without that mod. */
	public boolean scoreSkillProficiencies = true;

	/** How the sources that ANSWERED are folded into one score. */
	public CombineMode combineMode = CombineMode.MAX;

	/** Completed advancements that normalise that source to 1.0. */
	public int advancementTarget = Tuning.SCORE_ADVANCEMENT_TARGET;

	/** Summed skill levels that normalise that source to 1.0 (their ceiling is 1500). */
	public int skillTotalTarget = Tuning.SCORE_SKILL_TOTAL_TARGET;

	/**
	 * Extra spawn frequency at full progression, as a fraction: the multiplier becomes
	 * {@code 1 + this * score}. 0.0 is OFF, which is the shipped default — progression drives
	 * elites, and coupling it to raw spawn counts as well is opt-in.
	 */
	public double spawnRateProgressionBonus = Tuning.SPAWN_RATE_MAX_BONUS;

	// =====================================================================================
	// Client display. NOT preset-governed and never synced: how hard the game is and what
	// this player's own HUD shows are different questions, and a preset that reached across
	// and switched someone's overlay off would be the "hidden override layer" §8 forbids.
	//
	// Read ONLY from src/client. On a dedicated server this field is loaded and ignored;
	// each connected client reads its own file, which is the right owner for a display
	// preference.
	// =====================================================================================

	/**
	 * Draw the elite info plate — name, health bar and affix list — for the elite under the
	 * crosshair. Self-contained: no WTHIT and no Jade, and it needs neither installed.
	 */
	public boolean elitePlate = true;

	// =====================================================================================
	// Gear drops. Server-authoritative and read at the moment a mob dies, so both toggles
	// take effect on mobs that are already in the world — nothing is stamped at spawn time
	// and nothing has to be re-rolled for a change to apply.
	//
	// NOT preset-governed, deliberately and by instruction: whether worn equipment drops is
	// an economy decision, not a difficulty one. Hell on Earth arms its elites to the teeth
	// either way; whether you get to keep what they were wearing is a separate question, and
	// a preset that reached across and answered it would be exactly the hidden override
	// layer §8 forbids.
	// =====================================================================================

	/**
	 * Let an ELITE's worn equipment drop. Off means an elite never drops what it is wearing;
	 * its rarity loot table is untouched either way, because that is a different system.
	 *
	 * <p>Netherite armour never drops regardless of this setting — that rule is structural,
	 * not a knob.
	 */
	public boolean eliteGearDrops = true;

	/**
	 * Let ANY mob's naturally spawned equipment drop — the master switch, elites included. Off
	 * suppresses the whole vanilla gear-drop channel.
	 *
	 * <p>Equipment a mob PICKED UP is never suppressed by either toggle. Vanilla marks a
	 * picked-up slot as a guaranteed drop, and that item is something a player threw or lost;
	 * no difficulty setting should be able to eat it.
	 */
	public boolean allMobGearDrops = true;

	// =====================================================================================
	// PRESET-GOVERNED FIELDS START HERE.
	//
	// Every field below is named in Preset.GOVERNED_FIELDS and written by Preset.stamp.
	// Adding a governed field means adding it in BOTH places; Preset's class initialiser
	// verifies that every listed name resolves to a real field here and fails loudly if not.
	// =====================================================================================

	/**
	 * Pin the progression score to 1.0 and never read a source. Hell on Earth: the world is
	 * already at maximum from the first night.
	 */
	public boolean ignoreProgression = Tuning.PRESET_CLOUDS_IGNORE_PROGRESSION;

	// ---- Elites. Held as plain numbers rather than as the rarity/affix enums so that this
	// file, the config format on disk and the elite system can be retuned independently.

	/** Elite chance at full progression. The floor and the curve exponent are Tuning's. */
	public double eliteChanceMax = Tuning.PRESET_CLOUDS_ELITE_CHANCE_MAX;

	/** Highest rarity that may roll: 1 Uncommon, 2 Rare, 3 Epic, 4 Legendary. Loot tier follows rarity. */
	public int maxRarityTier = Tuning.PRESET_CLOUDS_MAX_RARITY_TIER;

	/** Minimum Legendary draw weight regardless of score. 0 leaves the quadratic curve alone. */
	public double legendaryWeightFloor = Tuning.PRESET_CLOUDS_LEGENDARY_WEIGHT_FLOOR;

	/** Extra affix points on top of the rarity's budget, from {@link #bonusFromRarityTier} up. */
	public int bonusAffixBudget = Tuning.PRESET_CLOUDS_BONUS_BUDGET;

	/** Extra affix slots on top of the rarity's slot count, from {@link #bonusFromRarityTier} up. */
	public int bonusAffixSlots = Tuning.PRESET_CLOUDS_BONUS_SLOTS;

	/** Lowest rarity tier (1..4) the two bonuses above apply to. */
	public int bonusFromRarityTier = Tuning.PRESET_CLOUDS_BONUS_FROM_TIER;

	/**
	 * Affix classes allowed to roll. <b>Empty means all</b>, which is the default; a non-empty
	 * list is a whitelist. Valid names are the {@code AffixClass} constants: {@code OFFENSE},
	 * {@code DEFENSE}, {@code CONTROL}, {@code MOBILITY}, {@code AURA}, {@code SUPPORT},
	 * {@code UTILITY}, {@code DEATH}.
	 *
	 * <p>Strings rather than the enum on purpose: the config file has to survive the affix
	 * catalogue growing, and an unknown name here must be ignored by the roller rather than
	 * failing to parse the whole file.
	 */
	public List<String> allowedAffixClasses = new ArrayList<>();

	// ---- Spawn control — docs/DESIGN.md §5.2.

	/** Let hostiles spawn in daylight. Surface monsters still need sky; only the light gate opens. */
	public boolean daySpawning = Tuning.PRESET_CLOUDS_DAY_SPAWNING;

	/**
	 * Suppress sunlight burning while {@link #daySpawning} is on. Without it, day spawns burn
	 * to death within seconds and the feature does nothing. Read only when day spawning is on.
	 */
	public boolean daySpawnBurnImmunity = Tuning.PRESET_CLOUDS_DAY_SPAWN_BURN_IMMUNITY;

	/** Natural spawn attempts per chunk per tick, as a multiplier. 1.0 is exactly vanilla. */
	public double spawnFrequencyMultiplier = Tuning.PRESET_CLOUDS_SPAWN_FREQUENCY;

	/** Monster mob cap, as a multiplier. One knob; it feeds both the global and the per-chunk cap. */
	public double mobCapMultiplier = Tuning.PRESET_CLOUDS_MOB_CAP;

	/** Monster despawn distances, as a multiplier. Below 1 they clear out sooner, above 1 they stick around. */
	public double despawnDistanceMultiplier = Tuning.PRESET_CLOUDS_DESPAWN_DISTANCE;

	/** The Hunt: untargeted monsters near a player are handed that player as a target. */
	public boolean theHunt = Tuning.PRESET_CLOUDS_THE_HUNT;

	/**
	 * Clamp every field into a sane range so a hand-edited file cannot break the math, then
	 * settle the preset label.
	 *
	 * <p>The clamp ranges here and the Cloth widget ranges must not disagree: the widget
	 * clamps what the screen can produce, this clamps a hand-edited file, and a disagreement
	 * would show the player a value the mod never uses. That exact bug is why Skill
	 * Proficiencies' screen and {@code sanitize()} carry matching ranges.
	 *
	 * <p>Order matters. Clamping runs FIRST so that the divergence check below compares the
	 * values that will actually run; the preset's own values are all in range, so a clamp
	 * never invents a divergence.
	 */
	public void sanitize() {
		if (this.preset == null) {
			this.preset = Preset.DEFAULT;
		}

		if (this.combineMode == null) {
			this.combineMode = CombineMode.MAX;
		}

		this.advancementTarget = Tuning.clampInt(this.advancementTarget, 1, 1000);
		this.skillTotalTarget = Tuning.clampInt(this.skillTotalTarget, 1, 1500);
		this.spawnRateProgressionBonus = Tuning.clamp(this.spawnRateProgressionBonus, 0.0, 3.0);

		this.eliteChanceMax = Tuning.clamp(this.eliteChanceMax, 0.0, 1.0);
		this.maxRarityTier = Tuning.clampInt(this.maxRarityTier, 1, 4);
		this.legendaryWeightFloor = Tuning.clamp(this.legendaryWeightFloor, 0.0, 100.0);
		this.bonusAffixBudget = Tuning.clampInt(this.bonusAffixBudget, 0, 12);
		this.bonusAffixSlots = Tuning.clampInt(this.bonusAffixSlots, 0, 4);
		this.bonusFromRarityTier = Tuning.clampInt(this.bonusFromRarityTier, 1, 4);
		this.allowedAffixClasses = normaliseClasses(this.allowedAffixClasses);

		this.spawnFrequencyMultiplier = Tuning.clamp(this.spawnFrequencyMultiplier, 0.0, Tuning.SPAWN_FREQUENCY_MAX);
		this.mobCapMultiplier = Tuning.clamp(this.mobCapMultiplier, Tuning.MOB_CAP_MULTIPLIER_MIN, Tuning.MOB_CAP_MULTIPLIER_MAX);
		this.despawnDistanceMultiplier = Tuning.clamp(this.despawnDistanceMultiplier, Tuning.DESPAWN_MULTIPLIER_MIN, Tuning.DESPAWN_MULTIPLIER_MAX);

		// The one place the "editing a stamped field flips the preset to CUSTOM" rule is
		// enforced. It runs on every save, so it catches a hand-edit of the file and a screen
		// edit alike, and it can only ever move the label TO Custom — a preset's values are
		// never written back over an edit here.
		if (this.preset != Preset.CUSTOM && !this.preset.matches(this)) {
			this.preset = Preset.CUSTOM;
		}
	}

	/** Uppercase, trim, drop blanks and duplicates. Unknown names survive — the roller ignores them. */
	private static List<String> normaliseClasses(final List<String> raw) {
		List<String> out = new ArrayList<>();
		if (raw == null) {
			return out;
		}

		for (String entry : raw) {
			if (entry == null) {
				continue;
			}

			String name = entry.trim().toUpperCase(Locale.ROOT);
			if (!name.isEmpty() && !out.contains(name)) {
				out.add(name);
			}
		}

		return out;
	}

	/** True when the affix class named {@code affixClassName} may roll under this config. */
	public boolean affixClassAllowed(final String affixClassName) {
		return this.allowedAffixClasses.isEmpty()
				|| this.allowedAffixClasses.contains(affixClassName.toUpperCase(Locale.ROOT));
	}

	/** Extra affix budget at a rarity tier (1..4), honouring {@link #bonusFromRarityTier}. */
	public int bonusBudgetAt(final int rarityTier) {
		return rarityTier >= this.bonusFromRarityTier ? this.bonusAffixBudget : 0;
	}

	/** Extra affix slots at a rarity tier (1..4), honouring {@link #bonusFromRarityTier}. */
	public int bonusSlotsAt(final int rarityTier) {
		return rarityTier >= this.bonusFromRarityTier ? this.bonusAffixSlots : 0;
	}
}
