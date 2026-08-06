package com.diredifficulty.spawn;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;
import com.diredifficulty.config.ConfigManager;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/**
 * Preset 4's focus mechanic — the "mobs actively seek the player" half of Hell on Earth
 * ({@code docs/DESIGN.md} §5.3). An {@code END_SERVER_TICK} listener in the sibling repos'
 * {@code *Ticker} shape.
 *
 * <p>Every {@link Tuning#HUNT_INTERVAL_TICKS}, each online player has up to
 * {@link Tuning#HUNT_MAX_PER_PLAYER} untargeted monsters within {@link Tuning#HUNT_RADIUS}
 * handed the player as a target.
 *
 * <h2>The part that is not obvious, and why this is a mechanic rather than a one-liner</h2>
 *
 * <p>A bare {@code setTarget} at distance is discarded within a tick or two, because
 * {@code NearestAttackableTargetGoal}'s continue-check re-evaluates its targeting conditions
 * against {@code FOLLOW_RANGE}. So the Hunt also applies a <b>transient {@code FOLLOW_RANGE}
 * modifier</b> sized to the hunt radius for as long as the mob is hunting, and removes it when
 * the hunt drops. The modifier is asserted (and released) on every pass, the same way
 * Archetypes' tickers assert their standing attribute modifiers — which is what makes this
 * respawn- and reload-safe without an event of its own.
 *
 * <h2>Bounds, on purpose</h2>
 *
 * <ul>
 * <li>At most {@link Tuning#HUNT_MAX_PER_PLAYER} newly assigned mobs per player per pass, so
 *     this cannot become an unbounded pathfinding storm on a busy server.</li>
 * <li>The hunting set is walked and pruned every pass independently of where the players are,
 *     so a mob left behind when a player runs away is still released instead of keeping an
 *     inflated follow range until it unloads.</li>
 * <li><b>Nothing teleports.</b> Mobs walk, they path around obstacles, and terrain still works
 *     as cover. Vanilla-plus.</li>
 * </ul>
 */
public final class TheHunt {
	private static final TheHunt INSTANCE = new TheHunt();

	/**
	 * The transient follow-range modifier's id. Namespaced to the permanent mod id, like every
	 * other identifier this mod mints.
	 */
	private static final Identifier HUNT_FOLLOW_RANGE_ID =
			Identifier.fromNamespaceAndPath(DireDifficulty.MOD_ID, "hunt_follow_range");

	/**
	 * Mobs currently hunting. Server thread only. Holding entity references between passes is
	 * safe here because the list is pruned at the top of every pass — a removed or dead mob
	 * survives in it for at most {@link Tuning#HUNT_INTERVAL_TICKS}.
	 */
	private final List<Mob> hunting = new ArrayList<>();

	private int nextPassTick;

	private TheHunt() {
	}

	/** Wire the pass. Called once from common init. */
	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::onEndTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> INSTANCE.hunting.clear());
	}

	private void onEndTick(final MinecraftServer server) {
		if (!ConfigManager.get().theHunt) {
			// Switched off mid-session: give every mob its own follow range back on the very
			// next tick rather than at the next scheduled pass. Turning a knob off has to
			// actually turn the thing off.
			if (!this.hunting.isEmpty()) {
				releaseAll();
			}

			return;
		}

		if (server.getTickCount() < this.nextPassTick) {
			return;
		}

		this.nextPassTick = server.getTickCount() + Math.max(1, Tuning.HUNT_INTERVAL_TICKS);

		prune();
		acquire(server);
	}

	/**
	 * Release every mob that has stopped hunting — dead, removed, target lost, or now beyond
	 * {@link Tuning#HUNT_RELEASE_RADIUS} of any player.
	 *
	 * <p>The release radius is strictly larger than the acquire radius so a mob sitting at the
	 * boundary does not thrash between acquired and released on alternate passes.
	 */
	private void prune() {
		Iterator<Mob> iterator = this.hunting.iterator();

		while (iterator.hasNext()) {
			Mob mob = iterator.next();

			boolean stillHunting = !mob.isRemoved()
					&& mob.isAlive()
					&& mob.getTarget() != null
					&& mob.level().getNearestPlayer(mob, Tuning.HUNT_RELEASE_RADIUS) != null;

			if (!stillHunting) {
				release(mob);
				iterator.remove();
			}
		}
	}

	private void acquire(final MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator() || player.isCreative()) {
					continue;
				}

				acquireFor(level, player);
			}
		}
	}

	private void acquireFor(final ServerLevel level, final ServerPlayer player) {
		AABB box = AABB.ofSize(player.position(),
				Tuning.HUNT_RADIUS * 2.0, Tuning.HUNT_RADIUS * 2.0, Tuning.HUNT_RADIUS * 2.0);

		// The predicate runs inside the broadphase, so the returned list is already only the
		// mobs worth looking at: hostile, alive, awake, and not already busy with someone.
		List<Mob> candidates = level.getEntitiesOfClass(Mob.class, box, mob ->
				mob.isAlive()
						&& !mob.isNoAi()
						&& mob.getTarget() == null
						&& mob.getType().getCategory() == MobCategory.MONSTER);

		double radiusSqr = Tuning.HUNT_RADIUS * Tuning.HUNT_RADIUS;
		int budget = Tuning.HUNT_MAX_PER_PLAYER;

		for (Mob mob : candidates) {
			if (budget <= 0) {
				break;
			}

			// AABB.ofSize gives a cube; the promise is a radius.
			if (mob.distanceToSqr(player) > radiusSqr) {
				continue;
			}

			// canAttack is the mob's own answer, so mobs that would never fight this player
			// (a creeper mid-swell aside, anything with a targeting exception) are left alone
			// rather than handed a target their goals will discard next tick anyway.
			if (!mob.canAttack(player)) {
				continue;
			}

			mob.setTarget(player);
			assertFollowRange(mob);
			this.hunting.add(mob);
			budget--;
		}
	}

	/**
	 * Raise this mob's follow range to {@link Tuning#HUNT_FOLLOW_RANGE} if its own is smaller.
	 *
	 * <p>Transient, so it is never written to disk and a reload can never leave a stranded
	 * modifier behind. {@code addOrUpdateTransientModifier} makes the call idempotent, which is
	 * what lets the pass simply assert the state it wants every time instead of tracking
	 * whether it already did.
	 */
	private static void assertFollowRange(final Mob mob) {
		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range == null) {
			return;
		}

		double needed = Tuning.HUNT_FOLLOW_RANGE - range.getBaseValue();
		if (needed <= 0.0) {
			// Already reaches further than the hunt does. Adding nothing is correct, but a
			// stale modifier from an earlier, smaller base would not be — drop it.
			range.removeModifier(HUNT_FOLLOW_RANGE_ID);
			return;
		}

		range.addOrUpdateTransientModifier(
				new AttributeModifier(HUNT_FOLLOW_RANGE_ID, needed, AttributeModifier.Operation.ADD_VALUE));
	}

	private static void release(final Mob mob) {
		if (mob.isRemoved()) {
			return;
		}

		AttributeInstance range = mob.getAttribute(Attributes.FOLLOW_RANGE);
		if (range != null) {
			range.removeModifier(HUNT_FOLLOW_RANGE_ID);
		}
	}

	private void releaseAll() {
		for (Mob mob : this.hunting) {
			release(mob);
		}

		this.hunting.clear();
	}
}
