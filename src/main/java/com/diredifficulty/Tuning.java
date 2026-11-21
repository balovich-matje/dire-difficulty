package com.diredifficulty;

/**
 * ALL balance constants and curves for this mod. One file, no conditionals, every node.
 *
 * <p>Empty on purpose — this is the scaffold. The house rule it exists to enforce is that
 * nothing else in the tree ever carries a balance number: rarity multipliers, affix costs,
 * loot amounts, the progression curves, the two hard guarantees' thresholds and every spawn
 * knob's default all land here ({@code docs/DESIGN.md} §2.1, §4.4, §5.1). The sibling repos
 * hold the same rule, and the property it buys is measurable: a one-character edit here must
 * move {@code Tuning.class} and nothing else in the built jar.
 *
 * <p>Two constraints on anything added below:
 *
 * <ul>
 * <li><b>Java 17 is the ceiling</b>, not the 25 this node compiles at. The port's 1.20.1
 *     node compiles this same file at source level 17. Records are fine; pattern-matching
 *     {@code switch}, sealed types and {@code Math.clamp} are not.</li>
 * <li><b>No conditionals of any kind</b> — no {@code //?} directives, no per-version
 *     branches. A version that needs a different number needs a different constant, not a
 *     fork of this file.</li>
 * </ul>
 */
public final class Tuning {
	private Tuning() {
	}

	// =================================================================================
	// PROGRESSION SCORE — docs/DESIGN.md §5.1
	//
	// Everything below is a DEFAULT that the config file may override; the config class
	// carries no numeric literal of its own, it initialises its fields from here. That is
	// what keeps the house rule literally true rather than approximately true.
	// =================================================================================

	/**
	 * Radius, in blocks, of the player search that turns per-player scores into the score at
	 * a position. 128 matches vanilla's own {@code NaturalSpawner.SPAWN_DISTANCE_BLOCK}, so a
	 * spawn that vanilla considered "near a player" is scored by that same player.
	 */
	// ---- Progression stage (2026-11-21). Four vanilla statistics, each eased-out to its
	// target and weighted; the weights sum to 1, so no single factor can carry the stage
	// past its own weight, and every factor shows diminishing returns on the way there.
	// The world difficulty then caps the result.

	public static final double STAGE_WEIGHT_ADVANCEMENTS = 0.35;
	public static final double STAGE_WEIGHT_KILLS = 0.25;
	public static final double STAGE_WEIGHT_DISTANCE = 0.20;
	public static final double STAGE_WEIGHT_SURVIVAL = 0.20;

	/** Completed advancements (recipe unlocks excluded) for that factor to be full. */
	public static final int STAGE_TARGET_ADVANCEMENTS = 60;
	/** Hostile mobs killed for that factor to be full. */
	public static final int STAGE_TARGET_KILLS = 1000;
	/** Blocks travelled (walking, swimming, riding, gliding — not creative flight). */
	public static final double STAGE_TARGET_DISTANCE = 100_000.0;
	/** Ticks since the last death; 216000 = 3 hours alive. A death resets this factor. */
	public static final int STAGE_TARGET_SURVIVAL_TICKS = 216_000;

	public static final double STAGE_CAP_PEACEFUL = 0.0;
	public static final double STAGE_CAP_EASY = 0.25;
	public static final double STAGE_CAP_NORMAL = 0.5;
	public static final double STAGE_CAP_HARD = 1.0;

	/** Spawn multipliers reached at stage 1 (config defaults; stage 0 is vanilla). */
	public static final double STAGE_SPAWN_FREQUENCY_MAX = 2.0;
	public static final double STAGE_MOB_CAP_MAX = 1.75;
	public static final double STAGE_DESPAWN_DISTANCE_MAX = 1.6;

	/** Ease-out: fast early, slower near the target, exactly 1 at the target. */
	public static double easeOut(final double value, final double target) {
		double c = clamp01(value / Math.max(1.0E-9, target));
		return 1.0 - (1.0 - c) * (1.0 - c);
	}

	/** Linear from 1.0 (vanilla) at stage 0 to {@code atMax} at stage 1. */
	public static double stageLerp(final double stage, final double atMax) {
		return 1.0 + (atMax - 1.0) * clamp01(stage);
	}

	public static final double SCORE_RADIUS = 128.0;

	/**
	 * Ticks between per-player score recomputations. Counting advancements and summing fifteen
	 * skills must never run per spawn attempt — every read is served from the cache.
	 */
	public static final int SCORE_REFRESH_TICKS = 100;

	// ---- Elite chance curve. Slow early, ramps late: a fresh world should feel almost vanilla.

	public static final double ELITE_CHANCE_MIN = 0.01;

	/** The curve's published ceiling. Each preset stamps its own max over this. */
	public static final double ELITE_CHANCE_MAX = 0.35;

	/** Highest rarity the normal roll can draw (Legendary). Mythical has its own spawn path. */
	public static final int MAX_RARITY_TIER = 4;

	public static final double ELITE_CHANCE_EXP = 1.6;

	/**
	 * Chance that a natural monster spawn becomes an elite, at {@code score} in 0..1.
	 *
	 * <p>{@code chanceMax} is the config/preset ceiling rather than {@link #ELITE_CHANCE_MAX}
	 * so that one curve serves all four presets — there is no second ladder to disagree with
	 * this one.
	 */
	public static double eliteChance(final double score, final double chanceMax) {
		double s = clamp01(score);
		double max = Math.max(ELITE_CHANCE_MIN, chanceMax);
		return ELITE_CHANCE_MIN + (max - ELITE_CHANCE_MIN) * Math.pow(s, ELITE_CHANCE_EXP);
	}

	// ---- Rarity weights, drawn once the elite roll has passed.
	//
	// Legendary is quadratic on purpose: genuinely absent early, genuinely common at the top.
	// At score 0 the weights are 100/20/4/0 (~81% uncommon, no legendaries); at score 1 they
	// are 40/55/34/12 (legendaries ~8.5% of elites).

	public static double rarityWeightUncommon(final double score) {
		return Math.max(0.0, 100.0 - 60.0 * clamp01(score));
	}

	public static double rarityWeightRare(final double score) {
		return 20.0 + 35.0 * clamp01(score);
	}

	public static double rarityWeightEpic(final double score) {
		return 4.0 + 30.0 * clamp01(score);
	}

	/**
	 * @param floor a preset's Legendary weight floor — Hell on Earth uses it so legendaries
	 *              appear from the first night instead of waiting on the quadratic.
	 */
	public static double rarityWeightLegendary(final double score, final double floor) {
		double s = clamp01(score);
		return Math.max(Math.max(0.0, floor), 12.0 * s * s);
	}

	// =================================================================================
	// SPAWN CONTROL — docs/DESIGN.md §5.2
	// =================================================================================


	/**
	 * Hard ceiling on the spawn-frequency multiplier, applied after the preset knob and the
	 * progression bonus have both been folded in. Bounds the number of extra
	 * {@code spawnForChunk} passes per chunk per tick, so no combination of config values can
	 * turn the spawner into an unbounded loop.
	 */
	public static final double SPAWN_FREQUENCY_MAX = 4.0;

	public static final double MOB_CAP_MULTIPLIER_MIN = 0.1;
	public static final double MOB_CAP_MULTIPLIER_MAX = 4.0;

	public static final double DESPAWN_MULTIPLIER_MIN = 0.25;
	public static final double DESPAWN_MULTIPLIER_MAX = 4.0;

	// =================================================================================
	// THE HUNT — preset 4's focus mechanic, docs/DESIGN.md §5.3
	// =================================================================================

	/** Ticks between hunt passes. Nothing teleports; mobs walk, and terrain still works as cover. */
	public static final int HUNT_INTERVAL_TICKS = 40;

	/** Blocks within which an untargeted monster is handed the player as a target. */
	public static final double HUNT_RADIUS = 96.0;

	/**
	 * Blocks beyond which a hunting monster is released and its follow-range modifier removed.
	 * Strictly larger than {@link #HUNT_RADIUS} so a mob at the boundary does not thrash
	 * between acquire and release on alternate passes.
	 */
	public static final double HUNT_RELEASE_RADIUS = 144.0;

	/**
	 * Mobs a single player may have newly assigned per pass. Bounded on purpose: a busy server
	 * must not turn this into an unbounded pathfinding storm.
	 */
	public static final int HUNT_MAX_PER_PLAYER = 12;

	/**
	 * Follow-range floor, in blocks, asserted on a hunting mob. A bare {@code setTarget} at
	 * distance is discarded within a tick or two because the target goal's continue-check
	 * re-evaluates against {@code FOLLOW_RANGE}; this is what makes the hand-out stick.
	 */
	public static final double HUNT_FOLLOW_RANGE = 100.0;

	// =================================================================================
	// THE RARITY LADDER — docs/DESIGN.md §2.1
	//
	// Eight columns, four rows. Every array below is in Rarity declaration order —
	// Uncommon, Rare, Epic, Legendary — and Rarity reads them by ordinal(), which is why
	// that enum carries no number of its own. "More simultaneous affixes with progression"
	// is delivered through rarity and nothing else: one input, one ladder, so the two can
	// never disagree.
	// =================================================================================

	/** Glow / name colour, RGB. Green, blue, purple, orange. */
	public static final int[] RARITY_COLOUR = {0x55FF55, 0x55AAFF, 0xAA55FF, 0xFFAA00, 0xFF3A3A};

	/** Affix slot count — the hard ceiling on how many affixes may be drawn. */
	public static final int[] RARITY_SLOTS = {1, 2, 3, 4, 5};

	/** Affix point budget. Affixes cost 1-3; the roll stops when nothing affordable is left. */
	public static final int[] RARITY_BUDGET = {2, 4, 6, 9, 12};

	/** {@code MAX_HEALTH} multiplier, applied as one ADD_MULTIPLIED_BASE modifier. */
	public static final double[] RARITY_MAX_HEALTH = {1.35, 1.80, 2.50, 3.40, 4.00};

	/** {@code ATTACK_DAMAGE} multiplier. The ONLY flat outgoing multiplier in the whole mod. */
	public static final double[] RARITY_DAMAGE = {1.10, 1.20, 1.32, 1.45, 3.00};

	/**
	 * {@code SCALE} multiplier — model AND hitbox, through vanilla attribute sync alone.
	 *
	 * <p>Design §2.1 records the measured consequence and accepts it: a vanilla zombie is
	 * 1.95 blocks tall, so any visible upscale stops it fitting a 2-block door and elites
	 * path around 2-high gaps instead of following you in. That reads as counterplay.
	 * {@link #SCALE_ENABLED} is the escape hatch for a server that disagrees.
	 */
	public static final double[] RARITY_SCALE = {1.08, 1.15, 1.22, 1.30, 1.45};

	/** Dropped-XP multiplier. Deliberately a much smaller dial than the loot tier. */
	public static final float[] RARITY_XP = {1.25F, 1.60F, 2.10F, 3.00F, 6.00F};

	/** Master switch for the scale modifier. Everything else about a rarity still applies. */
	public static final boolean SCALE_ENABLED = true;

	/** Dropped-XP multiplier for a rarity. */
	public static float eliteXpMultiplier(final int rarityIndex) {
		return RARITY_XP[rarityIndex];
	}

	// =================================================================================
	// THE TWO HARD GUARANTEES — docs/DESIGN.md §4.4
	//
	// The exclusion and class-cap tables are judgement. These are arithmetic, and they
	// hold no matter what rolls. Nothing in the preset block above can reach them.
	// =================================================================================

	// Guarantee A (ELITE_HIT_CEILING, a one-shot clamp on hits against players) was
	// RETIRED 2026-11-21 by user decision: elites are meant to be truly dangerous.

	/**
	 * <b>Guarantee B1.</b> Ceiling on the effective-HP index a drawn affix set may reach,
	 * measured against the base mob. Over it, the audit drops the highest-cost DEFENSE affix
	 * and re-runs. The published tables already pass; the audit is what keeps them passing
	 * after the first retune.
	 */
	public static final double MAX_EHP_INDEX = 6.0;

	/**
	 * <b>Guarantee B2.</b> Ticks for which {@code regenerating} and {@code vampiric} are
	 * suppressed after the elite takes damage from a player. A player who is landing hits
	 * always nets the health bar down.
	 */
	public static final int SUSTAIN_LOCKOUT_TICKS = 80;

	/**
	 * Fraction of the rarity budget one affix class may spend (§4.2 C1 and C2), rounded up.
	 * DEFENSE additionally caps at {@link #MAX_DEFENSE_AFFIXES} regardless of budget, and
	 * that is the rule that actually bites at Legendary.
	 */
	public static final double CLASS_POINT_CAP_FRACTION = 0.5;

	/** §4.2 C1 — an elite can never carry more than two defensive affixes, at any rarity. */
	public static final int MAX_DEFENSE_AFFIXES = 2;

	/** §4.2's {@code ceil(B x 0.5)}, for a budget that a preset may already have raised. */
	public static int classPointCap(final int budget) {
		return (int) Math.ceil(budget * CLASS_POINT_CAP_FRACTION);
	}

	// =================================================================================
	// MYTHICAL — the fifth tier (2026-11-21). A force of nature, not a fight: never drawn
	// by the normal roll (MAX_RARITY_TIER stops at Legendary), only by MythicalSpawns in
	// specific places, only once a nearby player has killed the Ender Dragon or a Wither.
	// =================================================================================

	/** Max health floor after the rarity multiplier. The Warden has 500. */
	public static final double MYTHICAL_MIN_HEALTH = 1000.0;

	/** Chance per eligible natural spawn (Nether biomes, fortress, outpost). */
	public static final double MYTHICAL_CHANCE_NATURAL = 0.004;

	/** Chance per eligible structure-generated mob (bastion brutes, mansion illagers). */
	public static final double MYTHICAL_CHANCE_STRUCTURE = 0.08;

	/** Chance per vindicator/evoker of a raid's final (or bonus) wave. */
	public static final double MYTHICAL_CHANCE_RAID = 0.25;

	/** No second mythical within this many blocks of a living one. */
	public static final double MYTHICAL_EXCLUSION_RADIUS = 128.0;

	/** Per-dimension pause after a natural or raid mythical spawns, ticks (20 min). */
	public static final int MYTHICAL_COOLDOWN_TICKS = 24_000;

	// ---- Dominance: Legendary+ elites sometimes attack a nearby monster of another kind.

	public static final int DOMINANCE_MIN_TIER = 4;
	public static final double DOMINANCE_RADIUS = 10.0;
	/** Chance per check that the elite picks a fight, when a victim is in sight. */
	public static final double DOMINANCE_CHANCE = 0.2;
	public static final int DOMINANCE_CHECK_TICKS = 40;
	/** Pause after each pick, ticks (60 s). */
	public static final int DOMINANCE_COOLDOWN_TICKS = 1200;

	/** Bloodletter: share of melee damage dealt returned as health. */
	public static final float ARTEFACT_LIFESTEAL = 0.2F;

	/** Second Wind: health after the cheated death, and the cooldown (10 min). */
	public static final float ARTEFACT_SECOND_WIND_HEALTH = 4.0F;
	public static final int ARTEFACT_SECOND_WIND_COOLDOWN_TICKS = 12_000;

	// =================================================================================
	// ELITE LOOT — docs/DESIGN.md §2.4
	//
	// EliteLoot holds the SHAPE, this holds the numbers. A whitelist, not a blacklist:
	// that is the enforcement mechanism for "usable resources only, no building blocks,
	// no decorative clutter", and it is why nothing can leak in. Looting deliberately
	// does NOT multiply any of this — the rarity IS the reward multiplier.
	// =================================================================================

	/*
	 * ONE material per kill, picked by weight from the tier's column, 1-2 of it. Elites
	 * must never make mining pointless (user decision 2026-11-21): uncommon drops no
	 * material, rare only iron, epic iron or gold, legendary any of the four.
	 * Columns: uncommon, rare, epic, legendary.
	 */
	public static final int[] LOOT_WEIGHT_IRON = {0, 1, 3, 4, 4};
	public static final int[] LOOT_WEIGHT_GOLD = {0, 0, 2, 3, 3};
	public static final int[] LOOT_WEIGHT_EMERALD = {0, 0, 0, 2, 2};
	public static final int[] LOOT_WEIGHT_DIAMOND = {0, 0, 0, 1, 1};

	public static final int[] LOOT_MATERIAL_MIN = {0, 1, 1, 1, 1};
	public static final int[] LOOT_MATERIAL_MAX = {0, 2, 2, 2, 2};

	/** Enchanted books: legendary only, one per kill. */
	public static final int[] LOOT_BOOK_MIN = {0, 0, 0, 1, 1};
	public static final int[] LOOT_BOOK_MAX = {0, 0, 0, 1, 1};

	/**
	 * Enchantment cost handed to vanilla's own book roller, per rarity. These are the
	 * numbers an enchanting table would use at that level, so a T4 book reads as a
	 * near-max table roll rather than as a modded item.
	 */
	public static final int[] LOOT_BOOK_COST_MIN = {5, 12, 20, 28, 30};
	public static final int[] LOOT_BOOK_COST_MAX = {12, 20, 28, 30, 30};

	// =================================================================================
	// ELITE NAMES — the fantasy-name generator's shape dials.
	//
	// EliteNames holds the WORD BANKS; this holds every number the generator reads. Same
	// split as EliteLoot's and Preset's: data over there, tuning here. A name is
	// "<Fantasy Name> (<rarity> <mob type>)" — the fantasy half is a literal string
	// generated once at promotion and stored in EliteData, the parenthesised half is
	// rebuilt from lang keys at display time so it translates.
	// =================================================================================

	/**
	 * Relative weights of the three word sources when a name's pool is assembled: the elite's
	 * own drawn affixes, its mob family, and the generic bank. Affix-heaviest on purpose —
	 * "semi-related to the affixes" is the requirement, so a Vampiric elite should usually
	 * read as one without the name simply spelling the affix list out a second time.
	 */
	public static final int NAME_AFFIX_WEIGHT = 3;

	public static final int NAME_FAMILY_WEIGHT = 2;

	public static final int NAME_GENERIC_WEIGHT = 1;

	/**
	 * Chance a name comes out as a single compound word ({@code Grave} + {@code bane} =
	 * "Gravebane") rather than as two words ("Grave Lord"). Rolled against the same uniform as
	 * {@link #NAME_DOMAIN_CHANCE}, so the two shapes and the plain one always sum to 1.
	 */
	public static final double NAME_COMPOUND_CHANCE = 0.32;

	/** Chance a name takes the grander "&lt;Title&gt; of &lt;Domain&gt;" shape. */
	public static final double NAME_DOMAIN_CHANCE = 0.18;

	/** Lowest rarity TIER (1..4) the "of &lt;Domain&gt;" shape may roll on. 2 is Rare. */
	public static final int NAME_DOMAIN_FROM_TIER = 2;

	/**
	 * Chance a Legendary name carries a trailing ", the &lt;Epithet&gt;". Rarity-scaled
	 * grandeur, and the only place it is applied: a two-part title on a common spawn stops
	 * reading as an event.
	 */
	public static final double NAME_EPITHET_CHANCE_LEGENDARY = 0.45;

	/** Mythicals always carry an epithet. */
	public static final double NAME_EPITHET_CHANCE_MYTHICAL = 1.0;

	/** The same, at Epic. Uncommon and Rare never carry one. */
	public static final double NAME_EPITHET_CHANCE_EPIC = 0.12;

	// =================================================================================
	// ELITE GEAR — what an elite spawns wearing, and what may drop off it.
	//
	// EliteGear holds the SHAPE and the item tables; this holds every number. All of it is
	// delivered through vanilla's own setItemSlot / setDropChance, so vanilla's rare-drop
	// roll and its damaged-on-drop path apply unchanged — see EliteGear's class javadoc for
	// the two lines of vanilla source that guarantee it.
	//
	// The MATERIAL LADDER, one index shared by both halves of a set:
	//
	//   0 leather / wooden   1 copper   2 gold   3 chainmail / stone
	//   4 iron               5 diamond  6 netherite
	//
	// Indices 0..5 are vanilla's OWN numbering from Mob.getEquipmentForSlot(slot, type), so
	// the armour half is vanilla's table rather than a copy of it. Index 6 is netherite,
	// which that method returns null for; EliteGear supplies those four pieces itself.
	// =================================================================================

	/** Chance a melee elite with an empty main hand is given a weapon, per rarity. */
	public static final double[] GEAR_WEAPON_CHANCE = {0.55, 0.75, 0.90, 1.00, 1.00};

	/** Chance EACH empty armour slot is filled, per rarity. Rolled independently per slot. */
	public static final double[] GEAR_ARMOR_PIECE_CHANCE = {0.30, 0.50, 0.70, 0.90, 1.00};

	/**
	 * Weapon material ladder per rarity, as a weighted draw: a repeated index is a heavier
	 * one. Legendary is netherite and only netherite — the design's own example.
	 */
	public static final int[][] GEAR_WEAPON_TIERS = {
		{2, 3, 4},          // Uncommon:  gold / stone / iron
		{3, 4, 4, 5},       // Rare:      stone / iron / iron / diamond
		{4, 5, 5},          // Epic:      iron / diamond / diamond
		{6},                // Legendary: netherite
		{6},                // Mythical:  netherite
	};

	/** Armour material ladder per rarity. Netherite is possible at Legendary, never certain. */
	public static final int[][] GEAR_ARMOR_TIERS = {
		{1, 2, 3},          // Uncommon:  copper / gold / chainmail
		{2, 3, 4, 4},       // Rare:      gold / chainmail / iron / iron
		{4, 4, 5},          // Epic:      iron / iron / diamond
		{5, 5, 6},          // Legendary: diamond / diamond / netherite
		{6},                // Mythical:  netherite
	};

	/** Chance a drawn weapon is an axe rather than a sword. Same material either way. */
	public static final double GEAR_AXE_CHANCE = 0.30;

	/** Chance the weapon is enchanted, per rarity. Legendary always is. */
	public static final double[] GEAR_WEAPON_ENCHANT_CHANCE = {0.0, 0.15, 0.50, 1.00, 1.00};

	/** Chance an armour piece is enchanted, per rarity. Deliberately far below the weapon's. */
	public static final double[] GEAR_ARMOR_ENCHANT_CHANCE = {0.0, 0.05, 0.20, 0.45, 1.00};

	/**
	 * Enchanting-table cost handed to vanilla's own roller, per rarity — the numbers a table
	 * would use at that level, so a Legendary's weapon reads as a near-max table roll.
	 */
	public static final int[] GEAR_ENCHANT_COST_MIN = {1, 5, 14, 22, 30};

	public static final int[] GEAR_ENCHANT_COST_MAX = {5, 14, 24, 30, 30};

	/**
	 * Drop chance for gear this mod equipped, per rarity. Vanilla's own default is
	 * {@code DropChances.DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F}; the ladder starts there and
	 * TAPERS as the gear gets better, because the same 8.5% on an enchanted netherite sword is
	 * a different economy from 8.5% on an iron one.
	 */
	public static final double[] GEAR_DROP_CHANCE = {0.085, 0.075, 0.060, 0.050, 0.050};

	/**
	 * Hard ceiling on any drop chance this mod writes.
	 *
	 * <p>Not a taste dial. {@code DropChances.PRESERVE_ITEM_DROP_CHANCE_THRESHOLD} is
	 * {@code 1.0F}, and a slot above it is <b>preserved</b>: vanilla then drops it every time
	 * AND skips the {@code setDamageValue} line that is the whole "no full-durability
	 * windfalls" rule. Staying below this is what keeps that rule structural.
	 */
	public static final double GEAR_DROP_CHANCE_MAX = 0.95;

	// =================================================================================
	// AFFIX CATALOGUE — docs/DESIGN.md §3.1
	//
	// One block per affix: its point cost first, then its own numbers. The affix classes
	// in affix/impl/ carry NO literal at all; each reads this block. Retuning one affix is
	// therefore an edit to one contiguous block here and nothing else.
	//
	// The *_EHP_FACTOR entries feed Guarantee B1's audit. Design §4.4 names four factors
	// (hp x armour x projectile x sustain); they collapse into one product here because
	// every affix contributes to exactly one of them, so the arithmetic is identical and
	// there is one number per affix to retune instead of four columns to keep aligned.
	// =================================================================================

	public static final int COST_SWIFT = 1;
	public static final double SWIFT_SPEED_MULTIPLIER = 1.35;

	public static final int COST_RELENTLESS = 1;
	public static final double RELENTLESS_FOLLOW_RANGE_MULTIPLIER = 2.5;

	public static final int COST_WARLORD = 2;
	public static final double WARLORD_RADIUS = 8.0;
	public static final double WARLORD_DAMAGE_BONUS = 0.20;
	public static final double WARLORD_SPEED_BONUS = 0.15;

	public static final int COST_SUMMONER = 3;
	public static final int SUMMONER_INTERVAL_TICKS = 240;
	public static final int SUMMONER_MIN_PER_PULSE = 1;
	public static final int SUMMONER_MAX_PER_PULSE = 2;
	public static final int SUMMONER_ALIVE_CAP = 4;
	public static final double SUMMONER_LEASH_RADIUS = 16.0;

	public static final int COST_CHILLING = 2;
	public static final double CHILLING_RADIUS = 5.0;
	public static final int CHILLING_AMPLIFIER = 0;
	public static final int CHILLING_DURATION_TICKS = 40;

	public static final int COST_SEARING = 2;
	public static final double SEARING_RADIUS = 4.0;
	/** Damage per second. A pressure aura, deliberately with NO knockback on the tick. */
	public static final float SEARING_DAMAGE_PER_SECOND = 1.0F;

	public static final int COST_SHIELDBREAKER = 2;
	public static final float SHIELDBREAKER_DISABLE_SECONDS = 3.0F;

	public static final int COST_SUNWALKER = 1;
	public static final double SUNWALKER_EHP_FACTOR = 1.02;

	public static final int COST_ESCAPIST = 2;
	public static final double ESCAPIST_HEALTH_TRIGGER = 0.25;
	public static final double ESCAPIST_HEAL_FRACTION = 0.40;
	public static final double ESCAPIST_MIN_DISTANCE = 12.0;
	public static final double ESCAPIST_MAX_DISTANCE = 20.0;
	public static final int ESCAPIST_TELEPORT_ATTEMPTS = 16;
	public static final double ESCAPIST_EHP_FACTOR = 1.40;

	public static final int COST_WARDED = 2;
	public static final float WARDED_PROJECTILE_REDUCTION = 0.50F;
	public static final double WARDED_EHP_FACTOR = 1.30;

	public static final int COST_ROOTED = 2;
	public static final double ROOTED_KNOCKBACK_RESISTANCE = 1.0;
	public static final double ROOTED_EHP_FACTOR = 1.10;

	public static final int COST_VAMPIRIC = 2;
	public static final float VAMPIRIC_LIFESTEAL = 0.35F;
	public static final double VAMPIRIC_EHP_FACTOR = 1.20;

	public static final int COST_FROSTBOUND = 2;
	public static final int FROSTBOUND_AMPLIFIER = 1;
	public static final int FROSTBOUND_DURATION_TICKS = 80;
	public static final int FROSTBOUND_FREEZE_TICKS = 60;

	public static final int COST_ENFEEBLING = 1;
	public static final int ENFEEBLING_AMPLIFIER = 0;
	public static final int ENFEEBLING_DURATION_TICKS = 120;

	public static final int COST_BLINKSTRIKE = 2;
	public static final int BLINKSTRIKE_INTERVAL_TICKS = 120;
	public static final double BLINKSTRIKE_MIN_RANGE = 6.0;
	public static final double BLINKSTRIKE_BEHIND_OFFSET = 1.4;

	public static final int COST_STALKER = 1;
	public static final int STALKER_MARK_TICKS = 200;

	public static final int COST_THORNED = 2;
	public static final float THORNED_REFLECT_FRACTION = 0.25F;

	public static final int COST_VOLATILE = 2;
	/** Explosion power. Block damage is OFF unconditionally — no elite ever grieves terrain. */
	public static final float VOLATILE_POWER = 2.0F;

	public static final int COST_SPLITTING = 3;
	public static final int SPLITTING_COPIES = 2;
	public static final float SPLITTING_HEALTH_FRACTION = 0.40F;
	/** One generation only. A copy carries generation 1 and can never split again. */
	public static final int SPLITTING_MAX_GENERATION = 1;
	public static final double SPLITTING_MAX_WIDTH = 1.2;
	public static final double SPLITTING_MAX_HEIGHT = 2.2;

	public static final int COST_NOXIOUS = 1;
	public static final float NOXIOUS_RADIUS = 4.0F;
	public static final int NOXIOUS_DURATION_TICKS = 160;
	public static final int NOXIOUS_POISON_TICKS = 100;
	public static final int NOXIOUS_POISON_AMPLIFIER = 0;

	public static final int COST_SUNDERING = 2;
	public static final double SUNDERING_ARMOR_PER_STACK = -2.0;
	public static final int SUNDERING_MAX_STACKS = 4;
	public static final int SUNDERING_DURATION_TICKS = 160;

	public static final int COST_VOLLEY = 3;
	public static final int VOLLEY_PROJECTILES = 3;
	/** A witch throws two potions rather than three — a potion volley is far ranker. */
	public static final int VOLLEY_PROJECTILES_POTION = 2;
	/**
	 * Ticks between volleys. Design §3.1 shapes this affix as "3 projectiles, attack cooldown
	 * x1.5, so sustained DPS rises much less than burst does". A ranged mob's cadence lives
	 * inside its own goal and is not an attribute, so the SHAPE is delivered the other way
	 * round and to the same end: the mob keeps its ordinary attack rate, and the extra shots
	 * arrive as a periodic burst on this interval rather than on every shot. Burst rises,
	 * sustained damage barely moves — which is the property the design is buying.
	 */
	public static final int VOLLEY_INTERVAL_TICKS = 60;

	public static final int COST_REGENERATING = 3;
	public static final double REGENERATING_FRACTION_PER_SECOND = 0.02;
	public static final double REGENERATING_EHP_FACTOR = 1.45;

	public static final int COST_BULWARK = 2;
	public static final double BULWARK_ARMOR = 6.0;
	public static final double BULWARK_ARMOR_TOUGHNESS = 4.0;
	public static final double BULWARK_EHP_FACTOR = 1.35;

	public static final int COST_QUAKING = 2;
	public static final int QUAKING_INTERVAL_TICKS = 160;
	public static final int QUAKING_WINDUP_TICKS = 10;
	public static final double QUAKING_RADIUS = 4.0;
	public static final float QUAKING_DAMAGE = 3.0F;
	public static final double QUAKING_KNOCKBACK = 0.8;

	// =================================================================================
	// ELITE TICKER CADENCE
	//
	// The elite ticker is one END_SERVER_TICK listener in the sibling repos' *Ticker
	// shape, with the work bounded per pass. Nothing here runs per entity per tick.
	// =================================================================================

	/** Ticks between elite ticker passes. Every affix interval is a multiple of this. */
	public static final int ELITE_TICK_INTERVAL = 10;

	/**
	 * How near a player an elite has to be to tick at all. Auras reach five blocks and a
	 * summoner needs a target, so an elite nobody can see has nothing to do — this is what
	 * bounds the pass instead of scanning every loaded entity on a busy server.
	 */
	public static final double ELITE_TICK_RADIUS = 48.0;

	// =================================================================================
	// CLIENT VISUALS — the rarity glow, the aura particles and the elite plate.
	//
	// Balance numbers and VISUAL numbers both live in this file (user instruction,
	// 2026-08-05). Nothing below is read by the server; the whole block is read from
	// `src/client`. It lives here anyway because the house rule is about WHERE a constant
	// lives, not about which logical side reads it — one file to retune, whether the knob
	// moves damage or pixels. `Rarity` exposes the per-rarity rows the same way it exposes
	// the stat columns, so no client class indexes these arrays directly.
	//
	// This block replaces the client-side `EliteGlowLayer.GLOW_ALPHA` constant that the
	// first iteration kept in the render class. That single flat 0x66 is exactly what the
	// first in-game look reported: a Legendary zombie "simply looked a bit yellow", because
	// a 40%-alpha tint modulated by the mob's own dark skin reads as a wash, not a glow.
	// =================================================================================

	// ---- The rarity aura (rework 2026-11-21, second in-game look). The user read the
	// old skin sheen + inflated shell as "a tinted copy of the texture, not bright". What
	// was asked for instead: a glow AROUND the mob that also lights the mob and the ground
	// near it, so an elite stands out in a crowd. Three parts, all client-side, all
	// depth-tested (walls still hide every one of them):
	//   1. the mob itself is lit as if it carried a light (block light raised on the state);
	//   2. a soft additive disc behind the mob, facing the camera, that the body occludes
	//      in its own silhouette, so it reads as light spilling out around the edges;
	//   3. a soft additive pool projected onto the tops of the blocks around its feet.

	/** Block light 0..15 the elite itself is drawn with, per rarity (never darker than real light). */
	public static final int[] AURA_MOB_LIGHT = {10, 12, 14, 15, 15};

	/** Centre alpha 0..255 of the aura disc, per rarity. Additive, so this is brightness. */
	public static final int[] AURA_ALPHA = {0x70, 0x90, 0xB8, 0xE0, 0xFF};

	/**
	 * Aura disc radius as a multiple of the mob's own half-height plus
	 * {@link #AURA_RADIUS_PAD}, per rarity.
	 */
	public static final double[] AURA_RADIUS_SCALE = {1.35, 1.5, 1.7, 1.95, 2.3};

	/** Blocks added to the half-height before scaling, so small mobs still get a visible aura. */
	public static final double AURA_RADIUS_PAD = 0.35;

	/**
	 * Height above the feet, in blocks, over which the aura disc fades in. At feet level it
	 * is fully transparent, so the flat disc never draws a hard line where it meets the floor.
	 */
	public static final double AURA_GROUND_FADE = 0.7;

	/**
	 * Distance from any nearby block, in blocks, over which the aura disc fades in. Touching a
	 * wall, step or slope it is fully transparent, so it never cuts a hard line into one.
	 */
	public static final double AURA_WALL_FADE = 0.6;

	// ------------------------------------------------------------------ elite voice

	/**
	 * Pitch multiplier on every sound an elite makes, per rarity. Minecraft plays a lower
	 * pitch slower too, so this is "deeper and slower" in one number. The client clamps the
	 * final pitch at 0.5, which the creeper's fuse hiss already sits on.
	 */
	public static final float[] ELITE_VOICE_PITCH = {0.85F, 0.75F, 0.67F, 0.6F, 0.5F};

	/**
	 * Volume multiplier on every sound an elite makes, per rarity. Above 1.0 Minecraft does
	 * not get louder up close; it carries farther (hearing range is 16 blocks times volume).
	 */
	public static final float[] ELITE_VOICE_VOLUME = {1.25F, 1.5F, 2.0F, 2.5F, 3.5F};

	/**
	 * Footstep volume multiplier per rarity. Steps start at 0.15; above 1.0 they carry
	 * farther (Mythical 0.15 x 8 = 1.2, heard from ~19 blocks).
	 */
	public static final float[] ELITE_STEP_VOLUME = {1.5F, 2.0F, 3.0F, 5.0F, 8.0F};

	/**
	 * Volume of the low "weight" layer played under each footstep, per rarity; 0 = none.
	 * Minecraft has no EQ, so bass comes from layering a heavy step pitched down.
	 */
	public static final float[] ELITE_STEP_WEIGHT_VOLUME = {0.0F, 0.0F, 0.12F, 0.25F, 0.6F};

	public static final float ELITE_STEP_WEIGHT_PITCH = 0.55F;

	/** Ground light pool radius in blocks, per rarity. */
	public static final double[] AURA_POOL_RADIUS = {2.0, 2.5, 3.0, 3.75, 4.5};

	/** Centre alpha 0..255 of the ground pool, per rarity. */
	public static final int[] AURA_POOL_ALPHA = {0x48, 0x60, 0x7C, 0x9C, 0xC0};

	/** Elites farther than this from the camera skip the ground pool (block lookups per frame). */
	public static final double AURA_POOL_MAX_DISTANCE = 48.0;

	/**
	 * Peak swing of the pulse either side of a rarity's alpha, as a fraction of it.
	 * {@code 0.0} freezes the aura.
	 */
	public static final double GLOW_PULSE_AMPLITUDE = 0.18;

	/** Ticks per full pulse cycle. 50 is one slow breath every two and a half seconds. */
	public static final double GLOW_PULSE_PERIOD_TICKS = 50.0;

	/**
	 * Pulse multiplier on an aura alpha at an entity's own age in ticks. Per-entity phase,
	 * so a pack breathes out of step instead of strobing together.
	 */
	public static double glowPulse(final double ageInTicks) {
		return 1.0 + GLOW_PULSE_AMPLITUDE
				* Math.sin(ageInTicks * (2.0 * Math.PI / GLOW_PULSE_PERIOD_TICKS));
	}

	/** An alpha after the pulse, clamped into 0..255. */
	public static int glowAlpha(final int baseAlpha, final double pulse) {
		return clampInt((int) Math.round(baseAlpha * pulse), 0, 255);
	}

	// ---- Rarity-coloured aura motes, Epic and up. Client-side particles only: nothing is
	// spawned on the server, nothing crosses the wire and nothing is authoritative.

	/** Lowest rarity TIER (1..4) that emits motes. 3 is Epic. */
	public static final int AURA_PARTICLE_MIN_TIER = 3;

	/** Ticks between emission passes. */
	public static final int AURA_PARTICLE_INTERVAL_TICKS = 3;

	/** Blocks from the camera within which an elite emits. */
	public static final double AURA_PARTICLE_RADIUS = 32.0;

	/** Hard cap on motes per pass, so a horde cannot become a fog bank. */
	public static final int AURA_PARTICLE_MAX_PER_PASS = 16;

	/** Chance an eligible elite emits on a given pass. */
	public static final double AURA_PARTICLE_CHANCE = 0.55;

	/**
	 * Initial Y velocity of a mote — negative, so it drips. Vanilla's own spell particle
	 * carries {@code gravity = -0.1F}, i.e. a slight upward acceleration, so a mote slows
	 * as it falls and hangs rather than raining.
	 */
	public static final double AURA_PARTICLE_DRIFT_Y = -0.06;

	// ---- The elite plate: a Diablo-style read-out for the elite under the crosshair.
	// Vanilla chrome only — it is drawn with the vanilla boss-bar sprites at the vanilla
	// boss-bar geometry, so there is no custom texture and no custom panel anywhere.

	/** Blocks of crosshair reach. The ray stops at the first block, so walls hide the plate. */
	public static final double PLATE_RAY_DISTANCE = 32.0;

	/** Ticks the plate stays up after the crosshair leaves the elite. 20 = one second. */
	public static final int PLATE_LINGER_TICKS = 20;

	/** Vanilla boss-bar geometry, reused verbatim. */
	public static final int PLATE_BAR_WIDTH = 182;

	public static final int PLATE_BAR_HEIGHT = 5;

	/** Y of the first vanilla boss bar, and of the plate when there are none. */
	public static final int PLATE_FIRST_BAR_Y = 12;

	/** Vertical stride vanilla's boss-bar loop advances by: its own {@code 10 + 9}. */
	public static final int PLATE_BOSS_BAR_STRIDE = 19;

	/** Vanilla's font line height. */
	public static final int PLATE_LINE_HEIGHT = 9;

	/** Gap between the bar and the first affix line. */
	public static final int PLATE_AFFIX_GAP = 3;

	/** Affix-line colour, ARGB. Container grey — the affixes are a caption, not the headline. */
	public static final int PLATE_AFFIX_COLOUR = 0xFFA0A0A0;

	// =================================================================================
	// Shared helpers. Deliberately java.lang only — no Minecraft import reaches this file,
	// which is what lets the port compile it unchanged on every node.
	// =================================================================================

	/** {@code Math.clamp} is 21+; the shared-code ceiling is Java 17. */
	public static double clamp01(final double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}

	public static double clamp(final double value, final double lo, final double hi) {
		return Math.max(lo, Math.min(hi, value));
	}

	public static int clampInt(final int value, final int lo, final int hi) {
		return Math.max(lo, Math.min(hi, value));
	}
}
