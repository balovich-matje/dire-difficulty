package com.diredifficulty.elite;

import java.util.List;

import com.diredifficulty.Tuning;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.mixin.accessor.MobGoalsAccessor;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.cow.CowVariants;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Easter egg (user idea 2026-11-21): Rufus the Bull, a nod to the farm channel's famous giant
 * bull. A rare naturally spawned cow in a warm biome becomes a Mythical, warm-coat giant named
 * "Rufus the Bull" that hunts every hostile mob it sees, and charges anyone who hits it. No
 * affixes, no artefact.
 *
 * <p>Marked by the entity tag {@link #TAG} (persists). Goals are not saved, so they are added
 * whenever a tagged cow is loaded.
 */
public final class Rufus {
	public static final String TAG = "dire_difficulty_rufus";
	public static final String NAME = "Rufus the Bull";

	private Rufus() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Cow cow && cow.entityTags().contains(TAG)) {
				installGoals(cow);
			}
		});
	}

	/** Natural-spawn hook. True when this cow became Rufus. */
	public static boolean tryCreate(final Mob mob, final ServerLevel level, final EntitySpawnReason reason) {
		if (!(mob instanceof Cow cow) || mob.getType() != EntityTypes.COW || cow.isBaby()) {
			return false;
		}

		if (reason != EntitySpawnReason.NATURAL && reason != EntitySpawnReason.CHUNK_GENERATION) {
			return false;
		}

		if (!ConfigManager.get().spawnElites
				|| !level.getBiome(cow.blockPosition()).is(BiomeTags.SPAWNS_WARM_VARIANT_FARM_ANIMALS)
				|| level.getRandom().nextDouble() >= Tuning.RUFUS_CHANCE) {
			return false;
		}

		make(level, cow);
		return true;
	}

	/** Turns this cow into Rufus. Also used by {@code /dire rufus}. */
	public static void make(final ServerLevel level, final Cow cow) {
		cow.addTag(TAG);
		level.registryAccess().lookupOrThrow(Registries.COW_VARIANT).get(CowVariants.WARM).ifPresent(cow::setVariant);
		EliteRoller.promote(level, cow, Rarity.MYTHICAL, List.of());
		EliteAttributes.set(cow, Attributes.SCALE, EliteAttributes.id("rufus_scale"), Tuning.RUFUS_EXTRA_SCALE,
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		cow.setCustomName(Component.literal(NAME).withStyle(Style.EMPTY.withColor(Rarity.MYTHICAL.colour())));
		cow.setPersistenceRequired();
	}

	public static boolean is(final Mob mob) {
		return mob.entityTags().contains(TAG);
	}

	private static void installGoals(final Cow cow) {
		MobGoalsAccessor goals = (MobGoalsAccessor) cow;
		goals.direDifficulty$goalSelector().removeAllGoals(goal -> goal instanceof PanicGoal);
		goals.direDifficulty$goalSelector().addGoal(1, new Charge(cow));
		goals.direDifficulty$targetSelector().addGoal(1, new HurtByTargetGoal(cow));
		goals.direDifficulty$targetSelector().addGoal(2, new NearestAttackableTargetGoal<>(cow, Mob.class, 10,
				true, false, (target, level) -> target instanceof Enemy));
	}

	/**
	 * Melee charge. A cow has no attack-damage attribute, so vanilla's {@code doHurtTarget}
	 * cannot be used; the hit is dealt here. The elite damage multiplier still applies on top
	 * in the damage funnel.
	 */
	static final class Charge extends MeleeAttackGoal {
		private final PathfinderMob bull;

		Charge(final PathfinderMob bull) {
			super(bull, Tuning.RUFUS_CHARGE_SPEED, true);
			this.bull = bull;
		}

		@Override
		protected void checkAndPerformAttack(final LivingEntity target) {
			if (!this.canPerformAttack(target) || !(this.bull.level() instanceof ServerLevel level)) {
				return;
			}

			this.resetAttackCooldown();
			net.minecraft.world.damagesource.DamageSource source = this.bull.damageSources().mobAttack(this.bull);
			target.hurtServer(level, source, Tuning.RUFUS_DAMAGE);

			Vec3 away = new Vec3(this.bull.getX() - target.getX(), 0.0, this.bull.getZ() - target.getZ());
			target.knockback(Tuning.RUFUS_KNOCKBACK, away.x, away.z, source, Tuning.RUFUS_DAMAGE);
			target.setDeltaMovement(target.getDeltaMovement().add(0.0, Tuning.RUFUS_LIFT, 0.0));

			if (target instanceof Player) {
				target.syncVelocity = true;
			}

			level.playSound(null, this.bull.getX(), this.bull.getY(), this.bull.getZ(), SoundEvents.RAVAGER_ATTACK,
					this.bull.getSoundSource(), 1.0F, 0.7F);
		}
	}

	/** Spawns a fresh Rufus at a position, for {@code /dire rufus}. */
	public static Cow spawnAt(final ServerLevel level, final BlockPos pos) {
		Cow cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);

		if (cow == null) {
			return null;
		}

		cow.snapTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
		make(level, cow);
		level.addFreshEntity(cow);
		return cow;
	}
}
