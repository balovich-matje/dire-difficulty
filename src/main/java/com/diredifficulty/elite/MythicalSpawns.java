package com.diredifficulty.elite;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.AffixBudget;
import com.diredifficulty.config.ConfigManager;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/**
 * Where and when a Mythical appears (2026-11-21). Never from the normal elite roll.
 *
 * <ul>
 *   <li>Natural spawns: zombified piglin in Nether Wastes, piglin in Crimson Forest, skeleton in
 *       Soul Sand Valley, wither skeleton inside a Nether Fortress, pillager at an outpost,
 *       enderman anywhere in the Overworld.</li>
 *   <li>Structure generation: piglin brute (bastions), vindicator / evoker (woodland mansions).</li>
 *   <li>Raids: vindicator / evoker of the final or bonus wave.</li>
 * </ul>
 *
 * <p>Gate: the nearest player has killed the Ender Dragon or a Wither. Natural and raid spawns
 * also need no living Mythical within {@code MYTHICAL_EXCLUSION_RADIUS} and respect a
 * per-dimension cooldown. Structure generation may run off the server thread, so there only
 * the chance and a cached "any online player is unlocked" flag apply.
 */
public final class MythicalSpawns {
	private static final Identifier KILL_DRAGON = Identifier.withDefaultNamespace("end/kill_dragon");
	private static final Identifier HEALTH_FLOOR = EliteAttributes.id("mythical_health");
	private static final Identifier KNOCKBACK = EliteAttributes.id("mythical_knockback");

	private static final Map<ResourceKey<Level>, Long> LAST_SPAWN = new HashMap<>();
	private static volatile boolean anyUnlocked;

	private MythicalSpawns() {
	}

	/** Refreshed from the progression tick, server thread. */
	public static void refresh(final MinecraftServer server) {
		boolean any = false;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (unlocked(player)) {
				any = true;
				break;
			}
		}

		anyUnlocked = any;
	}

	public static void clear() {
		LAST_SPAWN.clear();
		anyUnlocked = false;
	}

	/** True when this player has killed the Ender Dragon or a Wither. Server thread only. */
	public static boolean unlocked(final ServerPlayer player) {
		if (player.getStats().getValue(Stats.ENTITY_KILLED, EntityTypes.WITHER) > 0) {
			return true;
		}

		MinecraftServer server = player.level().getServer();
		AdvancementHolder dragon = server == null ? null : server.getAdvancements().get(KILL_DRAGON);
		return dragon != null && player.getAdvancements().getOrStartProgress(dragon).isDone();
	}

	/**
	 * Tries to make this freshly spawned mob a Mythical. Returns true when it did, in which case
	 * the normal elite roll must not run.
	 */
	public static boolean tryPromote(final Mob mob, final ServerLevel level, final EntitySpawnReason reason) {
		if (!ConfigManager.get().spawnElites || level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}

		RandomSource random = level.getRandom();
		double chance;
		boolean serverThread;

		if (reason == EntitySpawnReason.NATURAL) {
			if (!naturalPlace(mob, level)) {
				return false;
			}

			chance = Tuning.MYTHICAL_CHANCE_NATURAL;
			serverThread = true;
		} else if (reason == EntitySpawnReason.EVENT) {
			if (!finalRaidWave(mob, level)) {
				return false;
			}

			chance = Tuning.MYTHICAL_CHANCE_RAID;
			serverThread = true;
		} else if (reason == EntitySpawnReason.STRUCTURE) {
			EntityType<?> type = mob.getType();

			if (type != EntityTypes.PIGLIN_BRUTE && type != EntityTypes.VINDICATOR && type != EntityTypes.EVOKER) {
				return false;
			}

			chance = Tuning.MYTHICAL_CHANCE_STRUCTURE;
			serverThread = false;
		} else {
			return false;
		}

		if (random.nextDouble() >= chance) {
			return false;
		}

		if (serverThread) {
			ServerPlayer nearest = nearestPlayer(level, mob.blockPosition());

			if (nearest == null || !unlocked(nearest)) {
				return false;
			}

			Long last = LAST_SPAWN.get(level.dimension());

			if (last != null && level.getGameTime() - last < Tuning.MYTHICAL_COOLDOWN_TICKS) {
				return false;
			}

			if (mythicalNearby(level, mob)) {
				return false;
			}

			LAST_SPAWN.put(level.dimension(), level.getGameTime());
		} else if (!anyUnlocked) {
			return false;
		}

		EliteRoller.promote(level, mob, Rarity.MYTHICAL,
				AffixBudget.roll(mob, Rarity.MYTHICAL, EliteRules.active(), random));
		return true;
	}

	/**
	 * The Mythical-only part of a promotion, called from {@code EliteRoller.promote} after the
	 * rarity multipliers and before the ordinary elite gear: the artefact, the health floor and
	 * full knockback resistance.
	 */
	static void applyMythical(final ServerLevel level, final Mob mob, final RandomSource random) {
		if (!Rufus.is(mob)) {
			equipArtefact(level, mob, random);
		}

		double missing = Tuning.MYTHICAL_MIN_HEALTH - mob.getMaxHealth();

		if (missing > 0.0) {
			EliteAttributes.set(mob, Attributes.MAX_HEALTH, HEALTH_FLOOR, missing,
					AttributeModifier.Operation.ADD_VALUE);
		}

		EliteAttributes.set(mob, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK, 1.0,
				AttributeModifier.Operation.ADD_VALUE);
		mob.setHealth(mob.getMaxHealth());
	}

	private static void equipArtefact(final ServerLevel level, final Mob mob, final RandomSource random) {
		List<Artefact> options = new java.util.ArrayList<>();

		for (Artefact artefact : Artefact.forType(mob.getType())) {
			if (mob.canUseSlot(artefact.slot())) {
				options.add(artefact);
			}
		}

		if (options.isEmpty()) {
			for (Artefact artefact : Artefact.values()) {
				if (mob.canUseSlot(artefact.slot())) {
					options.add(artefact);
				}
			}
		}

		if (options.isEmpty()) {
			return;
		}

		Artefact artefact = options.get(random.nextInt(options.size()));
		EquipmentSlot slot = artefact.slot();
		ItemStack stack = artefact.create(level.registryAccess());
		mob.setItemSlot(slot, stack);
		mob.setGuaranteedDrop(slot);
	}

	private static boolean naturalPlace(final Mob mob, final ServerLevel level) {
		EntityType<?> type = mob.getType();
		BlockPos pos = mob.blockPosition();

		if (type == EntityTypes.ZOMBIFIED_PIGLIN) {
			return level.getBiome(pos).is(Biomes.NETHER_WASTES);
		}

		if (type == EntityTypes.PIGLIN) {
			return level.getBiome(pos).is(Biomes.CRIMSON_FOREST);
		}

		if (type == EntityTypes.SKELETON) {
			return level.getBiome(pos).is(Biomes.SOUL_SAND_VALLEY);
		}

		if (type == EntityTypes.WITHER_SKELETON) {
			return level.structureManager().getStructureWithPieceAt(pos,
					holder -> holder.is(BuiltinStructures.FORTRESS)).isValid();
		}

		if (type == EntityTypes.ENDERMAN) {
			return level.dimension() == Level.OVERWORLD;
		}

		if (type == EntityTypes.PILLAGER) {
			Structure outpost = structure(level, BuiltinStructures.PILLAGER_OUTPOST);
			return outpost != null && level.structureManager().getStructureAt(pos, outpost).isValid();
		}

		return false;
	}

	private static boolean finalRaidWave(final Mob mob, final ServerLevel level) {
		if (mob.getType() != EntityTypes.VINDICATOR && mob.getType() != EntityTypes.EVOKER) {
			return false;
		}

		if (!(mob instanceof Raider raider)) {
			return false;
		}

		Raid raid = raider.getCurrentRaid();
		return raid != null && raider.getWave() >= raid.getNumGroups(level.getDifficulty());
	}

	private static Structure structure(final ServerLevel level, final ResourceKey<Structure> key) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(key);
	}

	private static ServerPlayer nearestPlayer(final ServerLevel level, final BlockPos pos) {
		ServerPlayer best = null;
		double bestDist = Double.MAX_VALUE;

		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}

			double dist = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

			if (dist < bestDist) {
				bestDist = dist;
				best = player;
			}
		}

		return best;
	}

	private static boolean mythicalNearby(final ServerLevel level, final Mob self) {
		AABB box = self.getBoundingBox().inflate(Tuning.MYTHICAL_EXCLUSION_RADIUS);
		return !level.getEntitiesOfClass(Mob.class, box,
				other -> other != self && EliteMob.rarityOf(other) == Rarity.MYTHICAL).isEmpty();
	}
}
