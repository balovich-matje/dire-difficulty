package com.diredifficulty.client;

import com.diredifficulty.Tuning;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Which elite the crosshair is on, and for how long after it leaves — the input half of
 * {@link ElitePlate}.
 *
 * <h2>The raycast, and why it is vanilla's own</h2>
 * {@code Minecraft.hitResult} is no use here: it is clipped to the player's interaction
 * reach, three-and-a-bit blocks, and an elite plate that only appeared when you were already
 * inside a Legendary's swing would be a joke. So the pick is
 * {@code ProjectileUtil.getHitResultOnViewVector(camera, predicate, distance)} — public
 * static, taken off the 26.2 sources rather than reimplemented — at
 * {@link Tuning#PLATE_RAY_DISTANCE}.
 *
 * <p>Using vanilla's helper rather than a hand-rolled ray buys the wall rule for free, and
 * that matters: its first act is
 * {@code level.clipIncludingBorder(new ClipContext(from, to, COLLIDER, NONE, source))}, and
 * it then truncates the entity search to the block hit. An elite behind a wall is never
 * returned, so the plate cannot become an x-ray for the same mob whose GLOW the design goes
 * to such lengths to keep depth-tested ({@code docs/DESIGN.md} §2.2). One rule, both
 * surfaces.
 *
 * <p>The predicate is {@code EntitySelector.CAN_BE_PICKED}, so anything solid enough to be
 * looked at blocks the pick — a non-elite standing between you and the elite hides the plate,
 * exactly as it hides the mob. {@code getHitResultOnViewVector} excludes the source entity
 * itself through {@code Level.getEntities(except, …)}, so no self-check is needed.
 *
 * <h2>The linger, and what it is not</h2>
 * {@link Tuning#PLATE_LINGER_TICKS} — one second — of hysteresis after the crosshair leaves.
 * A crosshair drifts constantly in a fight, and a plate that blinked on and off with it would
 * be unreadable at exactly the moment it is worth reading. The linger is a DISPLAY delay, not
 * a cache: only the entity reference is held, and everything shown — health, rarity, the
 * affix list — is re-read from the synced attachment at draw time. So a lingering plate shows
 * the elite's health going down while you look away from it, and a plate whose elite stops
 * being an elite disappears on the next frame rather than at the end of the second.
 *
 * <p>Four conditions drop the target immediately rather than lingering: the level going away
 * (disconnect, dimension change), the entity dying, the entity being removed from the level,
 * and the entity ceasing to be an elite. Holding a strong reference to a removed entity for a
 * second is harmless, but showing a health bar for a corpse is not.
 */
public final class ElitePlateTracker {
	/**
	 * The elite under the crosshair, or the one that was there within the last
	 * {@link Tuning#PLATE_LINGER_TICKS}. Null the rest of the time.
	 *
	 * <p>Client-thread only — written from {@code END_CLIENT_TICK}, read from the HUD
	 * extract, both of which run on the render thread. No synchronisation, and none needed.
	 */
	private static LivingEntity target;

	private static int lingerTicksLeft;

	private ElitePlateTracker() {
	}

	/** {@code ClientTickEvents.END_CLIENT_TICK}. */
	public static void onEndClientTick(final Minecraft client) {
		Entity camera = client.getCameraEntity();

		if (client.level == null || camera == null) {
			clear();
			return;
		}

		LivingEntity aimed = aimedElite(camera);

		if (aimed != null) {
			target = aimed;
			lingerTicksLeft = Tuning.PLATE_LINGER_TICKS;
			return;
		}

		if (target == null) {
			return;
		}

		// Reasons to drop the target NOW rather than after the linger. `level() != client.level`
		// covers a dimension change and a world reload, both of which leave the old entity
		// object alive and orphaned.
		if (!target.isAlive() || target.isRemoved() || target.level() != client.level
				|| EliteClientState.eliteOf(target) == null) {
			clear();
			return;
		}

		lingerTicksLeft--;

		if (lingerTicksLeft <= 0) {
			clear();
		}
	}

	/** The elite the plate should draw, or {@code null}. */
	public static LivingEntity target() {
		return target;
	}

	private static void clear() {
		target = null;
		lingerTicksLeft = 0;
	}

	/**
	 * The elite the crosshair is on right now, or {@code null}.
	 *
	 * <p>{@code getHitResultOnViewVector} computes its view vector at partial tick 0, i.e.
	 * the rotation as of the previous tick. That is one tick of aim lag on ACQUISITION only,
	 * which is 50 ms against a one-second linger — not worth reimplementing the helper, and
	 * reimplementing it would mean re-deriving the block clip that gives the wall rule.
	 */
	private static LivingEntity aimedElite(final Entity camera) {
		HitResult hit = ProjectileUtil.getHitResultOnViewVector(
				camera, EntitySelector.CAN_BE_PICKED, Tuning.PLATE_RAY_DISTANCE);

		if (hit.getType() != HitResult.Type.ENTITY) {
			return null;
		}

		Entity entity = ((EntityHitResult) hit).getEntity();

		if (!(entity instanceof LivingEntity living)) {
			return null;
		}

		return EliteClientState.eliteOf(living) != null ? living : null;
	}
}
