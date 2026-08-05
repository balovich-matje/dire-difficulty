package com.escalation.progression;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.escalation.Tuning;
import com.escalation.compat.ArchetypesBridge;
import com.escalation.config.EscalationConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Progression measured by the player's Archetypes level, 0..60
 * ({@code docs/DESIGN.md} §5.1).
 *
 * <p>Normalised against <b>their</b> cap, read live through {@link ArchetypesBridge} rather
 * than mirrored here. If Archetypes raises its cap this follows without an edit; a mirrored
 * constant that quietly disagrees with the other mod is the exact failure this whole bridge
 * shape exists to prevent.
 *
 * <p>Absent without Archetypes installed — never 0. An enabled-but-unavailable provider that
 * reported 0 would drag the combined score down and silently make the game easier for players
 * who never had the mod.
 */
public final class ArchetypesScore implements ScoreProvider {
	public static final ArchetypesScore INSTANCE = new ArchetypesScore();

	private ArchetypesScore() {
	}

	@Override
	public String id() {
		return "archetypes_level";
	}

	@Override
	public boolean enabled(final EscalationConfig config) {
		return config.scoreArchetypes;
	}

	@Override
	public OptionalDouble score(final ServerPlayer player) {
		OptionalInt level = ArchetypesBridge.level(player);
		if (level.isEmpty()) {
			return OptionalDouble.empty();
		}

		int max = Math.max(1, ArchetypesBridge.maxLevel());
		return OptionalDouble.of(Tuning.clamp01((double) level.getAsInt() / max));
	}
}
