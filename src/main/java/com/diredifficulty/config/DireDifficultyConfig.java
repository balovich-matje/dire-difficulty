package com.diredifficulty.config;

import com.diredifficulty.Tuning;

/**
 * Every user-facing setting. Since 2026-11-21 there are no presets: how hard the game gets is
 * the WORLD's difficulty (Easy / Normal / Hard caps the progression stage, see
 * {@code ProgressionScore}), and progression is measured from vanilla statistics only, so the
 * mod behaves the same in any modpack. What is left here is on/off switches plus the three
 * spawn multipliers reached at full progression.
 *
 * <p>Field names are part of the file format. The three spawn multipliers were renamed
 * ({@code …AtMax}) when their meaning changed from "always" to "at full progression", so an
 * old file's values are ignored rather than silently reinterpreted.
 */
public final class DireDifficultyConfig {
	// ---- General

	public boolean spawnElites = true;

	public boolean modifySpawns = true;

	public boolean theHunt = true;

	// ---- Elites. elitePlate and eliteGlow are display-only, read by each client from its
	// own file; eliteGearDrops is read by the server when an elite dies.

	public boolean elitePlate = true;

	public boolean eliteGearDrops = false;

	public boolean eliteGlow = true;

	// ---- Spawning. The multipliers are the values at progression stage 1; at stage 0 the
	// game is vanilla, in between they scale linearly. Only applied while modifySpawns is on.

	public boolean daySpawning = false;

	public boolean daySpawnBurnImmunity = true;

	public double spawnFrequencyAtMax = Tuning.STAGE_SPAWN_FREQUENCY_MAX;

	public double mobCapAtMax = Tuning.STAGE_MOB_CAP_MAX;

	public double despawnDistanceAtMax = Tuning.STAGE_DESPAWN_DISTANCE_MAX;

	public void sanitize() {
		this.spawnFrequencyAtMax = Tuning.clamp(this.spawnFrequencyAtMax, 1.0, Tuning.SPAWN_FREQUENCY_MAX);
		this.mobCapAtMax = Tuning.clamp(this.mobCapAtMax, 1.0, Tuning.MOB_CAP_MULTIPLIER_MAX);
		this.despawnDistanceAtMax = Tuning.clamp(this.despawnDistanceAtMax, 1.0, Tuning.DESPAWN_MULTIPLIER_MAX);
	}
}
