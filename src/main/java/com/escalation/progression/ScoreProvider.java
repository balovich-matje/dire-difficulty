package com.escalation.progression;

import java.util.OptionalDouble;

import com.escalation.config.EscalationConfig;

import net.minecraft.server.level.ServerPlayer;

/**
 * One pluggable measure of how far a player has got, normalised to 0..1
 * ({@code docs/DESIGN.md} §5.1's {@code ScoreProvider}). {@link ProgressionScore} folds the
 * enabled providers into the single number the elite roller consumes through
 * {@link ScoreSource}.
 *
 * <p>Two interfaces, and the split is deliberate. {@link ScoreSource} is the <b>narrow</b>
 * question the elite core asks — one number, at a position, already decided. This is the
 * <b>wide</b> side: several providers, each of which may or may not be able to answer, folded
 * by a combine mode. The elite core never sees this file, which is what lets the two halves be
 * written, tested and ported independently.
 *
 * <p><b>{@link OptionalDouble}, not {@code double}, and that is the whole design.</b> A
 * provider whose backing mod is not installed returns <b>absent</b>, never 0. Returning 0
 * would let an enabled-but-unavailable provider drag the combined score down and silently make
 * the game easier — the same class of bug as Archetypes' mirrored {@code SPECIALITIES_SHIFT =
 * 7}, which compiled and drew fine while being seven pixels wrong (their design R-C4).
 * <b>Read live, never assume.</b>
 *
 * <p>A player who HAS the backing mod and has done nothing scores <b>0.0, present</b>. That is
 * a different thing from absent, and the difference is the point.
 *
 * <p>Every method must stay cheap and side-effect free. {@link #score} runs behind
 * {@link ProgressionScore}'s per-player cache on a fixed refresh interval; nothing may make it
 * run per spawn attempt.
 */
public interface ScoreProvider {
	/** Stable identifier — the config key and the log key. Never changes once shipped. */
	String id();

	/**
	 * Whether the player has this provider switched on. Says nothing about whether it can
	 * answer: availability of the backing mod is not an enabled-check, it is an absent
	 * {@link #score}.
	 */
	boolean enabled(EscalationConfig config);

	/**
	 * This player's progress through this provider, in 0..1 (already clamped), or
	 * {@link OptionalDouble#empty()} when the provider cannot answer — the backing mod is
	 * absent, or its API moved under us.
	 */
	OptionalDouble score(ServerPlayer player);
}
