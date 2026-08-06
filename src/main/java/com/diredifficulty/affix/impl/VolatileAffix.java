package com.diredifficulty.affix.impl;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

/**
 * §3.1 #18 — Volatile. On death, a 2.0-power explosion.
 *
 * <p><b>{@code ExplosionInteraction.NONE}, unconditionally, with no config knob and no
 * parameter.</b> Design §3.2 and §1 both say it: no affix damages terrain. The mod's whole
 * pitch is vanilla-plus — it amplifies vanilla activity and never takes anything away — and a
 * mob that eats the floor of the base you fought it next to is the fastest way to break that.
 * Making it a knob would mean a server could turn it on, so it is not a knob; the constant is
 * written at the call site where no configuration path can reach it.
 *
 * <p>Fire is off for the same reason. The explosion still knocks back and still hurts, which
 * is the mechanic: it makes killing this elite in melee cost something, and it makes killing
 * it at range the safe play — one of the six outs, still open.
 *
 * <p>§4.3's X10 bars this with {@code splitting}: a death explosion per split copy chains
 * into an area-denial cascade rather than one punctuation mark.
 */
public final class VolatileAffix implements Affix {
	@Override
	public String id() {
		return "volatile";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.DEATH;
	}

	@Override
	public int cost() {
		return Tuning.COST_VOLATILE;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onDeath(final ServerLevel level, final Mob mob, final Rarity rarity,
			final DamageSource source) {
		level.explode(mob, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5, mob.getZ(),
				Tuning.VOLATILE_POWER, false, Level.ExplosionInteraction.NONE);
	}
}
