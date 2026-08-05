package com.escalation.platform;

import com.escalation.elite.EliteData;
import com.escalation.elite.EliteTimers;

import net.minecraft.world.entity.Entity;

/**
 * THE SEAM for every piece of state this mod hangs on an entity —
 * {@code docs/DESIGN.md} §6.3 and §6.6.
 *
 * <p>Two values, opposite rules, one interface:
 *
 * <ul>
 * <li>{@link EliteData} is <b>persistent</b> and <b>synced to every tracking client</b>. A
 *     reloaded chunk keeps its elites, and every client that can see one needs the rarity to
 *     draw the glow and the nameplate.</li>
 * <li>{@link EliteTimers} is <b>transient</b> and <b>server-only</b>. Cooldowns and the
 *     sustain lockout must not survive a reload; see that record for why.</li>
 * </ul>
 *
 * <p>This is Skill Proficiencies' named-method seam rather than Archetypes' generic
 * {@code StateKey} table, and the choice follows the same reasoning Archetypes' own
 * {@code ArchetypeStore} javadoc gives for going the other way: the table pays for itself at
 * 74 keys and ~260 call sites, and costs more than it saves at two. Nothing about the port is
 * foreclosed — a later {@code StateKey} table implements this interface without touching one
 * call site, which is the whole point of it being a seam.
 *
 * <p><b>Nothing outside this package may name {@code AttachmentTarget},
 * {@code AttachmentType}, {@code AttachmentRegistry}, {@code AttachmentSyncPredicate},
 * {@code PayloadTypeRegistry}, {@code ServerPlayNetworking}, {@code FabricLoader} or any
 * NeoForge/Forge API.</b> A call site anywhere else is drift, and a grep is the review gate —
 * the same gate both sibling repos use.
 *
 * <p>At port time {@link #INSTANCE} becomes a three-arm block-form chain repeating the whole
 * field declaration; the maintained multiloader template's one-line inline elif cannot chain,
 * measured in Skill Proficiencies' Stage 6.
 */
public interface EliteStore {
	EliteStore INSTANCE = new FabricEliteStore();

	/**
	 * Registers the storage, once, from common init — before any world can load. Eager on
	 * every platform for that reason.
	 */
	void initialize();

	/**
	 * The elite state, or {@code null} when this entity has never had any set.
	 *
	 * <p>Readable on the CLIENT as well as the server for a tracked entity: the persistent
	 * value is broadcast to trackers, which is how the renderer learns a rarity without this
	 * mod owning a packet. See {@code docs/DESIGN.md} §6.5 for the port-time consequence —
	 * below 1.20.5 there is no attachment sync at all, and that node needs the payload the
	 * design specifies. The seam is where that fork lands; no caller changes.
	 */
	EliteData get(Entity target);

	void set(Entity target, EliteData data);

	/** Transient timers, or {@code null} when this entity has none yet this session. */
	EliteTimers timers(Entity target);

	void setTimers(Entity target, EliteTimers timers);
}
