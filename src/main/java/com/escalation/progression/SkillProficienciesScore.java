package com.escalation.progression;

import java.util.OptionalDouble;
import java.util.OptionalInt;

import com.escalation.Tuning;
import com.escalation.compat.SkillProficienciesBridge;
import com.escalation.config.EscalationConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * Progression measured by the player's summed Skill Proficiencies levels
 * ({@code docs/DESIGN.md} §5.1).
 *
 * <p>Their ceiling is 15 skills x level 100 = 1500, but the normalisation target defaults to
 * <b>900</b>, not 1500: an average of 60 per skill already means a player who has engaged with
 * that mod thoroughly, and requiring every skill maxed would leave this source pinned near 0
 * for essentially every real save. The target is a config knob
 * ({@link EscalationConfig#skillTotalTarget}) whose default lives in {@link Tuning}.
 *
 * <p>The target is additionally clamped to their live ceiling, so a hand-edited target above
 * what is reachable cannot make this source permanently unable to reach 1.0.
 *
 * <p>Absent without Skill Proficiencies installed — never 0. See {@link ScoreProvider}.
 */
public final class SkillProficienciesScore implements ScoreProvider {
	public static final SkillProficienciesScore INSTANCE = new SkillProficienciesScore();

	private SkillProficienciesScore() {
	}

	@Override
	public String id() {
		return "skill_proficiencies_total";
	}

	@Override
	public boolean enabled(final EscalationConfig config) {
		return config.scoreSkillProficiencies;
	}

	@Override
	public OptionalDouble score(final ServerPlayer player) {
		OptionalInt total = SkillProficienciesBridge.totalLevels(player);
		if (total.isEmpty()) {
			return OptionalDouble.empty();
		}

		int target = Math.max(1, ProgressionScore.config().skillTotalTarget);

		// Never ask for more than their engine can produce.
		OptionalInt ceiling = SkillProficienciesBridge.maxTotalLevels();
		if (ceiling.isPresent() && ceiling.getAsInt() > 0) {
			target = Math.min(target, ceiling.getAsInt());
		}

		return OptionalDouble.of(Tuning.clamp01((double) total.getAsInt() / target));
	}
}
