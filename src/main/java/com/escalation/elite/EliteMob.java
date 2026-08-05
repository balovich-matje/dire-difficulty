package com.escalation.elite;

import java.util.List;

import com.escalation.affix.Affix;
import com.escalation.affix.Affixes;
import com.escalation.platform.EliteStore;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

/**
 * The thin face over {@link EliteStore} — every read and write of elite state in the mod
 * goes through here, and nothing else calls the seam directly.
 *
 * <p>It exists for two reasons that are worth stating rather than assuming. First, the store
 * returns {@code null} for an entity that has never had state set, which is almost every
 * entity in the world; forcing every one of ~40 call sites to remember that is how a
 * null-check gets missed inside a damage funnel. Every accessor here answers with a total
 * value instead — {@code false}, {@code null} rarity, an empty list. Second, the affix ids on
 * disk are strings and the behaviours are objects, and resolving between them is exactly the
 * kind of thing that should happen in one place.
 *
 * <p><b>Allocation discipline.</b> {@link #affixes} builds a list and is for the roll,
 * commands and the loot path. The damage funnel and the ticker instead iterate
 * {@link EliteData#affixes()} directly and call {@link Affixes#byId}, which allocates
 * nothing — these run per hit and per pass over every elite near every player.
 */
public final class EliteMob {
	private EliteMob() {
	}

	/** The stored state, or {@code null}. Prefer the typed accessors below. */
	public static EliteData data(final Entity target) {
		return EliteStore.INSTANCE.get(target);
	}

	/** True only for a promoted elite. Safe on any entity, on either side. */
	public static boolean isElite(final Entity target) {
		EliteData data = data(target);
		return data != null && data.isElite();
	}

	/** The rarity, or {@code null} when this is not an elite. */
	public static Rarity rarityOf(final Entity target) {
		EliteData data = data(target);
		return data == null ? null : data.rarity();
	}

	/** The drawn affixes, resolved. Empty when not an elite, or when an id no longer exists. */
	public static List<Affix> affixes(final Entity target) {
		EliteData data = data(target);
		return data == null ? List.of() : Affixes.resolve(data.affixes());
	}

	/** True when this elite carries the affix. The id is the frozen registry id. */
	public static boolean has(final Entity target, final String affixId) {
		EliteData data = data(target);
		return data != null && data.affixes().contains(affixId);
	}

	/**
	 * True when this entity may never be promoted: a summon or a split copy.
	 *
	 * <p>Persistent, so a chunk reload cannot launder one back into a candidate. Design §3.1
	 * requires it of both {@code summoner} and {@code splitting}, and §4.3's X9 bars the two
	 * from ever appearing together — this flag is what makes each of them individually
	 * bounded, and the exclusion is what stops them compounding.
	 */
	public static boolean suppressed(final Entity target) {
		EliteData data = data(target);
		return data != null && data.noElite();
	}

	/** Stamps the never-promote flag, preserving anything already stored. */
	public static void suppress(final Entity target) {
		EliteData data = data(target);
		EliteStore.INSTANCE.set(target, data == null ? EliteData.SUPPRESSED : data.withNoElite());
	}

	/** How many times this lineage has already split. Zero for anything that never has. */
	public static int splitGeneration(final Entity target) {
		EliteData data = data(target);
		return data == null ? 0 : data.splitGeneration();
	}

	public static void setSplitGeneration(final Entity target, final int generation) {
		EliteData data = data(target);

		if (data != null) {
			EliteStore.INSTANCE.set(target, data.withSplitGeneration(generation));
		}
	}

	/**
	 * The once-per-lifetime escape flag. <b>Persistent on purpose</b> — a chunk reload must
	 * not refund it, or Guarantee B2's "escapist cannot loop" is simply false.
	 */
	public static boolean escapistUsed(final Entity target) {
		EliteData data = data(target);
		return data != null && data.escapistUsed();
	}

	public static void markEscapistUsed(final Entity target) {
		EliteData data = data(target);

		if (data != null) {
			EliteStore.INSTANCE.set(target, data.withEscapistUsed());
		}
	}

	/** Transient cooldowns. Never {@code null}: an elite with none yet reads as fresh. */
	public static EliteTimers timers(final Entity target) {
		EliteTimers timers = EliteStore.INSTANCE.timers(target);
		return timers == null ? EliteTimers.FRESH : timers;
	}

	public static void setTimers(final Entity target, final EliteTimers timers) {
		EliteStore.INSTANCE.setTimers(target, timers);
	}

	/**
	 * The generated fantasy name, or the empty string for anything that has none — a
	 * non-elite, or an elite promoted by a build that predates the name generator.
	 */
	public static String nameOf(final Entity target) {
		EliteData data = data(target);
		return data == null ? EliteData.NO_NAME : data.name();
	}

	/**
	 * Writes the promotion. Called once from {@code EliteRoller}, after the affix set has
	 * passed the caps, the exclusions and the EHP audit — never from an affix.
	 *
	 * @param name the generated fantasy name, already drawn from the elite's own seed
	 */
	public static void promote(final Mob mob, final Rarity rarity, final List<String> affixIds,
			final String name) {
		EliteStore.INSTANCE.set(mob, EliteData.of(rarity, affixIds, name));
		EliteStore.INSTANCE.setTimers(mob, EliteTimers.FRESH);
	}
}
