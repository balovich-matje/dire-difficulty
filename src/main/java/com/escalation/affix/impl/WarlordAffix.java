package com.escalation.affix.impl;

import java.util.List;

import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixClass;
import com.escalation.elite.EliteAttributes;
import com.escalation.elite.EliteMob;
import com.escalation.elite.Rarity;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;

/**
 * §3.1 #3 — Warlord. Non-elite hostiles within 8 blocks get +20% damage and +15% speed while
 * the Warlord lives; the buff drops the tick it dies.
 *
 * <p><b>"Drops the tick it dies" is the hard part, and it is why {@link #upkeep} exists.</b>
 * An attribute modifier has no duration — nothing in the engine will ever take it back off.
 * A dead Warlord runs no ticks, so it cannot clean up after itself either, and a buff granted
 * by something that no longer exists is exactly the kind of permanent invisible power creep
 * that turns into "why are the zombies near spawn faster". So the release is not the
 * Warlord's job: the ticker asserts the buff from every living Warlord and then sweeps every
 * buffed mob it can see, releasing any that no Warlord is currently vouching for. One rule,
 * evaluated from the surviving side, so death, distance and chunk unload are all the same
 * case and none of them needs its own event.
 *
 * <p>The modifiers are TRANSIENT for the same reason — vanilla does not serialise those, so
 * even the pathological case (server killed mid-pass) cannot write one to disk.
 */
public final class WarlordAffix implements Affix {
	private static final Identifier DAMAGE_MODIFIER = EliteAttributes.id("warlord_damage");

	private static final Identifier SPEED_MODIFIER = EliteAttributes.id("warlord_speed");

	/** The registry id, needed by the ticker's sweep as well as by the registry. */
	public static final String ID = "warlord";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public AffixClass affixClass() {
		return AffixClass.SUPPORT;
	}

	@Override
	public int cost() {
		return Tuning.COST_WARLORD;
	}

	@Override
	public boolean eligible(final Mob mob) {
		return true;
	}

	@Override
	public void onTick(final ServerLevel level, final Mob mob, final Rarity rarity, final int interval) {
		List<Mob> nearby = level.getEntitiesOfClass(Mob.class,
				mob.getBoundingBox().inflate(Tuning.WARLORD_RADIUS),
				candidate -> isBuffable(candidate) && candidate != mob);

		for (final Mob ally : nearby) {
			EliteAttributes.setTransient(ally, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER,
					Tuning.WARLORD_DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			EliteAttributes.setTransient(ally, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER,
					Tuning.WARLORD_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		}
	}

	/**
	 * Releases every mob in {@code candidates} that no living Warlord in {@code warlords} is
	 * currently within range of. Called once per ticker pass, after every Warlord has
	 * asserted; see the class javadoc for why the release is evaluated from this side.
	 *
	 * @param candidates every mob the pass looked at — a superset of the buffed ones
	 * @param warlords   the living Warlord elites the same pass found
	 */
	public static void upkeep(final List<Mob> candidates, final List<Mob> warlords) {
		double radiusSq = Tuning.WARLORD_RADIUS * Tuning.WARLORD_RADIUS;

		for (final Mob candidate : candidates) {
			if (!EliteAttributes.has(candidate, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER)) {
				continue;
			}

			boolean vouched = false;

			for (final Mob warlord : warlords) {
				if (warlord.isAlive() && warlord != candidate
						&& warlord.distanceToSqr(candidate) <= radiusSq) {
					vouched = true;
					break;
				}
			}

			if (!vouched) {
				EliteAttributes.clear(candidate, Attributes.ATTACK_DAMAGE, DAMAGE_MODIFIER);
				EliteAttributes.clear(candidate, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER);
			}
		}
	}

	/** Hostile, alive, and not itself an elite — an elite is already carrying its own ladder. */
	private static boolean isBuffable(final Mob mob) {
		return mob instanceof Enemy && mob.isAlive() && !EliteMob.isElite(mob);
	}
}
