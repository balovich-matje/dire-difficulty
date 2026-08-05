package com.escalation.client.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Builds the Cloth Config screen. Scaffold: the categories are declared and empty, because
 * the config class they edit does not exist yet.
 *
 * <p>Kept in its own class so it is only ever class-loaded once Cloth Config is confirmed
 * present (see {@link ModMenuIntegration}), which is what keeps Cloth a soft dependency.
 *
 * <p>When the knobs land, the shape is Skill Proficiencies' verbatim: the save consumers
 * mutate the live config instance in place and {@code setSavingRunnable} persists it. The
 * preset semantics are the part to get right rather than the widgets — selecting a preset
 * STAMPS the individual fields, and editing any stamped field flips the preset to CUSTOM.
 * There is no hidden override layer, so what the file says is what runs
 * ({@code docs/DESIGN.md} §8).
 */
public final class ClothConfigScreen {
	private ClothConfigScreen() {
	}

	public static Screen create(final Screen parent) {
		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Component.translatable("config.escalation.title"))
				.setSavingRunnable(() -> {
					// Nothing to persist yet.
				});

		// Declared so the screen has a category to open on; entries arrive with the config class.
		ConfigCategory general = builder.getOrCreateCategory(Component.translatable("config.escalation.category.general"));
		general.addEntry(builder.entryBuilder()
				.startTextDescription(Component.translatable("config.escalation.category.general.tooltip"))
				.build());

		return builder.build();
	}
}
