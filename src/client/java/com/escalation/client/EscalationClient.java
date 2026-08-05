package com.escalation.client;

import com.escalation.Escalation;

import net.fabricmc.api.ClientModInitializer;

/**
 * Client entrypoint. Scaffold only — it registers nothing yet.
 *
 * <p><b>The server decides everything</b>: rarity, affixes, loot, spawn control, the
 * progression score, the Hunt. This class is a renderer with no authority, which is the rule
 * both sibling mods hold ({@code docs/DESIGN.md} §6.2). Nothing here may mutate game state.
 *
 * <p>What lands here later, and why each one is here rather than behind the seam:
 *
 * <ul>
 * <li>The {@code ClientPlayNetworking} receiver for the elite-state payload —
 *     {@code com.escalation.platform} lives in {@code src/main}, which cannot see
 *     {@code net.minecraft.client} at all (measured in the sibling repos, not assumed), so a
 *     client receiver cannot be a seam member.</li>
 * <li>{@code EliteGlowLayer} / {@code EliteGlowRenderType} — registered through
 *     {@code LivingEntityRenderLayerRegistrationCallback}, no mixin. Note for the port: that
 *     class is named {@code LivingEntityFeatureRendererRegistrationCallback} on older
 *     fabric-api, which is a new predicate boundary to MEASURE, not to guess.</li>
 * <li>{@code EliteClientState} — entity id to rarity, fed by the payload and dropped on
 *     stop-tracking.</li>
 * </ul>
 *
 * <p>Scale needs none of this: {@code Attributes.SCALE} is syncable and drives both the
 * model and the hitbox through vanilla attribute sync. No packet, no mixin, no render code.
 */
public final class EscalationClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Escalation.LOGGER.info("{} client loaded (scaffold)", Escalation.MOD_ID);
	}
}
