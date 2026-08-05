package com.escalation.elite;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Mob;

/**
 * The rarity-coloured name tag — {@code docs/DESIGN.md} §2.1's name colour, server-side.
 *
 * <p>Built from the mob's OWN {@code EntityType.getDescription()}, which is a translatable
 * component: the client resolves "Zombie", "Zombi", "ゾンビ" from its own language file, and
 * this mod ships no string for any of the several hundred mob types it can promote. Wrapping
 * rather than replacing is also what keeps a mob that already had a name — a nametagged
 * villager-turned-zombie, a mod's boss — reading as itself.
 *
 * <p><b>Colour carries the rarity, and nothing else does.</b> Exactly the colour §2.1 assigns
 * the glow, so the two agree by construction rather than by two tables staying in step. There
 * is deliberately no rarity WORD: a word means four new translation keys in the shared
 * language file, and an untranslated key renders as {@code elite.escalation.rarity.legendary}
 * over the mob's head — a worse outcome than no word at all. When the client-side nameplate
 * lands it can add one, and it owns the language file it would need.
 *
 * <p>The tag is set but <b>not</b> forced visible. Vanilla then draws it only for the entity
 * under the player's crosshair, which is the difference between "I can identify this thing"
 * and a screen full of floating labels during a raid.
 */
public final class EliteNameTag {
	private EliteNameTag() {
	}

	/** Stamps the coloured name. Idempotent — re-running the promotion rewrites it in place. */
	public static void apply(final Mob mob, final Rarity rarity) {
		Component base = mob.hasCustomName() ? mob.getCustomName() : mob.getType().getDescription();
		MutableComponent named = Component.empty().append(base);
		mob.setCustomName(named.withStyle(Style.EMPTY.withColor(rarity.colour())));
	}
}
