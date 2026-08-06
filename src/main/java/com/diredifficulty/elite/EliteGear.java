package com.diredifficulty.elite;

import java.util.Locale;
import java.util.Optional;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Eligibility;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.config.DireDifficultyConfig;

import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.DropChances;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Elite equipment: what an elite spawns wearing, and the two rules about what it drops.
 *
 * <h2>Vanilla's mechanics, not a re-implementation of them</h2>
 * Everything here goes through {@code Mob.setItemSlot} and {@code Mob.setDropChance} and then
 * <b>stops</b>. Nothing in this file spawns an item, rolls a drop or damages a stack. That is
 * deliberate and it is what buys the required behaviour for free, measured off
 * {@code Mob.dropCustomDeathLoot} in the 26.2 sources:
 *
 * <ul>
 * <li><b>Rare to drop.</b> A slot drops on {@code random.nextFloat() < dropChance}, and only
 *     when the mob was killed by a player. Vanilla's own default is
 *     {@code DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F}; {@link Tuning#GEAR_DROP_CHANCE}
 *     sits at or below it and tapers as the gear gets better.</li>
 * <li><b>Damaged when dropped.</b> Vanilla's line is
 *     {@code if (!preserve && stack.isDamageableItem()) stack.setDamageValue(maxDamage -
 *     random.nextInt(1 + random.nextInt(max(maxDamage - 3, 1))))} — a stack that lands with
 *     most of its durability already gone. It runs for every slot this file touches, which is
 *     the entire "no full-durability windfalls" rule, and it is the reason
 *     {@link Tuning#GEAR_DROP_CHANCE_MAX} exists: a chance <b>above 1.0</b> makes a slot
 *     {@code preserved}, and preserved is exactly the branch that skips the damage AND drops
 *     every time. This file can never write one.</li>
 * </ul>
 *
 * <h2>The two hard rules</h2>
 * <ol>
 * <li><b>Netherite armour never drops.</b> The four netherite armour pieces get drop chance
 *     {@code 0.0F} at the moment they are equipped, and the death-time pass in
 *     {@link #beforeEquipmentDrops} re-asserts it for any netherite armour that reached the mob
 *     by another route. A netherite WEAPON is not covered: the design says the weapon may drop,
 *     and a damaged netherite sword at a few percent is a trophy rather than an economy.</li>
 * <li><b>Vanilla's own damaged-drop path applies</b>, as above.</li>
 * </ol>
 *
 * <h2>Only empty slots are filled, and that is what makes it work per family</h2>
 * The promotion runs at the TAIL of {@code Mob}'s own {@code finalizeSpawn}, i.e. inside the
 * {@code super} call a subclass makes before doing its own equipment work. Filling only empty
 * slots means the two directions both come out right, and both are measured:
 *
 * <ul>
 * <li>{@code Mob.populateDefaultEquipmentSlots} fills a slot only {@code if (itemStack
 *     .isEmpty())}, so vanilla never overwrites armour this file placed.</li>
 * <li>{@code AbstractSkeleton.populateDefaultEquipmentSlots} sets {@code MAINHAND} to a bow
 *     <b>unconditionally</b>, so a skeleton keeps its bow whatever this file does — which is
 *     the behaviour we want anyway. {@link Eligibility#ranged} keeps this file from making the
 *     pointless stack in the first place.</li>
 * </ul>
 *
 * <h2>Interaction with elite loot</h2>
 * None, checked rather than assumed. {@code EliteLoot} injects at the TAIL of
 * {@code LivingEntity.dropAllDeathLoot} and adds items from its own whitelist; the equipment
 * drop happens inside {@code Mob.dropCustomDeathLoot}, which that method calls earlier, and
 * neither one reads or writes the other's items. Gear drops are IN ADDITION to the rarity loot
 * table, with no double handling in either direction.
 */
public final class EliteGear {
	/**
	 * Types that may be given elite gear. Tag-driven, the house convention — and a positive
	 * list rather than a blacklist because the question it answers is "does this mob RENDER
	 * armour and a held item", which no runtime property exposes. A creeper can be handed a
	 * netherite chestplate: it takes the armour points and draws nothing, which is a hidden
	 * buff with no tell, and that is the failure this tag exists to prevent.
	 *
	 * <p>The shipped file is vanilla's humanoid hostile set. An absent tag resolves empty and
	 * every {@code is(tag)} test answers false, so a pack that deletes it turns the feature off
	 * rather than breaking it.
	 */
	public static final TagKey<EntityType<?>> GEAR_TAG = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(DireDifficulty.MOD_ID, "elite_gear"));

	/** Armour slots, in the order vanilla's own {@code EQUIPMENT_POPULATION_ORDER} uses. */
	private static final EquipmentSlot[] ARMOR_SLOTS = {
		EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/**
	 * The material ladder, one index for both halves of a set.
	 *
	 * <p>Indices 0..5 are <b>vanilla's own numbering</b> from
	 * {@code Mob.getEquipmentForSlot(slot, type)} — leather, copper, gold, chainmail, iron,
	 * diamond — so the armour half is vanilla's table rather than a copy of it. Index 6 is
	 * netherite, which that method does not know: it falls through its own switch and returns
	 * {@code null} for any type above 5, so the netherite row below is this file's own.
	 */
	private static final int TIER_NETHERITE = 6;

	/** Swords by ladder index. Chainmail has no weapon, so index 3 borrows stone. */
	private static final Item[] SWORDS = {
		Items.WOODEN_SWORD, Items.COPPER_SWORD, Items.GOLDEN_SWORD, Items.STONE_SWORD,
		Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};

	private static final Item[] AXES = {
		Items.WOODEN_AXE, Items.COPPER_AXE, Items.GOLDEN_AXE, Items.STONE_AXE,
		Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};

	/** Netherite armour, in {@link #ARMOR_SLOTS} order. The pieces that may never drop. */
	private static final Item[] NETHERITE_ARMOR = {
		Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
		Items.NETHERITE_BOOTS};

	private EliteGear() {
	}

	// =====================================================================================
	// Spawn side
	// =====================================================================================

	/**
	 * Arms and armours a freshly promoted elite. Called from {@code EliteRoller.promote}, after
	 * the stat modifiers and before the name tag.
	 *
	 * <p>Uses the level's own {@code RandomSource}, exactly as vanilla's equipment population
	 * does — the gear is flavour on top of a spawn, not part of the elite's identity the way
	 * its name is, so it does not need the name generator's per-entity seed.
	 */
	public static void equip(final ServerLevel level, final Mob mob, final Rarity rarity,
			final RandomSource random) {
		if (!mob.is(GEAR_TAG)) {
			return;
		}

		int tier = rarity.ordinal();

		equipWeapon(level, mob, tier, random);

		for (int i = 0; i < ARMOR_SLOTS.length; i++) {
			equipArmor(level, mob, ARMOR_SLOTS[i], i, tier, random);
		}
	}

	/**
	 * The main hand, for melee elites only.
	 *
	 * <p>{@code ranged(mob)} is excluded rather than merely deprioritised: a skeleton, a
	 * pillager and a drowned all have their own weapon and their own attack goal keyed to it,
	 * and handing one a sword either does nothing (vanilla overwrites the slot a moment later)
	 * or converts it into a different mob. They still get armour.
	 */
	private static void equipWeapon(final ServerLevel level, final Mob mob, final int tier,
			final RandomSource random) {
		if (!Eligibility.melee(mob) || Eligibility.ranged(mob)) {
			return;
		}

		if (!mob.canUseSlot(EquipmentSlot.MAINHAND) || !mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
			return;
		}

		if (random.nextDouble() >= Tuning.GEAR_WEAPON_CHANCE[tier]) {
			return;
		}

		int material = drawTier(random, Tuning.GEAR_WEAPON_TIERS[tier]);
		Item item = random.nextDouble() < Tuning.GEAR_AXE_CHANCE ? AXES[material] : SWORDS[material];
		ItemStack stack = new ItemStack(item);

		enchant(level, random, stack, tier, Tuning.GEAR_WEAPON_ENCHANT_CHANCE[tier]);
		place(mob, EquipmentSlot.MAINHAND, stack, tier);
	}

	private static void equipArmor(final ServerLevel level, final Mob mob, final EquipmentSlot slot,
			final int slotIndex, final int tier, final RandomSource random) {
		if (!mob.canUseSlot(slot) || !mob.getItemBySlot(slot).isEmpty()) {
			return;
		}

		if (random.nextDouble() >= Tuning.GEAR_ARMOR_PIECE_CHANCE[tier]) {
			return;
		}

		int material = drawTier(random, Tuning.GEAR_ARMOR_TIERS[tier]);
		Item item = material >= TIER_NETHERITE
				? NETHERITE_ARMOR[slotIndex]
				: Mob.getEquipmentForSlot(slot, material);

		if (item == null) {
			return;
		}

		ItemStack stack = new ItemStack(item);

		enchant(level, random, stack, tier, Tuning.GEAR_ARMOR_ENCHANT_CHANCE[tier]);
		place(mob, slot, stack, tier);
	}

	/**
	 * Sets the slot and the drop chance together, because doing one without the other is how
	 * the hard rules get missed. This is the only place either call is made on spawn.
	 */
	private static void place(final Mob mob, final EquipmentSlot slot, final ItemStack stack, final int tier) {
		mob.setItemSlot(slot, stack);
		mob.setDropChance(slot, dropChanceFor(stack, tier));
	}

	/**
	 * The drop chance for one piece.
	 *
	 * <p>Zero for netherite armour — hard rule 1. Otherwise the rarity's own value, clamped
	 * below {@link Tuning#GEAR_DROP_CHANCE_MAX} so it can never reach
	 * {@code DropChances.PRESERVE_ITEM_DROP_CHANCE_THRESHOLD}, which is the value that would
	 * turn the slot into a guaranteed, undamaged drop.
	 */
	private static float dropChanceFor(final ItemStack stack, final int tier) {
		if (isNoDropArmor(stack)) {
			return 0.0F;
		}

		return (float) Tuning.clamp(Tuning.GEAR_DROP_CHANCE[tier], 0.0, Tuning.GEAR_DROP_CHANCE_MAX);
	}

	/** A weighted draw expressed as a list — a repeated entry is a heavier one. */
	private static int drawTier(final RandomSource random, final int[] ladder) {
		return ladder[random.nextInt(ladder.length)];
	}

	/**
	 * Vanilla's own enchanting roll, at a Tuning-set cost range, restricted to the
	 * {@code in_enchanting_table} set.
	 *
	 * <p>Same call {@code EliteLoot} uses for its books and for the same reasons: the
	 * level-appropriate selection, the multi-enchantment chance and the incompatibility
	 * filtering are all vanilla's, so a Legendary's netherite sword reads as a near-max table
	 * roll rather than as a modded item — and restricting to the table's own set is what keeps
	 * curses and treasure enchantments off a mob that is going to drop the thing.
	 *
	 * <p>{@code enchantItem} returns the same stack for anything that is not a plain book, so
	 * the in-place call is correct here where it would not be for a book.
	 */
	private static void enchant(final ServerLevel level, final RandomSource random, final ItemStack stack,
			final int tier, final double chance) {
		if (chance <= 0.0 || random.nextDouble() >= chance) {
			return;
		}

		int min = Tuning.GEAR_ENCHANT_COST_MIN[tier];
		int max = Tuning.GEAR_ENCHANT_COST_MAX[tier];
		int cost = max <= min ? min : min + random.nextInt(max - min + 1);

		if (cost <= 0) {
			return;
		}

		Optional<? extends HolderSet<Enchantment>> pool = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.get(EnchantmentTags.IN_ENCHANTING_TABLE);

		EnchantmentHelper.enchantItem(random, stack, cost, level.registryAccess(), pool);
	}

	// =====================================================================================
	// Death side — the two config toggles and hard rule 1's re-assertion
	// =====================================================================================

	/**
	 * Applies the gear-drop rules to a mob that is about to run vanilla's equipment drop loop.
	 * Called from the HEAD of {@code Mob.dropCustomDeathLoot}.
	 *
	 * <p><b>It suppresses by zeroing vanilla's own drop chance, not by cancelling the loop.</b>
	 * That keeps every other thing that loop does — the looting enchantment's contribution, the
	 * {@code PREVENT_EQUIPMENT_DROP} check, the player-kill gate, the damage roll — exactly as
	 * vanilla wrote it, and it means the two toggles cannot interact with anything except the
	 * chance they name.
	 *
	 * <p><b>Preserved slots are never touched, and that is a safety rule rather than a
	 * nicety.</b> A drop chance above 1.0 means the mob PICKED THAT ITEM UP
	 * ({@code Mob.equipItemIfPossible} calls {@code setItemSlotAndDropWhenKilled}, which calls
	 * {@code setGuaranteedDrop}). Suppressing those would eat items a player threw or lost,
	 * which no difficulty setting should be able to do. It is also what makes toggle (b) mean
	 * exactly what it says: no <i>naturally spawned</i> gear drops, while gear the world handed
	 * the mob still comes back.
	 */
	public static void beforeEquipmentDrops(final Mob mob) {
		DireDifficultyConfig config = ConfigManager.get();
		boolean elite = EliteMob.isElite(mob);
		boolean suppress = !config.allMobGearDrops || (elite && !config.eliteGearDrops);
		DropChances chances = mob.getDropChances();

		for (final EquipmentSlot slot : EquipmentSlot.VALUES) {
			if (chances.isPreserved(slot)) {
				continue;
			}

			ItemStack stack = mob.getItemBySlot(slot);

			if (stack.isEmpty()) {
				continue;
			}

			if (suppress || isNoDropArmor(stack)) {
				mob.setDropChance(slot, 0.0F);
			}
		}
	}

	/** The four netherite armour pieces — hard rule 1's subject, named explicitly. */
	private static boolean isNoDropArmor(final ItemStack stack) {
		for (final Item piece : NETHERITE_ARMOR) {
			if (stack.is(piece)) {
				return true;
			}
		}

		return false;
	}

	// =====================================================================================
	// Introspection, for the test kit
	// =====================================================================================

	/** A one-line "mainhand / armour" summary for {@code /dire info}. */
	public static String describe(final Mob mob) {
		StringBuilder out = new StringBuilder();

		append(out, mob, EquipmentSlot.MAINHAND);

		for (final EquipmentSlot slot : ARMOR_SLOTS) {
			append(out, mob, slot);
		}

		return out.length() == 0 ? "-" : out.toString();
	}

	private static void append(final StringBuilder out, final Mob mob, final EquipmentSlot slot) {
		ItemStack stack = mob.getItemBySlot(slot);

		if (stack.isEmpty()) {
			return;
		}

		if (out.length() > 0) {
			out.append(", ");
		}

		out.append(slot.getName()).append('=').append(stack.getHoverName().getString());

		int enchantments = stack.getEnchantments().size();

		if (enchantments > 0) {
			out.append('+').append(enchantments);
		}

		out.append('@').append(String.format(Locale.ROOT, "%.3f",
				mob.getDropChances().byEquipment(slot)));
	}
}
