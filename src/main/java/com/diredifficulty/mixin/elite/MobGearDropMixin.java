package com.diredifficulty.mixin.elite;

import com.diredifficulty.elite.EliteGear;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The gear-drop toggles — the server-side hook for both of them, and it is one line.
 *
 * <h2>Why this method and not another</h2>
 * {@code Mob.dropCustomDeathLoot(ServerLevel, DamageSource, boolean)} is where worn equipment
 * drops in 26.2, verified by reading it: the whole loop over {@code EquipmentSlot.VALUES}, the
 * {@code dropChance != 0.0F} gate, the {@code killedByPlayer || preserve} gate, the
 * {@code PREVENT_EQUIPMENT_DROP} check, the looting adjustment through
 * {@code EnchantmentHelper.processEquipmentDropChance} and the {@code setDamageValue} line that
 * damages the stack all live inside it. Nothing above or below it touches equipment.
 *
 * <h2>Why HEAD and not cancellable</h2>
 * The handler ZEROES drop chances rather than cancelling the loop, so every one of those vanilla
 * behaviours stays exactly as vanilla wrote it and the toggles can only ever change the number
 * they name. A cancel would also swallow {@code super.dropCustomDeathLoot}, which is
 * {@code LivingEntity}'s and is not this feature's business.
 *
 * <p>This is the whole of both config toggles' enforcement and of the netherite-armour rule's
 * re-assertion; the decision itself is in {@code EliteGear.beforeEquipmentDrops}, which is
 * shared code with no mixin, loader or version token in it — the §5a convention, "mixin
 * annotations fork, balance logic never".
 *
 * <p>Separate from {@code LivingEntityLootMixin} because that one targets
 * {@code LivingEntity} and this one targets {@code Mob}: {@code dropCustomDeathLoot} is
 * declared on both, and the equipment loop is only in the {@code Mob} override. A subclass that
 * overrides it and calls {@code super} still routes through here.
 */
@Mixin(Mob.class)
public abstract class MobGearDropMixin {
	@Inject(method = "dropCustomDeathLoot", at = @At("HEAD"))
	private void direDifficulty$gearDropRules(final ServerLevel level, final DamageSource source,
			final boolean killedByPlayer, final CallbackInfo ci) {
		EliteGear.beforeEquipmentDrops((Mob) (Object) this);
	}
}
