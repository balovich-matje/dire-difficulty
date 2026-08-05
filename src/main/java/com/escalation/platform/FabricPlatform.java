package com.escalation.platform;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Fabric implementation of {@link Platform}.
 *
 * <p>Package-private and named after its loader. That naming is not cosmetic: when the port
 * registers NeoForge and Forge nodes, each node's build script excludes the two
 * implementations it does not use by a glob on the file name, and the three classes are
 * mutually exclusive because each names loader API the others have no classpath for.
 * {@code Forge*} does not match {@code NeoForge*} — the pattern is anchored — which is what
 * makes those globs safe.
 */
final class FabricPlatform implements Platform {
	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public boolean isModLoaded(final String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}
}
