package com.diredifficulty.client;

import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.Rarity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;

/**
 * The rarity glow: two depth-tested emissive passes over the elite's own model, breathing
 * on a slow pulse. Walls occlude both.
 *
 * <h2>What changed after the first in-game look, and why</h2>
 * Iteration 1 drew ONE pass — the mob's model re-submitted through
 * {@link EliteGlowRenderType} at a flat {@code 0x66} of the rarity colour. The report back
 * was that a Legendary zombie "simply looked a bit yellow": it read as a skin TINT, not as
 * a glow. The diagnosis is in the pipeline rather than in the number.
 * {@code ENTITY_TRANSLUCENT_EMISSIVE} multiplies the vertex colour by the model texture, so
 * a 40%-alpha orange over a zombie's dark green-grey skin can only ever come out as a muted
 * wash — full-bright, but still bounded by how bright the mob's own texture is, and entirely
 * inside the silhouette where there is nothing to read as light spilling off a body.
 *
 * <p>Three things fix that, and all three are dials in {@link Tuning}:
 *
 * <ol>
 * <li><b>Alpha that actually climbs the ladder.</b> {@link Rarity#glowSkinAlpha()} runs
 *     {@code 0x40 / 0x70 / 0xA0 / 0xCC}. Uncommon stays a hint — it must not make a common
 *     spawn look like an event — and Legendary is meant to be unmistakable at thirty
 *     blocks.</li>
 * <li><b>A halo.</b> {@link #submitHalo} re-submits the same model inflated by
 *     {@link Rarity#glowHaloInflate()}. Because the pipeline is {@code withCull(false)} the
 *     shell's BACK faces are drawn too — and they sit behind the mob's own body, which
 *     already wrote depth in the opaque pass, so they survive only in the rim that extends
 *     past the silhouette. The rim therefore receives both the shell's near and far faces
 *     while the middle receives one. That gradient off the edge of the body is the thing a
 *     player reads as "glowing", and no amount of alpha on a single in-silhouette pass can
 *     produce it.</li>
 * <li><b>Motion.</b> {@link Tuning#glowPulse} breathes both passes together on a 2.5-second
 *     sine. A static colour is furniture; a moving one is a signal.</li>
 * </ol>
 *
 * <h2>The occlusion guarantee is untouched, and that is the point</h2>
 * {@code docs/DESIGN.md} §2.2's hard rule is that the glow must be occluded by walls, which
 * vanilla's glowing outline structurally cannot be. Nothing here weakens it:
 *
 * <ul>
 * <li>Both passes go through the same {@link EliteGlowRenderType}, whose
 *     {@code DepthStencilState(GREATER_THAN_OR_EQUAL, false)} depth-TESTS and does not
 *     depth-WRITE. The halo is geometry in the scene like any other — terrain in front of it
 *     fails it out.</li>
 * <li>The shell is inflated by at most 7.5%, so at a zombie's 0.6-block half-width it
 *     protrudes about 2 cm. It cannot poke through a wall the mob is standing against in any
 *     way the mob's own model does not already.</li>
 * <li>{@code affectsOutline = false} on the render type and {@code NO_OUTLINE} on every
 *     submit keep both passes out of {@code entityOutlineTarget}, whose blit is the thing
 *     that x-rays.</li>
 * </ul>
 *
 * <p><b>It is still not a light source.</b> The emissive define means the pass takes no
 * lightmap, so an elite reads at the bottom of a cave — but nothing is written into the
 * level's block light, so the floor under it stays dark and a Legendary standing in a field
 * at midnight does not wash the screen out. The alpha ceiling is 0xCC on a translucent
 * blend, i.e. the mob is never drawn as an opaque coloured statue.
 *
 * <h2>Shape kept from iteration 1</h2>
 * Copied from vanilla's {@code EyesLayer}: re-submit {@code getParentModel()} through a
 * different render type on top of the base pass. {@code LivingEntityRenderer.submit} runs its
 * layer loop <em>inside</em> the same {@code pushPose()} that already applied the entity's
 * scale, rotations and the {@code -1.501} model offset, and calls
 * {@code this.model.setupAnim(state)} immediately before the loop — so both passes land on
 * exactly the posed geometry of the first, the glow cannot drift out of step with the
 * animation, and it follows the {@code Attributes.SCALE} upsize for free.
 *
 * <p><b>Colour and every dial come from {@link Tuning}, read live.</b> This class holds no
 * copy of a number. That matters more than it looks: Archetypes mirrored Skill
 * Proficiencies' {@code HUD_SHIFT = 7} as a client constant, Skill Proficiencies then made
 * that number 0 at runtime through a config toggle, and both builds compiled and drew —
 * seven pixels apart. Iteration 1 kept {@code GLOW_ALPHA} here on the sibling repos'
 * precedent for pure render dials; it has moved to {@code Tuning} on the standing
 * instruction that balance AND visual constants live in that one file.
 *
 * <h2>Why one layer on every living renderer, not a monsters-only registration</h2>
 * Eligibility for eliting is a <b>server</b> decision ({@code docs/DESIGN.md} §2.3: the
 * {@code MobCategory.MONSTER} test plus an {@code dire_difficulty:elite_blacklist} tag, both
 * evaluated where the roll happens). Repeating that predicate here would be a second copy of
 * a rule that can change, and its failure mode is the quiet one — the day a pack or a later
 * iteration elites something outside {@code MONSTER}, the mob would carry a rarity and simply
 * not glow, with nothing logged. So the layer goes on every living entity type and draws
 * whatever the server marked. The cost is one virtual call and one null check per living
 * entity per frame; the render state has already been extracted either way.
 *
 * <h2>What it does not draw</h2>
 * <ul>
 * <li><b>Invisible mobs.</b> An invisible elite writes no depth, so an unculled translucent
 *     shell would show its far side through its near side and give away a mob vanilla says
 *     you cannot see. Same check vanilla's {@code LivingEntityEmissiveLayer} makes.</li>
 * <li><b>Suppressed state, or a rarity ordinal this build does not know.</b> Both come back
 *     as {@code null} from {@link EliteClientState#eliteOf}, and a stale-versioned ordinal
 *     resolves to {@code null} from {@link EliteData#rarity()} as well — checked here too,
 *     because the render state was written a frame ago and a null there must not throw inside
 *     the render loop.</li>
 * </ul>
 */
public final class EliteGlowLayer<S extends LivingEntityRenderState, M extends EntityModel<S>>
		extends RenderLayer<S, M> {
	/**
	 * Submit order. The base model is order 0 ({@code SubmitNodeStorage} routes every
	 * un-ordered submit through {@code this.order(0)}), and every vanilla overlay layer —
	 * eyes, breeze eyes, wolf collar, horse markings, the emissive layer — uses 1. Both glow
	 * passes belong with them.
	 *
	 * <p>Order barely matters for correctness here, and that is worth writing down: neither
	 * pass writes depth, and armour is drawn opaque at a depth strictly in front of the body,
	 * so an armour-covered part of the body fails the {@code GREATER_THAN_OR_EQUAL} test
	 * whichever order the three land in. An armoured elite glows on its bare parts and keeps
	 * its halo, which is the honest answer.
	 */
	private static final int GLOW_ORDER = 1;

	private final LivingEntityRenderer<?, S, M> renderer;

	private EliteGlowLayer(final LivingEntityRenderer<?, S, M> renderer) {
		super(renderer);
		// Kept in addition to RenderLayer's own parent field because the layer needs
		// getTextureLocation(state), and RenderLayerParent exposes only getModel().
		this.renderer = renderer;
	}

	/**
	 * Attach a glow layer to one renderer. Called from
	 * {@code LivingEntityRenderLayerRegistrationCallback}.
	 *
	 * <p>The raw types are forced by the fabric-api signature and are safe at runtime.
	 * {@code RegistrationHelper.register} is declared
	 * {@code <T extends EntityRenderState> void register(RenderLayer<T, ? extends EntityModel<T>>)},
	 * while the callback hands over a {@code LivingEntityRenderer<?, ?, ?>} whose model is
	 * only {@code EntityModel<? super S>} — the two cannot be reconciled generically. The
	 * erased call is well typed: {@code RegistrationHelperImpl.register} does nothing but
	 * forward to the renderer's own {@code addLayer} (verified by bytecode), and
	 * {@code OrderedSubmitNodeCollector.submitModel} erases to {@code (Model, Object, …)}.
	 * The layer is only ever handed the state its own renderer extracted.
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static void attach(final LivingEntityRenderer<?, ?, ?> renderer,
			final LivingEntityRenderLayerRegistrationCallback.RegistrationHelper helper) {
		helper.register(new EliteGlowLayer(renderer));
	}

	@Override
	public void submit(final PoseStack poseStack, final SubmitNodeCollector collector,
			final int lightCoords, final S state, final float yRot, final float xRot) {
		if (state.isInvisible) {
			return;
		}

		// Written at extraction by LivingEntityRendererMixin, because a RenderLayer is handed
		// the state and nothing else, and EntityRenderState carries no entity id. The cast is
		// how every call site reaches FabricRenderState: the interface is injected onto
		// EntityRenderState by fabric-rendering-v1's class tweaker, so an interface cast
		// compiles whether or not javac can see the injected supertype.
		EliteData elite = ((FabricRenderState) state).getData(EliteClientState.RENDER_KEY);

		if (elite == null) {
			return;
		}

		Rarity rarity = elite.rarity();

		if (rarity == null) {
			return;
		}

		// One pulse value for both passes, off the entity's OWN age, so a pack of elites
		// breathes out of phase with itself instead of strobing in lockstep.
		double pulse = Tuning.glowPulse(state.ageInTicks);
		RenderType glow = EliteGlowRenderType.forEntityTexture(this.renderer.getTextureLocation(state));

		submitGlow(collector, poseStack, state, glow, lightCoords,
				Tuning.glowAlpha(rarity.glowSkinAlpha(), pulse), rarity.colour());
		submitHalo(collector, poseStack, state, glow, lightCoords, rarity, pulse);
	}

	/**
	 * The inflated second pass. Scaled about {@link Tuning#GLOW_HALO_PIVOT_Y} rather than
	 * about the raw pose origin: at layer time the origin is the mob's shoulders and +Y runs
	 * down to the feet at 1.5, so scaling about it would push a Legendary's feet through the
	 * floor by about 11 cm. The pivot halves and centres that drift.
	 *
	 * <p>The push/pop is safe against the submit that happens inside it because
	 * {@code SubmitNodeCollection.submitModel} takes {@code poseStack.last().copy()} — the
	 * node keeps a snapshot, not a reference, so popping afterwards cannot reach back into a
	 * queued draw.
	 */
	private void submitHalo(final SubmitNodeCollector collector, final PoseStack poseStack,
			final S state, final RenderType glow, final int lightCoords, final Rarity rarity,
			final double pulse) {
		float inflate = (float) rarity.glowHaloInflate();
		int alpha = Tuning.glowAlpha(rarity.glowHaloAlpha(), pulse);

		if (inflate <= 1.0F || alpha <= 0) {
			return;
		}

		float pivot = (float) Tuning.GLOW_HALO_PIVOT_Y;

		poseStack.pushPose();
		poseStack.translate(0.0F, pivot, 0.0F);
		poseStack.scale(inflate, inflate, inflate);
		poseStack.translate(0.0F, -pivot, 0.0F);
		submitGlow(collector, poseStack, state, glow, lightCoords, alpha, rarity.colour());
		poseStack.popPose();
	}

	/** One emissive pass of the parent model in {@code rgb} at {@code alpha}. */
	private void submitGlow(final SubmitNodeCollector collector, final PoseStack poseStack,
			final S state, final RenderType glow, final int lightCoords, final int alpha,
			final int rgb) {
		collector.order(GLOW_ORDER).submitModel(
				this.getParentModel(),
				state,
				poseStack,
				glow,
				// Ignored: ENTITY_EMISSIVE_SNIPPET binds SAMPLER0 only and takes no lightmap,
				// which is what makes an elite readable at the bottom of a cave. Passed anyway
				// so the call reads like every other submitModel in the tree.
				lightCoords,
				// NO_OVERLAY, not getOverlayCoords: the base model already flashes red when
				// hurt, and a rarity indicator that flashes with it is harder to read, not
				// easier. Vanilla's EyesLayer makes the same choice.
				OverlayTexture.NO_OVERLAY,
				ARGB.color(alpha, rgb),
				null,
				// NO_OUTLINE, and this is the second half of the occlusion guarantee. The
				// render type already declines the outline pass (affectsOutline=false); a
				// non-zero outline colour here would be the other way into entityOutlineTarget,
				// whose blit ignores scene depth. Both doors stay shut.
				EntityRenderState.NO_OUTLINE);
	}
}
