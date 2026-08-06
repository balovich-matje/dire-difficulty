package com.diredifficulty.elite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.diredifficulty.Tuning;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;

/**
 * The fantasy-name generator and, in one place, every word it can produce.
 *
 * <h2>What a name is</h2>
 * An elite's display name is two halves that are built and stored differently, and the split
 * is the whole design:
 *
 * <ul>
 * <li>The <b>fantasy name</b> — "Grave Lord", "Bonecaller", "Thirstfang, the Insatiable" — is
 *     a LITERAL string. Fantasy names are not translated in any game that has them, this mod
 *     has no translator, and a name assembled from lang keys would need one key per word and
 *     would still read as English word order in every other language. It is generated once,
 *     server-side, and stored in {@link EliteData}.</li>
 * <li>The <b>"(rarity mob type)" suffix</b> IS translatable, and is built at the point of
 *     display by {@link EliteNameTag} out of this mod's four rarity keys and the mob's own
 *     {@code EntityType.getDescription()}. So a French client reads
 *     "Grave Lord (Légendaire Zombie)" without this mod shipping a single mob name.</li>
 * </ul>
 *
 * <h2>Semi-related to the affixes, and that is what "semi" is doing</h2>
 * A name drawn purely from the mob family says nothing about the fight; a name drawn purely
 * from the affixes would spell the affix list out twice, since the plate already lists it. So
 * the word pool for one elite is a WEIGHTED merge of three sources — the drawn affixes, the
 * mob's family, and a generic bank — at {@link Tuning#NAME_AFFIX_WEIGHT} /
 * {@link Tuning#NAME_FAMILY_WEIGHT} / {@link Tuning#NAME_GENERIC_WEIGHT}. A Vampiric zombie is
 * likelier to be a "Blood" something than a "Grave" something and likelier to be either than a
 * "Dread" something, but any of the three can come up, so two Vampiric zombies do not share a
 * name.
 *
 * <h2>Determinism</h2>
 * Seeded from the entity's own UUID mixed with its rarity and its drawn affix ids, through a
 * {@code RandomSource} of its own — never the level's. The stored name is what is read back
 * after a reload, so storage already guarantees stability; seeding this way means the
 * generator would produce the SAME name again from the same elite, which is what makes it
 * testable and what stops two elites promoted in the same tick from correlating.
 *
 * <h2>Why the pools live here and not in {@code Tuning}</h2>
 * The house rule is that no balance NUMBER lives outside {@code Tuning}, and none does: the
 * shape probabilities and the three source weights are all Tuning constants read below. These
 * are word banks — data, not tuning — and they are in one data class rather than scattered
 * through twenty-five affix files precisely so that "how big is the pool" is answerable by
 * reading one file.
 */
public final class EliteNames {
	// =====================================================================================
	// Word banks. Three roles, and every source contributes to whichever it has words for:
	//
	//   PREFIX   the leading word of a two-word name, and the head of a compound
	//   TITLE    the trailing noun of a two-word name ("Lord", "Reaver")
	//   TAIL     the lowercase tail of a compound name ("bane" -> "Gravebane")
	//
	// EPITHETS and DOMAINS are shared banks, not per-source: "the Unbroken" and "of the Pit"
	// carry grandeur rather than subject matter, which is the job rarity gives them.
	// =====================================================================================

	/** One source's contribution to the three word roles. Any array may be empty. */
	private record Parts(String[] prefixes, String[] titles, String[] tails) {
	}

	private static final String[] NONE = new String[0];

	private static final Parts GENERIC = new Parts(
			new String[] {"Dread", "Grim", "Iron", "Ashen", "Hollow", "Bitter", "Savage", "Wretched",
				"Vile", "Gloom", "Storm", "Ember", "Night", "Cinder", "Rime", "Murk", "Thorn", "Bane",
				"Cruel", "Fell", "Sable", "Rust", "Void", "Wither", "Scourge", "Pale", "Doom", "Wrath",
				"Shroud", "Blight", "Havoc", "Sorrow"},
			new String[] {"Lord", "Reaver", "Warden", "Butcher", "Stalker", "Hunter", "Marauder",
				"Fiend", "Brute", "Slayer", "Herald", "Beast", "Ravager", "Tyrant", "Prowler", "Render",
				"Breaker", "Champion", "Harbinger", "Sentinel", "Wretch", "Raider", "Warlord",
				"Executioner", "Devourer", "Scourge", "Menace", "Terror"},
			new String[] {"bane", "maw", "fang", "claw", "shard", "rend", "gore", "husk", "grim",
				"reave", "blight", "shade", "wrack", "thorn", "crag", "sunder"});

	/**
	 * Mob-family banks, keyed by the family {@link #familyOf} derives. Vanilla-agnostic where
	 * it can be: the derivation prefers entity-type TAGS, so a modded undead lands in the
	 * undead bank without this file naming it.
	 */
	private static final Map<String, Parts> BY_FAMILY = new HashMap<>();

	/**
	 * Per-affix banks — {@code docs/DESIGN.md} §3.1's twenty-five ids. Keyed by the FROZEN
	 * affix id, so a rename that broke the link would show up as an affix contributing no
	 * words rather than as a crash. An id with no entry simply contributes nothing.
	 */
	private static final Map<String, Parts> BY_AFFIX = new HashMap<>();

	/** Legendary's "the &lt;Epithet&gt;" bank. Grandeur, not subject matter. */
	private static final String[] EPITHETS = {
		"Unbroken", "Undying", "Merciless", "Insatiable", "Nameless", "Sleepless", "Unspoken",
		"Wretched", "Ceaseless", "Unfed", "Faceless", "Thrice-Slain", "Ever-Hungry", "Iron-Willed",
		"Unquiet", "Pitiless", "Long-Buried", "Unmourned", "Hollow One", "Dreadful"};

	/** The "&lt;Title&gt; of &lt;Domain&gt;" bank. */
	private static final String[] DOMAINS = {
		"the Deep", "the Pit", "the Hollow", "the Gloom", "Ashes", "Rot", "Ruin", "the Cinder",
		"Dusk", "the Mire", "the Barrow", "Silence", "the Long Night", "Cold Iron", "the Waste",
		"the Black Hour", "Empty Halls", "the Last Light"};

	static {
		registerFamilies();
		registerAffixes();
	}

	private EliteNames() {
	}

	// =====================================================================================
	// Generation
	// =====================================================================================

	/**
	 * The fantasy name for one elite. Pure: same mob, same rarity, same affix ids, same name.
	 *
	 * @param affixIds the drawn affix ids, in draw order — the affix half of the word pool
	 */
	public static String generate(final Mob mob, final Rarity rarity, final List<String> affixIds) {
		return generate(mob.getUUID(), familyOf(mob), rarity, affixIds);
	}

	/**
	 * The same, with the two things a mob is read for supplied directly.
	 *
	 * <p>Split out so {@code /dire names} can draw a batch without spawning a batch of
	 * entities — a probe that had to summon one mob per name would measure the spawn path
	 * rather than the generator.
	 */
	public static String generate(final UUID uuid, final String family, final Rarity rarity,
			final List<String> affixIds) {
		RandomSource random = RandomSource.create(seedOf(uuid, rarity, affixIds));

		List<String> prefixes = pool(family, affixIds, Role.PREFIX);
		List<String> titles = pool(family, affixIds, Role.TITLE);
		List<String> tails = pool(family, affixIds, Role.TAIL);

		String core = core(random, rarity, prefixes, titles, tails);

		// Grandeur, rarity-scaled: only the top tiers may carry a trailing title, and only
		// Legendary carries it often. A two-part name on a common spawn stops reading as an
		// event, which is the whole point of the rarity ladder.
		if (random.nextDouble() < epithetChance(rarity)) {
			return core + ", the " + pick(random, EPITHETS);
		}

		return core;
	}

	/** The two-word, compound and "of the" shapes, drawn by the rarity's own probabilities. */
	private static String core(final RandomSource random, final Rarity rarity, final List<String> prefixes,
			final List<String> titles, final List<String> tails) {
		double roll = random.nextDouble();

		// "of the Deep" is a grander shape, so it is off at Uncommon: the chance is zero there
		// and the roll falls through to the two ordinary shapes.
		if (rarity.tier() >= Tuning.NAME_DOMAIN_FROM_TIER && roll < Tuning.NAME_DOMAIN_CHANCE
				&& !titles.isEmpty()) {
			return pick(random, titles) + " of " + pick(random, DOMAINS);
		}

		if (roll < Tuning.NAME_DOMAIN_CHANCE + Tuning.NAME_COMPOUND_CHANCE
				&& !prefixes.isEmpty() && !tails.isEmpty()) {
			// "Grave" + "bane" -> "Gravebane". The tail bank is lowercase by construction so
			// the join needs no case handling and cannot produce "GraveBane".
			return pick(random, prefixes) + pick(random, tails);
		}

		if (prefixes.isEmpty() || titles.isEmpty()) {
			// Unreachable with the shipped banks — GENERIC alone fills all three roles — but a
			// pack that emptied a bank gets a name rather than an exception.
			return titles.isEmpty() ? pick(random, GENERIC.titles()) : pick(random, titles);
		}

		return pick(random, prefixes) + " " + pick(random, titles);
	}

	private static double epithetChance(final Rarity rarity) {
		switch (rarity) {
			case LEGENDARY:
				return Tuning.NAME_EPITHET_CHANCE_LEGENDARY;
			case EPIC:
				return Tuning.NAME_EPITHET_CHANCE_EPIC;
			default:
				return 0.0;
		}
	}

	private enum Role {
		PREFIX,
		TITLE,
		TAIL
	}

	/**
	 * The weighted merge. Each source's words are added to the candidate list as many times as
	 * its weight, which is a weighted draw expressed as a list — deliberately, because the
	 * alternative is three nested rolls that can each fall through to an empty bank.
	 */
	private static List<String> pool(final String family, final List<String> affixIds, final Role role) {
		List<String> out = new ArrayList<>();

		for (final String affixId : affixIds) {
			addRepeated(out, wordsOf(BY_AFFIX.get(affixId), role), Tuning.NAME_AFFIX_WEIGHT);
		}

		addRepeated(out, wordsOf(BY_FAMILY.get(family), role), Tuning.NAME_FAMILY_WEIGHT);
		addRepeated(out, wordsOf(GENERIC, role), Tuning.NAME_GENERIC_WEIGHT);
		return out;
	}

	private static String[] wordsOf(final Parts parts, final Role role) {
		if (parts == null) {
			return NONE;
		}

		switch (role) {
			case PREFIX:
				return parts.prefixes();
			case TITLE:
				return parts.titles();
			case TAIL:
			default:
				return parts.tails();
		}
	}

	private static void addRepeated(final List<String> out, final String[] words, final int times) {
		for (int i = 0; i < times; i++) {
			for (final String word : words) {
				out.add(word);
			}
		}
	}

	private static String pick(final RandomSource random, final List<String> words) {
		return words.get(random.nextInt(words.size()));
	}

	private static String pick(final RandomSource random, final String[] words) {
		return words[random.nextInt(words.length)];
	}

	/**
	 * The seed. The UUID is the entity's own identity and survives a reload; rarity and the
	 * affix ids are folded in so that two elites which somehow shared a UUID — the debug
	 * command re-promoting one entity — still get different names when their roll differs.
	 */
	private static long seedOf(final UUID uuid, final Rarity rarity, final List<String> affixIds) {
		long seed = uuid.getMostSignificantBits() * 31L + uuid.getLeastSignificantBits();
		seed = seed * 31L + rarity.ordinal();

		for (final String affixId : affixIds) {
			seed = seed * 31L + affixId.hashCode();
		}

		return seed;
	}

	// =====================================================================================
	// Family derivation
	// =====================================================================================

	/**
	 * Which family bank a mob draws from.
	 *
	 * <p><b>Tags first, id second.</b> The vanilla entity-type tags cover the families that
	 * have one and pick up modded members for free — a modded skeleton in
	 * {@code #minecraft:skeletons} gets bone words without this file naming it. The id checks
	 * below them cover the families vanilla has no tag for, and they match on the PATH so that
	 * {@code somemod:cave_creeper} still reads as a creeper.
	 */
	public static String familyOf(final Mob mob) {
		if (mob.is(EntityTypeTags.ZOMBIES)) {
			return "zombie";
		}

		if (mob.is(EntityTypeTags.SKELETONS)) {
			return "skeleton";
		}

		if (mob.is(EntityTypeTags.ILLAGER) || mob.is(EntityTypeTags.RAIDERS)) {
			return "illager";
		}

		if (mob.is(EntityTypeTags.ARTHROPOD)) {
			return "arthropod";
		}

		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		String path = id == null ? "" : id.getPath().toLowerCase(Locale.ROOT);

		if (path.contains("creeper")) {
			return "creeper";
		}

		if (path.contains("enderman") || path.contains("endermite") || path.contains("shulker")) {
			return "ender";
		}

		if (path.contains("piglin") || path.contains("hoglin") || path.contains("blaze")
				|| path.contains("ghast") || path.contains("magma") || path.contains("strider")) {
			return "nether";
		}

		if (path.contains("guardian") || path.contains("drowned") || path.contains("squid")) {
			return "deep";
		}

		if (path.contains("slime")) {
			return "slime";
		}

		if (path.contains("phantom") || path.contains("vex")) {
			return "sky";
		}

		if (path.contains("warden") || path.contains("sculk")) {
			return "dark";
		}

		if (path.contains("witch")) {
			return "illager";
		}

		// The undead test is LAST of the tag tests on purpose: it is the widest one, so a
		// zombie or a skeleton has already been claimed by its own bank before it gets here.
		if (mob.is(EntityTypeTags.UNDEAD)) {
			return "zombie";
		}

		return "generic";
	}

	// =====================================================================================
	// The banks themselves
	// =====================================================================================

	private static void registerFamilies() {
		family("zombie",
				new String[] {"Grave", "Rot", "Carrion", "Corpse", "Charnel", "Pallid", "Gut", "Mould",
					"Sallow", "Graveyard", "Crypt", "Barrow"},
				new String[] {"Lord", "Gnasher", "Shambler", "Devourer", "Glutton", "Hunger", "Maw",
					"Risen", "Feaster", "Gravelord"},
				new String[] {"rot", "gut", "grave", "flesh", "mire", "crypt"});

		family("skeleton",
				new String[] {"Bone", "Marrow", "Rib", "Ivory", "Chalk", "Ash", "Splinter", "Hollow"},
				new String[] {"Marksman", "Archer", "Rattler", "Fletcher", "Clatter", "Bonelord",
					"Sharpshot", "Quiver"},
				new String[] {"shot", "bone", "shaft", "rattle", "arrow"});

		family("illager",
				new String[] {"Grey", "Banner", "Hymn", "Iron", "Cloister", "Grim", "Chant"},
				new String[] {"Captain", "Zealot", "Inquisitor", "Bannerman", "Preacher", "Warcaptain",
					"Enforcer"},
				new String[] {"banner", "chant", "oath", "toll"});

		family("arthropod",
				new String[] {"Silk", "Web", "Fang", "Chitin", "Carapace", "Hairleg", "Nest"},
				new String[] {"Weaver", "Spinner", "Broodmother", "Skitterer", "Trapper", "Silkfang"},
				new String[] {"silk", "web", "fang", "leg", "skitter"});

		family("creeper",
				new String[] {"Hiss", "Fuse", "Sulphur", "Powder", "Green", "Blast"},
				new String[] {"Bloom", "Fuse", "Sunder", "Cracker", "Detonation"},
				new String[] {"blast", "burst", "hiss", "fuse"});

		family("ender",
				new String[] {"Void", "Warp", "Rift", "Pearl", "Umbral", "Far"},
				new String[] {"Walker", "Strider", "Thief", "Wanderer", "Stepper"},
				new String[] {"step", "warp", "rift", "void"});

		family("nether",
				new String[] {"Ember", "Soot", "Brimstone", "Cinder", "Char", "Basalt", "Gilded"},
				new String[] {"Tusk", "Forgeborn", "Emberlord", "Kindler", "Smelter"},
				new String[] {"ember", "char", "soot", "forge"});

		family("deep",
				new String[] {"Brine", "Tide", "Kelp", "Drowned", "Coral", "Fathom", "Undertow"},
				new String[] {"Tidecaller", "Drowner", "Depths", "Fisher", "Trawler"},
				new String[] {"brine", "tide", "deep", "kelp"});

		family("slime",
				new String[] {"Ooze", "Gel", "Sludge", "Mire", "Glob"},
				new String[] {"Blob", "Splitter", "Congealer", "Wobbler"},
				new String[] {"ooze", "sludge", "gel"});

		family("sky",
				new String[] {"Night", "Wing", "Shriek", "Cloud", "Starless"},
				new String[] {"Screamer", "Wing", "Diver", "Nightwing"},
				new String[] {"wing", "shriek", "dive"});

		family("dark",
				new String[] {"Sculk", "Echo", "Silence", "Deaf", "Blind"},
				new String[] {"Listener", "Silence", "Echo", "Unseen"},
				new String[] {"echo", "hush", "sculk"});

		// "generic" is the fallback family, and it is deliberately NOT a copy of GENERIC: a
		// mob with no family of its own draws from the generic bank once (as every mob does)
		// rather than twice, so it reads as unremarkable instead of as doubly generic.
		family("generic", NONE, NONE, NONE);
	}

	/**
	 * §3.1's twenty-five affixes, each with the words that make the name say something about
	 * the fight ahead. Every id here is the frozen registry id.
	 */
	private static void registerAffixes() {
		affix("swift", new String[] {"Fleet", "Wind", "Swift", "Gale", "Quick"},
				new String[] {"Runner", "Gale", "Courser", "Sprinter"},
				new String[] {"wind", "gale", "dash"});
		affix("relentless", new String[] {"Hound", "Tireless", "Ever", "Dogged"},
				new String[] {"Pursuer", "Hound", "Tracker", "Follower"},
				new String[] {"hound", "chase"});
		affix("warlord", new String[] {"Banner", "War", "Iron", "Rally"},
				new String[] {"Warlord", "Commander", "Banneret", "Captain"},
				new String[] {"banner", "horn"});
		affix("summoner", new String[] {"Brood", "Horde", "Call", "Swarm"},
				new String[] {"Lord", "Caller", "Broodmother", "Summoner", "Shepherd"},
				new String[] {"caller", "brood", "horde"});
		affix("chilling", new String[] {"Frost", "Cold", "Rime", "Winter"},
				new String[] {"Chill", "Winter", "Frostlord", "Numb"},
				new String[] {"chill", "frost", "rime"});
		affix("searing", new String[] {"Ember", "Cinder", "Scorch", "Blaze"},
				new String[] {"Pyre", "Scorcher", "Burn", "Kiln"},
				new String[] {"burn", "sear", "pyre"});
		affix("shieldbreaker", new String[] {"Shatter", "Crack", "Split", "Cleave"},
				new String[] {"Shieldbreaker", "Breaker", "Sunderer", "Splitter"},
				new String[] {"break", "crack", "shatter"});
		affix("sunwalker", new String[] {"Dawn", "Sun", "Day", "Noon", "Solar"},
				new String[] {"Dawnbringer", "Sunwalker", "Daywalker", "Sunlord"},
				new String[] {"dawn", "sun", "light"});
		affix("escapist", new String[] {"Fade", "Ghost", "Slip", "Waning"},
				new String[] {"Fade", "Vanisher", "Wisp", "Slipper"},
				new String[] {"fade", "slip", "wisp"});
		affix("warded", new String[] {"Ward", "Aegis", "Rune", "Sigil"},
				new String[] {"Warden", "Aegis", "Sigilbearer", "Ward"},
				new String[] {"ward", "rune", "sigil"});
		affix("rooted", new String[] {"Stone", "Root", "Anchor", "Deep"},
				new String[] {"Immovable", "Root", "Monolith", "Anchor"},
				new String[] {"root", "stone", "anchor"});
		affix("vampiric", new String[] {"Blood", "Thirst", "Crimson", "Sanguine", "Red"},
				new String[] {"Thirst", "Drinker", "Leech", "Bloodletter", "Sanguine"},
				new String[] {"thirst", "blood", "drinker", "vein"});
		affix("frostbound", new String[] {"Ice", "Glacier", "Hoar", "Sleet"},
				new String[] {"Icebinder", "Glacier", "Freezer", "Hoarlord"},
				new String[] {"ice", "freeze", "glacier"});
		affix("enfeebling", new String[] {"Wither", "Sap", "Blight", "Weary"},
				new String[] {"Wither", "Sapper", "Blight", "Waster"},
				new String[] {"sap", "wither", "wane"});
		affix("blinkstrike", new String[] {"Blink", "Flicker", "Warp", "Sudden"},
				new String[] {"Flicker", "Ghostblade", "Blade", "Blink"},
				new String[] {"blink", "flicker", "step"});
		affix("stalker", new String[] {"Hunt", "Shadow", "Track", "Silent"},
				new String[] {"Stalker", "Hunter", "Shadow", "Tracker"},
				new String[] {"hunt", "shadow", "track"});
		affix("thorned", new String[] {"Thorn", "Barb", "Spine", "Bramble"},
				new String[] {"Thorn", "Bramble", "Spinelord", "Barb"},
				new String[] {"thorn", "barb", "spine"});
		affix("volatile", new String[] {"Blast", "Powder", "Fuse", "Volatile"},
				new String[] {"Bomb", "Fuse", "Bursting", "Detonator"},
				new String[] {"blast", "burst", "boom"});
		affix("splitting", new String[] {"Twin", "Spawn", "Fracture", "Cloven"},
				new String[] {"Splitter", "Twinborn", "Fracture", "Halved"},
				new String[] {"split", "twin", "shard"});
		affix("noxious", new String[] {"Venom", "Plague", "Miasma", "Pox"},
				new String[] {"Plaguebearer", "Venom", "Miasma", "Poxlord"},
				new String[] {"venom", "plague", "rot"});
		affix("sundering", new String[] {"Rend", "Shred", "Cleave", "Flense"},
				new String[] {"Render", "Cleaver", "Shredder", "Flenser"},
				new String[] {"rend", "shred", "cleave"});
		affix("volley", new String[] {"Storm", "Arrow", "Quiver", "Hail"},
				new String[] {"Volley", "Storm", "Marksman", "Hailbringer"},
				new String[] {"storm", "shot", "hail"});
		affix("regenerating", new String[] {"Ever", "Knit", "Renew", "Unspent"},
				new String[] {"Undying", "Mender", "Everknit", "Renewer"},
				new String[] {"knit", "mend", "ever"});
		affix("bulwark", new String[] {"Iron", "Wall", "Plate", "Bastion"},
				new String[] {"Bulwark", "Wall", "Ironclad", "Bastion"},
				new String[] {"wall", "plate", "guard"});
		affix("quaking", new String[] {"Quake", "Tremor", "Stone", "Rumble"},
				new String[] {"Earthshaker", "Quake", "Tremor", "Shaker"},
				new String[] {"quake", "tremor", "shock"});
	}

	private static void family(final String key, final String[] prefixes, final String[] titles,
			final String[] tails) {
		BY_FAMILY.put(key, new Parts(prefixes, titles, tails));
	}

	private static void affix(final String id, final String[] prefixes, final String[] titles,
			final String[] tails) {
		BY_AFFIX.put(id, new Parts(prefixes, titles, tails));
	}

	// =====================================================================================
	// Introspection, for the test kit
	// =====================================================================================

	/** How many affix ids carry a word bank. {@code /dire names} reports it. */
	public static int affixBankCount() {
		return BY_AFFIX.size();
	}

	/** How many mob families carry a word bank, including the empty {@code generic} one. */
	public static int familyBankCount() {
		return BY_FAMILY.size();
	}
}
