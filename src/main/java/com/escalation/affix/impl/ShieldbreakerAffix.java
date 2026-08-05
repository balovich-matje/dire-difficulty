package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;

/**
 * §3.1 #7 — Shieldbreaker. A landed melee hit disables the victim's shield for 3s,
 * Warden-style.
 *
 * <p><b>There is no {@code Player.disableShield} on 26.2.</b> A tree-wide grep across all
 * 4849 common sources returns nothing for that name. Shield blocking is the
 * {@code BlocksAttacks} data component, the disable is that component's own public
 * {@code disable(ServerLevel, LivingEntity, float, ItemStack)}, and the stack currently
 * blocking is found through {@code LivingEntity.getItemBlockingWith()}. All three verified in
 * the decompiled 26.2 sources; the 1.20.1 equivalent is a different method entirely and is a
 * boundary for the port to measure, not to remember.
 *
 * <p>Reading {@code getItemBlockingWith} rather than testing for a shield item is what makes
 * this work against any modded blocking item, and what makes it correctly do nothing when the
 * victim was not actually blocking at the moment of the hit.
 *
 * <p>Fires from {@code onHitLanded}, so a hit the funnel rejected disables nothing. §4.3's X8
 * bars this from ever pairing with {@code quaking}: a shockwave that strips the shield and is
 * also the damage removes the block channel entirely rather than punishing it.
 */
public final class ShieldbreakerAffix implements Affix {
	@Override
	public String id() {
		return "shieldbreaker";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.OFFENSE;
	}

	@Override
	public int cost() {
		return Tuning.COST_SHIELDBREAKER;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.melee(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		ItemStack blocking = victim.getItemBlockingWith();

		if (blocking == null || blocking.isEmpty()) {
			return;
		}

		BlocksAttacks blocksAttacks = blocking.get(DataComponents.BLOCKS_ATTACKS);

		if (blocksAttacks != null) {
			blocksAttacks.disable(level, victim, Tuning.SHIELDBREAKER_DISABLE_SECONDS, blocking);
		}
	}
}
