package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;
import com.escalation.elite.EliteMob;
import com.escalation.elite.EliteTimers;
import com.escalation.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Witch;

/**
 * §3.1 #22 — Volley. Ranged attacks fire 3 projectiles; a witch throws 2 potions instead.
 *
 * <p><b>Delivered as a periodic burst rather than as a per-shot multiplier, and the
 * difference is the design's own point.</b> The row asks for three projectiles per attack
 * with the attack cooldown multiplied by 1.5, "so sustained DPS rises much less than burst
 * does". A ranged mob's cadence lives inside its own attack goal — it is not an attribute and
 * there is no hook that both counts its shots and can slow them. So the same shape is bought
 * the other way round: the mob keeps its ordinary rate untouched, and the extra projectiles
 * arrive on their own interval. Burst goes up sharply, sustained damage barely moves. The
 * property the design is paying three points for is preserved; the mechanism differs, and it
 * differs in a direction that needs no mixin on any of the seven port targets.
 *
 * <p>Each extra shot goes through the mob's OWN {@code performRangedAttack}, so a skeleton
 * fires its own arrow with its own difficulty-scaled inaccuracy, a stray fires a cold arrow,
 * a pillager fires a bolt and a witch throws its own potion — including every modded ranged
 * mob, for free. That inaccuracy is also what supplies the spread; a hand-rolled one would
 * have to re-derive per-mob ballistics that vanilla already owns.
 *
 * <p>Line of sight is required, so this cannot fire blind through terrain. §4.3's X15 bars it
 * with {@code relentless} for exactly that reason — wall-piercing aggro on a triple-shot
 * archer means being shot before the mob is ever visible — and X11 bars it with
 * {@code blinkstrike}, which would leave no approach window at all.
 */
public final class VolleyAffix implements Affix {
	@Override
	public String id() {
		return "volley";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.OFFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_VOLLEY;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.ranged(mob);
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		if (!(mob instanceof RangedAttackMob shooter)) {
			return;
		}

		long now = level.getGameTime();
		EliteTimers timers = EliteMob.timers(mob);

		if (now < timers.volleyReadyAt()) {
			return;
		}

		LivingEntity target = mob.getTarget();

		if (target == null || !target.isAlive() || !mob.hasLineOfSight(target)) {
			return;
		}

		int shots = mob instanceof Witch ? Tuning.VOLLEY_PROJECTILES_POTION : Tuning.VOLLEY_PROJECTILES;

		for (int i = 0; i < shots; i++) {
			// The power argument is vanilla's own difficulty ramp, 0..1. Full power: the
			// affix is the volume of fire, not a hidden damage bonus — §3.2 rules out any
			// raw outgoing multiplier beyond the rarity column.
			shooter.performRangedAttack(target, 1.0F);
		}

		EliteMob.setTimers(mob, timers.withVolleyReadyAt(now + Tuning.VOLLEY_INTERVAL_TICKS));
	}
}
