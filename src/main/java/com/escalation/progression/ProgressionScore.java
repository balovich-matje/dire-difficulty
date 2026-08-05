package com.escalation.progression;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

import com.escalation.Tuning;
import com.escalation.config.ConfigManager;
import com.escalation.config.EscalationConfig;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The wide half of {@code docs/DESIGN.md} §5.1: several {@link ScoreProvider}s, a combine mode,
 * a per-player cache, and the rule that turns per-player scores into the score at a POSITION.
 * Installs itself as the {@link ScoreSource} the elite roller consumes.
 *
 * <p><b>Everything here is server-side and server-authoritative.</b> Scores are computed from
 * server state, cached on the server, and never sent anywhere. The client has no opinion about
 * how progressed anyone is.
 *
 * <h2>Why the cache exists</h2>
 *
 * <p>Counting advancements walks the whole datapack advancement set and summing skills walks
 * fifteen map entries. Elite rolls happen inside the spawn path — once per eligible mob, on
 * every spawn attempt in every loaded chunk — so neither may run there. Per-player scores are
 * recomputed every {@link Tuning#SCORE_REFRESH_TICKS} on the server tick and every read is
 * served from the map.
 *
 * <h2>From per-player score to a per-spawn score</h2>
 *
 * <p>A spawn happens at a position, not for a player. The score at a position is the
 * <b>maximum</b> among players within {@link Tuning#SCORE_RADIUS} — 128 blocks, matching
 * vanilla's own {@code NaturalSpawner.SPAWN_DISTANCE_BLOCK}, so a spawn vanilla considered
 * "near a player" is scored by that same player — falling back to the nearest player when none
 * is in range. Maximum rather than average: a group's difficulty should track the player the
 * elites will actually have to fight.
 */
public final class ProgressionScore implements ScoreSource {
	public static final ProgressionScore INSTANCE = new ProgressionScore();

	/**
	 * The shipped providers, in a fixed order. Order is irrelevant to every combine mode —
	 * MAX, AVERAGE and SUM_CLAMPED are all commutative — and it is fixed anyway so that log
	 * output and any future debug command read the same way twice.
	 */
	private static final ScoreProvider[] PROVIDERS = {
		AdvancementScore.INSTANCE,
		ArchetypesScore.INSTANCE,
		SkillProficienciesScore.INSTANCE,
	};

	/** Player UUID to last computed score. Server thread only; no synchronisation by design. */
	private final Map<UUID, Double> cache = new HashMap<>();

	/** Absolute server tick at which the next full refresh is due. */
	private int nextRefreshTick;

	private ProgressionScore() {
	}

	/**
	 * Wire the refresh tick and install this as the active {@link ScoreSource}. Called once
	 * from common init, before any world can load — until it runs, the elite roller sees
	 * {@link ScoreSource#ZERO} and plays at the score-0 end of every curve, which is a coherent
	 * game rather than a crash.
	 */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onEndTick);

		// A single-player client can start a second, unrelated world in the same JVM. Dropping
		// the cache on stop means the next world never reads a score computed for a player in
		// the previous one.
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> INSTANCE.clear());

		ScoreSource.install(INSTANCE);
	}

	/** The live config. One accessor so the providers do not each reach for the manager. */
	static EscalationConfig config() {
		return ConfigManager.get();
	}

	/**
	 * The shipped providers, for {@code /escalation score}'s per-source breakdown.
	 *
	 * <p>Exposed rather than mirrored, so the command can only ever report the providers that
	 * actually feed {@link #compute}. Mirroring the list is the exact shape of Archetypes'
	 * {@code SPECIALITIES_SHIFT} bug — a second copy that compiles, reads plausibly, and is
	 * wrong the day the first one changes. Defensive copy: the array is the fold's own input.
	 */
	public static ScoreProvider[] providers() {
		return PROVIDERS.clone();
	}

	@Override
	public String id() {
		return "progression";
	}

	@Override
	public double scoreFor(final ServerPlayer player) {
		if (config().ignoreProgression) {
			return 1.0;
		}

		Double cached = this.cache.get(player.getUUID());
		if (cached != null) {
			return cached;
		}

		// A player who joined between refreshes still gets a real number rather than 0.
		double fresh = compute(player);
		this.cache.put(player.getUUID(), fresh);
		return fresh;
	}

	@Override
	public double scoreAt(final ServerLevel level, final BlockPos pos) {
		if (config().ignoreProgression) {
			return 1.0;
		}

		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return 0.0;
		}

		double radiusSqr = Tuning.SCORE_RADIUS * Tuning.SCORE_RADIUS;
		double inRangeBest = -1.0;
		double nearestScore = 0.0;
		double nearestDistSqr = Double.MAX_VALUE;

		for (ServerPlayer player : players) {
			if (player.isSpectator()) {
				continue;
			}

			double distSqr = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			double score = scoreFor(player);

			if (distSqr <= radiusSqr && score > inRangeBest) {
				inRangeBest = score;
			}

			if (distSqr < nearestDistSqr) {
				nearestDistSqr = distSqr;
				nearestScore = score;
			}
		}

		if (inRangeBest >= 0.0) {
			return inRangeBest;
		}

		// Nobody within the radius: fall back to the nearest player rather than to 0. A spawn
		// far from everyone is still happening in a world someone has progressed through.
		return nearestDistSqr == Double.MAX_VALUE ? 0.0 : nearestScore;
	}

	/**
	 * Recompute every online player's score, then drop cache entries for anyone who has left.
	 * Runs on the server thread at {@link Tuning#SCORE_REFRESH_TICKS}.
	 */
	private void onEndTick(final MinecraftServer server) {
		if (server.getTickCount() < this.nextRefreshTick) {
			return;
		}

		this.nextRefreshTick = server.getTickCount() + Math.max(1, Tuning.SCORE_REFRESH_TICKS);

		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		Set<UUID> present = new HashSet<>(online.size());

		for (ServerPlayer player : online) {
			UUID uuid = player.getUUID();
			present.add(uuid);
			this.cache.put(uuid, compute(player));
		}

		this.cache.keySet().retainAll(present);
	}

	/**
	 * Fold this player's enabled, ANSWERING providers into one 0..1 score.
	 *
	 * <p>The absent-is-not-zero rule is enforced right here and nowhere else: a provider that
	 * returns empty never enters the array, so no combine mode can ever see a placeholder zero
	 * standing in for a mod that is not installed.
	 *
	 * <p>When nothing answered at all — every source disabled, or none of their mods present
	 * and advancements switched off — the result is 0.0. That is the bottom of the curve (1%
	 * elite chance, ~81% uncommon, no legendaries), i.e. very nearly vanilla, which is the
	 * right thing for a config that has opted out of measuring progress.
	 */
	private static double compute(final ServerPlayer player) {
		EscalationConfig config = config();
		if (config.ignoreProgression) {
			return 1.0;
		}

		double[] answered = new double[PROVIDERS.length];
		int count = 0;

		for (ScoreProvider provider : PROVIDERS) {
			if (!provider.enabled(config)) {
				continue;
			}

			OptionalDouble score = provider.score(player);
			if (score.isPresent()) {
				answered[count] = Tuning.clamp01(score.getAsDouble());
				count++;
			}
		}

		return config.combineMode.combine(answered, count).orElse(0.0);
	}

	/** Forget every cached score. */
	public void clear() {
		this.cache.clear();
		this.nextRefreshTick = 0;
	}
}
