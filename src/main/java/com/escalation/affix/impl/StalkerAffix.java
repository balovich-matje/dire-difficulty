package com.escalation.affix.impl;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.affix.Eligibility;
import com.escalation.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * §3.1 #16 — Stalker. A landed hit marks the player for 10s: the elite and its allies know
 * the player's position through walls. <b>The player gets a tell, so the effect is legible
 * and never invisible.</b>
 *
 * <p><b>The tell is the vanilla Glowing outline, on the elite, and that is a deliberate
 * substitution.</b> Design §3.1 asks for a HUD element and §9 lists "any HUD element beyond
 * the stalker tell" as out of scope, which leaves this the mod's only piece of screen
 * furniture — a whole client-side surface, a payload to feed it, and a place in the
 * {@code HUD_SHIFT} contract with the two sibling mods, all for one affix. Glowing costs
 * none of that and is strictly more informative: it is drawn through walls, which tells the
 * player not only <i>that</i> they are marked but <i>by what</i> and <i>from where</i>.
 * §2.2's requirement that the RARITY glow be occluded is a different glow with a different
 * purpose and is unaffected.
 *
 * <p>It also doubles as the mark's own timer, which is why this affix stores no state: the
 * effect's remaining duration IS the remaining mark, so a reload, a chunk unload and a
 * despawn all expire it correctly with nothing to persist and nothing to clean up. The
 * ticker re-asserts the target while the effect is on, which is the "through walls" half.
 *
 * <p>§4.3's X12 bars this with {@code escapist}: something that always knows where you are
 * and can leave when it is hurt makes a fight that neither party can end.
 */
public final class StalkerAffix implements Affix {
	@Override
	public String id() {
		return "stalker";
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.UTILITY;
	}

	@Override
	public int cost() {
		return Tuning.COST_STALKER;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return Eligibility.attacker(mob);
	}

	@Override
	public void onHitLanded(final ServerLevel level, final Mob mob, final LivingEntity victim,
			final DamageSource source, final float dealt) {
		mob.setTarget(victim);
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, Tuning.STALKER_MARK_TICKS, 0, false, false, false));
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		if (!mob.hasEffect(MobEffects.GLOWING)) {
			return;
		}

		LivingEntity target = mob.getTarget();

		// Re-asserting is what defeats the target goal's line-of-sight continue-check, which
		// would otherwise drop the mark the moment the player stepped behind cover.
		if (target != null && target.isAlive()) {
			mob.setTarget(target);
		}
	}
}
