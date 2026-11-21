package com.diredifficulty.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Loads and saves {@link DireDifficultyConfig} using the Gson bundled with Minecraft — no external
 * dependency, so the config works whether or not Mod Menu and Cloth Config are installed. The
 * in-game GUI, when Cloth is present, mutates the held instance and calls {@link #save()}.
 *
 * <p><b>The file NAME follows the published mod name; the mod ID does not.</b> Skill
 * Proficiencies' GitHub issue #2 is the precedent: that project shipped as "Skill
 * Proficiencies" on Modrinth while its file was {@code specialities.json}, which nothing in a
 * big pack connects back to the mod. So the file is named after the display name, and
 * {@link #FILE_NAME} is the only place that spelling lives.
 *
 * <p><b>The migration hook shipped from day one, and it has already earned its keep.</b> The
 * 2026-08-06 rename to "Dire Difficulty" was exactly the case it was built for: the whole
 * change was one entry added to {@link #LEGACY_FILE_NAMES} and one new {@link #FILE_NAME}.
 * Nothing else moved and no local test instance lost a setting. Every future display-name
 * change works the same way — building the hook after the rename is how settings get lost.
 *
 * <p>{@link #load()} never parses anything away: if a rename fails (read-only config dir, a
 * lock) the old file is still READ and the new one written from it, so a settings loss needs
 * both the rename and the write to fail.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Named after the published mod, not the mod id — see the class javadoc. */
	private static final String FILE_NAME = "dire-difficulty.json";

	/**
	 * Older file names, newest first. One entry so far: the working name the mod carried
	 * before the 2026-08-06 rename, when the display name was "Advanced Difficulty Settings"
	 * and the id was {@code escalation}. A local test instance keeps its settings across that
	 * rename; the next display-name change prepends its own entry here.
	 */
	private static final String[] LEGACY_FILE_NAMES = {"advanced-difficulty-settings.json"};

	private static final Path PATH = Platform.INSTANCE.configDir().resolve(FILE_NAME);

	private static DireDifficultyConfig instance = new DireDifficultyConfig();

	private ConfigManager() {
	}

	/** The live config. Never null; the fields are mutable so the GUI can edit in place. */
	public static DireDifficultyConfig get() {
		return instance;
	}

	/** Read the file if present (else keep defaults), sanitize, then rewrite so new fields are persisted. */
	public static void load() {
		Path source = migrate();

		if (source != null) {
			try (Reader reader = Files.newBufferedReader(source)) {
				JsonElement root = JsonParser.parseReader(reader);
				if (root != null && root.isJsonObject()) {
					JsonObject json = root.getAsJsonObject();
					DireDifficultyConfig loaded = GSON.fromJson(json, DireDifficultyConfig.class);
					if (loaded != null) {
						instance = loaded;
					}
				}
			} catch (IOException | JsonParseException e) {
				DireDifficulty.LOGGER.warn("Couldn't read {} — using defaults", source, e);
			}
		}

		save();
	}

	/**
	 * Settle which file this launch reads, renaming a legacy one onto the current name if that
	 * is what is on disk. Returns the path to read, or {@code null} when no file exists (first
	 * launch — defaults, then {@link #save()} writes the current name).
	 *
	 * <p>Order matters: the CURRENT name wins whenever it exists, so a stale file left behind
	 * by a failed rename or by a hand-copied instance can never overwrite newer settings.
	 */
	private static Path migrate() {
		if (Files.exists(PATH)) {
			return PATH;
		}

		for (String legacyName : LEGACY_FILE_NAMES) {
			Path legacy = Platform.INSTANCE.configDir().resolve(legacyName);
			if (!Files.exists(legacy)) {
				continue;
			}

			try {
				Files.move(legacy, PATH);
				DireDifficulty.LOGGER.info("Renamed config {} to {} — same settings, name now matches the mod",
						legacyName, FILE_NAME);
				return PATH;
			} catch (IOException e) {
				// Not fatal and deliberately not a rethrow: the old file is still perfectly
				// readable, and save() will write the current name from it a moment later.
				DireDifficulty.LOGGER.warn("Couldn't rename {} to {} — reading the old file in place",
						legacy, FILE_NAME, e);
				return legacy;
			}
		}

		return null;
	}

	public static void save() {
		instance.sanitize();

		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			DireDifficulty.LOGGER.warn("Couldn't write {}", PATH, e);
		}
	}
}
