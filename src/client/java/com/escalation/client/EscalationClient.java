package com.escalation.client;

import com.escalation.Escalation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;

/**
 * Client entrypoint.
 *
 * <p><b>The server decides everything</b>: rarity, affixes, loot, spawn control, the
 * progression score, the Hunt. This class is a renderer with no authority, which is the rule
 * both sibling mods hold ({@code docs/DESIGN.md} §6.2). Nothing here may mutate game state,
 * and nothing here holds a copy of a balance number.
 *
 * <h2>The two things the client needs, and the two routes they take</h2>
 *
 * <h3>1. Scale — nothing. No packet, no mixin, no render code.</h3>
 * The server adds an {@code AttributeModifier} to {@code Attributes.SCALE} and vanilla does
 * the rest. Every link in that chain was read off the 26.2 sources rather than assumed:
 * <ul>
 * <li>{@code LivingEntity.createLivingAttributes()} adds {@code Attributes.SCALE}
 *     ({@code LivingEntity.java:344}), so <em>every</em> living entity has it — no
 *     per-entity-type registration is needed to make an arbitrary mob resizable.</li>
 * <li>{@code Attributes.SCALE} is a {@code RangedAttribute} — default 1.0, min 0.0625, max
 *     16.0 — declared {@code .setSyncable(true)} ({@code Attributes.java:89-90}). Syncable is
 *     what puts it in {@code ServerEntity}'s {@code getAttributesToSync()} sweep
 *     ({@code ServerEntity.java:342}) and onto the wire as
 *     {@code ClientboundUpdateAttributesPacket}.</li>
 * <li>On the client, {@code ClientPacketListener.handleUpdateAttributes}
 *     ({@code ClientPacketListener.java:2245}) writes the base value and re-adds the
 *     modifiers as transients. Each write marks the instance dirty, and the next
 *     {@code LivingEntity.tick()} ({@code LivingEntity.java:2739}) calls
 *     {@code refreshDirtyAttributes()}, which routes {@code SCALE} to
 *     {@code refreshDimensions()} ({@code LivingEntity.java:1137-1138}).</li>
 * <li><b>Hitbox:</b> {@code getDimensions(pose)} is
 *     {@code getDefaultDimensions(pose).scale(this.getScale())}
 *     ({@code LivingEntity.java:3705-3706}), and {@code refreshDimensions}
 *     ({@code Entity.java:3372}) re-reads it and re-derives the eye height. So the collision
 *     box and the eye height follow the attribute, on both sides, within one tick of the
 *     packet.</li>
 * <li><b>Model:</b> {@code LivingEntityRenderer.extractRenderState} sets
 *     {@code state.scale = entity.getScale()} ({@code LivingEntityRenderer.java:267}) and
 *     {@code submit} applies it as {@code poseStack.scale(scale, scale, scale)}.</li>
 * </ul>
 * Conclusion, and it is a measurement rather than a hope: one attribute modifier resizes the
 * model <em>and</em> the hitbox on every client, through vanilla sync, with no code in this
 * package. The behaviour to confirm in the in-game pass is the doorway consequence
 * {@code docs/DESIGN.md} §2.1 already calls out — a 1.95-block zombie stops fitting a
 * 2-block gap above roughly 1.026x, so elites path around instead of following you in.
 *
 * <h3>2. Rarity — the synced attachment, read live.</h3>
 * {@code FabricEliteStore} registers {@code EliteData} with a sync predicate covering every
 * tracker, so each client already holds it and {@code EliteStore.INSTANCE.get(entity)}
 * answers here. The attachment API is named only inside {@code platform/}, per the seam rule.
 * {@link EliteClientState} is the reader, {@code LivingEntityRendererMixin} carries the
 * answer onto the render state, and {@link EliteGlowLayer} draws it. <b>No payload, no
 * receiver, no client-side cache</b> — see {@link EliteClientState} for why building one
 * would be strictly worse, and {@link EliteGlowRenderType} for why the glow is occluded by
 * walls when vanilla's glowing outline is not.
 *
 * <h2>Shape this class keeps when the port comes</h2>
 * Only the class declaration and the {@code @Override} are Fabric-only tokens.
 * <ul>
 * <li>The layer registration forks:
 *     {@code LivingEntityRenderLayerRegistrationCallback} is named
 *     {@code LivingEntityFeatureRendererRegistrationCallback} on older fabric-api, and both
 *     loader nodes have their own registration event entirely. That is a new predicate
 *     boundary to MEASURE, not to guess.</li>
 * <li>Below 1.20.5 there is no attachment sync, so those nodes need the payload
 *     {@code docs/DESIGN.md} §6.5 specifies. It is written inside
 *     {@code platform/FabricEliteStore} — behind the seam — and its client receiver is the
 *     one line that would land in this method there. Nothing else in this package moves,
 *     because nothing else in this package knows how the value arrived.</li>
 * </ul>
 */
public final class EscalationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// One glow layer on every living renderer. NOT filtered to MobCategory.MONSTER on
		// purpose: eligibility is a server rule (docs/DESIGN.md §2.3 — the MONSTER test plus
		// the escalation:elite_blacklist tag), and a second copy of it here would fail
		// silently the day that rule widens. The layer draws whatever the server marked and
		// nothing otherwise; see EliteGlowLayer.
		LivingEntityRenderLayerRegistrationCallback.EVENT.register(
				(entityType, renderer, helper, context) -> EliteGlowLayer.attach(renderer, helper));

		Escalation.LOGGER.info("{} client loaded (elite rarity glow)", Escalation.MOD_ID);
	}
}
