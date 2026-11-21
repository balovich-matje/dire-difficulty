package com.diredifficulty.client.config;

import com.diredifficulty.Tuning;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.config.DireDifficultyConfig;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Mod Menu settings screen: General, Elites, Spawning. No presets since 2026-11-21 — the
 * world difficulty sets how far progression can push the game.
 */
public final class ClothConfigScreen {
	private static final String KEY = "config.dire_difficulty.";

	private ClothConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		DireDifficultyConfig config = ConfigManager.get();
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable(KEY + "title"))
				.setSavingRunnable(ConfigManager::save);
		ConfigEntryBuilder eb = builder.entryBuilder();

		ConfigCategory general = builder.getOrCreateCategory(Component.translatable(KEY + "category.general"));
		general.addEntry(eb.startTextDescription(Component.translatable(KEY + "category.general.tooltip")).build());
		general.addEntry(toggle(eb, "spawnElites", config.spawnElites, true, v -> config.spawnElites = v));
		general.addEntry(toggle(eb, "modifySpawns", config.modifySpawns, true, v -> config.modifySpawns = v));
		general.addEntry(toggle(eb, "theHunt", config.theHunt, true, v -> config.theHunt = v));

		ConfigCategory elites = builder.getOrCreateCategory(Component.translatable(KEY + "category.elites"));
		elites.addEntry(toggle(eb, "elitePlate", config.elitePlate, true, v -> config.elitePlate = v));
		elites.addEntry(toggle(eb, "eliteGearDrops", config.eliteGearDrops, false, v -> config.eliteGearDrops = v));
		elites.addEntry(toggle(eb, "eliteGlow", config.eliteGlow, true, v -> config.eliteGlow = v));

		ConfigCategory spawning = builder.getOrCreateCategory(Component.translatable(KEY + "category.spawning"));
		spawning.addEntry(toggle(eb, "daySpawning", config.daySpawning, false, v -> config.daySpawning = v));
		spawning.addEntry(toggle(eb, "daySpawnBurnImmunity", config.daySpawnBurnImmunity, true,
				v -> config.daySpawnBurnImmunity = v));
		spawning.addEntry(eb.startDoubleField(Component.translatable(KEY + "spawnFrequencyAtMax"), config.spawnFrequencyAtMax)
				.setDefaultValue(Tuning.STAGE_SPAWN_FREQUENCY_MAX)
				.setMin(1.0).setMax(Tuning.SPAWN_FREQUENCY_MAX)
				.setTooltip(Component.translatable(KEY + "spawnFrequencyAtMax.tooltip"))
				.setSaveConsumer(v -> config.spawnFrequencyAtMax = v)
				.build());
		spawning.addEntry(eb.startDoubleField(Component.translatable(KEY + "mobCapAtMax"), config.mobCapAtMax)
				.setDefaultValue(Tuning.STAGE_MOB_CAP_MAX)
				.setMin(1.0).setMax(Tuning.MOB_CAP_MULTIPLIER_MAX)
				.setTooltip(Component.translatable(KEY + "mobCapAtMax.tooltip"))
				.setSaveConsumer(v -> config.mobCapAtMax = v)
				.build());
		spawning.addEntry(eb.startDoubleField(Component.translatable(KEY + "despawnDistanceAtMax"), config.despawnDistanceAtMax)
				.setDefaultValue(Tuning.STAGE_DESPAWN_DISTANCE_MAX)
				.setMin(1.0).setMax(Tuning.DESPAWN_MULTIPLIER_MAX)
				.setTooltip(Component.translatable(KEY + "despawnDistanceAtMax.tooltip"))
				.setSaveConsumer(v -> config.despawnDistanceAtMax = v)
				.build());

		return builder.build();
	}

	private static me.shedaniel.clothconfig2.api.AbstractConfigListEntry<?> toggle(final ConfigEntryBuilder eb,
			final String id, final boolean value, final boolean defaultValue,
			final java.util.function.Consumer<Boolean> save) {
		return eb.startBooleanToggle(Component.translatable(KEY + id), value)
				.setDefaultValue(defaultValue)
				.setTooltip(Component.translatable(KEY + id + ".tooltip"))
				.setSaveConsumer(save)
				.build();
	}
}
