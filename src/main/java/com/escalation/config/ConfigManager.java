package com.escalation.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.escalation.Escalation;
import com.escalation.platform.Platform;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Loads and saves {@link EscalationConfig} using the Gson bundled with Minecraft — no external
 * dependency, so the config works whether or not Mod Menu and Cloth Config are installed. The
 * in-game GUI, when Cloth is present, mutates the held instance and calls {@link #save()}.
 *
 * <p><b>The file NAME follows the published mod name; the mod ID does not.</b> Skill
 * Proficiencies' GitHub issue #2 is the precedent: that project shipped as "Skill
 * Proficiencies" on Modrinth while its file was {@code specialities.json}, which nothing in a
 * big pack connects back to the mod. So the file is named after the display name, and
 * {@link #FILE_NAME} is the only place that spelling lives.
 *
 * <p><b>The migration hook ships from day one, empty.</b> {@code docs/DESIGN.md} §7 recommends
 * renaming the display name (to "Escalation" or similar) while the mod id stays
 * {@code escalation} forever. When that happens the whole change is: put the current name into
 * {@link #LEGACY_FILE_NAMES} and change {@link #FILE_NAME}. Nothing else moves, and no player
 * loses a setting. Building the hook after the rename is how settings get lost.
 *
 * <p>{@link #load()} never parses anything away: if a rename fails (read-only config dir, a
 * lock) the old file is still READ and the new one written from it, so a settings loss needs
 * both the rename and the write to fail.
 */
public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Named after the published mod, not the mod id — see the class javadoc. */
	private static final String FILE_NAME = "advanced-difficulty-settings.json";

	/**
	 * Older file names, newest first. Empty today because nothing has shipped yet; the first
	 * display-name rename adds the previous name here and that is the entire migration.
	 */
	private static final String[] LEGACY_FILE_NAMES = new String[0];

	private static final Path PATH = Platform.INSTANCE.configDir().resolve(FILE_NAME);

	private static EscalationConfig instance = new EscalationConfig();

	private ConfigManager() {
	}

	/** The live config. Never null; the fields are mutable so the GUI can edit in place. */
	public static EscalationConfig get() {
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
					EscalationConfig loaded = GSON.fromJson(json, EscalationConfig.class);
					if (loaded != null) {
						applyPresetIfOnlyNamed(json, loaded);
						instance = loaded;
					}
				}
			} catch (IOException | JsonParseException e) {
				Escalation.LOGGER.warn("Couldn't read {} — using defaults", source, e);
			}
		}

		save();
	}

	/**
	 * Stamp the named preset when — and only when — the file names a preset and spells out
	 * <b>none</b> of the fields that preset governs.
	 *
	 * <p>This is the one piece of nuance the preset semantics need on disk, and skipping it
	 * breaks the obvious case. A server admin who writes {@code {"preset": "HELL_ON_EARTH"}}
	 * by hand and nothing else means "run Hell on Earth"; without this, every governed field
	 * would come up at its Gathering Clouds field-initialiser default, diverge from the named
	 * preset, and {@link EscalationConfig#sanitize()} would relabel the whole thing Custom.
	 *
	 * <p>The moment the file spells out even one governed value, this does nothing and the
	 * file runs verbatim — which is §8's rule: what the file says is what runs. The values
	 * then either match the label or {@code sanitize()} moves the label to Custom. Presence is
	 * tested on the raw {@link JsonObject}, because a field ABSENT from the JSON and a field
	 * present with the default value are indistinguishable after binding.
	 */
	private static void applyPresetIfOnlyNamed(final JsonObject json, final EscalationConfig loaded) {
		if (loaded.preset == null || loaded.preset == Preset.CUSTOM) {
			return;
		}

		for (String governed : Preset.GOVERNED_FIELDS) {
			if (json.has(governed)) {
				return;
			}
		}

		loaded.preset.stamp(loaded);
	}

	/**
	 * Select a preset and stamp its values. The only entry point that lets a preset write over
	 * existing values, which is what makes "selecting a preset" a deliberate act rather than
	 * something that can happen to a config behind the player's back.
	 */
	public static void applyPreset(final Preset preset) {
		instance.preset = preset;
		preset.stamp(instance);
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
				Escalation.LOGGER.info("Renamed config {} to {} — same settings, name now matches the mod",
						legacyName, FILE_NAME);
				return PATH;
			} catch (IOException e) {
				// Not fatal and deliberately not a rethrow: the old file is still perfectly
				// readable, and save() will write the current name from it a moment later.
				Escalation.LOGGER.warn("Couldn't rename {} to {} — reading the old file in place",
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
			Escalation.LOGGER.warn("Couldn't write {}", PATH, e);
		}
	}
}
