package com.diredifficulty.elite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.RegistryAccess;

/**
 * The Mythical artefacts: a fixed, hand-made set (user decision 2026-11-21). Every Mythical
 * carries exactly one, in the slot that shows it, and always drops it. Over-the-cap
 * enchantment levels are written straight into the enchantments component; vanilla honours
 * any level up to 255.
 *
 * <p>Each artefact is tagged {@code dire_difficulty_artefact: <id>} in custom data, so the
 * mod can recognise one later without reading its (renamable) name.
 */
public enum Artefact {
	DAWNBRAND(Items.GOLDEN_SWORD, EquipmentSlot.MAINHAND, "Dawnbrand", "Bane of the Restless",
			s -> s.enchant(Enchantments.FIRE_ASPECT, 2).enchant(Enchantments.BREACH, 4)
					.enchant(Enchantments.UNBREAKING, 4).enchant(Enchantments.SMITE, 10)),
	THE_LAST_WALL(Items.SHIELD, EquipmentSlot.OFFHAND, "The Last Wall", "Nothing passed. Nothing ever will.",
			s -> s.enchant(Enchantments.UNBREAKING, 4)
					.attribute(Attributes.ARMOR, 4.0, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.OFFHAND)
					.attribute(Attributes.ARMOR_TOUGHNESS, 2.0, AttributeModifier.Operation.ADD_VALUE, EquipmentSlotGroup.OFFHAND)
					.banner(DyeColor.LIGHT_GRAY, BannerPatterns.BRICKS, DyeColor.GRAY, BannerPatterns.CURLY_BORDER, DyeColor.BLACK)),
	SKULLCRACKER(Items.IRON_AXE, EquipmentSlot.MAINHAND, "Skullcracker", "One edge. One purpose.",
			s -> s.enchant(Enchantments.SHARPNESS, 10)),
	KINGSFALL(Items.DIAMOND_SWORD, EquipmentSlot.MAINHAND, "Kingsfall", "No crown is thick enough.",
			s -> s.enchant(Enchantments.SHARPNESS, 6).enchant(Enchantments.BREACH, 4)),
	WHISPERSTRING(Items.BOW, EquipmentSlot.MAINHAND, "Whisperstring", "You will not hear it.",
			s -> s.enchant(Enchantments.POWER, 6).enchant(Enchantments.PUNCH, 3).enchant(Enchantments.INFINITY, 1)),
	GRUDGEBEARER(Items.CROSSBOW, EquipmentSlot.MAINHAND, "Grudgebearer", "It remembers every village.",
			s -> s.enchant(Enchantments.MULTISHOT, 1).enchant(Enchantments.PIERCING, 5)
					.enchant(Enchantments.QUICK_CHARGE, 4)),
	BASTION_BREAKER(Items.GOLDEN_AXE, EquipmentSlot.MAINHAND, "Bastion Breaker", "Gold, bent to ruin.",
			s -> s.enchant(Enchantments.EFFICIENCY, 7).enchant(Enchantments.SHARPNESS, 5)
					.enchant(Enchantments.UNBREAKING, 5).durability(Items.NETHERITE_AXE)),
	GRAVETAKER(Items.STONE_SWORD, EquipmentSlot.MAINHAND, "Gravetaker", "What falls, it keeps.",
			s -> s.enchant(Enchantments.SHARPNESS, 5).enchant(Enchantments.LOOTING, 4)),
	CROWN_OF_THE_UNBOWED(Items.NETHERITE_HELMET, EquipmentSlot.HEAD, "Crown of the Unbowed",
			"Worn by those who never knelt.",
			s -> s.enchant(Enchantments.PROTECTION, 5).enchant(Enchantments.RESPIRATION, 4)
					.enchant(Enchantments.AQUA_AFFINITY, 1)),
	IRONHEART(Items.IRON_CHESTPLATE, EquipmentSlot.CHEST, "Ironheart", "Strike it and bleed.",
			s -> s.enchant(Enchantments.PROTECTION, 6).enchant(Enchantments.THORNS, 4)),
	STILLWATER_GREAVES(Items.DIAMOND_LEGGINGS, EquipmentSlot.LEGS, "Stillwater Greaves",
			"Silent, and unshaken by fire.",
			s -> s.enchant(Enchantments.SWIFT_SNEAK, 5).enchant(Enchantments.BLAST_PROTECTION, 6)),
	CLOUDWALKERS(Items.DIAMOND_BOOTS, EquipmentSlot.FEET, "Cloudwalkers", "The ground forgets you.",
			s -> s.enchant(Enchantments.FEATHER_FALLING, 6).enchant(Enchantments.SOUL_SPEED, 4)),
	GALESKIPPERS(Items.LEATHER_BOOTS, EquipmentSlot.FEET, "Galeskippers", "Borrowed from the wind.",
			s -> s.attribute(Attributes.MOVEMENT_SPEED, 0.4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE,
					EquipmentSlotGroup.FEET).dye(0xBFE3F2));

	/** Custom-data key marking an artefact stack; the value is the lower-case enum name. */
	public static final String TAG = "dire_difficulty_artefact";

	private final Item item;
	private final EquipmentSlot slot;
	private final String displayName;
	private final String lore;
	private final Consumer<Spec> spec;

	Artefact(final Item item, final EquipmentSlot slot, final String displayName, final String lore,
			final Consumer<Spec> spec) {
		this.item = item;
		this.slot = slot;
		this.displayName = displayName;
		this.lore = lore;
		this.spec = spec;
	}

	public EquipmentSlot slot() {
		return this.slot;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public static Artefact byId(final String id) {
		for (Artefact artefact : values()) {
			if (artefact.id().equals(id)) {
				return artefact;
			}
		}

		return null;
	}

	/** The artefacts a Mythical of this type may carry; empty means "any it can wear". */
	public static List<Artefact> forType(final EntityType<?> type) {
		if (type == EntityTypes.ZOMBIFIED_PIGLIN) {
			return List.of(DAWNBRAND, CROWN_OF_THE_UNBOWED, IRONHEART);
		}

		if (type == EntityTypes.PIGLIN) {
			return List.of(DAWNBRAND, CROWN_OF_THE_UNBOWED, IRONHEART, GALESKIPPERS);
		}

		if (type == EntityTypes.SKELETON) {
			return List.of(WHISPERSTRING, CLOUDWALKERS, GALESKIPPERS);
		}

		if (type == EntityTypes.WITHER_SKELETON) {
			return List.of(GRAVETAKER, KINGSFALL, STILLWATER_GREAVES);
		}

		if (type == EntityTypes.PIGLIN_BRUTE) {
			return List.of(BASTION_BREAKER);
		}

		if (type == EntityTypes.PILLAGER) {
			return List.of(GRUDGEBEARER);
		}

		if (type == EntityTypes.VINDICATOR) {
			return List.of(SKULLCRACKER, KINGSFALL);
		}

		if (type == EntityTypes.EVOKER) {
			return List.of(THE_LAST_WALL);
		}

		return List.of();
	}

	/** Builds a fresh stack of this artefact. */
	public ItemStack create(final RegistryAccess registries) {
		ItemStack stack = new ItemStack(this.item);
		Spec built = new Spec(registries, stack);
		this.spec.accept(built);
		built.finish();

		stack.set(DataComponents.CUSTOM_NAME, Component.literal(this.displayName)
				.withStyle(Style.EMPTY.withColor(Tuning.RARITY_COLOUR[Rarity.MYTHICAL.ordinal()])
						.withBold(true).withItalic(false)));
		stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(this.lore)
				.withStyle(Style.EMPTY.withColor(0xAAAAAA).withItalic(true)))));
		stack.set(DataComponents.RARITY, net.minecraft.world.item.Rarity.EPIC);

		CompoundTag tag = new CompoundTag();
		tag.putString(TAG, this.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/** The small builder each artefact's spec writes into. */
	static final class Spec {
		private final RegistryAccess registries;
		private final ItemStack stack;
		private final ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		private ItemAttributeModifiers modifiers;
		private final List<String> attributeNames = new ArrayList<>();

		Spec(final RegistryAccess registries, final ItemStack stack) {
			this.registries = registries;
			this.stack = stack;
			this.modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		}

		Spec enchant(final ResourceKey<Enchantment> key, final int level) {
			HolderLookup.RegistryLookup<Enchantment> lookup = this.registries.lookupOrThrow(Registries.ENCHANTMENT);
			this.enchantments.set(lookup.getOrThrow(key), level);
			return this;
		}

		Spec attribute(final Holder<Attribute> attribute, final double amount,
				final AttributeModifier.Operation operation, final EquipmentSlotGroup group) {
			String path = "artefact_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this.stack.getItem()).getPath()
					+ "_" + this.attributeNames.size();
			this.attributeNames.add(path);
			this.modifiers = this.modifiers.withModifierAdded(attribute,
					new AttributeModifier(Identifier.fromNamespaceAndPath(DireDifficulty.MOD_ID, path), amount, operation),
					group);
			return this;
		}

		Spec banner(final DyeColor base, final ResourceKey<BannerPattern> first, final DyeColor firstColour,
				final ResourceKey<BannerPattern> second, final DyeColor secondColour) {
			HolderGetter<BannerPattern> patterns = this.registries.lookupOrThrow(Registries.BANNER_PATTERN);
			this.stack.set(DataComponents.BASE_COLOR, base);
			this.stack.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
					.add(patterns.getOrThrow(first), firstColour)
					.add(patterns.getOrThrow(second), secondColour)
					.build());
			return this;
		}

		Spec durability(final Item like) {
			this.stack.set(DataComponents.MAX_DAMAGE, new ItemStack(like).getMaxDamage());
			return this;
		}

		Spec dye(final int rgb) {
			this.stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
			return this;
		}

		void finish() {
			ItemEnchantments built = this.enchantments.toImmutable();

			if (!built.isEmpty()) {
				this.stack.set(DataComponents.ENCHANTMENTS, built);
			}

			if (!this.attributeNames.isEmpty()) {
				this.stack.set(DataComponents.ATTRIBUTE_MODIFIERS, this.modifiers);
			}
		}
	}
}
