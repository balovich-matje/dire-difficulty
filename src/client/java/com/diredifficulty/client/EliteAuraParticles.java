package com.diredifficulty.client;

import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.Rarity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Rarity-coloured motes drifting off Epic and Legendary elites — the second half of "this is
 * not an ordinary zombie", for the two tiers that earn it.
 *
 * <h2>Why a client ticker and not the render layer</h2>
 * {@link EliteAura} runs per entity per FRAME, and a frame count is a frame rate: the
 * same elite would emit twice as fast at 120 fps as at 60. Particles are a tick-rate
 * behaviour, so they belong on {@code ClientTickEvents.END_CLIENT_TICK} where the cadence is
 * the game's, not the GPU's. It also keeps the render layer doing exactly one thing.
 *
 * <p><b>Nothing here is authoritative and nothing crosses the wire.</b> The server neither
 * spawns nor knows about these; each client draws them from state it already has. That is
 * the {@code docs/DESIGN.md} §6.2 rule read the useful way round — the client may invent
 * decoration, it may never invent state.
 *
 * <h2>Bounded on purpose</h2>
 * Three separate limits, all in {@link Tuning}, because a Hell-on-Earth horde is a real
 * scenario and a fog bank is not the goal:
 *
 * <ul>
 * <li>{@code AURA_PARTICLE_INTERVAL_TICKS} — the pass runs every third tick, not every
 *     tick.</li>
 * <li>{@code AURA_PARTICLE_RADIUS} — only elites within 32 blocks of the camera, checked
 *     before the attachment lookup so the cheap test runs first.</li>
 * <li>{@code AURA_PARTICLE_MAX_PER_PASS} plus {@code AURA_PARTICLE_CHANCE} — a hard cap on
 *     motes per pass, and a coin flip per elite so the cap is not always spent on whichever
 *     entities {@code entitiesForRendering()} happens to list first.</li>
 * </ul>
 *
 * <p>The walk is over {@code ClientLevel.entitiesForRendering()}, which is the set the
 * client is already iterating to draw the frame — no lookup, no query, no allocation.
 */
public final class EliteAuraParticles {
	/**
	 * Decoration only, so a dedicated {@code RandomSource} rather than the level's. Nothing
	 * this draws may consume randomness the client shares with anything simulated.
	 */
	private static final RandomSource RANDOM = RandomSource.create();

	private static int tickCounter;

	private EliteAuraParticles() {
	}

	/** {@code ClientTickEvents.END_CLIENT_TICK}. */
	public static void onEndClientTick(final Minecraft client) {
		ClientLevel level = client.level;
		Entity camera = client.getCameraEntity();

		if (level == null || camera == null || client.isPaused()
				|| !com.diredifficulty.config.ConfigManager.get().eliteGlow) {
			return;
		}

		tickCounter++;

		if (tickCounter % Tuning.AURA_PARTICLE_INTERVAL_TICKS != 0) {
			return;
		}

		double radiusSq = Tuning.AURA_PARTICLE_RADIUS * Tuning.AURA_PARTICLE_RADIUS;
		int emitted = 0;

		for (final Entity entity : level.entitiesForRendering()) {
			if (emitted >= Tuning.AURA_PARTICLE_MAX_PER_PASS) {
				return;
			}

			if (!(entity instanceof LivingEntity living) || living.isInvisible()) {
				continue;
			}

			// Cheapest test first: a distance check on a number the entity already holds,
			// before touching the attachment store.
			if (living.distanceToSqr(camera) > radiusSq) {
				continue;
			}

			EliteData elite = EliteClientState.eliteOf(living);

			if (elite == null) {
				continue;
			}

			Rarity rarity = elite.rarity();

			// Epic and up. Uncommon and Rare carry the glow and nothing else — motes on every
			// green elite in a cave would be noise rather than a signal.
			if (rarity == null || rarity.tier() < Tuning.AURA_PARTICLE_MIN_TIER) {
				continue;
			}

			if (RANDOM.nextDouble() >= Tuning.AURA_PARTICLE_CHANCE) {
				continue;
			}

			emit(level, living, rarity);
			emitted++;
		}
	}

	/**
	 * One mote, somewhere in the elite's own bounding box.
	 *
	 * <p>{@code ParticleTypes.ENTITY_EFFECT} takes a {@link ColorParticleOption}, so the mote
	 * is the rarity colour by construction rather than by a second palette — the same
	 * {@code Tuning.RARITY_COLOUR} row the glow and the name tag read. Vanilla's own
	 * {@code SpellParticle} carries {@code gravity = -0.1F}, a slight UPWARD acceleration, so
	 * a negative launch velocity produces a mote that drips and then hangs rather than one
	 * that rains — which is the look, and the reason the drift constant is small.
	 *
	 * <p>The box is read live rather than cached: {@code Attributes.SCALE} makes an elite's
	 * hitbox bigger than its type's default, and that is exactly the volume the motes should
	 * fill.
	 */
	private static void emit(final ClientLevel level, final LivingEntity elite, final Rarity rarity) {
		AABB box = elite.getBoundingBox();

		double x = Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX);
		double y = Mth.lerp(RANDOM.nextDouble(), box.minY, box.maxY);
		double z = Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ);

		level.addParticle(
				ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, ARGB.opaque(rarity.colour())),
				x, y, z,
				0.0, Tuning.AURA_PARTICLE_DRIFT_Y, 0.0);
	}
}
