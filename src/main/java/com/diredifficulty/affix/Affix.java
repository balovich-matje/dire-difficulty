package com.diredifficulty.affix;

import com.diredifficulty.elite.Rarity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * One affix — {@code docs/DESIGN.md} §3.1's row, as an object.
 *
 * <p>Every method below {@link #eligible} is a hook with a do-nothing default, so an affix
 * class in {@code affix/impl} is exactly the mechanic it implements and nothing else. The
 * twenty-five of them average under forty lines apiece for that reason.
 *
 * <p><b>Where the hooks are driven from, and why it matters for the port.</b> None of these
 * methods knows what a mixin is:
 *
 * <ul>
 * <li>{@link #onApply} runs once, at the tail of the promotion, from {@code EliteRoller}.</li>
 * <li>{@link #onTick} runs from {@code EliteTicker}'s single {@code END_SERVER_TICK}
 *     listener, every {@code Tuning.ELITE_TICK_INTERVAL} ticks and only for elites near a
 *     player. An affix that wants a longer interval counts ticks itself against the elite's
 *     timers — there is one cadence, not twenty-five.</li>
 * <li>{@link #modifyOutgoing} and {@link #modifyIncoming} run inside the damage funnel, from
 *     {@code EliteCombat}. They are the only two hooks that touch a damage number, and
 *     Guarantee A's ceiling is applied after every one of them by code no affix can reach.</li>
 * <li>{@link #onHitLanded} runs at the RETURN of the same funnel, so it fires only for a hit
 *     that actually landed — a blocked or cancelled hit applies no debuff.</li>
 * <li>{@link #onDeath} runs from {@code ServerLivingEntityEvents.AFTER_DEATH}.</li>
 * </ul>
 *
 * <p>That indirection is the §5a rule of the multiversion conventions applied one level up:
 * the loader-specific and version-specific work lives in the four call sites, and the
 * balance logic — all twenty-five implementations — is written once for every node.
 *
 * <p><b>Two invariants an implementation must not break.</b> First, no affix may add a raw
 * outgoing damage multiplier beyond the rarity column (§3.2): burst is shaped or conditional,
 * never a flat number, because that is what keeps Guarantee A's surface small. Second, no
 * affix may damage terrain, steal or damage inventory, or disable player movement outright —
 * that is the line between "hard" and "spiteful".
 */
public interface Affix {
	/**
	 * The registry id. <b>Frozen:</b> this string is what gets persisted on the entity, so
	 * renaming one orphans that affix on every elite already in a world. Ordinals are never
	 * used for the same reason — a registry grows and reorders, a string key does not.
	 */
	String id();

	/**
	 * The lang key for this affix's display name — {@code affix.dire_difficulty.<id>}.
	 *
	 * <p>Derived from {@link #id()} rather than declared per implementation, so a key and
	 * an id cannot drift apart and adding an affix cannot forget one. The client's elite
	 * plate is the only reader today; the key exists in {@code src/main} because the id it
	 * is built from does, and because a server-side report will want the same string.
	 *
	 * <p>The id is frozen (see {@link #id()}), so this key is frozen with it: renaming one
	 * would orphan the translation as surely as it orphans the stored affix.
	 */
	default String nameKey() {
		return "affix.dire_difficulty." + id();
	}

	/** The class §4.2's caps are expressed in. */
	AffixClass affixClass();

	/** Point cost against the rarity budget, 1–3. Read from {@code Tuning}, never a literal. */
	int cost();

	/**
	 * Roll-time eligibility, evaluated against the concrete mob.
	 *
	 * <p>An affix that could not function is never drawn, rather than being drawn and doing
	 * nothing: a Volley on a zombie would consume three of a Legendary's nine points and one
	 * of its four slots to produce no behaviour at all. See {@link Eligibility} for the
	 * shared predicates.
	 */
	boolean eligible(Mob mob);

	/**
	 * This affix's contribution to Guarantee B1's effective-HP index, as a multiplier on the
	 * base mob. {@code 1.0} — no contribution — for everything that is not defensive.
	 *
	 * <p>Design §4.4 B1 names four factors (hp, armour, projectile, sustain). They collapse
	 * to one product here because each affix contributes to exactly one of them; the audit's
	 * arithmetic is unchanged and there is one number per affix to retune instead of four
	 * columns to keep aligned.
	 */
	default double ehpFactor() {
		return 1.0;
	}

	/**
	 * Applied once when the elite is promoted, and re-applied on nothing else. Attribute
	 * modifiers added here are PERMANENT modifiers keyed by a stable id, so re-running the
	 * promotion on a reloaded entity replaces rather than stacks them.
	 */
	default void onApply(ServerLevel level, Mob mob, Rarity rarity) {
	}

	/**
	 * Periodic behaviour. {@code elapsed} is the elite's own tick counter in game ticks, a
	 * multiple of {@code Tuning.ELITE_TICK_INTERVAL}; an affix with its own cadence gates on
	 * the timers in {@code EliteTimers} rather than on this number, so a chunk reload does
	 * not hand it a free pulse.
	 */
	default void onTick(ServerLevel level, Mob mob, Rarity rarity, int interval) {
	}

	/**
	 * Shapes damage the elite is DEALING, before the rarity multiplier and before Guarantee
	 * A's ceiling. Return the damage unchanged to opt out.
	 */
	default float modifyOutgoing(ServerLevel level, Mob mob, LivingEntity victim, DamageSource source, float damage) {
		return damage;
	}

	/** Shapes damage the elite is TAKING. Return the damage unchanged to opt out. */
	default float modifyIncoming(ServerLevel level, Mob mob, DamageSource source, float damage) {
		return damage;
	}

	/**
	 * Fires after a hit BY the elite has actually landed — the funnel returned true. Debuffs,
	 * shield disables and lifesteal go here rather than in {@link #modifyOutgoing}, so a
	 * blocked or cancelled hit applies none of them.
	 *
	 * @param dealt the damage the funnel accepted, after every modifier and the ceiling
	 */
	default void onHitLanded(ServerLevel level, Mob mob, LivingEntity victim, DamageSource source, float dealt) {
	}

	/**
	 * Fires after a hit ON the elite has landed. Reflects and the once-per-lifetime escape
	 * live here for the same reason.
	 */
	default void onDamageTaken(ServerLevel level, Mob mob, DamageSource source, float taken) {
	}

	/** Fires once, server-side, when the elite dies. */
	default void onDeath(ServerLevel level, Mob mob, Rarity rarity, DamageSource source) {
	}
}
