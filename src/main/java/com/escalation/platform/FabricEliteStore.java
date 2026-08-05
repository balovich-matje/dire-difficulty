package com.escalation.platform;

import com.escalation.Escalation;
import com.escalation.elite.EliteData;
import com.escalation.elite.EliteTimers;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

/**
 * {@link EliteStore} on Fabric: two fabric-api attachments.
 *
 * <p>This is the ONLY file in the mod that names {@code AttachmentRegistry},
 * {@code AttachmentType}, {@code AttachmentTarget} or {@code AttachmentSyncPredicate}. The
 * seam-hygiene rule, and a grep is the review gate for it.
 *
 * <p><b>Why the state attachment syncs itself rather than riding a payload.</b> Design §6.5
 * specifies a clientbound payload on {@code START_TRACKING}, and the reason it gives is a
 * port reason: fabric-api 0.92.11 on the 1.20.1 node has the persistent attachment builder
 * and <b>no sync of any kind</b> — no {@code syncWith}, no {@code AttachmentSyncPredicate} —
 * so a payload is the one shape that works identically on all seven targets. That reasoning
 * is about where the fork lands, not about what this node does: on 26.2
 * {@code AttachmentSyncPredicate.all()} is the native broadcast and costs one builder call,
 * and the fork it avoids is confined to this file behind an interface no caller can see
 * through. When the legacy nodes arrive, the payload is written HERE, exactly as Archetypes
 * had to build {@code LegacyStateSync} in the same position — and no reader, ticker or
 * renderer changes.
 *
 * <p>The wire encoding itself is already portable: {@link EliteData#write} and
 * {@link EliteData#read} are spelled in {@code FriendlyByteBuf} primitives, which is the
 * oldest buffer type present on every target, so the bytes the payload will carry are the
 * bytes this attachment carries today.
 */
final class FabricEliteStore implements EliteStore {
	/**
	 * <b>Frozen ids.</b> These are the on-disk keys; renaming one orphans that value in every
	 * existing world. They are also why the mod id is permanent — see {@code Escalation}.
	 */
	private static final Identifier STATE_ID = Identifier.fromNamespaceAndPath(Escalation.MOD_ID, "elite_state");

	private static final Identifier TIMERS_ID = Identifier.fromNamespaceAndPath(Escalation.MOD_ID, "elite_timers");

	private AttachmentType<EliteData> state;

	private AttachmentType<EliteTimers> timers;

	FabricEliteStore() {
	}

	@Override
	public void initialize() {
		this.state = AttachmentRegistry.create(STATE_ID, builder -> builder
				.persistent(EliteData.CODEC)
				.syncWith(stateStreamCodec(), AttachmentSyncPredicate.all()));

		// No `persistent` and no `syncWith`: a cooldown that survived a reload would mean an
		// elite loaded back in already owing a shockwave it never wound up for, and no client
		// draws any of these.
		this.timers = AttachmentRegistry.create(TIMERS_ID);
	}

	/**
	 * Adapts the portable read/write pair to the payload stack's {@code StreamCodec}.
	 *
	 * <p>Typed on {@code FriendlyByteBuf} rather than {@code RegistryFriendlyByteBuf} on
	 * purpose: the builder wants a {@code StreamCodec<? super RegistryFriendlyByteBuf, A>},
	 * and nothing in {@link EliteData} is registry-bound — the affixes are this mod's own
	 * string ids — so the wider buffer type is both sufficient and honest about that.
	 */
	private static StreamCodec<FriendlyByteBuf, EliteData> stateStreamCodec() {
		return StreamCodec.of(EliteData::write, EliteData::read);
	}

	@Override
	public EliteData get(final Entity target) {
		return ((AttachmentTarget) target).getAttached(this.state);
	}

	@Override
	public void set(final Entity target, final EliteData data) {
		((AttachmentTarget) target).setAttached(this.state, data);
	}

	@Override
	public EliteTimers timers(final Entity target) {
		return ((AttachmentTarget) target).getAttached(this.timers);
	}

	@Override
	public void setTimers(final Entity target, final EliteTimers value) {
		((AttachmentTarget) target).setAttached(this.timers, value);
	}
}
