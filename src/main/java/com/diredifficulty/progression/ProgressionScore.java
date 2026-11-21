package com.diredifficulty.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.diredifficulty.Tuning;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * The progression stage, 0..1 (rewritten 2026-11-21).
 *
 * <p>Four vanilla statistics, nothing from any other mod, so the stage means the same thing in
 * every modpack: completed advancements, hostile kills, distance travelled and time since the
 * last death. Each is eased out to its target (fast early, diminishing near the target) and
 * weighted, and the weights sum to 1 — so no single statistic can carry the stage beyond its
 * own weight. The world difficulty then caps the result: Easy 0.25, Normal 0.5, Hard 1.
 *
 * <p>Per player, cached and refreshed every {@code SCORE_REFRESH_TICKS}. A position reads the
 * best stage among players within {@code SCORE_RADIUS}, else the nearest player's.
 */
public final class ProgressionScore implements ScoreSource {
	public static final ProgressionScore INSTANCE = new ProgressionScore();

	/** One player's factors, each already eased to 0..1, plus the weighted, capped total. */
	public record Breakdown(double advancements, double kills, double distance, double survival,
			double uncapped, double cap, double stage) {
	}

	private static final Identifier[] DISTANCE_STATS = {
		Stats.WALK_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SPRINT_ONE_CM, Stats.WALK_ON_WATER_ONE_CM,
		Stats.WALK_UNDER_WATER_ONE_CM, Stats.SWIM_ONE_CM, Stats.CLIMB_ONE_CM, Stats.AVIATE_ONE_CM,
		Stats.MINECART_ONE_CM, Stats.BOAT_ONE_CM, Stats.PIG_ONE_CM, Stats.HORSE_ONE_CM,
		Stats.STRIDER_ONE_CM, Stats.HAPPY_GHAST_ONE_CM, Stats.NAUTILUS_ONE_CM,
	};

	private final Map<UUID, Double> cache = new HashMap<>();
	private int nextRefreshTick;
	private double highestOnline;

	private ServerAdvancementManager cachedFor;
	private List<AdvancementHolder> cachedEligible = List.of();
	private List<EntityType<?>> hostileTypes;

	private ProgressionScore() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onEndTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> INSTANCE.clear());
		ScoreSource.install(INSTANCE);
	}

	@Override
	public String id() {
		return "progression";
	}

	@Override
	public double scoreFor(final ServerPlayer player) {
		Double cached = this.cache.get(player.getUUID());

		if (cached != null) {
			return cached;
		}

		double fresh = breakdown(player).stage();
		this.cache.put(player.getUUID(), fresh);
		return fresh;
	}

	@Override
	public double scoreAt(final ServerLevel level, final BlockPos pos) {
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

		return nearestDistSqr == Double.MAX_VALUE ? 0.0 : nearestScore;
	}

	/**
	 * The highest stage among online players, refreshed with the cache. For the settings that
	 * vanilla applies world-wide rather than at a position (mob cap, despawn distance).
	 */
	public double highestOnline() {
		return this.highestOnline;
	}

	/** Computes one player's stage from scratch. Also what {@code /dire score} prints. */
	public Breakdown breakdown(final ServerPlayer player) {
		ServerStatsCounter stats = player.getStats();

		double advancements = Tuning.easeOut(completedAdvancements(player), Tuning.STAGE_TARGET_ADVANCEMENTS);
		double kills = Tuning.easeOut(hostileKills(stats), Tuning.STAGE_TARGET_KILLS);

		long centimetres = 0L;

		for (Identifier stat : DISTANCE_STATS) {
			centimetres += stats.getValue(Stats.CUSTOM, stat);
		}

		double distance = Tuning.easeOut(centimetres / 100.0, Tuning.STAGE_TARGET_DISTANCE);
		double survival = Tuning.easeOut(stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH),
				Tuning.STAGE_TARGET_SURVIVAL_TICKS);

		double uncapped = Tuning.clamp01(advancements * Tuning.STAGE_WEIGHT_ADVANCEMENTS
				+ kills * Tuning.STAGE_WEIGHT_KILLS
				+ distance * Tuning.STAGE_WEIGHT_DISTANCE
				+ survival * Tuning.STAGE_WEIGHT_SURVIVAL);
		double cap = cap(player.level().getDifficulty());
		return new Breakdown(advancements, kills, distance, survival, uncapped, cap, Math.min(uncapped, cap));
	}

	public static double cap(final Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL -> Tuning.STAGE_CAP_PEACEFUL;
			case EASY -> Tuning.STAGE_CAP_EASY;
			case NORMAL -> Tuning.STAGE_CAP_NORMAL;
			case HARD -> Tuning.STAGE_CAP_HARD;
		};
	}

	private int completedAdvancements(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();

		if (server == null) {
			return 0;
		}

		PlayerAdvancements progress = player.getAdvancements();
		int done = 0;

		for (AdvancementHolder holder : eligible(server.getAdvancements())) {
			if (progress.getOrStartProgress(holder).isDone()) {
				done++;
			}
		}

		return done;
	}

	/** Advancements with a display (no hidden plumbing) and not a recipe unlock. */
	private List<AdvancementHolder> eligible(final ServerAdvancementManager manager) {
		if (manager == this.cachedFor) {
			return this.cachedEligible;
		}

		Collection<AdvancementHolder> all = manager.getAllAdvancements();
		List<AdvancementHolder> out = new ArrayList<>(all.size());

		for (AdvancementHolder holder : all) {
			if (holder.value().display().isPresent() && !holder.id().getPath().startsWith("recipes/")) {
				out.add(holder);
			}
		}

		this.cachedFor = manager;
		this.cachedEligible = List.copyOf(out);
		return this.cachedEligible;
	}

	/** Sum of the "killed" statistic over every MONSTER-category entity type. */
	private int hostileKills(final ServerStatsCounter stats) {
		if (this.hostileTypes == null) {
			List<EntityType<?>> types = new ArrayList<>();

			for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
				if (type.getCategory() == MobCategory.MONSTER) {
					types.add(type);
				}
			}

			this.hostileTypes = List.copyOf(types);
		}

		long total = 0L;

		for (EntityType<?> type : this.hostileTypes) {
			total += stats.getValue(Stats.ENTITY_KILLED, type);
		}

		return (int) Math.min(total, Integer.MAX_VALUE);
	}

	private void onEndTick(final MinecraftServer server) {
		if (server.getTickCount() < this.nextRefreshTick) {
			return;
		}

		this.nextRefreshTick = server.getTickCount() + Math.max(1, Tuning.SCORE_REFRESH_TICKS);
		List<ServerPlayer> online = server.getPlayerList().getPlayers();
		Set<UUID> present = new HashSet<>(online.size());
		double highest = 0.0;

		for (ServerPlayer player : online) {
			UUID uuid = player.getUUID();
			present.add(uuid);
			double stage = breakdown(player).stage();
			this.cache.put(uuid, stage);

			if (!player.isSpectator()) {
				highest = Math.max(highest, stage);
			}
		}

		this.cache.keySet().retainAll(present);
		this.highestOnline = highest;
		com.diredifficulty.elite.MythicalSpawns.refresh(server);
	}

	public void clear() {
		this.cache.clear();
		this.nextRefreshTick = 0;
		this.highestOnline = 0.0;
		com.diredifficulty.elite.MythicalSpawns.clear();
		this.cachedFor = null;
		this.cachedEligible = List.of();
	}
}
