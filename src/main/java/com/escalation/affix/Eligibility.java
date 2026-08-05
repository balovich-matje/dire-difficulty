package com.escalation.affix;

import com.escalation.Tuning;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * The shared roll-time predicates behind {@code docs/DESIGN.md} §3.1's <i>Rolls on</i>
 * column.
 *
 * <p>They exist as one file rather than as twenty-five inline lambdas because several
 * affixes share a predicate exactly, and a predicate that drifts between two affixes is the
 * kind of bug that only shows up as "why does that never roll on skeletons" months later.
 *
 * <p>Every one is a pure function of the concrete mob and is evaluated <b>before</b> the
 * affix is drawn, which is the point: an affix that cannot function is never drawn, rather
 * than being drawn and doing nothing. On a Legendary, a Volley that no-ops would burn three
 * of nine points and one of four slots.
 */
public final class Eligibility {
	private Eligibility() {
	}

	/**
	 * "Any mob with a {@code targetSelector}" (§3.1 rows 2 and 15).
	 *
	 * <p><b>Measured, and it is why this returns a constant.</b> {@code targetSelector} is a
	 * field on {@code Mob} itself, not on a subclass — every {@code Mob} has one. The roll
	 * only ever sees {@code MobCategory.MONSTER} entities, all of which use it. The predicate
	 * is kept named rather than inlined so that the day a caller wants a real distinction
	 * there is one place to put it.
	 */
	public static boolean hasTargeting(final Mob mob) {
		return true;
	}

	/**
	 * Melee-capable: the mob has a positive {@code ATTACK_DAMAGE}.
	 *
	 * <p>Reading the attribute rather than testing for a class is what makes this work for
	 * modded mobs too, and {@code getAttribute} returning {@code null} for a type that never
	 * registered the attribute is the honest "no" — a creeper has no attack damage because
	 * it does not swing.
	 */
	public static boolean melee(final Mob mob) {
		AttributeInstance attack = mob.getAttribute(Attributes.ATTACK_DAMAGE);
		return attack != null && attack.getValue() > 0.0;
	}

	/**
	 * Ranged attacker: the skeleton family, pillagers, illusioners, witches, and any modded
	 * mob that implements the same vanilla interface.
	 */
	public static boolean ranged(final Mob mob) {
		return mob instanceof RangedAttackMob;
	}

	/** "Melee or projectile attacker" (§3.1 rows 13 and 16). */
	public static boolean attacker(final Mob mob) {
		return melee(mob) || ranged(mob);
	}

	/**
	 * "Undead only, i.e. mobs that burn in daylight" (§3.1 row 8, {@code sunwalker}).
	 *
	 * <p>The {@code minecraft:undead} entity-type tag is the right test on 26.2: the
	 * {@code MobType} enum that used to answer this is gone, and the tag is also what
	 * vanilla's own smite and daylight logic keys off, so a pack that adds an undead mob gets
	 * the affix for free.
	 */
	public static boolean undead(final Mob mob) {
		return mob.is(EntityTypeTags.UNDEAD);
	}

	/**
	 * "Small/medium mobs" (§3.1 row 19, {@code splitting}).
	 *
	 * <p>Measured on the hitbox rather than declared per type, so it holds for modded mobs.
	 * The exclusion of anything already Rooted is NOT here — that is a pair in §4.3 and
	 * belongs in the exclusion table, where it is symmetric and cannot be defeated by roll
	 * order.
	 */
	public static boolean splittable(final Mob mob) {
		return mob.getBbWidth() <= Tuning.SPLITTING_MAX_WIDTH
				&& mob.getBbHeight() <= Tuning.SPLITTING_MAX_HEIGHT;
	}
}
