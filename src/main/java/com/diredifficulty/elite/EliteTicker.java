package com.diredifficulty.elite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.Affixes;
import com.diredifficulty.affix.impl.SunderingAffix;
import com.diredifficulty.affix.impl.WarlordAffix;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Every periodic elite behaviour, in ONE {@code END_SERVER_TICK} listener — the {@code *Ticker}
 * shape both sibling repos use, for the reason they use it.
 *
 * <p><b>The pass is bounded by construction, and that is the whole design of this file.</b>
 * Iterating every loaded entity on every tick is how a mod that is fine in single-player
 * becomes unplayable on a server with forty chunk-loaders. So the pass runs every
 * {@code ELITE_TICK_INTERVAL} ticks, not every tick, and it looks only within
 * {@code ELITE_TICK_RADIUS} of an online player. Nothing is lost by that: the longest aura
 * reaches five blocks, a summoner needs a target, and a shockwave with nobody to shock is not
 * an event. An elite nobody can see has nothing to do.
 *
 * <p><b>Deduplicated across players.</b> Two players standing together must not make an
 * elite's aura tick twice, which would double every aura's damage-per-second and halve every
 * cooldown. The set is keyed on entity id.
 *
 * <p>The pass also carries two pieces of upkeep that belong to no single elite, because both
 * are about releasing something an elite granted and the granter may be gone:
 * {@link WarlordAffix#upkeep} takes an aura back off allies no living Warlord is vouching for,
 * and {@link SunderingAffix#expire} takes an armour shred back off a player whose stack has
 * lapsed. Both ride the entity lists this pass already built, so neither costs a scan.
 */
public final class EliteTicker {
	private static int tickCounter;

	private EliteTicker() {
	}

	/** Called every server tick. Almost every call returns on the first line. */
	public static void onEndServerTick(final MinecraftServer server) {
		tickCounter++;

		if (tickCounter % Tuning.ELITE_TICK_INTERVAL != 0) {
			return;
		}

		for (final ServerLevel level : server.getAllLevels()) {
			tickLevel(level);
		}
	}

	private static void tickLevel(final ServerLevel level) {
		List<ServerPlayer> players = level.players();

		if (players.isEmpty()) {
			return;
		}

		long now = level.getGameTime();
		Set<Integer> seen = new HashSet<>();
		List<Mob> nearby = new ArrayList<>();
		List<Mob> elites = new ArrayList<>();
		List<Mob> warlords = new ArrayList<>();

		for (final ServerPlayer player : players) {
			SunderingAffix.expire(player, now);

			if (player.isSpectator()) {
				continue;
			}

			List<Mob> found = level.getEntitiesOfClass(Mob.class,
					player.getBoundingBox().inflate(Tuning.ELITE_TICK_RADIUS), Mob::isAlive);

			for (final Mob mob : found) {
				if (!seen.add(mob.getId())) {
					continue;
				}

				nearby.add(mob);

				if (EliteMob.isElite(mob)) {
					elites.add(mob);

					if (EliteMob.has(mob, WarlordAffix.ID)) {
						warlords.add(mob);
					}
				}
			}
		}

		for (final Mob elite : elites) {
			tickElite(level, elite);
		}

		// After every Warlord has asserted this pass, so a mob that moved between two
		// Warlords is not released and re-granted on alternate passes.
		WarlordAffix.upkeep(nearby, warlords);
	}

	private static void tickElite(final ServerLevel level, final Mob mob) {
		EliteData data = EliteMob.data(mob);

		// A frozen elite (/dire freeze, NoAI) is a statue: no affix ticks, no dominance.
		if (data == null || !data.isElite() || mob.isNoAi()) {
			return;
		}

		Rarity rarity = data.rarity();

		EliteDominance.tick(level, mob, rarity);

		for (final String id : data.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix != null) {
				affix.onTick(level, mob, rarity, Tuning.ELITE_TICK_INTERVAL);
			}
		}
	}
}
