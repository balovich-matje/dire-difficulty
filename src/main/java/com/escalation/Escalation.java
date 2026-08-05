package com.escalation;

import com.escalation.platform.Platform;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Scaffold only — it registers nothing yet.
 *
 * <p><b>The mod id is permanent.</b> {@code escalation} names the mechanic, not the
 * marketing, so it survives any later change of the display name ("Advanced Difficulty
 * Settings" today). This is Skill Proficiencies' hardest-learned lesson applied up front:
 * that project was renamed and its id, namespace, attachment keys and source directory
 * stayed {@code specialities} forever, because changing an id orphans every world's stored
 * data. See {@code docs/DESIGN.md} §7.5 — never "fix" the id to match a title.
 *
 * <p><b>Shape this class keeps when the port comes.</b> Only the class declaration and the
 * {@code @Override} are Fabric-only tokens: {@code ModInitializer} does not exist on
 * NeoForge or LexForge. On the loader axis {@code onInitialize()} becomes a plain public
 * instance method that each loader's {@code @Mod} entrypoint calls from its constructor,
 * and the body — the init ORDER — stays shared, single-implementation, byte for byte. Write
 * every future init step into the body, never into the declaration.
 */
public class Escalation implements ModInitializer {
	/** The permanent mod id. Namespace, attachment keys and payload channels all key off it. */
	public static final String MOD_ID = "escalation";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Nothing is registered yet. The committed init ORDER, once the systems land, is a
		// Tier-2 assertion: ConfigManager.load -> EliteStore.INSTANCE.initialize ->
		// Net.INSTANCE.registerClientbound -> Affixes.register -> event wiring ->
		// DifficultyCommands.register. One copy of that sequence, every loader.
		LOGGER.info("{} loaded (scaffold; config dir {})", MOD_ID, Platform.INSTANCE.configDir());
	}
}
