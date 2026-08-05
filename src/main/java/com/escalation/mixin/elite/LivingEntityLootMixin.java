package com.escalation.mixin.elite;

import com.escalation.elite.EliteLoot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Elite drops and elite experience — {@code docs/DESIGN.md} §6.4's {@code LivingEntityLootMixin}
 * row.
 *
 * <p>Separate from {@code LivingEntityMixin} even though both target {@code LivingEntity},
 * because that one is pinned at priority 1500 to order a damage clamp against two other mods
 * and this one has no ordering requirement at all. Folding them together would attach a
 * priority to loot for no reason and make the reason for the priority harder to find.
 *
 * <p>Two injections, both TAIL-or-later so vanilla has already done its work:
 *
 * <ul>
 * <li>{@code dropAllDeathLoot} at TAIL — the vanilla loot table, the equipment drop and the
 *     experience award have all run, so elite drops land <i>alongside</i> a normal death
 *     rather than instead of one.</li>
 * <li>{@code ExperienceOrb.award}'s amount argument inside {@code dropExperience} — the
 *     honest way to multiply the experience reward, because the base is computed by a
 *     protected method and a second batch of orbs would be visibly two drops rather than one
 *     bigger one.</li>
 * </ul>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLootMixin {
	@Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
	private void escalation$dropEliteLoot(final ServerLevel level, final DamageSource source,
			final CallbackInfo ci) {
		EliteLoot.onDropAllDeathLoot((LivingEntity) (Object) this, level, source);
	}

	@ModifyArg(method = "dropExperience",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"),
			index = 2)
	private int escalation$scaleEliteExperience(final int amount) {
		return EliteLoot.scaleExperience((LivingEntity) (Object) this, amount);
	}
}
