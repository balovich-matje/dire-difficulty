package com.diredifficulty.affix;

import java.util.ArrayList;
import java.util.List;

import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteRules;
import com.diredifficulty.elite.Rarity;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * The roll — {@code docs/DESIGN.md} §4.1's algorithm, §4.2's class caps and §4.4 B1's
 * effective-HP audit, in that order.
 *
 * <p>This is the file that makes "no combination can be unkillable or one-shot you"
 * <b>structural rather than a matter of reviewing the table</b>. Nothing downstream of here
 * re-checks a cap, and nothing upstream can bypass one.
 *
 * <p><b>Order is the design, not an implementation detail.</b> The class caps and the
 * exclusions are checked BEFORE each pick, because both are properties of the set-so-far and
 * a set that never becomes illegal never has to be repaired. The EHP audit runs AFTER the
 * whole set is drawn, because it is a property of the finished set — a two-affix combination
 * that is fine on its own can exceed the index once a third lands, and auditing per pick
 * would reject sets that would have been legal.
 *
 * <p><b>An elite may legitimately end up with fewer affixes than its slot count.</b> That is
 * the system working: the pool is filtered by eligibility, then by cap, then by exclusion,
 * and a Legendary rolling on a creeper simply has fewer legal things to draw. Retrying until
 * the slots fill would defeat every one of those filters.
 */
public final class AffixBudget {
	private AffixBudget() {
	}

	/**
	 * Draws a legal affix set for this mob at this rarity.
	 *
	 * @param random the level's own {@code RandomSource}, so a roll is reproducible with the
	 *               world seed rather than with a private generator nothing can inspect
	 * @return the drawn affixes, possibly empty, never containing an illegal combination
	 */
	public static List<Affix> roll(final Mob mob, final Rarity rarity, final EliteRules rules,
			final RandomSource random) {
		int slots = rarity.slots(rules);
		int budget = rarity.budget(rules);

		List<Affix> pool = eligiblePool(mob, rules);
		shuffle(pool, random);

		List<Affix> picked = new ArrayList<>(slots);
		int spent = 0;

		for (final Affix candidate : pool) {
			if (picked.size() >= slots) {
				break;
			}

			if (candidate.cost() > budget - spent) {
				continue;
			}

			if (violatesClassCap(picked, candidate, budget)) {
				continue;
			}

			if (isExcluded(picked, candidate)) {
				continue;
			}

			picked.add(candidate);
			spent += candidate.cost();
		}

		audit(picked, rarity);
		return picked;
	}

	/** Eligible for this mob AND permitted by the preset's allowed-class set (§8.1). */
	private static List<Affix> eligiblePool(final Mob mob, final EliteRules rules) {
		List<Affix> pool = new ArrayList<>();

		for (final Affix affix : Affixes.all()) {
			if (rules.allows(affix.affixClass()) && affix.eligible(mob)) {
				pool.add(affix);
			}
		}

		return pool;
	}

	/**
	 * Fisher–Yates against the level's {@code RandomSource}.
	 *
	 * <p>Hand-rolled rather than {@code Collections.shuffle}, which wants a
	 * {@code java.util.Random} — wrapping the level's source in one to satisfy that signature
	 * would mean the roll no longer draws from the world's own generator, which is the whole
	 * reason the source is threaded down here.
	 */
	private static void shuffle(final List<Affix> pool, final RandomSource random) {
		for (int i = pool.size() - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);
			Affix swap = pool.get(i);
			pool.set(i, pool.get(j));
			pool.set(j, swap);
		}
	}

	/**
	 * §4.2. C1 and C2 cap OFFENSE and DEFENSE points at {@code ceil(B x 0.5)} each; C1
	 * additionally caps DEFENSE at two affixes at every rarity. C3–C7 cap AURA, DEATH,
	 * MOBILITY, SUPPORT and CONTROL at one apiece.
	 *
	 * <p>C1 is the one that matters most, and it is the reason the count cap is separate from
	 * the point cap: at Legendary the budget is nine, so half of it is five points, which
	 * would otherwise buy three cheap defensive affixes. Two is the cap, and it holds at every
	 * rarity regardless of what a preset does to the budget.
	 */
	private static boolean violatesClassCap(final List<Affix> picked, final Affix candidate,
			final int budget) {
		AffixClass affixClass = candidate.affixClass();
		int count = 0;
		int points = 0;

		for (final Affix affix : picked) {
			if (affix.affixClass() == affixClass) {
				count++;
				points += affix.cost();
			}
		}

		int countCap = affixClass.countCap();

		if (countCap > 0 && count + 1 > countCap) {
			return true;
		}

		if (affixClass == AffixClass.DEFENSE && count + 1 > Tuning.MAX_DEFENSE_AFFIXES) {
			return true;
		}

		return affixClass.pointCapped() && points + candidate.cost() > Tuning.classPointCap(budget);
	}

	private static boolean isExcluded(final List<Affix> picked, final Affix candidate) {
		for (final Affix affix : picked) {
			if (Affixes.excluded(affix, candidate)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * §4.4 B1 — Guarantee B at roll time.
	 *
	 * <p>Computes an effective-HP index against the base mob: the rarity's health multiplier
	 * times every drawn affix's own contribution. Over {@code Tuning.MAX_EHP_INDEX}, the
	 * highest-cost DEFENSE affix is dropped and the audit re-runs.
	 *
	 * <p><b>The published tables already pass; this is what keeps them passing after the first
	 * retune.</b> Worked example, which is also the case that makes it bite: a Legendary has
	 * budget 9, so DEFENSE may spend 5 — enough for {@code regenerating} (3) plus
	 * {@code bulwark} (2). That set indexes at 3.40 x 1.45 x 1.35 = 6.65, over the 6.0
	 * ceiling, so {@code regenerating} is dropped as the highest-cost defensive affix and the
	 * set settles at 4.59. Nothing in §4.2 or §4.3 would have caught it.
	 *
	 * <p>Dropping by cost rather than by contribution is deliberate: cost is what the player
	 * cannot see and contribution is what they can, so removing the expensive one preserves
	 * the most visible mechanics per point of budget spent. Ties break on the larger
	 * contribution, which is the direction that converges fastest.
	 */
	private static void audit(final List<Affix> picked, final Rarity rarity) {
		while (index(picked, rarity) > Tuning.MAX_EHP_INDEX) {
			Affix worst = null;

			for (final Affix affix : picked) {
				if (affix.affixClass() != AffixClass.DEFENSE) {
					continue;
				}

				if (worst == null || affix.cost() > worst.cost()
						|| (affix.cost() == worst.cost() && affix.ehpFactor() > worst.ehpFactor())) {
					worst = affix;
				}
			}

			if (worst == null) {
				// Over the index with nothing defensive left to drop. Unreachable with the
				// published tables — every factor above 1.0 belongs to a DEFENSE affix — but
				// the loop must terminate on a future catalogue where that stops being true,
				// and terminating with a slightly-hot elite beats hanging the spawn thread.
				return;
			}

			picked.remove(worst);
		}
	}

	/** {@code hpMultiplier x armourFactor x projectileFactor x sustainFactor}, as one product. */
	private static double index(final List<Affix> picked, final Rarity rarity) {
		double value = rarity.maxHealthMultiplier();

		for (final Affix affix : picked) {
			value *= affix.ehpFactor();
		}

		return value;
	}
}
