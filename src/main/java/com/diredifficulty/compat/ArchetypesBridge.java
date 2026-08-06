package com.diredifficulty.compat;

import java.lang.reflect.Method;
import java.util.OptionalInt;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;
import com.diredifficulty.platform.Platform;

import net.minecraft.world.entity.player.Player;

/**
 * The only place this mod touches Archetypes from.
 *
 * <p>Same shape and same reasoning as {@link SkillProficienciesBridge}: gated on the mod being
 * loaded, every name that belongs to it confined to the private {@link Linked} holder, so the
 * JVM never resolves those classes when the mod is absent. Without Archetypes this mod runs
 * completely — that progression source simply reports absent and the combine ignores it.
 *
 * <p>Archetypes is the counterweight this mod was designed against: it is a power-fantasy class
 * mod whose levels run 0..60, so a player deep into an Archetypes build is exactly the player
 * elites are supposed to escalate for.
 */
public final class ArchetypesBridge {
	public static final String MOD_ID = "archetypes";

	private static final boolean LOADED = Platform.INSTANCE.isModLoaded(MOD_ID);

	private ArchetypesBridge() {
	}

	/** True when the mod is installed AND every entry point this class needs resolved. */
	public static boolean available() {
		return LOADED && Linked.OK;
	}

	/** The player's archetype level, 0..{@link #maxLevel()}, or empty without the mod. */
	public static OptionalInt level(final Player player) {
		return available() ? Linked.level(player) : OptionalInt.empty();
	}

	/**
	 * Their level cap, read live from their own class rather than mirrored. 60 today.
	 * {@link Tuning#SCORE_ARCHETYPES_MAX_LEVEL} is only the fallback for when the field cannot
	 * be read at all — a mirrored constant that silently disagrees with the other mod is the
	 * exact failure mode {@code docs/DESIGN.md} §6.7 records as R-C4.
	 */
	public static int maxLevel() {
		return available() && Linked.MAX_LEVEL > 0 ? Linked.MAX_LEVEL : Tuning.SCORE_ARCHETYPES_MAX_LEVEL;
	}

	/** Everything that names an Archetypes class. See {@code SkillProficienciesBridge.Linked}. */
	private static final class Linked {
		static final boolean OK;
		static final int MAX_LEVEL;

		private static final Method LEVEL_OF;

		static {
			boolean ok = false;
			int maxLevel = 0;
			Method levelOf = null;

			try {
				Class<?> skillPoints = Class.forName("com.archetypes.SkillPoints");
				levelOf = skillPoints.getMethod("level", Player.class);
				maxLevel = skillPoints.getField("MAX_LEVEL").getInt(null);
				ok = maxLevel > 0;
			} catch (ReflectiveOperationException | RuntimeException e) {
				DireDifficulty.LOGGER.warn("Archetypes is installed but its API did not resolve — "
						+ "that progression source will report absent", e);
			}

			OK = ok;
			MAX_LEVEL = maxLevel;
			LEVEL_OF = levelOf;
		}

		private Linked() {
		}

		static OptionalInt level(final Player player) {
			try {
				return OptionalInt.of((Integer) LEVEL_OF.invoke(null, player));
			} catch (ReflectiveOperationException | RuntimeException e) {
				return OptionalInt.empty();
			}
		}
	}
}
