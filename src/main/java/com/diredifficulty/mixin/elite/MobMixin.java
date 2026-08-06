package com.diredifficulty.mixin.elite;

import com.diredifficulty.elite.EliteRoller;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spawn interception — {@code docs/DESIGN.md} §2.3 and the {@code MobMixin} row of §6.4.
 *
 * <p>{@code Mob.finalizeSpawn} at TAIL is the chosen hook, and the reasons are measurable
 * rather than assumed. The natural spawner calls it with {@code NATURAL} and chunk generation
 * with {@code CHUNK_GENERATION}; the spawn reason is <b>an argument in hand</b>, so the roll
 * can be restricted to exactly those two out of nineteen — a mob farm must not print elites
 * and a re-loaded chunk must not re-roll one; and it runs after vanilla's own difficulty
 * finalisation, so elite modifiers layer on top rather than being overwritten.
 *
 * <p><b>One ordering fact worth stating precisely, because the design doc states it loosely.</b>
 * This is the TAIL of {@code Mob}'s OWN body — the follow-range spawn bonus and the
 * left-handed roll. Subclasses that override {@code finalizeSpawn} call {@code super} first and
 * then do their own work, so a zombie's equipment, its baby roll and its reinforcement chance
 * all land <i>after</i> the promotion, not before. Nothing in the promotion collides with any
 * of them: the attribute modifiers are id-keyed under this mod's namespace, the name tag wraps
 * whatever name is there at the time, and health is topped up from the multiplied maximum. It
 * is still the kind of interaction an in-game pass should confirm rather than a reader assume,
 * and the case to look at is a baby zombie, whose size and speed are settled by the subclass.
 *
 * <p>The handler body is one delegation. Everything it delegates to is shared code with no
 * mixin, loader or version token in it — the §5a convention, "mixin annotations fork, balance
 * logic never", which is what lets the same promotion run on all seven port targets from one
 * implementation.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Inject(method = "finalizeSpawn", at = @At("TAIL"))
	private void direDifficulty$rollElite(final ServerLevelAccessor level, final DifficultyInstance difficulty,
			final EntitySpawnReason spawnReason, final SpawnGroupData groupData,
			final CallbackInfoReturnable<SpawnGroupData> cir) {
		EliteRoller.onFinalizeSpawn((Mob) (Object) this, level, spawnReason);
	}
}
