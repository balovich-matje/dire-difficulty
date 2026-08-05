package com.escalation.client.config;

import com.escalation.Tuning;
import com.escalation.config.CombineMode;
import com.escalation.config.ConfigManager;
import com.escalation.config.EscalationConfig;
import com.escalation.config.Preset;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen for our knobs, mirroring Skill Proficiencies'
 * {@code client/config/ClothConfigScreen} structure: the save consumers mutate the live
 * {@link ConfigManager#get()} instance in place, and {@code setSavingRunnable} persists (and
 * sanitizes) it.
 *
 * <p>Kept in its own class so it is only ever class-loaded once Cloth Config is confirmed
 * present (see {@link ModMenuIntegration}), which is what keeps Cloth a soft dependency. The
 * Gson config file is the real interface; this screen is a convenience on top of it, and every
 * knob here is also editable by hand.
 *
 * <h2>The preset dropdown, which is the only non-obvious widget on the screen</h2>
 *
 * <p>{@code docs/DESIGN.md} §8: selecting a preset STAMPS the individual fields, and editing
 * any stamped field flips the preset to {@link Preset#CUSTOM}. Cloth runs every save consumer
 * and then the saving runnable, in an order this screen does not control, so "did the player
 * touch the dropdown?" has to be answered by comparing against the preset the screen OPENED
 * with rather than by widget ordering:
 *
 * <ul>
 * <li><b>Dropdown changed</b> — the player made a deliberate choice, so it is stamped, and it
 *     wins over any field they also edited in the same session. Picking "Hell on Earth" and
 *     getting Hell on Earth is the only behaviour that is not a surprise.</li>
 * <li><b>Dropdown unchanged, a field edited</b> — nothing is stamped, and
 *     {@link EscalationConfig#sanitize()} moves the label to Custom on save. No hidden
 *     override layer: what the file says is what runs.</li>
 * </ul>
 *
 * <h2>Ranges</h2>
 *
 * <p>Every {@code setMin}/{@code setMax} below matches {@link EscalationConfig#sanitize()}
 * exactly. The widget clamps what this screen can produce and {@code sanitize()} clamps a
 * hand-edited file; if the two disagreed the screen would show the player a value the mod
 * never uses. Defaults are {@link Tuning} constants for the same reason there are no numeric
 * literals in the config class.
 *
 * <p>Port note: Cloth and Mod Menu are gated to the newer versions only, so on the legacy nodes
 * this whole package leaves the client source set and the config file is the entire UI. That is
 * why nothing in the mod is reachable ONLY from this screen.
 */
public final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		EscalationConfig config = ConfigManager.get();

		// The preset this screen opened with. See the class javadoc — this is what makes
		// "the player touched the dropdown" answerable without depending on Cloth's save order.
		Preset opened = config.preset;

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("config.escalation.title"))
				.setSavingRunnable(() -> {
					if (config.preset != opened && config.preset != Preset.CUSTOM) {
						config.preset.stamp(config);
					}

					ConfigManager.save();
				});

		ConfigEntryBuilder eb = builder.entryBuilder();

		buildPresets(builder, eb, config);
		buildProgression(builder, eb, config);
		buildElites(builder, eb, config);
		buildSpawning(builder, eb, config);

		return builder.build();
	}

	private static void buildPresets(final ConfigBuilder builder, final ConfigEntryBuilder eb, final EscalationConfig config) {
		ConfigCategory presets = builder.getOrCreateCategory(Component.translatable("config.escalation.category.presets"));

		presets.addEntry(eb.startTextDescription(Component.translatable("config.escalation.category.presets.tooltip")).build());

		presets.addEntry(eb.startEnumSelector(Component.translatable("config.escalation.preset"), Preset.class, config.preset)
				.setDefaultValue(Preset.DEFAULT)
				.setEnumNameProvider(value -> Component.translatable(((Preset) value).translationKey()))
				.setTooltip(Component.translatable("config.escalation.preset.tooltip"))
				.setSaveConsumer(value -> config.preset = value)
				.build());
	}

	private static void buildProgression(final ConfigBuilder builder, final ConfigEntryBuilder eb, final EscalationConfig config) {
		ConfigCategory progression = builder.getOrCreateCategory(Component.translatable("config.escalation.category.progression"));

		progression.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.scoreAdvancements"), config.scoreAdvancements)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.escalation.scoreAdvancements.tooltip"))
				.setSaveConsumer(value -> config.scoreAdvancements = value)
				.build());
		progression.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.scoreArchetypes"), config.scoreArchetypes)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.escalation.scoreArchetypes.tooltip"))
				.setSaveConsumer(value -> config.scoreArchetypes = value)
				.build());
		progression.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.scoreSkillProficiencies"), config.scoreSkillProficiencies)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.escalation.scoreSkillProficiencies.tooltip"))
				.setSaveConsumer(value -> config.scoreSkillProficiencies = value)
				.build());

		progression.addEntry(eb.startEnumSelector(Component.translatable("config.escalation.combineMode"), CombineMode.class, config.combineMode)
				.setDefaultValue(CombineMode.MAX)
				.setEnumNameProvider(value -> Component.translatable(((CombineMode) value).translationKey()))
				.setTooltip(Component.translatable("config.escalation.combineMode.tooltip"))
				.setSaveConsumer(value -> config.combineMode = value)
				.build());

		progression.addEntry(eb.startIntField(Component.translatable("config.escalation.advancementTarget"), config.advancementTarget)
				.setDefaultValue(Tuning.SCORE_ADVANCEMENT_TARGET)
				.setMin(1).setMax(1000)
				.setTooltip(Component.translatable("config.escalation.advancementTarget.tooltip"))
				.setSaveConsumer(value -> config.advancementTarget = value)
				.build());
		progression.addEntry(eb.startIntField(Component.translatable("config.escalation.skillTotalTarget"), config.skillTotalTarget)
				.setDefaultValue(Tuning.SCORE_SKILL_TOTAL_TARGET)
				.setMin(1).setMax(1500)
				.setTooltip(Component.translatable("config.escalation.skillTotalTarget.tooltip"))
				.setSaveConsumer(value -> config.skillTotalTarget = value)
				.build());

		progression.addEntry(eb.startDoubleField(Component.translatable("config.escalation.spawnRateProgressionBonus"), config.spawnRateProgressionBonus)
				.setDefaultValue(Tuning.SPAWN_RATE_MAX_BONUS)
				.setMin(0.0).setMax(3.0)
				.setTooltip(Component.translatable("config.escalation.spawnRateProgressionBonus.tooltip"))
				.setSaveConsumer(value -> config.spawnRateProgressionBonus = value)
				.build());
	}

	private static void buildElites(final ConfigBuilder builder, final ConfigEntryBuilder eb, final EscalationConfig config) {
		ConfigCategory elites = builder.getOrCreateCategory(Component.translatable("config.escalation.category.elites"));

		// First on the tab, and the only entry here that is NOT preset-governed: it is a
		// display preference on THIS client, so no preset stamps it and nothing syncs it. The
		// tooltip says so, because everything under it on this tab behaves the other way.
		elites.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.elitePlate"), config.elitePlate)
				.setDefaultValue(true)
				.setTooltip(Component.translatable("config.escalation.elitePlate.tooltip"))
				.setSaveConsumer(value -> config.elitePlate = value)
				.build());

		elites.addEntry(eb.startDoubleField(Component.translatable("config.escalation.eliteChanceMax"), config.eliteChanceMax)
				.setDefaultValue(Tuning.PRESET_CLOUDS_ELITE_CHANCE_MAX)
				.setMin(0.0).setMax(1.0)
				.setTooltip(Component.translatable("config.escalation.eliteChanceMax.tooltip"))
				.setSaveConsumer(value -> config.eliteChanceMax = value)
				.build());
		elites.addEntry(eb.startIntField(Component.translatable("config.escalation.maxRarityTier"), config.maxRarityTier)
				.setDefaultValue(Tuning.PRESET_CLOUDS_MAX_RARITY_TIER)
				.setMin(1).setMax(4)
				.setTooltip(Component.translatable("config.escalation.maxRarityTier.tooltip"))
				.setSaveConsumer(value -> config.maxRarityTier = value)
				.build());
		elites.addEntry(eb.startDoubleField(Component.translatable("config.escalation.legendaryWeightFloor"), config.legendaryWeightFloor)
				.setDefaultValue(Tuning.PRESET_CLOUDS_LEGENDARY_WEIGHT_FLOOR)
				.setMin(0.0).setMax(100.0)
				.setTooltip(Component.translatable("config.escalation.legendaryWeightFloor.tooltip"))
				.setSaveConsumer(value -> config.legendaryWeightFloor = value)
				.build());

		elites.addEntry(eb.startIntField(Component.translatable("config.escalation.bonusAffixBudget"), config.bonusAffixBudget)
				.setDefaultValue(Tuning.PRESET_CLOUDS_BONUS_BUDGET)
				.setMin(0).setMax(12)
				.setTooltip(Component.translatable("config.escalation.bonusAffixBudget.tooltip"))
				.setSaveConsumer(value -> config.bonusAffixBudget = value)
				.build());
		elites.addEntry(eb.startIntField(Component.translatable("config.escalation.bonusAffixSlots"), config.bonusAffixSlots)
				.setDefaultValue(Tuning.PRESET_CLOUDS_BONUS_SLOTS)
				.setMin(0).setMax(4)
				.setTooltip(Component.translatable("config.escalation.bonusAffixSlots.tooltip"))
				.setSaveConsumer(value -> config.bonusAffixSlots = value)
				.build());
		elites.addEntry(eb.startIntField(Component.translatable("config.escalation.bonusFromRarityTier"), config.bonusFromRarityTier)
				.setDefaultValue(Tuning.PRESET_CLOUDS_BONUS_FROM_TIER)
				.setMin(1).setMax(4)
				.setTooltip(Component.translatable("config.escalation.bonusFromRarityTier.tooltip"))
				.setSaveConsumer(value -> config.bonusFromRarityTier = value)
				.build());

		elites.addEntry(eb.startStrList(Component.translatable("config.escalation.allowedAffixClasses"), config.allowedAffixClasses)
				.setDefaultValue(java.util.List.of())
				.setTooltip(Component.translatable("config.escalation.allowedAffixClasses.tooltip"))
				.setSaveConsumer(value -> config.allowedAffixClasses = value)
				.build());
	}

	private static void buildSpawning(final ConfigBuilder builder, final ConfigEntryBuilder eb, final EscalationConfig config) {
		ConfigCategory spawning = builder.getOrCreateCategory(Component.translatable("config.escalation.category.spawning"));

		spawning.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.daySpawning"), config.daySpawning)
				.setDefaultValue(Tuning.PRESET_CLOUDS_DAY_SPAWNING)
				.setTooltip(Component.translatable("config.escalation.daySpawning.tooltip"))
				.setSaveConsumer(value -> config.daySpawning = value)
				.build());
		spawning.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.daySpawnBurnImmunity"), config.daySpawnBurnImmunity)
				.setDefaultValue(Tuning.PRESET_CLOUDS_DAY_SPAWN_BURN_IMMUNITY)
				.setTooltip(Component.translatable("config.escalation.daySpawnBurnImmunity.tooltip"))
				.setSaveConsumer(value -> config.daySpawnBurnImmunity = value)
				.build());

		spawning.addEntry(eb.startDoubleField(Component.translatable("config.escalation.spawnFrequencyMultiplier"), config.spawnFrequencyMultiplier)
				.setDefaultValue(Tuning.PRESET_CLOUDS_SPAWN_FREQUENCY)
				.setMin(0.0).setMax(Tuning.SPAWN_FREQUENCY_MAX)
				.setTooltip(Component.translatable("config.escalation.spawnFrequencyMultiplier.tooltip"))
				.setSaveConsumer(value -> config.spawnFrequencyMultiplier = value)
				.build());
		spawning.addEntry(eb.startDoubleField(Component.translatable("config.escalation.mobCapMultiplier"), config.mobCapMultiplier)
				.setDefaultValue(Tuning.PRESET_CLOUDS_MOB_CAP)
				.setMin(Tuning.MOB_CAP_MULTIPLIER_MIN).setMax(Tuning.MOB_CAP_MULTIPLIER_MAX)
				.setTooltip(Component.translatable("config.escalation.mobCapMultiplier.tooltip"))
				.setSaveConsumer(value -> config.mobCapMultiplier = value)
				.build());
		spawning.addEntry(eb.startDoubleField(Component.translatable("config.escalation.despawnDistanceMultiplier"), config.despawnDistanceMultiplier)
				.setDefaultValue(Tuning.PRESET_CLOUDS_DESPAWN_DISTANCE)
				.setMin(Tuning.DESPAWN_MULTIPLIER_MIN).setMax(Tuning.DESPAWN_MULTIPLIER_MAX)
				.setTooltip(Component.translatable("config.escalation.despawnDistanceMultiplier.tooltip"))
				.setSaveConsumer(value -> config.despawnDistanceMultiplier = value)
				.build());

		spawning.addEntry(eb.startBooleanToggle(Component.translatable("config.escalation.theHunt"), config.theHunt)
				.setDefaultValue(Tuning.PRESET_CLOUDS_THE_HUNT)
				.setTooltip(Component.translatable("config.escalation.theHunt.tooltip"))
				.setSaveConsumer(value -> config.theHunt = value)
				.build());
	}
}
