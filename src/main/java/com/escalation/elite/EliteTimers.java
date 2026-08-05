package com.escalation.elite;

/**
 * The transient, server-only combat state of one entity — {@code docs/DESIGN.md} §6.3's
 * {@code SUSTAIN_LOCKED_UNTIL}, {@code SUMMON_READY_AT} and their siblings.
 *
 * <p>Separate from {@link EliteData} because the two have opposite persistence rules and
 * mixing them would get one of them wrong. {@code ESCAPIST_USED} over there is persistent
 * <b>on purpose</b>: a chunk reload must not refund the once-per-lifetime escape, or
 * Guarantee B2 is false. Everything here is the mirror image — a cooldown that survived a
 * reload would mean an elite loaded back in already owing a shockwave it never wound up for,
 * and a sustain lockout that survived would silently be a permanent nerf.
 *
 * <p><b>It hangs on victims too, not only on elites.</b> {@link #sunderStacks} is the
 * armour shred {@code sundering} applies to whoever it hit, and the natural owner of "how
 * shredded is this player and until when" is the player. One transient attachment type
 * serves both roles because both are the same thing: per-entity combat bookkeeping that must
 * not outlive the session.
 *
 * <p>Every deadline is an absolute game time rather than a countdown, so a pass that gets
 * skipped — the elite drifted out of ticker range, the chunk unloaded, the server stuttered —
 * cannot leak ticks in either direction.
 */
public record EliteTimers(long sustainLockedUntil, long summonReadyAt, long quakeReadyAt,
		long quakeFireAt, long blinkReadyAt, long volleyReadyAt, int sunderStacks,
		long sunderExpiresAt) {

	/** A freshly promoted elite: nothing locked out, every cooldown due immediately. */
	public static final EliteTimers FRESH = new EliteTimers(0L, 0L, 0L, 0L, 0L, 0L, 0, 0L);

	/**
	 * §4.4 B2. Set when a PLAYER damages the elite; while it is in the future,
	 * {@code regenerating} and {@code vampiric} are both suppressed. A player who is landing
	 * hits always nets the health bar down.
	 */
	public EliteTimers withSustainLockedUntil(final long time) {
		return new EliteTimers(time, this.summonReadyAt, this.quakeReadyAt, this.quakeFireAt,
				this.blinkReadyAt, this.volleyReadyAt, this.sunderStacks, this.sunderExpiresAt);
	}

	public EliteTimers withSummonReadyAt(final long time) {
		return new EliteTimers(this.sustainLockedUntil, time, this.quakeReadyAt, this.quakeFireAt,
				this.blinkReadyAt, this.volleyReadyAt, this.sunderStacks, this.sunderExpiresAt);
	}

	/**
	 * Quaking is a two-stage affix: {@code quakeFireAt} is the moment the shockwave lands and
	 * {@code quakeReadyAt} the moment the next wind-up may begin. Two deadlines rather than
	 * one plus a boolean, so the telegraph cannot be skipped by any interleaving — a
	 * shockwave that fires without its wind-up is an unavoidable hit, and §3.2's line is that
	 * burst must always be shaped and readable.
	 */
	public EliteTimers withQuake(final long readyAt, final long fireAt) {
		return new EliteTimers(this.sustainLockedUntil, this.summonReadyAt, readyAt, fireAt,
				this.blinkReadyAt, this.volleyReadyAt, this.sunderStacks, this.sunderExpiresAt);
	}

	public EliteTimers withBlinkReadyAt(final long time) {
		return new EliteTimers(this.sustainLockedUntil, this.summonReadyAt, this.quakeReadyAt,
				this.quakeFireAt, time, this.volleyReadyAt, this.sunderStacks, this.sunderExpiresAt);
	}

	public EliteTimers withVolleyReadyAt(final long time) {
		return new EliteTimers(this.sustainLockedUntil, this.summonReadyAt, this.quakeReadyAt,
				this.quakeFireAt, this.blinkReadyAt, time, this.sunderStacks, this.sunderExpiresAt);
	}

	/** The armour-shred stack count on a victim, and when the whole stack lapses. */
	public EliteTimers withSunder(final int stacks, final long expiresAt) {
		return new EliteTimers(this.sustainLockedUntil, this.summonReadyAt, this.quakeReadyAt,
				this.quakeFireAt, this.blinkReadyAt, this.volleyReadyAt, stacks, expiresAt);
	}

	/** True while sustain is suppressed. */
	public boolean sustainLocked(final long now) {
		return now < this.sustainLockedUntil;
	}
}
