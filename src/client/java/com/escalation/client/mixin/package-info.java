/**
 * Client-only mixins. One class, and it is not the glow.
 *
 * <p>{@code docs/DESIGN.md} §6.4 is right that the rarity glow itself needs no mixin — it is
 * a {@code RenderLayer} registered through fabric-api's
 * {@code LivingEntityRenderLayerRegistrationCallback}. What the design table does not cover
 * is the step before it: a {@code RenderLayer} is handed a render state and nothing else, and
 * {@code EntityRenderState} carries no entity id, so "is this entity an elite?" has to be
 * answered while the entity is still in hand. {@link
 * com.escalation.client.mixin.LivingEntityRendererMixin} is that one hop and does nothing
 * else. See its javadoc for the descriptor and the freshness assumption it rests on.
 *
 * <p>Nothing else is expected here. The scale route needs no client code at all
 * ({@code Attributes.SCALE} is syncable and drives both the model and the hitbox through
 * vanilla attribute sync), and iteration 1 draws no HUD element beyond the {@code stalker}
 * tell (§9).
 */
package com.escalation.client.mixin;
