package com.escalation.mixin.elite;

import com.escalation.elite.EliteCombat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The damage funnel — {@code docs/DESIGN.md} §6.4's {@code LivingEntityMixin} row, carrying
 * <b>Guarantee A</b>.
 *
 * <p><b>Priority 1500, and it is a measurement rather than a preference.</b> The ceiling has to
 * run after Skill Proficiencies' damage multipliers and after Archetypes' shapers: a stacked
 * multiplier that lands after the clamp makes the guarantee false while every build still
 * compiles and every log stays clean. Archetypes hit the identical problem with
 * {@code FlenseMixin}, settled on this same priority, and its architecture doc is explicit that
 * the resulting order was <b>verified by mixin bytecode export, not by reading the priority
 * documentation</b>. The same verification is owed here before anything ships: all three mods
 * installed, {@code -Dmixin.debug.export=true} (already on in this repo's run configs), and the
 * handler order read off the transformed {@code LivingEntity}.
 *
 * <p>One handler, not two. The shared code behind it decides whether this entity is an elite's
 * victim, an elite itself, or — overwhelmingly the common case — neither.
 *
 * <p>Everything that happens <i>after</i> a hit lands is deliberately NOT here: it rides
 * fabric-api's {@code AFTER_DAMAGE}, which reports the damage actually taken after armour,
 * resistance and blocking. See {@code EliteCombat}.
 */
@Mixin(value = LivingEntity.class, priority = 1500)
public abstract class LivingEntityMixin {
	/**
	 * {@code argsOnly = true} with arg capture, which is the combination that works: the same
	 * annotation against a LOCAL fails with "Scanned 0 target(s)" when args are captured — a
	 * finding already recorded in Skill Proficiencies' mixin notes and re-used rather than
	 * re-derived.
	 *
	 * <p>Capture requires the handler's trailing parameters to match the target's descriptor
	 * exactly, {@code hurtServer(ServerLevel, DamageSource, float)}, which is why the damage
	 * appears twice: {@code damage} is the variable being modified and {@code originalDamage}
	 * is the same slot arriving again as part of the captured argument list. Only the first is
	 * read. With one {@code float} among the args and a {@code float} return, the target slot
	 * is unambiguous and needs no ordinal.
	 */
	@ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
	private float escalation$shapeEliteDamage(final float damage, final ServerLevel level,
			final DamageSource source, final float originalDamage) {
		return EliteCombat.modifyDamage((LivingEntity) (Object) this, level, source, damage);
	}
}
