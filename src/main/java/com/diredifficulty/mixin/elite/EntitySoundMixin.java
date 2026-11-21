package com.diredifficulty.mixin.elite;

import com.diredifficulty.elite.EliteMob;
import com.diredifficulty.elite.Rarity;

import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Elite voices: every sound a mob plays through {@code Entity.playSound} (ambient, hurt,
 * death, steps, the creeper fuse, most attack sounds) comes out deeper, slower and audible
 * from farther away on an elite, more so per rarity. Players are never elites, so their own
 * {@code playSound} override is never in question.
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {
	@ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"),
			argsOnly = true, ordinal = 0)
	private float direDifficulty$eliteVolume(final float volume) {
		Rarity rarity = EliteMob.rarityOf((Entity) (Object) this);
		return rarity == null ? volume : volume * rarity.voiceVolume();
	}

	@ModifyVariable(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"),
			argsOnly = true, ordinal = 1)
	private float direDifficulty$elitePitch(final float pitch) {
		Rarity rarity = EliteMob.rarityOf((Entity) (Object) this);
		return rarity == null ? pitch : pitch * rarity.voicePitch();
	}
}
