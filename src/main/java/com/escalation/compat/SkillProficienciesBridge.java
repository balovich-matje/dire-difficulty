package com.escalation.compat;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.OptionalInt;

import com.escalation.Escalation;
import com.escalation.platform.Platform;

import net.minecraft.world.entity.player.Player;

/**
 * The only place this mod touches Skill Proficiencies from.
 *
 * <p>Soft dependency in the strongest sense available here: every call is gated on the mod
 * being loaded, and every name that belongs to it sits inside the private {@link Linked}
 * holder, so the JVM never even attempts to resolve those classes when the mod is absent. This
 * is Archetypes' {@code compat/SpecialitiesBridge} shape, kept verbatim because it is the shape
 * the two sibling mods already prove works both ways ({@code docs/DESIGN.md} §5.1, §6.7).
 *
 * <p><b>Reflection rather than a compile-time reference, and it is not laziness.</b> Archetypes
 * can name {@code com.specialities.skills.SkillManager} directly because it takes a per-node
 * file dependency on their built jar. This mod deliberately does not: it reads only public,
 * stable, primitive-returning entry points, and taking a build-time dependency on two sibling
 * repos to read four methods would mean a {@code clean} in either of them broke <b>this
 * repo's configuration phase</b> — the consequence §6.7 warns about — for no benefit. If a
 * future release needs to see their types rather than their numbers, add the file dependency
 * then and delete the reflection; the public surface of this class does not change.
 *
 * <p><b>Absent is not zero.</b> Every accessor returns an empty {@link OptionalInt} when the
 * mod is not installed or its API has moved. The caller must drop the source from the combine
 * rather than fold in a 0 — see {@code progression/ScoreProvider}.
 */
public final class SkillProficienciesBridge {
	/**
	 * Their mod id. It is {@code specialities} and it always will be: the project was renamed
	 * to "Skill Proficiencies" in 2026-07 and the id, namespace, attachment keys and source
	 * directory stayed put, because changing an id orphans every world's stored skill data.
	 * Never "fix" this to match their title.
	 */
	public static final String MOD_ID = "specialities";

	private static final boolean LOADED = Platform.INSTANCE.isModLoaded(MOD_ID);

	private SkillProficienciesBridge() {
	}

	/** True when the mod is installed AND every entry point this class needs resolved. */
	public static boolean available() {
		return LOADED && Linked.OK;
	}

	/**
	 * The sum of the player's levels across the fifteen built-in skills, or empty without the
	 * mod.
	 *
	 * <p>Built-ins only. External skills registered through their {@code specialities:skills}
	 * entrypoint are deliberately excluded — Archetypes contributes Spellcasting that way, and
	 * counting it here would double-count the same progress that the Archetypes provider
	 * already reports.
	 */
	public static OptionalInt totalLevels(final Player player) {
		return available() ? Linked.totalLevels(player) : OptionalInt.empty();
	}

	/**
	 * Their ceiling — skill count times their per-skill max level, read live from their own
	 * classes rather than mirrored here. 15 x 100 = 1500 today; if they add a skill or move
	 * the cap, this follows without an edit.
	 */
	public static OptionalInt maxTotalLevels() {
		return available() ? OptionalInt.of(Linked.MAX_TOTAL) : OptionalInt.empty();
	}

	/**
	 * Everything that names a Skill Proficiencies class. Loaded lazily and only behind the
	 * {@link #LOADED} check above: {@link #OK} is assigned in a static block rather than
	 * initialised to a literal, so it is not a compile-time constant and reading it really
	 * does trigger this class's initialisation — which is the point.
	 */
	private static final class Linked {
		static final boolean OK;
		static final int MAX_TOTAL;

		private static final Method GET_SKILLS;
		private static final Method SKILLS_XP;
		private static final Method LEVEL_FOR_TOTAL_XP;
		private static final String[] SKILL_IDS;

		static {
			boolean ok = false;
			int maxTotal = 0;
			Method getSkills = null;
			Method skillsXp = null;
			Method levelForTotalXp = null;
			String[] skillIds = new String[0];

			try {
				Class<?> skillManager = Class.forName("com.specialities.skills.SkillManager");
				Class<?> playerSkills = Class.forName("com.specialities.skills.PlayerSkills");
				Class<?> tuning = Class.forName("com.specialities.skills.Tuning");
				Class<?> skill = Class.forName("com.specialities.skills.Skill");

				getSkills = skillManager.getMethod("get", Player.class);
				skillsXp = playerSkills.getMethod("xp");
				levelForTotalXp = tuning.getMethod("levelForTotalXp", int.class);

				Object[] constants = skill.getEnumConstants();
				Method idOf = skill.getMethod("id");
				skillIds = new String[constants.length];
				for (int i = 0; i < constants.length; i++) {
					skillIds[i] = (String) idOf.invoke(constants[i]);
				}

				maxTotal = skillIds.length * tuning.getField("MAX_LEVEL").getInt(null);
				ok = maxTotal > 0;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// Not fatal, by construction. Their API moved, or a version we do not know
				// shipped; the provider goes ABSENT and the combined score is computed from
				// whatever else answered. It must not go to 0 and it must not crash.
				Escalation.LOGGER.warn("Skill Proficiencies is installed but its API did not resolve — "
						+ "that progression source will report absent", e);
			}

			OK = ok;
			MAX_TOTAL = maxTotal;
			GET_SKILLS = getSkills;
			SKILLS_XP = skillsXp;
			LEVEL_FOR_TOTAL_XP = levelForTotalXp;
			SKILL_IDS = skillIds;
		}

		private Linked() {
		}

		@SuppressWarnings("unchecked")
		static OptionalInt totalLevels(final Player player) {
			try {
				// Their PlayerSkills is a record of total accumulated XP per skill id; levels
				// are derived from it by their own curve. Going through that curve rather than
				// re-deriving it here means a rebalance on their side moves this with it.
				Object skills = GET_SKILLS.invoke(null, player);
				Map<String, Integer> xp = (Map<String, Integer>) SKILLS_XP.invoke(skills);

				int total = 0;
				for (String id : SKILL_IDS) {
					Integer skillXp = xp.get(id);
					if (skillXp != null) {
						total += (Integer) LEVEL_FOR_TOTAL_XP.invoke(null, skillXp.intValue());
					}
				}

				return OptionalInt.of(total);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return OptionalInt.empty();
			}
		}
	}
}
