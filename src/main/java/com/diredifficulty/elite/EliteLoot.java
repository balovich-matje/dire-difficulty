package com.diredifficulty.elite;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.diredifficulty.Tuning;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ItemLike;

/**
 * Elite drops — {@code docs/DESIGN.md} §2.4.
 *
 * <p><b>A whitelist, and that is the enforcement mechanism, not a stylistic choice.</b> "No
 * building blocks, no decorative clutter" is a promise this mod makes about every elite of
 * every rarity on every future retune. A blacklist would need updating every time the item
 * registry grows and would be wrong silently in between. The seven entries below are the
 * complete set of things an elite can ever drop; nothing can leak in, because there is no path
 * that adds an item.
 *
 * <p>This class holds the SHAPE. Every amount lives in {@code Tuning}, indexed by rarity.
 *
 * <p><b>Looting does not multiply any of it.</b> The rarity is already the reward multiplier —
 * letting Looting stack on top double-dips, and it does so hardest exactly where the drops are
 * biggest. Looting still applies normally to the vanilla table underneath, which is untouched.
 *
 * <p>Player-kill gated, because loot that a cactus can farm is not a reward for beating
 * anything.
 */
public final class EliteLoot {
	private EliteLoot() {
	}

	/**
	 * The mixin entry point, from the TAIL of {@code LivingEntity.dropAllDeathLoot}.
	 *
	 * <p>That method has already run the vanilla loot table, the equipment drop and the
	 * experience award by the time this fires, so these items land alongside a normal death
	 * rather than instead of one — {@code vanilla-plus, amplify don't gate}, applied to loot.
	 */
	public static void onDropAllDeathLoot(final LivingEntity entity, final ServerLevel level,
			final DamageSource source) {
		Rarity rarity = EliteMob.rarityOf(entity);

		if (rarity == null || !wasPlayerKilled(entity, source)) {
			return;
		}

		for (final ItemStack stack : rollDrops(level, rarity)) {
			entity.spawnAtLocation(level, stack);
		}
	}

	/**
	 * Rolls one elite's extra drops without spawning them.
	 *
	 * <p><b>Split out of {@link #onDropAllDeathLoot} so the table is probe-able.</b> The drop
	 * path is player-kill gated, which is correct for the game and useless for verification: a
	 * headless smoke server has no player, so the only way to see this table run at all is to be
	 * able to roll it without a corpse. {@code /dire loot} calls exactly this method, so
	 * what the probe measures is the shipped table rather than a copy of it.
	 *
	 * @return the stacks to drop, in whitelist order, never containing an empty stack
	 */
	public static List<ItemStack> rollDrops(final ServerLevel level, final Rarity rarity) {
		int tier = rarity.ordinal();
		RandomSource random = level.getRandom();
		List<ItemStack> drops = new ArrayList<>();

		add(drops, Items.IRON_INGOT, roll(random, Tuning.LOOT_IRON_MIN[tier], Tuning.LOOT_IRON_MAX[tier]));
		add(drops, Items.GOLD_INGOT, roll(random, Tuning.LOOT_GOLD_MIN[tier], Tuning.LOOT_GOLD_MAX[tier]));
		add(drops, Items.COAL, roll(random, Tuning.LOOT_COAL_MIN[tier], Tuning.LOOT_COAL_MAX[tier]));
		add(drops, Items.EMERALD, roll(random, Tuning.LOOT_EMERALD_MIN[tier], Tuning.LOOT_EMERALD_MAX[tier]));
		add(drops, Items.DIAMOND, roll(random, Tuning.LOOT_DIAMOND_MIN[tier], Tuning.LOOT_DIAMOND_MAX[tier]));
		add(drops, Items.ENDER_PEARL,
				roll(random, Tuning.LOOT_ENDER_PEARL_MIN[tier], Tuning.LOOT_ENDER_PEARL_MAX[tier]));
		add(drops, Items.NETHERITE_SCRAP,
				roll(random, Tuning.LOOT_NETHERITE_SCRAP_MIN[tier], Tuning.LOOT_NETHERITE_SCRAP_MAX[tier]));

		int books = roll(random, Tuning.LOOT_BOOK_MIN[tier], Tuning.LOOT_BOOK_MAX[tier]);

		for (int i = 0; i < books; i++) {
			ItemStack book = enchantedBook(level, random, tier);

			if (!book.isEmpty()) {
				drops.add(book);
			}
		}

		return drops;
	}

	/**
	 * Scales the experience {@code dropExperience} is about to award — §2.4's separate, and
	 * deliberately much smaller, dial.
	 *
	 * <p>Applied as a {@code @ModifyArg} on vanilla's own {@code ExperienceOrb.award} call
	 * rather than by awarding a second batch of orbs, because the base amount is computed
	 * inside a protected method and the only honest way to multiply a number is to have the
	 * real one in hand. It also means an elite drops ONE stream of orbs, not two.
	 */
	public static int scaleExperience(final LivingEntity entity, final int amount) {
		Rarity rarity = EliteMob.rarityOf(entity);
		return rarity == null ? amount : Math.round(amount * rarity.xpMultiplier());
	}

	/**
	 * Vanilla computes the same thing as a local at the top of {@code dropAllDeathLoot}, from
	 * a protected field this cannot see. {@code getKillCredit} is the public equivalent — it
	 * returns the remembered player when there is one, including the tamed-wolf case, and
	 * falls back to the last attacking mob otherwise, which is why the result is type-tested
	 * rather than null-tested. The damage source is checked too, for the kill that lands
	 * before any memory is written.
	 */
	private static boolean wasPlayerKilled(final LivingEntity entity, final DamageSource source) {
		return entity.getKillCredit() instanceof Player || source.getEntity() instanceof Player;
	}

	private static int roll(final RandomSource random, final int min, final int max) {
		if (max <= 0) {
			return 0;
		}

		return min >= max ? min : Mth.nextInt(random, min, max);
	}

	private static void add(final List<ItemStack> drops, final ItemLike item, final int count) {
		if (count > 0) {
			drops.add(new ItemStack(item, count));
		}
	}

	/**
	 * A book rolled through vanilla's own enchanting machinery, restricted to the
	 * {@code in_enchanting_table} set.
	 *
	 * <p>Using {@code EnchantmentHelper.enchantItem} rather than picking an enchantment
	 * directly buys the whole vanilla feel for free: the level-appropriate selection, the
	 * multi-enchantment chance and the incompatibility filtering are all vanilla's, so a T4
	 * book reads as a near-max table roll rather than as a modded item. Restricting to the
	 * table's own set is what keeps curses and treasure enchantments out — an unrestricted
	 * roll is allowed to hand back a Curse of Binding book as a reward.
	 *
	 * <p>{@code enchantItem} converts the plain book to an enchanted one itself; a book that
	 * drew nothing at all comes back as an ordinary book and is dropped rather than discarded,
	 * because a plain book is a usable resource and silently swallowing a rolled drop is worse
	 * than a modest one.
	 */
	private static ItemStack enchantedBook(final ServerLevel level, final RandomSource random, final int tier) {
		int cost = roll(random, Tuning.LOOT_BOOK_COST_MIN[tier], Tuning.LOOT_BOOK_COST_MAX[tier]);
		Optional<? extends HolderSet<Enchantment>> pool = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(EnchantmentTags.IN_ENCHANTING_TABLE);

		return EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), cost,
				level.registryAccess(), pool);
	}
}
