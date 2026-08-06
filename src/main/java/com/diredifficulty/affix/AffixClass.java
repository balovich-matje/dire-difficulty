package com.diredifficulty.affix;

/**
 * The eight affix classes — {@code docs/DESIGN.md} §3.1's <i>Class</i> column, and the thing
 * §4.2's structural caps are expressed in.
 *
 * <p>A class is not flavour text. C1 and C2 cap DEFENSE and OFFENSE <i>points</i> at half the
 * rarity budget each; C3–C7 cap AURA, DEATH, MOBILITY, SUPPORT and CONTROL at one affix
 * apiece. Together those force every elite to be a mixed thing, which is also what makes them
 * read as characters rather than as stat blocks — an elite can never be more than half
 * defensive, and never carry more than two defensive affixes even at Legendary.
 *
 * <p><b>Frozen order.</b> {@link #ordinal()} is never persisted or sent (affixes are stored
 * by string id — see {@code EliteData}), but a preset's allowed-class set is config data, so
 * reordering these would silently re-point an existing config. Add at the end.
 */
public enum AffixClass {
	/** Adds or shapes outgoing damage. Capped by points, at half the budget. */
	OFFENSE,
	/** Reduces incoming damage, or sustains through it. Capped by points AND by count. */
	DEFENSE,
	/** Applies a debuff on hit. One only. */
	CONTROL,
	/** Closes or keeps distance. One only — two gap-closers close the "walk away" out. */
	MOBILITY,
	/** A radius effect that ticks. One only. */
	AURA,
	/** Buffs or creates allies. One only. */
	SUPPORT,
	/** Information and reach: aggro, tracking. Uncapped by count. */
	UTILITY,
	/** Fires once, when the elite dies. One only. */
	DEATH;

	/** The per-class affix-count cap of §4.2 C3–C7. {@code 0} means "no count cap". */
	public int countCap() {
		switch (this) {
			case AURA:
			case DEATH:
			case MOBILITY:
			case SUPPORT:
			case CONTROL:
				return 1;
			default:
				return 0;
		}
	}

	/** True for the two classes §4.2 C1/C2 cap by POINTS rather than by count. */
	public boolean pointCapped() {
		return this == OFFENSE || this == DEFENSE;
	}
}
