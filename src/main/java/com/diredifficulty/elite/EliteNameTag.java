package com.diredifficulty.elite;

import java.util.Locale;

import com.diredifficulty.DireDifficulty;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Mob;

/**
 * The elite's display name — {@code docs/DESIGN.md} §2.1's name colour plus the generated
 * fantasy name — assembled server-side, in one place.
 *
 * <h2>The format, and why it is built the way it is</h2>
 *
 * <pre>
 *   Grave Lord (Legendary Zombie)
 *   ^^^^^^^^^^  ^^^^^^^^^ ^^^^^^
 *   literal     lang key  the mob's OWN translatable type name
 * </pre>
 *
 * <p>The two halves are stored and resolved differently on purpose:
 *
 * <ul>
 * <li>The <b>fantasy name</b> is a literal string, generated once at promotion by
 *     {@link EliteNames} and stored in {@link EliteData}. Fantasy names are not translated in
 *     any game that has them, and a name assembled from lang keys would need one key per word
 *     and would still come out in English word order everywhere else.</li>
 * <li>The <b>suffix</b> is a {@code translatable} component every time: this mod's four rarity
 *     keys, and {@code EntityType.getDescription()} for the mob. So the server ships no mob
 *     name for any of the several hundred types it can promote, and a client reads its own
 *     language for both words.</li>
 * </ul>
 *
 * <p><b>Casing follows the lang file, not the code.</b> {@code elite.dire_difficulty.rarity.*} is
 * shipped capitalised so the suffix reads consistently against vanilla's own capitalised entity
 * names ("Legendary Zombie", not "legendary Zombie"). Lower-casing the entity half in code is
 * not an option that exists: {@code getDescription()} is a component the CLIENT resolves, so
 * case-folding it server-side would first have to bake the server's locale into the name.
 *
 * <h2>One source, both surfaces</h2>
 * This is the only place the display name is composed. The name tag it writes IS what
 * {@code ElitePlate} draws, because the plate reads {@code entity.getName()} — so the plate
 * and the in-world nameplate cannot disagree, and adding a surface later costs nothing.
 *
 * <p>The tag is set but deliberately <b>not</b> forced visible ({@code setCustomNameVisible} is
 * never called). Vanilla then draws it only for the entity under the player's crosshair, which
 * is the difference between "I can identify this thing" and a screen full of floating labels
 * during a raid. That is unchanged from iteration 1 and it is what keeps one consistent source.
 */
public final class EliteNameTag {
	/** {@code elite.dire_difficulty.rarity.legendary} — one key per {@link Rarity}, lower-cased. */
	private static final String RARITY_KEY = "elite." + DireDifficulty.MOD_ID + ".rarity.";

	/** {@code "%s (%s %s)"} in en_us: fantasy name, rarity word, entity type name. */
	private static final String NAME_KEY = "elite." + DireDifficulty.MOD_ID + ".name";

	/** The suffix alone, for an elite with no generated name (an iteration-1 world). */
	private static final String NAME_PLAIN_KEY = "elite." + DireDifficulty.MOD_ID + ".name.plain";

	private EliteNameTag() {
	}

	/**
	 * Stamps the composed, rarity-coloured name. Idempotent — re-running the promotion rewrites
	 * it in place rather than wrapping the previous one again.
	 */
	public static void apply(final Mob mob, final Rarity rarity, final String fantasyName) {
		mob.setCustomName(compose(mob, rarity, fantasyName));
	}

	/**
	 * Builds the display component.
	 *
	 * <p>The base name is the mob's own {@code EntityType.getDescription()} rather than
	 * {@code getCustomName()}: by the time this runs the promotion may have set a name already
	 * (a re-promotion), and reading it back would nest "Grave Lord (Legendary Zombie)" inside
	 * the next suffix. A mob that arrived with a hand-given name — a nametagged villager turned
	 * zombie — loses it here, which is the trade for the format being stable under a repeat.
	 */
	public static Component compose(final Mob mob, final Rarity rarity, final String fantasyName) {
		Component type = mob.getType().getDescription();
		Component rarityWord = Component.translatable(RARITY_KEY + rarity.name().toLowerCase(Locale.ROOT));

		MutableComponent composed = fantasyName == null || fantasyName.isEmpty()
				? Component.translatable(NAME_PLAIN_KEY, rarityWord, type)
				: Component.translatable(NAME_KEY, fantasyName, rarityWord, type);

		return composed.withStyle(Style.EMPTY.withColor(rarity.colour()));
	}
}
