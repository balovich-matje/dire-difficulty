package com.escalation.elite;

import java.util.ArrayList;
import java.util.List;

import com.escalation.Escalation;
import com.escalation.Tuning;
import com.escalation.affix.Affix;
import com.escalation.affix.AffixBudget;
import com.escalation.progression.ScoreSource;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The promotion — {@code docs/DESIGN.md} §2.3 — from spawn interception to a finished elite.
 *
 * <p><b>Entered from {@code Mob.finalizeSpawn} at TAIL</b>, which is the right point for three
 * measured reasons rather than one assumed one. The natural spawner calls it with
 * {@code NATURAL} and chunk generation with {@code CHUNK_GENERATION}; the spawn reason is an
 * argument in hand, so the roll can be restricted to exactly those two out of the nineteen the
 * enum defines; and it runs after vanilla's own equipment and difficulty finalisation, so
 * elite modifiers layer on top instead of being overwritten.
 *
 * <p><b>The spawn-reason gate is the anti-farm rule and it is not negotiable.</b> A mob farm
 * must not print elites, so {@code SPAWNER} and {@code TRIAL_SPAWNER} are out. A re-loaded
 * chunk must not re-roll one, so {@code LOAD} is out. And every conversion, breeding,
 * reinforcement, jockey, bucket and command path is out too, which between them is where every
 * "infinite elites" exploit would otherwise live.
 *
 * <p>Nothing here re-runs on load. Permanent attribute modifiers, the custom name and the
 * mob-effect the {@code sunwalker} affix applies are all serialised by vanilla, and
 * {@code EliteData} is a persistent attachment — so a reloaded elite is already complete.
 * That is also why every modifier this mod writes is id-keyed and replace-not-add: if the
 * promotion ever DOES run twice on one entity, it must be a no-op rather than a doubling.
 */
public final class EliteRoller {
	/**
	 * Types that may never be promoted. Tag-driven so a pack can adjust without a code change
	 * — the house convention — and it needs no shipped file to work: an absent tag resolves
	 * empty and every {@code is(tag)} test answers false.
	 */
	public static final TagKey<EntityType<?>> BLACKLIST = TagKey.create(Registries.ENTITY_TYPE,
			Identifier.fromNamespaceAndPath(Escalation.MOD_ID, "elite_blacklist"));

	private static final Identifier HEALTH_MODIFIER = EliteAttributes.id("elite_health");

	private static final Identifier SCALE_MODIFIER = EliteAttributes.id("elite_scale");

	private EliteRoller() {
	}

	/**
	 * The mixin entry point. Everything above this line is engine plumbing; everything below
	 * is shared, single-implementation balance logic that every port target runs unchanged —
	 * the §5a convention ("mixin annotations fork, balance logic never") applied at the system
	 * boundary rather than inside one handler.
	 */
	public static void onFinalizeSpawn(final Mob mob, final ServerLevelAccessor accessor,
			final EntitySpawnReason reason) {
		if (!isRollableReason(reason) || !isEligible(mob)) {
			return;
		}

		ServerLevel level = accessor.getLevel();
		EliteRules rules = EliteRules.active();
		RandomSource random = level.getRandom();
		double score = rules.ignoreProgression()
				? 1.0
				: ScoreSource.active().scoreAt(level, mob.blockPosition());

		if (random.nextDouble() >= Tuning.eliteChance(score, rules.eliteChanceMax())) {
			return;
		}

		Rarity rarity = drawRarity(score, rules, random);

		if (rarity == null) {
			return;
		}

		promote(level, mob, rarity, AffixBudget.roll(mob, rarity, rules, random));
	}

	/** Only the two reasons the natural spawner and chunk generation actually use. */
	private static boolean isRollableReason(final EntitySpawnReason reason) {
		return reason == EntitySpawnReason.NATURAL || reason == EntitySpawnReason.CHUNK_GENERATION;
	}

	private static boolean isEligible(final Mob mob) {
		if (mob.getType().getCategory() != MobCategory.MONSTER) {
			return false;
		}

		if (mob.is(BLACKLIST)) {
			return false;
		}

		// Summons and split copies carry a persistent never-promote flag, and an entity that
		// is somehow already an elite is not promoted twice.
		EliteData existing = EliteMob.data(mob);
		return existing == null || (!existing.noElite() && !existing.isElite());
	}

	/**
	 * §5.1's weight table, drawn once the elite roll has passed.
	 *
	 * <p>Tiers above the preset's ceiling are ZEROED rather than redrawn. Rejecting and
	 * retrying would make a preset that locks Legendary produce fewer elites overall; zeroing
	 * makes it produce the same number, redistributed downward — which is what "Legendary
	 * locked" should mean.
	 *
	 * @return the drawn rarity, or {@code null} if every weight is zero, which only a preset
	 *         with a ceiling below tier 1 could produce
	 */
	private static Rarity drawRarity(final double score, final EliteRules rules, final RandomSource random) {
		int count = Rarity.count();
		double[] weights = new double[count];
		double total = 0.0;

		for (int i = 0; i < count; i++) {
			Rarity rarity = Rarity.at(i);
			double weight = rarity.tier() > rules.maxRarityTier() ? 0.0 : weightOf(rarity, score, rules);
			weights[i] = weight;
			total += weight;
		}

		if (total <= 0.0) {
			return null;
		}

		double roll = random.nextDouble() * total;

		for (int i = 0; i < count; i++) {
			roll -= weights[i];

			if (roll < 0.0) {
				return Rarity.at(i);
			}
		}

		// Floating-point residue only; the last non-zero tier is the honest answer.
		for (int i = count - 1; i >= 0; i--) {
			if (weights[i] > 0.0) {
				return Rarity.at(i);
			}
		}

		return null;
	}

	private static double weightOf(final Rarity rarity, final double score, final EliteRules rules) {
		switch (rarity) {
			case UNCOMMON:
				return Tuning.rarityWeightUncommon(score);
			case RARE:
				return Tuning.rarityWeightRare(score);
			case EPIC:
				return Tuning.rarityWeightEpic(score);
			case LEGENDARY:
			default:
				return Tuning.rarityWeightLegendary(score, rules.legendaryWeightFloor());
		}
	}

	/**
	 * Writes the promotion: state first, then the rarity's three stat modifiers, then every
	 * affix's own application, then the gear, then the name tag.
	 *
	 * <p>State goes first because an affix's {@code onApply} may read it — and because if
	 * anything below throws, the entity is at least consistently marked rather than
	 * half-promoted with no record of it.
	 *
	 * <p><b>The name is generated before the state is written</b>, because the state is where
	 * it lives: {@code EliteNames} is a pure function of the mob's UUID, its rarity and the
	 * drawn affix ids, so it needs nothing this method has not already settled, and computing
	 * it first means there is no second write to the store.
	 *
	 * <p><b>Gear comes after the affixes and before the name tag.</b> After, because an affix
	 * may set an attribute the gear's own modifiers stack with and the order those are applied
	 * in is vanilla's business, not this method's; before the name tag only because the name
	 * tag is the last visible thing and reads better as the closing line.
	 */
	public static void promote(final ServerLevel level, final Mob mob, final Rarity rarity,
			final List<Affix> affixes) {
		List<String> ids = new ArrayList<>(affixes.size());

		for (final Affix affix : affixes) {
			ids.add(affix.id());
		}

		EliteMob.promote(mob, rarity, ids, EliteNames.generate(mob, rarity, ids));

		EliteAttributes.multiply(mob, Attributes.MAX_HEALTH, HEALTH_MODIFIER, rarity.maxHealthMultiplier());

		// The rarity DAMAGE multiplier is deliberately NOT an attribute modifier. ATTACK_DAMAGE
		// governs a melee swing and nothing else, so an attribute here would silently exempt
		// every arrow, potion and fireball from §2.1's own Damage column. It is applied in the
		// funnel instead — see EliteCombat, which is also where §4.4's ordering requires it.

		if (Tuning.SCALE_ENABLED) {
			// The whole of §2.1's resizing. SCALE is syncable, drives getScale(), and
			// triggers refreshDimensions() on change — so this one line moves the model AND
			// the hitbox on every tracking client with no packet, no mixin and no render code.
			EliteAttributes.multiply(mob, Attributes.SCALE, SCALE_MODIFIER, rarity.scaleMultiplier());
		}

		// AFTER the health modifier: raising MAX_HEALTH does not raise current health, so an
		// un-topped-up Legendary would spawn at 29% of its bar.
		mob.setHealth(mob.getMaxHealth());

		for (final Affix affix : affixes) {
			affix.onApply(level, mob, rarity);
		}

		// Only into slots vanilla left empty, and only through setItemSlot/setDropChance — so
		// vanilla's own rare-drop roll and its damaged-on-drop path apply untouched. See
		// EliteGear for the measured ordering against a subclass's own equipment work.
		EliteGear.equip(level, mob, rarity, level.getRandom());

		EliteNameTag.apply(mob, rarity, EliteMob.nameOf(mob));
	}
}
