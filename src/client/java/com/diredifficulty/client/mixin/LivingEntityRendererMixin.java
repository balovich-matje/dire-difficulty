package com.diredifficulty.client.mixin;

import com.diredifficulty.client.EliteClientState;
import com.diredifficulty.elite.EliteData;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;

/**
 * Carries "this entity is an elite" across the extraction boundary, from the entity (which
 * the synced attachment hangs on) to the render state (which the glow layer is handed).
 *
 * <h2>Why a mixin exists at all, when {@code docs/DESIGN.md} §6.4 says the glow needs none</h2>
 * The design's table is right about the <b>layer</b>: {@code EliteGlowLayer} is registered
 * through {@code LivingEntityRenderLayerRegistrationCallback} and mixes into nothing. What
 * the table does not cover is how the layer learns <em>which</em> entity it is drawing.
 * {@code RenderLayer.submit} receives {@code (PoseStack, SubmitNodeCollector, int light,
 * S state, float yRot, float xRot)} — the state and nothing else — and
 * {@code EntityRenderState} carries no entity id and no entity reference.
 *
 * <p>fabric-rendering-v1 25.3.1+6988455e9e — the 26.2 module — ships
 * {@code RenderStateDataKey} and {@code FabricRenderState} for exactly this purpose but no
 * event that populates them; its whole class list was checked for one. So the write has to
 * happen inside extraction, the last place the entity and its state are both in hand.
 * Archetypes solved the identical problem the identical way ({@code AvatarRendererMixin}).
 * One injector, no game logic, nothing conditional, no balance number.
 *
 * <h2>The descriptor is mandatory, not decoration</h2>
 * {@code LivingEntityRenderer} carries <b>two</b> methods named {@code extractRenderState}:
 * the real one and javac's bridge for {@code EntityRenderer}'s erasure. Confirmed with
 * {@code javap -s} on the 26.2 client jar:
 * <pre>
 * (Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V
 * (Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V
 * </pre>
 * A bare {@code method = "extractRenderState"} is ambiguous between them. The full descriptor
 * pins the real one, which is also what conventions §5h requires of every mixin in these
 * repos, and it keeps {@code injectors.defaultRequire: 1} meaningful — exactly one target
 * must resolve, on every node the port later adds.
 *
 * <h2>TAIL, and the one assumption it rests on</h2>
 * {@code TAIL} rather than {@code HEAD}, so nothing vanilla does later in the method can
 * clobber the write. Correctness relies on render states being <b>fresh per entity per
 * frame</b>, which they are on 26.2: {@code EntityRenderer.createRenderState(T, float)} at
 * line 155 opens with {@code S state = this.createRenderState();} — a new object — and only
 * then extracts into it. That is why the key is written only for elites instead of being
 * cleared for everyone: with no pooling there is nothing stale to clear, and an unconditional
 * {@code setData} would force fabric's backing map to be allocated for every living entity
 * every frame. <b>If a later version starts pooling render states this becomes a stale-glow
 * bug</b>, and the fix is to write a "not an elite" sentinel unconditionally — re-check that
 * line on any version bump.
 *
 * <h2>Cost</h2>
 * One attachment lookup per living entity per frame, through the seam. That is cheaper than
 * the id-keyed cache the design sketches and cannot disagree with the server, because it
 * <b>is</b> the synced value rather than a copy of it.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	@Inject(
			method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;"
					+ "Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
			at = @At("TAIL"))
	private void direDifficulty$extractEliteState(final LivingEntity entity,
			final LivingEntityRenderState state, final float partialTicks, final CallbackInfo ci) {
		EliteData elite = EliteClientState.eliteOf(entity);

		if (elite != null) {
			((FabricRenderState) state).setData(EliteClientState.RENDER_KEY, elite);
		}
	}
}
