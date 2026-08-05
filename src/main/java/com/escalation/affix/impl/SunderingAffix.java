package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;
import com.escalation.elite.EliteAttributes;
import com.escalation.elite.EliteMob;
import com.escalation.elite.EliteTimers;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * §3.1 #21 — Sundering. Hits apply a stacking armour shred: −2 armour per stack, max 4
 * stacks, 8s, refreshed on hit.
 *
 * <p><b>Attribute modifiers have no duration, so the expiry is somebody's job.</b> The stack
 * count and its deadline live in {@link EliteTimers} <i>on the victim</i> — that record is a
 * transient per-entity attachment, and the natural owner of "how shredded is this player and
 * until when" is the player, not whichever elite last hit them. The elite ticker sweeps
 * expired stacks off the players it is already iterating for aura purposes, so the release
 * costs no extra scan; see {@link #expire}.
 *
 * <p>Transient on both counts: the modifier is a transient attribute modifier and the
 * bookkeeping is a transient attachment, so nothing here can be written to disk. A shred that
 * survived a logout would be a permanent, invisible, unexplainable armour loss.
 *
 * <p>The whole stack lapses together rather than decaying one stack at a time. Four
 * independent deadlines is four times the state for a difference no player can perceive at
 * eight seconds, and the single deadline is refreshed on every hit — so staying in melee
 * keeps the shred and disengaging drops all of it, which is the counterplay the affix is
 * offering.
 */
public final class SunderingAffix implements Affix {
	private static final Identifier MODIFIER = EliteAttributes.id("affix_sundering");

	@Override
	public String id() {
		return "sundering";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.OFFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_SUNDERING;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.melee(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		long now = level.getGameTime();
		EliteTimers timers = EliteMob.timers(victim);
		int stacks = timers.sunderExpiresAt() > now ? timers.sunderStacks() : 0;
		stacks = Math.min(stacks + 1, Tuning.SUNDERING_MAX_STACKS);

		EliteMob.setTimers(victim, timers.withSunder(stacks, now + Tuning.SUNDERING_DURATION_TICKS));
		EliteAttributes.setTransient(victim, Attributes.ARMOR, MODIFIER,
				Tuning.SUNDERING_ARMOR_PER_STACK * stacks, AttributeModifier.Operation.ADD_VALUE);
	}

	/**
	 * Drops the shred once its deadline has passed. Called from the elite ticker for every
	 * player it looks at; safe and cheap for a player who was never sundered, which is almost
	 * all of them.
	 */
	public static void expire(final LivingEntity victim, final long now) {
		EliteTimers timers = EliteMob.timers(victim);

		if (timers.sunderStacks() == 0 || now < timers.sunderExpiresAt()) {
			return;
		}

		EliteAttributes.clear(victim, Attributes.ARMOR, MODIFIER);
		EliteMob.setTimers(victim, timers.withSunder(0, 0L));
	}
}
