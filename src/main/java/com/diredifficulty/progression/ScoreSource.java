package com.diredifficulty.progression;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The progression score, as the elite roller consumes it — a single 0..1 number, resolved
 * at a POSITION rather than for a player.
 *
 * <p>This is deliberately the narrow half of {@code docs/DESIGN.md} §5.1. That section
 * describes a pluggable provider stack ({@code score/ScoreProvider}, three providers,
 * {@code OptionalDouble} so an absent mod never drags the combined score down, three combine
 * modes, a per-player cache and the max-within-{@code SCORE_RADIUS} position resolution).
 * <b>None of that lives here.</b> All of it lives behind {@link #scoreAt}, because the elite
 * roller has exactly one question and asking it exactly one way is what lets the two systems
 * be written, tested and ported independently.
 *
 * <p><b>Standalone by construction.</b> {@link #ZERO} answers 0 for everything, and it is
 * what is installed until something better is. A build with only the elite half compiles,
 * boots and rolls elites at the score-0 end of every curve — 1% chance, ~81% uncommon, no
 * legendaries — which is a coherent game rather than a crash.
 *
 * <p><b>The absent-is-not-zero rule still binds the implementer, not this file.</b> A
 * provider whose backing mod is not installed must report ABSENT and be dropped from the
 * combine, never report 0. Returning 0 would let an enabled-but-unavailable provider silently
 * make the game easier — the same class of bug as Archetypes' mirrored {@code
 * SPECIALITIES_SHIFT = 7}, which compiled and drew fine while being seven pixels wrong.
 * By the time a number reaches {@link #scoreAt} that decision has already been made.
 */
public interface ScoreSource {
	/**
	 * The constant-zero source. Installed at class-init and replaced by
	 * {@link #install(ScoreSource)} when the real provider stack registers itself.
	 */
	ScoreSource ZERO = new ScoreSource() {
		@Override
		public String id() {
			return "zero";
		}

		@Override
		public double scoreAt(final ServerLevel level, final BlockPos pos) {
			return 0.0;
		}

		@Override
		public double scoreFor(final ServerPlayer player) {
			return 0.0;
		}
	};

	/** Config key / debug name. */
	String id();

	/**
	 * The score that governs a spawn at this position, 0..1.
	 *
	 * <p>Called on the server thread from inside the spawn path, once per eligible mob, so
	 * the implementation must be cheap: design §5.1 requires per-player scores to be cached
	 * and recomputed every {@code Tuning.SCORE_REFRESH_TICKS}, never per spawn attempt.
	 */
	double scoreAt(ServerLevel level, BlockPos pos);

	/** The score for one player, 0..1. Used by debug commands and by the position resolver. */
	double scoreFor(ServerPlayer player);

	/** The source in force. Never null. */
	static ScoreSource active() {
		return Holder.ACTIVE.get();
	}

	/**
	 * Installs the real provider stack, replacing {@link #ZERO}. Called once from common
	 * init, before any world can load.
	 */
	static void install(final ScoreSource source) {
		Holder.ACTIVE.set(source);
	}

	/**
	 * Holds the installed source. A nested class rather than an interface field because an
	 * interface field is implicitly {@code final} and this one has to be replaceable; the
	 * {@link AtomicReference} is for visibility across the init thread and the server
	 * thread, not for contention.
	 */
	final class Holder {
		static final AtomicReference<ScoreSource> ACTIVE = new AtomicReference<>(ZERO);

		private Holder() {
		}
	}
}
