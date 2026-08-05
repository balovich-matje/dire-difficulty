package com.escalation.elite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The whole persistent elite state of one entity, in one immutable value —
 * {@code docs/DESIGN.md} §6.3's key table, collapsed.
 *
 * <p><b>Why one record instead of the seven separate keys the design tables.</b> Archetypes
 * needs a generic {@code StateKey} table because it has 74 keys with 47 sync scopes across
 * ~260 call sites; one method per value there would be 150 interface methods. This mod has
 * seven values on one subject, they are read and written together, and every one of them is
 * meaningless without {@link #rarityOrdinal}. A record is the same data with a fifth of the
 * machinery, and it makes the invariant "not elite implies no affixes and no flags" a thing
 * the type system can hold rather than a thing seven call sites have to remember.
 *
 * <p><b>Affixes are stored as string ids, never as ordinals or a bitmask.</b> A registry
 * grows and reorders between versions; a string key does not. This is how Skill Proficiencies
 * persists external skills and it is the same reasoning — an ordinal that shifts silently
 * re-points every elite already saved in a world.
 *
 * <p><b>What crosses the wire.</b> The client needs rarity for the glow and the nameplate,
 * and scale needs nothing at all ({@code Attributes.SCALE} is syncable and drives both model
 * and hitbox through vanilla). The whole record is written rather than a projection, because
 * a projection is a second format to keep in step and none of these five fields is a secret.
 * {@link #write} / {@link #read} are spelled in {@code FriendlyByteBuf} primitives on purpose:
 * that is the oldest buffer type that exists on every port target, so the format survives the
 * 1.20.5 payload-stack boundary unchanged.
 */
public record EliteData(int rarityOrdinal, List<String> affixes, boolean escapistUsed,
		int splitGeneration, boolean noElite) {

	/** {@link #rarityOrdinal} for an entity that carries state but is not an elite. */
	public static final int NOT_ELITE = -1;

	/**
	 * Stamped on summons and split copies so they can never themselves roll elite, summon or
	 * split. Persistent on purpose — a chunk reload must not launder a summon into a
	 * promotion candidate.
	 */
	public static final EliteData SUPPRESSED =
			new EliteData(NOT_ELITE, Collections.emptyList(), false, 0, true);

	public static final Codec<EliteData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("rarity", NOT_ELITE).forGetter(EliteData::rarityOrdinal),
			Codec.STRING.listOf().optionalFieldOf("affixes", Collections.emptyList())
					.forGetter(EliteData::affixes),
			Codec.BOOL.optionalFieldOf("escapist_used", false).forGetter(EliteData::escapistUsed),
			Codec.INT.optionalFieldOf("split_generation", 0).forGetter(EliteData::splitGeneration),
			Codec.BOOL.optionalFieldOf("no_elite", false).forGetter(EliteData::noElite))
			.apply(i, EliteData::new));

	/** Defensive copy, so a caller cannot mutate a list this record already published. */
	public EliteData {
		affixes = List.copyOf(affixes);
	}

	public static EliteData of(final Rarity rarity, final List<String> affixes) {
		return new EliteData(rarity.ordinal(), affixes, false, 0, false);
	}

	/** {@code null} when this entity is not an elite, or carries a rarity a newer build wrote. */
	public Rarity rarity() {
		return this.rarityOrdinal == NOT_ELITE ? null : Rarity.byOrdinal(this.rarityOrdinal);
	}

	public boolean isElite() {
		return this.rarity() != null;
	}

	public EliteData withEscapistUsed() {
		return new EliteData(this.rarityOrdinal, this.affixes, true, this.splitGeneration, this.noElite);
	}

	public EliteData withSplitGeneration(final int generation) {
		return new EliteData(this.rarityOrdinal, this.affixes, this.escapistUsed, generation, this.noElite);
	}

	public EliteData withNoElite() {
		return new EliteData(this.rarityOrdinal, this.affixes, this.escapistUsed, this.splitGeneration, true);
	}

	/**
	 * Portable wire encoding. Byte-identical to what the equivalent {@code ByteBufCodecs}
	 * composition would produce — var-int, then a var-int-counted list of UTF-8 strings, then
	 * two bytes and a var-int — because a client and a server built from different nodes of
	 * the same Minecraft version have to stay compatible.
	 */
	public static void write(final FriendlyByteBuf buf, final EliteData data) {
		buf.writeVarInt(data.rarityOrdinal());
		buf.writeVarInt(data.affixes().size());

		for (final String id : data.affixes()) {
			buf.writeUtf(id);
		}

		buf.writeBoolean(data.escapistUsed());
		buf.writeVarInt(data.splitGeneration());
		buf.writeBoolean(data.noElite());
	}

	public static EliteData read(final FriendlyByteBuf buf) {
		int rarity = buf.readVarInt();
		int size = buf.readVarInt();
		List<String> affixes = new ArrayList<>(size);

		for (int i = 0; i < size; i++) {
			affixes.add(buf.readUtf());
		}

		return new EliteData(rarity, affixes, buf.readBoolean(), buf.readVarInt(), buf.readBoolean());
	}
}
