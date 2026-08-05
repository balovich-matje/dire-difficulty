package com.escalation;

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

	/** Completed non-recipe advancements that normalise the advancement source to 1.0. */
	public static final int SCORE_ADVANCEMENT_TARGET = 40;

	/**
	 * Summed Skill Proficiencies levels that normalise that source to 1.0. Their ceiling is
	 * 15 skills x level 100 = 1500; 900 means "an average of 60 per skill counts as done",
	 * so the source saturates before the grind does.
	 */
	public static final int SCORE_SKILL_TOTAL_TARGET = 900;

	/** Archetypes' own level cap. Read live from their class when they are installed; this is the fallback. */
	public static final int SCORE_ARCHETYPES_MAX_LEVEL = 60;

	/**
	 * Radius, in blocks, of the player search that turns per-player scores into the score at
	 * a position. 128 matches vanilla's own {@code NaturalSpawner.SPAWN_DISTANCE_BLOCK}, so a
	 * spawn that vanilla considered "near a player" is scored by that same player.
	 */
	public static final double SCORE_RADIUS = 128.0;

	/**
	 * Ticks between per-player score recomputations. Counting advancements and summing fifteen
	 * skills must never run per spawn attempt — every read is served from the cache.
	 */
	public static final int SCORE_REFRESH_TICKS = 100;

	// ---- Elite chance curve. Slow early, ramps late: a fresh world should feel almost vanilla.

	public static final double ELITE_CHANCE_MIN = 0.01;

	/** The curve's published ceiling. Each preset stamps its own max over this. */
	public static final double ELITE_CHANCE_MAX = 0.18;

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

	/** Progression's contribution to the spawn rate. 0.0 = OFF, which is the default. */
	public static final double SPAWN_RATE_MAX_BONUS = 0.0;

	/** {@code 1 + bonus * score}. At the default bonus this is a constant 1.0 and costs nothing. */
	public static double spawnRateMultiplier(final double score, final double maxBonus) {
		return 1.0 + Math.max(0.0, maxBonus) * clamp01(score);
	}

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
	// PRESETS — docs/DESIGN.md §8
	//
	// Preset.java holds the SHAPE (which fields a preset governs, and how selecting one
	// stamps them); every NUMBER a preset stamps lives here. Same split as EliteLoot's.
	// =================================================================================

	// ---- 8.1 Sunny Day: gentler than vanilla; elites are a rare treat, never a threat.

	public static final double PRESET_SUNNY_ELITE_CHANCE_MAX = 0.03;
	public static final int PRESET_SUNNY_MAX_RARITY_TIER = 1;
	public static final double PRESET_SUNNY_LEGENDARY_WEIGHT_FLOOR = 0.0;
	public static final int PRESET_SUNNY_BONUS_BUDGET = 0;
	public static final int PRESET_SUNNY_BONUS_SLOTS = 0;
	public static final int PRESET_SUNNY_BONUS_FROM_TIER = 1;
	public static final boolean PRESET_SUNNY_IGNORE_PROGRESSION = false;
	public static final boolean PRESET_SUNNY_DAY_SPAWNING = false;
	public static final boolean PRESET_SUNNY_DAY_SPAWN_BURN_IMMUNITY = true;
	public static final double PRESET_SUNNY_SPAWN_FREQUENCY = 1.0;
	public static final double PRESET_SUNNY_MOB_CAP = 0.85;
	public static final double PRESET_SUNNY_DESPAWN_DISTANCE = 0.85;
	public static final boolean PRESET_SUNNY_THE_HUNT = false;

	// ---- 8.2 Gathering Clouds: the intended default. Vanilla, plus an elite layer that grows.

	public static final double PRESET_CLOUDS_ELITE_CHANCE_MAX = 0.10;
	public static final int PRESET_CLOUDS_MAX_RARITY_TIER = 3;
	public static final double PRESET_CLOUDS_LEGENDARY_WEIGHT_FLOOR = 0.0;
	public static final int PRESET_CLOUDS_BONUS_BUDGET = 0;
	public static final int PRESET_CLOUDS_BONUS_SLOTS = 0;
	public static final int PRESET_CLOUDS_BONUS_FROM_TIER = 1;
	public static final boolean PRESET_CLOUDS_IGNORE_PROGRESSION = false;
	public static final boolean PRESET_CLOUDS_DAY_SPAWNING = false;
	public static final boolean PRESET_CLOUDS_DAY_SPAWN_BURN_IMMUNITY = true;
	public static final double PRESET_CLOUDS_SPAWN_FREQUENCY = 1.0;
	public static final double PRESET_CLOUDS_MOB_CAP = 1.0;
	public static final double PRESET_CLOUDS_DESPAWN_DISTANCE = 1.0;
	public static final boolean PRESET_CLOUDS_THE_HUNT = false;

	// ---- 8.3 Storm Front: for a party with an Archetypes build online. Elites are the content.

	public static final double PRESET_STORM_ELITE_CHANCE_MAX = 0.22;
	public static final int PRESET_STORM_MAX_RARITY_TIER = 4;
	public static final double PRESET_STORM_LEGENDARY_WEIGHT_FLOOR = 0.0;
	public static final int PRESET_STORM_BONUS_BUDGET = 2;
	public static final int PRESET_STORM_BONUS_SLOTS = 1;
	/** Epic and Legendary only — 3 is the Epic tier. */
	public static final int PRESET_STORM_BONUS_FROM_TIER = 3;
	public static final boolean PRESET_STORM_IGNORE_PROGRESSION = false;
	public static final boolean PRESET_STORM_DAY_SPAWNING = false;
	public static final boolean PRESET_STORM_DAY_SPAWN_BURN_IMMUNITY = true;
	public static final double PRESET_STORM_SPAWN_FREQUENCY = 1.5;
	public static final double PRESET_STORM_MOB_CAP = 1.35;
	public static final double PRESET_STORM_DESPAWN_DISTANCE = 1.25;
	public static final boolean PRESET_STORM_THE_HUNT = false;

	// ---- 8.4 Hell on Earth: progression ignored; the world is already at maximum.
	//
	// Guarantees A and B still hold here. The ceiling and the EHP audit are NOT preset knobs
	// and nothing in this block can reach them.

	public static final double PRESET_HELL_ELITE_CHANCE_MAX = 0.35;
	public static final int PRESET_HELL_MAX_RARITY_TIER = 4;
	/** Legendaries appear from the first night instead of waiting on the quadratic. */
	public static final double PRESET_HELL_LEGENDARY_WEIGHT_FLOOR = 8.0;
	public static final int PRESET_HELL_BONUS_BUDGET = 3;
	public static final int PRESET_HELL_BONUS_SLOTS = 1;
	public static final int PRESET_HELL_BONUS_FROM_TIER = 1;
	public static final boolean PRESET_HELL_IGNORE_PROGRESSION = true;
	public static final boolean PRESET_HELL_DAY_SPAWNING = true;
	public static final boolean PRESET_HELL_DAY_SPAWN_BURN_IMMUNITY = true;
	public static final double PRESET_HELL_SPAWN_FREQUENCY = 2.0;
	public static final double PRESET_HELL_MOB_CAP = 1.75;
	public static final double PRESET_HELL_DESPAWN_DISTANCE = 1.6;
	public static final boolean PRESET_HELL_THE_HUNT = true;

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
	public static final int[] RARITY_COLOUR = {0x55FF55, 0x55AAFF, 0xAA55FF, 0xFFAA00};

	/** Affix slot count — the hard ceiling on how many affixes may be drawn. */
	public static final int[] RARITY_SLOTS = {1, 2, 3, 4};

	/** Affix point budget. Affixes cost 1-3; the roll stops when nothing affordable is left. */
	public static final int[] RARITY_BUDGET = {2, 4, 6, 9};

	/** {@code MAX_HEALTH} multiplier, applied as one ADD_MULTIPLIED_BASE modifier. */
	public static final double[] RARITY_MAX_HEALTH = {1.35, 1.80, 2.50, 3.40};

	/** {@code ATTACK_DAMAGE} multiplier. The ONLY flat outgoing multiplier in the whole mod. */
	public static final double[] RARITY_DAMAGE = {1.10, 1.20, 1.32, 1.45};

	/**
	 * {@code SCALE} multiplier — model AND hitbox, through vanilla attribute sync alone.
	 *
	 * <p>Design §2.1 records the measured consequence and accepts it: a vanilla zombie is
	 * 1.95 blocks tall, so any visible upscale stops it fitting a 2-block door and elites
	 * path around 2-high gaps instead of following you in. That reads as counterplay.
	 * {@link #SCALE_ENABLED} is the escape hatch for a server that disagrees.
	 */
	public static final double[] RARITY_SCALE = {1.08, 1.15, 1.22, 1.30};

	/** Dropped-XP multiplier. Deliberately a much smaller dial than the loot tier. */
	public static final float[] RARITY_XP = {1.25F, 1.60F, 2.10F, 3.00F};

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

	/**
	 * <b>Guarantee A.</b> Outgoing elite damage against a PLAYER victim is clamped to this
	 * fraction of the victim's max health, applied after every affix contribution and after
	 * the rarity damage multiplier. A player at full health with no armour survives any
	 * single elite hit, always. No affix participates in the clamp, which is exactly why no
	 * affix review can break it.
	 */
	public static final float ELITE_HIT_CEILING = 0.55F;

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
	// ELITE LOOT — docs/DESIGN.md §2.4
	//
	// EliteLoot holds the SHAPE, this holds the numbers. A whitelist, not a blacklist:
	// that is the enforcement mechanism for "usable resources only, no building blocks,
	// no decorative clutter", and it is why nothing can leak in. Looting deliberately
	// does NOT multiply any of this — the rarity IS the reward multiplier.
	// =================================================================================

	public static final int[] LOOT_IRON_MIN = {4, 10, 18, 30};
	public static final int[] LOOT_IRON_MAX = {9, 18, 30, 48};

	public static final int[] LOOT_GOLD_MIN = {1, 4, 8, 14};
	public static final int[] LOOT_GOLD_MAX = {3, 8, 14, 24};

	public static final int[] LOOT_COAL_MIN = {2, 0, 0, 0};
	public static final int[] LOOT_COAL_MAX = {6, 0, 0, 0};

	public static final int[] LOOT_EMERALD_MIN = {0, 2, 4, 8};
	public static final int[] LOOT_EMERALD_MAX = {0, 4, 8, 14};

	public static final int[] LOOT_DIAMOND_MIN = {0, 0, 1, 3};
	public static final int[] LOOT_DIAMOND_MAX = {0, 1, 3, 6};

	public static final int[] LOOT_ENDER_PEARL_MIN = {0, 0, 1, 0};
	public static final int[] LOOT_ENDER_PEARL_MAX = {0, 0, 2, 0};

	public static final int[] LOOT_NETHERITE_SCRAP_MIN = {0, 0, 0, 0};
	public static final int[] LOOT_NETHERITE_SCRAP_MAX = {0, 0, 0, 1};

	public static final int[] LOOT_BOOK_MIN = {0, 1, 1, 2};
	public static final int[] LOOT_BOOK_MAX = {1, 1, 2, 2};

	/**
	 * Enchantment cost handed to vanilla's own book roller, per rarity. These are the
	 * numbers an enchanting table would use at that level, so a T4 book reads as a
	 * near-max table roll rather than as a modded item.
	 */
	public static final int[] LOOT_BOOK_COST_MIN = {5, 12, 20, 28};
	public static final int[] LOOT_BOOK_COST_MAX = {12, 20, 28, 30};

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
