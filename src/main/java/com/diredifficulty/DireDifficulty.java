package com.diredifficulty;

import com.diredifficulty.config.ConfigEliteRules;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.elite.EliteSystems;
import com.diredifficulty.platform.Platform;
import com.diredifficulty.progression.ProgressionScore;
import com.diredifficulty.spawn.TheHunt;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint — config, the score stack, the elite core, The Hunt and the test kit, in
 * that order.
 *
 * <p><b>The mod id is permanent from here on.</b> {@code dire_difficulty} was set by the
 * 2026-08-06 rename, which moved the working name "Advanced Difficulty Settings" / id
 * {@code escalation} to the shipping name <b>Dire Difficulty</b>. That change was legal
 * exactly once: nothing had been published and only local test worlds existed. It is now
 * closed. This is Skill Proficiencies' hardest-learned lesson: that project was renamed
 * after release and its id, namespace, attachment keys and source directory stayed
 * {@code specialities} forever, because changing an id orphans every world's stored data.
 * See {@code docs/DESIGN.md} §7.5 — never "fix" the id to match a title again.
 *
 * <p><b>Shape this class keeps when the port comes.</b> Only the class declaration and the
 * {@code @Override} are Fabric-only tokens: {@code ModInitializer} does not exist on
 * NeoForge or LexForge. On the loader axis {@code onInitialize()} becomes a plain public
 * instance method that each loader's {@code @Mod} entrypoint calls from its constructor,
 * and the body — the init ORDER — stays shared, single-implementation, byte for byte. Write
 * every future init step into the body, never into the declaration.
 */
public class DireDifficulty implements ModInitializer {
	/** The permanent mod id. Namespace, attachment keys and payload channels all key off it. */
	public static final String MOD_ID = "dire_difficulty";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// The committed init ORDER, and it is a Tier-2 assertion rather than a preference:
		// ConfigManager.load -> the two plug-point installs -> EliteSystems (store, catalogue,
		// listeners) -> TheHunt -> the command tree. One copy of that sequence, every loader.
		//
		// There is no `Net.INSTANCE.registerClientbound` step: design §6.5's clientbound payload
		// is not built on this node, because EliteData syncs itself as an attachment. See
		// docs/DESIGN.md §10.4 — the payload is written inside FabricEliteStore when the legacy
		// nodes arrive, and it lands here in the order between the store and the catalogue.
		//
		// The config loads FIRST because everything downstream reads it. ConfigEliteRules and
		// ProgressionScore are the elite core's two plug points: until they are installed it
		// runs on its own built-in defaults (Gathering Clouds, score 0), which is a coherent
		// game rather than a crash — but no world may load between the two states, so both
		// installs happen here, in common init, before any world exists.
		ConfigManager.load();
		ConfigEliteRules.register();
		ProgressionScore.register();

		// The elite core: state store, then the affix catalogue, then its event wiring — in
		// that order, and EliteSystems asserts it. It comes after both installs above so that
		// nothing can observe the built-in fallbacks, and before TheHunt because a hunting mob
		// may already be an elite.
		EliteSystems.register();
		TheHunt.register();

		// Last, and it has to be: the command tree names the affix catalogue in its suggestion
		// providers and the config in its `preset` branch, so every system it can reach must
		// already be up. The callback itself fires per server, long after this returns.
		DireDifficultyCommands.register();

		LOGGER.info("{} loaded (config dir {})", MOD_ID, Platform.INSTANCE.configDir());
	}
}
