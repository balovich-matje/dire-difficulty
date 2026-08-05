package com.escalation.config;

import java.util.OptionalDouble;

/**
 * How the enabled progression sources are folded into one 0..1 score
 * ({@code docs/DESIGN.md} §5.1).
 *
 * <p><b>Only sources that ANSWERED are combined.</b> A source whose backing mod is not
 * installed returns absent, never 0 — see {@link com.escalation.progression.ScoreSource} for
 * why that distinction is the whole design. Every mode here therefore operates on the values
 * that came back, and none of them ever sees a placeholder zero.
 */
public enum CombineMode {
	/**
	 * The highest score among the sources that answered. The default: a player who has done
	 * any one of these things is progressed, and averaging would punish someone who engages
	 * with only one of the three mods.
	 */
	MAX {
		@Override
		public OptionalDouble combine(final double[] values, final int count) {
			if (count == 0) {
				return OptionalDouble.empty();
			}

			double best = values[0];
			for (int i = 1; i < count; i++) {
				if (values[i] > best) {
					best = values[i];
				}
			}

			return OptionalDouble.of(best);
		}
	},

	/** Mean over the sources that answered. */
	AVERAGE {
		@Override
		public OptionalDouble combine(final double[] values, final int count) {
			if (count == 0) {
				return OptionalDouble.empty();
			}

			double sum = 0.0;
			for (int i = 0; i < count; i++) {
				sum += values[i];
			}

			return OptionalDouble.of(sum / count);
		}
	},

	/** Sum over the sources that answered, clamped to 1.0. */
	SUM_CLAMPED {
		@Override
		public OptionalDouble combine(final double[] values, final int count) {
			if (count == 0) {
				return OptionalDouble.empty();
			}

			double sum = 0.0;
			for (int i = 0; i < count; i++) {
				sum += values[i];
			}

			return OptionalDouble.of(Math.min(1.0, sum));
		}
	};

	/**
	 * Fold the first {@code count} entries of {@code values} into one score, or absent when
	 * nothing answered.
	 *
	 * <p>Takes an array plus a length rather than a collection because this runs behind the
	 * per-player cache on every refresh and allocating a list per player per refresh buys
	 * nothing. The caller owns the array and reuses it.
	 */
	public abstract OptionalDouble combine(double[] values, int count);

	/** The lang key for this mode's display name in the Cloth screen. */
	public String translationKey() {
		return "config.escalation.combineMode." + this.name().toLowerCase(java.util.Locale.ROOT);
	}
}
