package com.diredifficulty.elite;

import com.diredifficulty.Tuning;

/**
 * The four rarity tiers — {@code docs/DESIGN.md} §2.1.
 *
 * <p>Rarity is rolled FIRST and fixes everything else: stat multipliers, affix slot count,
 * affix point budget, glow colour and loot tier. "More simultaneous affixes with progression"
 * is delivered through this one ladder rather than through a second independent dial, so the
 * two can never disagree.
 *
 * <p><b>This enum deliberately carries no number.</b> All eight columns live in
 * {@link Tuning}, indexed by {@link #ordinal()}; the accessors below are the only readers.
 * That is what makes the house rule literally true — a rebalance is an edit to one file, and
 * a one-character edit there moves {@code Tuning.class} and nothing else.
 *
 * <p><b>Frozen order.</b> The ordinal is persisted in {@code EliteData} and crosses the wire
 * to the client for the glow and the nameplate. Reordering these would re-colour every elite
 * already in a world; adding a fifth goes at the end, and every array in {@link Tuning} grows
 * with it.
 */
public enum Rarity {
	UNCOMMON,
	RARE,
	EPIC,
	LEGENDARY;

	private static final Rarity[] VALUES = values();

	/**
	 * 1..4, matching the preset knobs' {@code maxRarityTier} and {@code bonusFromTier} and
	 * the loot table's T1..T4. One-based because the design's tables are, and because a
	 * zero-based "tier 0" in a config file reads as "off" rather than as "Uncommon".
	 */
	public int tier() {
		return ordinal() + 1;
	}

	/** Glow and name-tag colour, RGB. */
	public int colour() {
		return Tuning.RARITY_COLOUR[ordinal()];
	}

	/** Block light the elite itself is drawn with. */
	public int auraMobLight() {
		return Tuning.AURA_MOB_LIGHT[ordinal()];
	}

	/** Centre alpha 0..255 of the aura disc, before the pulse. */
	public int auraAlpha() {
		return Tuning.AURA_ALPHA[ordinal()];
	}

	/** Aura disc radius multiplier. */
	public double auraRadiusScale() {
		return Tuning.AURA_RADIUS_SCALE[ordinal()];
	}

	/** Ground light pool radius, blocks. */
	public double auraPoolRadius() {
		return Tuning.AURA_POOL_RADIUS[ordinal()];
	}

	/** Centre alpha 0..255 of the ground pool, before the pulse. */
	public int auraPoolAlpha() {
		return Tuning.AURA_POOL_ALPHA[ordinal()];
	}

	/** {@code MAX_HEALTH} multiplier. */
	public double maxHealthMultiplier() {
		return Tuning.RARITY_MAX_HEALTH[ordinal()];
	}

	/** {@code ATTACK_DAMAGE} multiplier — the only flat outgoing multiplier in the mod. */
	public double damageMultiplier() {
		return Tuning.RARITY_DAMAGE[ordinal()];
	}

	/** {@code SCALE} multiplier: model and hitbox together, through vanilla attribute sync. */
	public double scaleMultiplier() {
		return Tuning.RARITY_SCALE[ordinal()];
	}

	/** Dropped-XP multiplier. */
	public float xpMultiplier() {
		return Tuning.eliteXpMultiplier(ordinal());
	}

	/**
	 * Affix slots after the active preset's bonus. §8.3 grants +1 at Epic and above; §8.4
	 * grants +1 at every tier.
	 */
	public int slots(final EliteRules rules) {
		return Tuning.RARITY_SLOTS[ordinal()] + (bonusApplies(rules) ? rules.bonusSlots() : 0);
	}

	/** Affix point budget after the active preset's bonus. Same gate as {@link #slots}. */
	public int budget(final EliteRules rules) {
		return Tuning.RARITY_BUDGET[ordinal()] + (bonusApplies(rules) ? rules.bonusBudget() : 0);
	}

	private boolean bonusApplies(final EliteRules rules) {
		return tier() >= rules.bonusFromTier();
	}

	/** {@code null} for an out-of-range ordinal — a stored value from a future version. */
	public static Rarity byOrdinal(final int ordinal) {
		return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : null;
	}

	/** The whole ladder, without the defensive copy {@code values()} makes on every call. */
	public static Rarity[] all() {
		return VALUES.clone();
	}

	/** How many tiers there are, for weight loops that must not allocate. */
	public static int count() {
		return VALUES.length;
	}

	/** The tier at index {@code i}, 0-based. Callers loop {@code i < count()}. */
	public static Rarity at(final int index) {
		return VALUES[index];
	}
}
