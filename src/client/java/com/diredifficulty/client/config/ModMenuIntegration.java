package com.diredifficulty.client.config;

import com.diredifficulty.platform.Platform;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu entrypoint. Scaffold wiring only — the screen it opens is still empty.
 *
 * <p>This class references the Mod Menu API and our own {@link ClothConfigScreen}, and
 * nothing else. The actual Cloth Config classes live in that other class, which is only
 * touched once the screen is opened — so if Mod Menu is present but Cloth Config is not, the
 * mod reports "no config screen" instead of class-loading (and crashing on) the missing
 * Cloth API. Both are soft dependencies: the Gson config file works with neither installed.
 *
 * <p>Port note kept from Skill Proficiencies, because getting it wrong is crash-level. Cloth
 * and Mod Menu are gated to the newer versions only, and below that boundary this whole
 * package leaves the source set — at which point the {@code modmenu} entrypoint in
 * {@code fabric.mod.json} names a class that is not in the jar. That entry is blanked by
 * {@code processResources} conditioning in the build script and <b>never</b> by a
 * preprocessor directive: Fabric parses {@code fabric.mod.json} with a non-lenient JSON
 * reader, so a leftover directive line there is an unloadable mod.
 */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		Platform loader = Platform.INSTANCE;
		boolean clothPresent = loader.isModLoaded("cloth-config")
				|| loader.isModLoaded("cloth_config")
				|| loader.isModLoaded("cloth-config2");

		if (!clothPresent) {
			return parent -> null;
		}

		return ClothConfigScreen::create;
	}
}
