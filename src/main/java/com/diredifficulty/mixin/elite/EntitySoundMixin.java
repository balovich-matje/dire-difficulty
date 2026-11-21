package com.diredifficulty.mixin.elite;

import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteMob;
import com.diredifficulty.elite.Rarity;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Elite voices: every sound a mob plays through {@code Entity.playSound} comes out deeper,
 * slower and audible from farther away on an elite, more so per rarity.
 *
 * <p>Footsteps get their own volume column (they start at 0.15, so the voice multiplier left
 * them quiet and short-ranged) and, from Epic up, a second low "weight" layer: Minecraft has
 * no EQ, so bass is added by playing a heavy step underneath, pitched down. Players are never
 * elites, so their own {@code playSound} override is never in question.
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {
	@ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"),
			argsOnly = true, ordinal = 0)
	private float direDifficulty$eliteVolume(final float volume, final SoundEvent sound,
			final float originalVolume, final float pitch) {
		Rarity rarity = EliteMob.rarityOf((Entity) (Object) this);

		if (rarity == null) {
			return volume;
		}

		return volume * (direDifficulty$isStep(sound) ? rarity.stepVolume() : rarity.voiceVolume());
	}

	@ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"),
			argsOnly = true, ordinal = 1)
	private float direDifficulty$elitePitch(final float pitch) {
		Rarity rarity = EliteMob.rarityOf((Entity) (Object) this);
		return rarity == null ? pitch : pitch * rarity.voicePitch();
	}

	@Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("TAIL"))
	private void direDifficulty$stepWeight(final SoundEvent sound, final float volume, final float pitch,
			final CallbackInfo ci) {
		Entity self = (Entity) (Object) this;

		if (self.level().isClientSide() || self.isSilent() || !direDifficulty$isStep(sound)) {
			return;
		}

		Rarity rarity = EliteMob.rarityOf(self);

		if (rarity == null || rarity.stepWeightVolume() <= 0.0F) {
			return;
		}

		// Straight to the level, not this.playSound: no recursion, no second pass through the
		// multipliers above.
		self.level().playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.RAVAGER_STEP,
				self.getSoundSource(), rarity.stepWeightVolume(), Tuning.ELITE_STEP_WEIGHT_PITCH);
	}

	private static boolean direDifficulty$isStep(final SoundEvent sound) {
		return sound.location().getPath().endsWith(".step");
	}
}
