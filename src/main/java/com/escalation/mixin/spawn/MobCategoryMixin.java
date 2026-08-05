package com.escalation.mixin.spawn;

import com.escalation.spawn.SpawnControl;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import net.minecraft.world.entity.MobCategory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mob cap and despawn distance — {@code docs/DESIGN.md} §5.2(d). All three knobs are
 * {@link MobCategory} getters, and all three are applied to {@code MONSTER} only (the filter
 * lives in {@link SpawnControl}, so this file has no policy in it).
 *
 * <p><b>Why the getter and not {@code SpawnState}.</b> {@code getMaxInstancesPerChunk} feeds
 * <b>both</b> caps: the global one in
 * {@code NaturalSpawner$SpawnState.canSpawnForCategoryGlobal} and the per-chunk one in
 * {@code LocalMobCapCalculator$MobCounts.canSpawn} (both verified reading this getter in the
 * 26.2 sources). One knob, both caps, and no way for the two to disagree. A {@code SpawnState}
 * mixin would have moved one of them and left the other.
 *
 * <p>The despawn pair is read by {@code Mob.checkDespawn}: scaling down clears mobs out sooner,
 * scaling up keeps them around. Both are scaled by the same factor so vanilla's 32/128 ratio
 * survives any setting.
 *
 * <p>Verified against the 26.2 sources: {@code MobCategory.java:47, 59, 63}. Note that
 * {@code getNoDespawnDistance} returns a literal {@code 32} rather than the field — hence
 * {@code @ModifyReturnValue} rather than anything that reads state.
 */
@Mixin(MobCategory.class)
public abstract class MobCategoryMixin {
	@ModifyReturnValue(method = "getMaxInstancesPerChunk()I", at = @At("RETURN"))
	private int escalation$mobCap(final int original) {
		return SpawnControl.maxInstancesPerChunk((MobCategory) (Object) this, original);
	}

	@ModifyReturnValue(method = "getDespawnDistance()I", at = @At("RETURN"))
	private int escalation$despawnDistance(final int original) {
		return SpawnControl.despawnDistance((MobCategory) (Object) this, original);
	}

	@ModifyReturnValue(method = "getNoDespawnDistance()I", at = @At("RETURN"))
	private int escalation$noDespawnDistance(final int original) {
		return SpawnControl.noDespawnDistance((MobCategory) (Object) this, original);
	}
}
