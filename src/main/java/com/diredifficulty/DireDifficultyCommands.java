package com.diredifficulty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.AffixBudget;
import com.diredifficulty.affix.AffixClass;
import com.diredifficulty.affix.Affixes;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.elite.Artefact;
import com.diredifficulty.config.DireDifficultyConfig;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.EliteGear;
import com.diredifficulty.elite.EliteLoot;
import com.diredifficulty.elite.EliteMob;
import com.diredifficulty.elite.EliteNames;
import com.diredifficulty.elite.EliteRoller;
import com.diredifficulty.elite.EliteRules;
import com.diredifficulty.elite.Rarity;
import com.diredifficulty.progression.ProgressionScore;
import com.diredifficulty.progression.ScoreSource;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /dire} — the developer test kit ({@code docs/DESIGN.md} §6.1's
 * {@code DifficultyCommands}), with {@code /advdiff} registered as a redirect alias.
 *
 * <h2>Why it ships</h2>
 * Every claim this mod makes to players is probabilistic. An elite appears on ~1% of natural
 * spawns in a fresh world, its rarity is a weighted draw, and its affix set is a shuffle
 * filtered by three independent rule systems. None of that is observable by playing: waiting
 * for a Legendary {@code warded}+{@code bulwark} to turn up in order to discover the exclusion
 * table is broken is not a verification strategy. The four probe subcommands make the roll, the
 * caps, the exclusions, the EHP audit and the loot table each answerable in one line.
 *
 * <h2>Who can see it</h2>
 * Permission level 2 ({@code Commands.LEVEL_GAMEMASTERS}) AND — for a PLAYER source — creative
 * mode, exactly as Archetypes gates {@code /archetypes}. Brigadier consults {@code requires}
 * both when it builds the tree it sends a client and when it parses what that client typed, so
 * the pair keeps the kit out of a survival playthrough's tab-complete and refuses it if typed
 * anyway.
 *
 * <p><b>One deliberate divergence from the Archetypes gate: a non-player source is allowed.</b>
 * Archetypes refuses the console because every one of its subcommands mutates the CALLER — it
 * grants the caller levels and points, so a source with no player has nothing to act on. This
 * kit is the opposite: it acts on the world and on the tables, and the console is how the
 * headless dedicated-server smoke test reaches it. A command the smoke test cannot invoke
 * cannot be smoke-tested, and "creative players and the server operator's own shell" is the
 * same audience "creative players only" was reaching for. The survival-playthrough gate — the
 * actual point — is unchanged: a survival player is refused either way.
 *
 * <h2>Position</h2>
 * {@code spawn} uses the source's position and takes no coordinate argument. The console's
 * source position is the world spawn, and vanilla's own {@code /execute positioned x y z run
 * dire spawn …} moves it — a coordinate argument here would be a second, worse spelling
 * of a vanilla feature, and it would collide with the greedy affix list.
 */
public final class DireDifficultyCommands {
	private static final String KEY = "commands.dire_difficulty.";

	/**
	 * The command literal, and deliberately NOT {@link DireDifficulty#MOD_ID}. The mod id is
	 * {@code dire_difficulty}; typing that is a chore, so the kit answers to the short
	 * {@code /dire} with {@link #ALIAS} redirected onto the same node set.
	 */
	private static final String ROOT = "dire";

	/** The long-hand alias, a redirect onto the {@link #ROOT} tree rather than a second tree. */
	private static final String ALIAS = "advdiff";

	/** {@code spawn}'s rarity wildcard: draw the rarity from the live curve at this position. */
	private static final String ROLL = "roll";

	/**
	 * Permission level 2. 1.21.11 turned {@code Commands.LEVEL_GAMEMASTERS} from an {@code int}
	 * into a {@code PermissionCheck} and added {@code Commands.hasPermission(check)}; below it
	 * the constant is the plain level and the predicate is written by hand. Its own constant so
	 * the source test below can be {@code and}ed on without a generic witness.
	 */
	private static final Predicate<CommandSourceStack> GAMEMASTER =
			Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

	private DireDifficultyCommands() {
	}

	/**
	 * The second half of the gate. A player source must be in creative; a source with no player
	 * — the console, a command block, a function — passes. See the class javadoc for why this
	 * differs from Archetypes' identical-looking predicate.
	 */
	private static boolean allowedSource(final CommandSourceStack source) {
		ServerPlayer player = source.getPlayer();
		return player == null || player.isCreative();
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> {
			LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
					Commands.literal(ROOT)
							.requires(GAMEMASTER.and(DireDifficultyCommands::allowedSource))
							.then(scoreCommand())
							.then(infoCommand())
							.then(spawnCommand(registries))
							.then(auditCommand())
							.then(namesCommand(registries))
							.then(lootCommand())
							.then(artefactCommand())
							.then(freezeCommand())
							.then(Commands.literal("rufus").executes(context -> {
								CommandSourceStack source = context.getSource();
								com.diredifficulty.elite.Rufus.spawnAt(source.getLevel(), BlockPos.containing(source.getPosition()));
								source.sendSuccess(() -> Component.literal("Rufus has arrived."), true);
								return 1;
							})));

			// The alias is a REDIRECT rather than a second tree: one set of nodes, so the two
			// spellings cannot drift and the client is sent one copy of the arguments.
			dispatcher.register(Commands.literal(ALIAS)
					.requires(GAMEMASTER.and(DireDifficultyCommands::allowedSource))
					.redirect(root));
		});
	}

	// --- /dire score [player] ---

	/**
	 * The combined score and the per-source breakdown, read live through the same accessors the
	 * elite roll uses.
	 *
	 * <p>The breakdown prints {@code absent} rather than {@code 0.00} for a provider that could
	 * not answer, because that distinction is the whole of §5.1: a provider whose backing mod is
	 * missing must be dropped from the fold, never folded in as zero. Rendering them the same
	 * way would hide exactly the bug the {@code OptionalDouble} exists to prevent.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> scoreCommand() {
		return Commands.literal("score")
				.executes(context -> reportScore(context.getSource(),
						context.getSource().getPlayerOrException()))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> reportScore(context.getSource(),
								EntityArgument.getPlayer(context, "player"))));
	}

	private static int reportScore(final CommandSourceStack source, final ServerPlayer player) {
		ProgressionScore.Breakdown b = ProgressionScore.INSTANCE.breakdown(player);

		source.sendSuccess(() -> Component.translatable(KEY + "score.header",
				player.getName(), String.format(Locale.ROOT, "%.3f", b.stage()),
				String.format(Locale.ROOT, "%.2f", b.cap())), false);
		reportFactor(source, "advancements", b.advancements(), Tuning.STAGE_WEIGHT_ADVANCEMENTS);
		reportFactor(source, "kills", b.kills(), Tuning.STAGE_WEIGHT_KILLS);
		reportFactor(source, "distance", b.distance(), Tuning.STAGE_WEIGHT_DISTANCE);
		reportFactor(source, "survival", b.survival(), Tuning.STAGE_WEIGHT_SURVIVAL);
		source.sendSuccess(() -> Component.translatable(KEY + "score.chance",
				String.format(Locale.ROOT, "%.4f",
						Tuning.eliteChance(b.stage(), EliteRules.active().eliteChanceMax()))), false);
		return 1;
	}

	private static void reportFactor(final CommandSourceStack source, final String id, final double value,
			final double weight) {
		source.sendSuccess(() -> Component.translatable(KEY + "score.source", id,
				String.format(Locale.ROOT, "%.3f (adds %.3f of %.2f)", value, value * weight, weight)), false);
	}

	// --- /dire info ---

	/**
	 * Dumps the elite state of the nearest elite within {@link #INFO_RADIUS}. Nearest rather
	 * than looked-at: a ray-cast needs a player, and the console is a first-class caller here.
	 */
	private static final double INFO_RADIUS = 32.0;

	private static LiteralArgumentBuilder<CommandSourceStack> infoCommand() {
		return Commands.literal("info").executes(context -> {
			CommandSourceStack source = context.getSource();
			ServerLevel level = source.getLevel();
			Vec3 origin = source.getPosition();
			Mob best = null;
			double bestDistSqr = INFO_RADIUS * INFO_RADIUS;

			for (final Entity entity : level.getEntities(
					(Entity) null, new AABB(origin, origin).inflate(INFO_RADIUS),
					e -> e instanceof Mob && EliteMob.isElite(e))) {
				double distSqr = entity.distanceToSqr(origin);

				if (distSqr <= bestDistSqr) {
					bestDistSqr = distSqr;
					best = (Mob) entity;
				}
			}

			if (best == null) {
				source.sendFailure(Component.translatable(KEY + "info.none", (int) INFO_RADIUS));
				return 0;
			}

			Mob mob = best;
			EliteData data = EliteMob.data(mob);
			Rarity rarity = data.rarity();

			source.sendSuccess(() -> Component.translatable(KEY + "info.header",
					mob.getName(), rarity.name(),
					String.format(Locale.ROOT, "%.1f/%.1f", mob.getHealth(), mob.getMaxHealth()),
					String.format(Locale.ROOT, "%.2f", mob.getScale())), false);
			source.sendSuccess(() -> Component.translatable(KEY + "info.affixes",
					data.affixes().isEmpty() ? "-" : String.join(", ", data.affixes())), false);
			// The generated name RAW, next to the composed one the mob is wearing: the header
			// above prints mob.getName(), which is the translated "Grave Lord (Legendary
			// Zombie)". Printing the stored literal too is what tells a stored name apart from
			// a name tag that happens to look right.
			source.sendSuccess(() -> Component.translatable(KEY + "info.name",
					data.hasName() ? data.name() : "-", EliteNames.familyOf(mob)), false);
			source.sendSuccess(() -> Component.translatable(KEY + "info.gear",
					EliteGear.describe(mob)), false);
			source.sendSuccess(() -> Component.translatable(KEY + "info.flags",
					String.valueOf(data.escapistUsed()), String.valueOf(data.splitGeneration()),
					String.format(Locale.ROOT, "%.2f", ehpIndex(EliteMob.affixes(mob), rarity))), false);
			return 1;
		});
	}

	// --- /dire spawn <entity> [rarity] [affixes...] ---

	/**
	 * Summons a mob through vanilla's own {@code SummonCommand.createEntity} and force-promotes
	 * it.
	 *
	 * <p><b>Vanilla's method, not a hand-rolled spawn.</b> It carries the spawnable-bounds
	 * check, the peaceful-difficulty check and the duplicate-UUID check, and it finalises with
	 * {@code EntitySpawnReason.COMMAND} — a reason {@link EliteRoller} deliberately refuses, so
	 * the natural roll cannot fire underneath this command and produce a double promotion.
	 * Everything elite about the result comes from the explicit {@code promote} below.
	 *
	 * <p><b>An explicit affix list bypasses the caps, the exclusions and the EHP audit, on
	 * purpose.</b> The point of a test kit is to be able to stand in front of the combinations
	 * the roller refuses. The command says so in its output when the given set would have been
	 * refused, naming the rule — so the bypass is legible rather than silent, and the report
	 * doubles as a check that the rule fires. Guarantee A is untouched either way: it lives in
	 * the damage funnel, which no affix and no command can reach.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> spawnCommand(
			final CommandBuildContext registries) {
		return Commands.literal("spawn")
				.then(Commands.argument("entity", ResourceArgument.resource(registries, Registries.ENTITY_TYPE))
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(
								monsterIds(), builder))
						.executes(context -> spawn(context, ROLL, ""))
						.then(Commands.argument("rarity", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										rarityNames(), builder))
								.executes(context -> spawn(context,
										StringArgumentType.getString(context, "rarity"), ""))
								.then(Commands.argument("affixes", StringArgumentType.greedyString())
										.suggests((context, builder) -> SharedSuggestionProvider.suggest(
												affixIds(), builder))
										.executes(context -> spawn(context,
												StringArgumentType.getString(context, "rarity"),
												StringArgumentType.getString(context, "affixes"))))));
	}

	private static int spawn(final CommandContext<CommandSourceStack> context, final String rarityArg,
			final String affixArg) throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		Holder.Reference<EntityType<?>> type = ResourceArgument.getSummonableEntityType(context, "entity");

		Rarity rarity;

		if (ROLL.equalsIgnoreCase(rarityArg)) {
			// The live curve at this position, so `roll` measures the shipped weights rather
			// than a uniform pick nothing else in the mod would ever make.
			rarity = drawRarityFromCurve(level, source);
		} else {
			rarity = rarityByName(rarityArg);

			if (rarity == null) {
				source.sendFailure(Component.translatable(KEY + "spawn.unknownRarity", rarityArg,
						String.join(", ", rarityNames())));
				return 0;
			}
		}

		boolean explicit = !affixArg.isBlank();
		List<Affix> affixes = new ArrayList<>();

		if (explicit) {
			for (final String id : affixArg.trim().split("\\s+")) {
				Affix affix = Affixes.byId(id.toLowerCase(Locale.ROOT));

				if (affix == null) {
					source.sendFailure(Component.translatable(KEY + "spawn.unknownAffix", id));
					return 0;
				}

				affixes.add(affix);
			}
		}

		// Summoned BEFORE the affix roll, deliberately: the eligibility predicates ask about a
		// concrete mob — its class, its attributes, whether it burns in daylight — so the roll
		// wants the real entity rather than a throwaway prototype that might answer differently.
		// Vanilla's method finalises with EntitySpawnReason.COMMAND, a reason EliteRoller
		// refuses, so the natural roll cannot fire underneath this and double-promote.
		Entity spawned = SummonCommand.createEntity(source, type, source.getPosition(),
				new CompoundTag(), true);

		if (!(spawned instanceof Mob mob)) {
			source.sendFailure(Component.translatable(KEY + "spawn.notAMob",
					type.key().identifier().toString()));
			spawned.discard();
			return 0;
		}

		if (!explicit) {
			affixes = AffixBudget.roll(mob, rarity, EliteRules.active(), level.getRandom());
		}

		EliteRoller.promote(level, mob, rarity, affixes);

		Rarity drawn = rarity;
		List<Affix> applied = affixes;
		source.sendSuccess(() -> Component.translatable(KEY + "spawn.done",
				mob.getName(), drawn.name(),
				applied.isEmpty() ? "-" : String.join(", ", idsOf(applied)),
				String.format(Locale.ROOT, "%.1f", mob.getMaxHealth()),
				String.format(Locale.ROOT, "%.2f", ehpIndex(applied, drawn))), true);

		if (explicit) {
			for (final String violation : violations(applied, drawn)) {
				source.sendSuccess(() -> Component.translatable(KEY + "spawn.bypassed", violation), false);
			}
		}

		return 1;
	}

	private static Rarity drawRarityFromCurve(final ServerLevel level, final CommandSourceStack source) {
		EliteRules rules = EliteRules.active();
		double score = ScoreSource.active().scoreAt(level, BlockPos.containing(source.getPosition()));
		RandomSource random = level.getRandom();
		double[] weights = {
			Tuning.rarityWeightUncommon(score),
			Tuning.rarityWeightRare(score),
			Tuning.rarityWeightEpic(score),
			Tuning.rarityWeightLegendary(score, rules.legendaryWeightFloor()),
		};
		double total = 0.0;

		for (int i = 0; i < weights.length; i++) {
			if (Rarity.at(i).tier() > rules.maxRarityTier()) {
				weights[i] = 0.0;
			}

			total += weights[i];
		}

		if (total <= 0.0) {
			return Rarity.UNCOMMON;
		}

		double roll = random.nextDouble() * total;

		for (int i = 0; i < weights.length; i++) {
			roll -= weights[i];

			if (roll < 0.0) {
				return Rarity.at(i);
			}
		}

		return Rarity.UNCOMMON;
	}

	// --- /dire audit <rarity> <count> ---

	/**
	 * §9.4's roll probe, made runnable: draw {@code count} affix sets at this rarity and assert
	 * that none of them violates a class cap, an exclusion pair or the EHP index.
	 *
	 * <p>Rolled against a zombie prototype rather than against a mob the caller supplies, so the
	 * eligible pool is the widest one a stock catalogue offers and the run is comparable between
	 * invocations. It reports the worst EHP index seen and the mean set size alongside the
	 * violation count, because a probe that only ever prints "0 violations" cannot tell a
	 * working rule system from a rule system that drew nothing.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> auditCommand() {
		return Commands.literal("audit")
				.then(Commands.argument("rarity", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(rarityNames(), builder))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 100000))
								.executes(context -> {
									CommandSourceStack source = context.getSource();
									String name = StringArgumentType.getString(context, "rarity");
									Rarity rarity = rarityByName(name);

									if (rarity == null) {
										source.sendFailure(Component.translatable(KEY + "spawn.unknownRarity",
												name, String.join(", ", rarityNames())));
										return 0;
									}

									return audit(source, rarity,
											IntegerArgumentType.getInteger(context, "count"));
								})));
	}

	private static int audit(final CommandSourceStack source, final Rarity rarity, final int count) {
		ServerLevel level = source.getLevel();
		Mob prototype = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);

		if (prototype == null) {
			source.sendFailure(Component.translatable(KEY + "audit.noPrototype"));
			return 0;
		}

		EliteRules rules = EliteRules.active();
		RandomSource random = level.getRandom();
		List<String> failures = new ArrayList<>();
		double worstIndex = 0.0;
		int totalPicked = 0;

		for (int i = 0; i < count; i++) {
			List<Affix> drawn = AffixBudget.roll(prototype, rarity, rules, random);
			totalPicked += drawn.size();
			worstIndex = Math.max(worstIndex, ehpIndex(drawn, rarity));

			for (final String violation : violations(drawn, rarity)) {
				if (failures.size() < 8) {
					failures.add(violation + "  [" + String.join(" ", idsOf(drawn)) + "]");
				}
			}
		}

		prototype.discard();

		int rolls = count;
		int picked = totalPicked;
		double worst = worstIndex;
		int failureCount = failures.size();

		source.sendSuccess(() -> Component.translatable(KEY + "audit.done",
				rolls, rarity.name(), failureCount,
				String.format(Locale.ROOT, "%.2f", worst),
				String.format(Locale.ROOT, "%.2f", picked / (double) rolls),
				String.format(Locale.ROOT, "%.2f", Tuning.MAX_EHP_INDEX)), true);

		for (final String failure : failures) {
			source.sendFailure(Component.literal(failure));
		}

		return failureCount == 0 ? 1 : 0;
	}

	// --- /dire names <entity> <rarity> [count] ---

	/**
	 * The name generator's probe: draw {@code count} names for one mob type at one rarity,
	 * each with its own freshly rolled affix set, and print them.
	 *
	 * <p>It exists for the same reason {@code audit} does. A name is drawn from a weighted
	 * merge of three word banks and one of four shapes, so "does it produce varied names that
	 * relate to the affixes" is not a question playing the game can answer in an afternoon. It
	 * also reports the DISTINCT count, because a generator that returns one name a thousand
	 * times and a generator that returns a thousand names print the same first line otherwise.
	 *
	 * <p>Nothing is spawned. {@code EliteNames.generate} is called with a random UUID and the
	 * type's own family, which is exactly what the promotion passes it.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> namesCommand(
			final CommandBuildContext registries) {
		return Commands.literal("names")
				.then(Commands.argument("entity", ResourceArgument.resource(registries, Registries.ENTITY_TYPE))
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(monsterIds(), builder))
						.then(Commands.argument("rarity", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										rarityNames(), builder))
								.executes(context -> names(context, 8))
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 200))
										.executes(context -> names(context,
												IntegerArgumentType.getInteger(context, "count"))))));
	}

	private static int names(final CommandContext<CommandSourceStack> context, final int count)
			throws CommandSyntaxException {
		CommandSourceStack source = context.getSource();
		ServerLevel level = source.getLevel();
		String rarityArg = StringArgumentType.getString(context, "rarity");
		Rarity rarity = rarityByName(rarityArg);

		if (rarity == null) {
			source.sendFailure(Component.translatable(KEY + "spawn.unknownRarity", rarityArg,
					String.join(", ", rarityNames())));
			return 0;
		}

		Holder.Reference<EntityType<?>> type = ResourceArgument.getSummonableEntityType(context, "entity");
		Entity prototype = type.value().create(level, EntitySpawnReason.COMMAND);

		if (!(prototype instanceof Mob mob)) {
			source.sendFailure(Component.translatable(KEY + "spawn.notAMob",
					type.key().identifier().toString()));

			if (prototype != null) {
				prototype.discard();
			}

			return 0;
		}

		EliteRules rules = EliteRules.active();
		RandomSource random = level.getRandom();
		String family = EliteNames.familyOf(mob);
		Set<String> distinct = new LinkedHashSet<>();

		for (int i = 0; i < count; i++) {
			List<Affix> drawn = AffixBudget.roll(mob, rarity, rules, random);
			String name = EliteNames.generate(UUID.randomUUID(), family, rarity, idsOf(drawn));

			distinct.add(name);
			source.sendSuccess(() -> Component.translatable(KEY + "names.row", name,
					drawn.isEmpty() ? "-" : String.join(" ", idsOf(drawn))), false);
		}

		mob.discard();

		int unique = distinct.size();
		source.sendSuccess(() -> Component.translatable(KEY + "names.done", count, rarity.name(),
				family, unique, EliteNames.affixBankCount(), EliteNames.familyBankCount()), true);
		return 1;
	}

	// --- /dire freeze ---

	/**
	 * Screenshot helper: toggles NoAI on every elite within 32 blocks and turns them to face the
	 * caller. Frozen elites also skip their affix ticks (EliteTicker). Run again to release.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> freezeCommand() {
		return Commands.literal("freeze").executes(context -> {
			CommandSourceStack source = context.getSource();
			ServerLevel level = source.getLevel();
			net.minecraft.world.phys.Vec3 at = source.getPosition();
			List<Mob> elites = level.getEntitiesOfClass(Mob.class,
					new net.minecraft.world.phys.AABB(at, at).inflate(32.0), EliteMob::isElite);
			boolean freeze = false;

			for (final Mob mob : elites) {
				if (!mob.isNoAi()) {
					freeze = true;
					break;
				}
			}

			for (final Mob mob : elites) {
				mob.setNoAi(freeze);

				if (freeze) {
					mob.setTarget(null);
					mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
					double dx = at.x - mob.getX();
					double dz = at.z - mob.getZ();
					float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
					mob.setYRot(yaw);
					mob.setYHeadRot(yaw);
					mob.setYBodyRot(yaw);
					mob.setXRot(0.0F);
				}
			}

			int count = elites.size();
			boolean frozen = freeze;
			source.sendSuccess(() -> Component.literal((frozen ? "Froze " : "Released ") + count + " elites"), true);
			return count;
		});
	}

	// --- /dire artefact <id> ---

	private static LiteralArgumentBuilder<CommandSourceStack> artefactCommand() {
		return Commands.literal("artefact")
				.then(Commands.argument("id", StringArgumentType.word())
						.suggests((context, builder) -> {
							List<String> ids = new ArrayList<>();

							for (final Artefact artefact : Artefact.values()) {
								ids.add(artefact.id());
							}

							return SharedSuggestionProvider.suggest(ids, builder);
						})
						.executes(context -> {
							CommandSourceStack source = context.getSource();
							Artefact artefact = Artefact.byId(StringArgumentType.getString(context, "id"));

							if (artefact == null) {
								source.sendFailure(Component.literal("Unknown artefact"));
								return 0;
							}

							ServerPlayer player = source.getPlayerOrException();
							ItemStack stack = artefact.create(source.getLevel().registryAccess());

							player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);

							source.sendSuccess(() -> Component.literal("Gave ").append(stack.getHoverName()), true);
							return 1;
						}));
	}

	// --- /dire loot <rarity> <count> ---

	/**
	 * §2.4's table, rolled without a corpse — {@link EliteLoot#rollDrops} is the shipped path,
	 * not a copy. The drop path itself is player-kill gated, so this is the only way a headless
	 * server can see the table run at all.
	 */
	private static LiteralArgumentBuilder<CommandSourceStack> lootCommand() {
		return Commands.literal("loot")
				.then(Commands.argument("rarity", StringArgumentType.word())
						.suggests((context, builder) -> SharedSuggestionProvider.suggest(rarityNames(), builder))
						.executes(context -> loot(context, 1))
						.then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
								.executes(context -> loot(context,
										IntegerArgumentType.getInteger(context, "count")))));
	}

	private static int loot(final CommandContext<CommandSourceStack> context, final int count) {
		CommandSourceStack source = context.getSource();
		String name = StringArgumentType.getString(context, "rarity");
		Rarity rarity = rarityByName(name);

		if (rarity == null) {
			source.sendFailure(Component.translatable(KEY + "spawn.unknownRarity", name,
					String.join(", ", rarityNames())));
			return 0;
		}

		ServerLevel level = source.getLevel();
		Map<String, Integer> totals = new LinkedHashMap<>();

		for (int i = 0; i < count; i++) {
			for (final ItemStack stack : EliteLoot.rollDrops(level, rarity)) {
				totals.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
			}
		}

		StringBuilder rendered = new StringBuilder();

		for (final Map.Entry<String, Integer> entry : totals.entrySet()) {
			if (rendered.length() > 0) {
				rendered.append(", ");
			}

			rendered.append(entry.getValue()).append("x ").append(entry.getKey());
		}

		String body = rendered.length() == 0 ? "-" : rendered.toString();
		source.sendSuccess(() -> Component.translatable(KEY + "loot.done", count, rarity.name(),
				String.format(Locale.ROOT, "%.1f", rarity.xpMultiplier()), body), true);
		return 1;
	}

	// --- shared helpers ---

	/**
	 * Re-checks a finished set against §4.2's caps, §4.3's exclusions and §4.4 B1's index.
	 *
	 * <p><b>An independent re-derivation, not a call back into {@link AffixBudget}.</b> A probe
	 * that asked the roller whether the roller was right could only ever agree with itself. The
	 * caps and the index are re-read from {@code Tuning} and from {@code AffixClass}, so the
	 * only shared surface is the constants — which is the surface a retune moves.
	 */
	private static List<String> violations(final List<Affix> picked, final Rarity rarity) {
		List<String> found = new ArrayList<>();
		EliteRules rules = EliteRules.active();
		int budget = rarity.budget(rules);
		int pointCap = Tuning.classPointCap(budget);

		for (final AffixClass affixClass : AffixClass.values()) {
			int count = 0;
			int points = 0;

			for (final Affix affix : picked) {
				if (affix.affixClass() == affixClass) {
					count++;
					points += affix.cost();
				}
			}

			if (affixClass.countCap() > 0 && count > affixClass.countCap()) {
				found.add("class-cap " + affixClass + " count " + count + " > " + affixClass.countCap());
			}

			if (affixClass == AffixClass.DEFENSE && count > Tuning.MAX_DEFENSE_AFFIXES) {
				found.add("class-cap DEFENSE count " + count + " > " + Tuning.MAX_DEFENSE_AFFIXES);
			}

			if (affixClass.pointCapped() && points > pointCap) {
				found.add("class-cap " + affixClass + " points " + points + " > " + pointCap);
			}
		}

		for (int i = 0; i < picked.size(); i++) {
			for (int j = i + 1; j < picked.size(); j++) {
				if (Affixes.excluded(picked.get(i), picked.get(j))) {
					found.add("exclusion " + picked.get(i).id() + " x " + picked.get(j).id());
				}
			}
		}

		if (picked.size() > rarity.slots(rules)) {
			found.add("slots " + picked.size() + " > " + rarity.slots(rules));
		}

		int spent = 0;

		for (final Affix affix : picked) {
			spent += affix.cost();
		}

		if (spent > budget) {
			found.add("budget " + spent + " > " + budget);
		}

		double index = ehpIndex(picked, rarity);

		if (index > Tuning.MAX_EHP_INDEX) {
			found.add(String.format(Locale.ROOT, "ehp %.2f > %.2f", index, Tuning.MAX_EHP_INDEX));
		}

		return found;
	}

	private static double ehpIndex(final List<Affix> picked, final Rarity rarity) {
		double value = rarity.maxHealthMultiplier();

		for (final Affix affix : picked) {
			value *= affix.ehpFactor();
		}

		return value;
	}

	private static List<String> idsOf(final List<Affix> affixes) {
		List<String> ids = new ArrayList<>(affixes.size());

		for (final Affix affix : affixes) {
			ids.add(affix.id());
		}

		return ids;
	}

	private static List<String> affixIds() {
		return idsOf(Affixes.all());
	}

	private static List<String> rarityNames() {
		List<String> names = new ArrayList<>(Rarity.count() + 1);

		for (final Rarity rarity : Rarity.all()) {
			names.add(rarity.name().toLowerCase(Locale.ROOT));
		}

		names.add(ROLL);
		return names;
	}

	private static Rarity rarityByName(final String name) {
		for (final Rarity rarity : Rarity.all()) {
			if (rarity.name().equalsIgnoreCase(name)) {
				return rarity;
			}
		}

		return null;
	}

	/**
	 * Monster-category ids only — the set {@link EliteRoller} will actually promote.
	 *
	 * <p>A suggestion list, not a gate: the argument still accepts any entity type, because
	 * refusing one here would make the command unable to demonstrate that the eligibility check
	 * refuses it. The suggestions are the useful ones; the check is still the roller's.
	 */
	private static List<String> monsterIds() {
		List<String> ids = new ArrayList<>();

		for (final EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
			if (type.getCategory() == MobCategory.MONSTER) {
				ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
			}
		}

		return ids;
	}
}
