package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #20 — Noxious. On death leaves a lingering poison cloud, 4-block radius, 8s.
 *
 * <p>Vanilla's own {@code AreaEffectCloud} carries the whole mechanic: the reapplication
 * delay, the particle colour derived from the effect, the entity filtering and the client
 * rendering are all already there. {@code addEffect} routes through {@code setPotionContents},
 * which recomputes the particle colour, so the cloud is the right green without this mod
 * naming a particle at all.
 *
 * <p>The wait time is zeroed and the radius held constant, which is the difference between
 * this and a thrown lingering potion: a death cloud that spent a second inert or shrank away
 * would not be an area-denial punctuation on the kill, it would be scenery.
 *
 * <p>Poison, not Wither — poison cannot kill. §3.2's line is that nothing in the catalogue is
 * spiteful, and a death effect that finishes off a player who already won the fight is the
 * definition of it.
 */
public final class NoxiousAffix implements Affix {
	@Override
	public String id() {
		return "noxious";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEATH;
	}

	@Override
	public int cost() {
		return Tuning.COST_NOXIOUS;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onDeath(final ServerLevel level, final Mob mob, final Rarity rarity,
			final DamageSource source) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, mob.getX(), mob.getY(), mob.getZ());
		cloud.setOwner(mob);
		cloud.setRadius(Tuning.NOXIOUS_RADIUS);
		cloud.setDuration(Tuning.NOXIOUS_DURATION_TICKS);
		cloud.setWaitTime(0);
		cloud.setRadiusOnUse(0.0F);
		cloud.setRadiusPerTick(0.0F);
		cloud.addEffect(new MobEffectInstance(MobEffects.POISON, Tuning.NOXIOUS_POISON_TICKS,
				Tuning.NOXIOUS_POISON_AMPLIFIER));
		level.addFreshEntity(cloud);
	}
}
