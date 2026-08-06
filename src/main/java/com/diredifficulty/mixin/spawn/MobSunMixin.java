package com.diredifficulty.mixin.spawn;

import com.diredifficulty.spawn.SpawnControl;

import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sunlight-burn immunity — <b>one hook, two callers</b>.
 *
 * <p>{@code docs/DESIGN.md} §5.2(b): without this, day spawns burn to death within seconds and
 * the day-spawning feature does nothing at all. §3.1: the {@code sunwalker} affix needs the
 * same suppression for a completely different reason.
 *
 * <p>Both go through {@link SpawnControl#suppressSunBurn(Mob)} rather than through two mixins
 * on one target. The affix system contributes its half by registering a predicate
 * ({@link SpawnControl#addSunImmunity}) at init, so neither feature has to import the other and
 * either can be changed or cut without touching this file.
 *
 * <p>Verified against the 26.2 sources: {@code Mob.java:502}, <b>private</b>, called from
 * {@code burnUndead} at {@code Mob.java:484}. Private is fine for a mixin target.
 */
@Mixin(Mob.class)
public abstract class MobSunMixin {
	@Inject(method = "isSunBurnTick()Z", at = @At("HEAD"), cancellable = true)
	private void direDifficulty$sunBurnImmunity(final CallbackInfoReturnable<Boolean> cir) {
		if (SpawnControl.suppressSunBurn((Mob) (Object) this)) {
			cir.setReturnValue(false);
		}
	}
}
