package com.escalation.client;

import com.escalation.elite.EliteData;
import com.escalation.elite.Rarity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;

/**
 * Draws the elite's own model a second time in its rarity colour — a depth-tested emissive
 * sheen that walls occlude.
 *
 * <p>Shape copied from vanilla's {@code EyesLayer}, which does the same thing: re-submit
 * {@code getParentModel()} through a different render type on top of the base pass. Because
 * {@code LivingEntityRenderer.submit} runs its layer loop <em>inside</em> the same
 * {@code poseStack.pushPose()} that already applied the entity's scale, rotations and the
 * {@code -1.501} model offset — and calls {@code this.model.setupAnim(state)} immediately
 * before the loop — the second submit lands on exactly the posed geometry of the first. No
 * transform is re-derived here, so the glow cannot drift out of step with the animation, and
 * it follows the {@code Attributes.SCALE} upsize for free because that is applied to the same
 * pose stack.
 *
 * <h2>Colour comes from the server's table, read live</h2>
 * {@link Rarity#colour()} returns {@code Tuning.RARITY_COLOUR[ordinal]}. This class holds no
 * copy of it. That matters more than it looks: Archetypes mirrored Skill Proficiencies'
 * {@code HUD_SHIFT = 7} as a client constant, Skill Proficiencies then made that number 0 at
 * runtime through a config toggle, and both builds compiled and drew — seven pixels apart.
 * {@code docs/DESIGN.md} records the same rule for this repo twice (§5.1's
 * {@code OptionalDouble}, §6.7's HUD note): read live, never mirror.
 *
 * <p>{@link #GLOW_ALPHA} is the exception and it is not a balance number — it is the render
 * dial that decides how strongly the sheen reads, the same role
 * {@code SkillXpHudBar.BASE_ALPHA} plays in Skill Proficiencies and
 * {@code NightEyesLayer.FAINT_TINT} in Archetypes. Both siblings keep that constant in the
 * client class; this follows them.
 *
 * <h2>Why one layer on every living renderer, not a monsters-only registration</h2>
 * Eligibility for eliting is a <b>server</b> decision ({@code docs/DESIGN.md} §2.3: the
 * {@code MobCategory.MONSTER} test plus an {@code escalation:elite_blacklist} tag, both
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
	 * How strongly the sheen reads, 0..255, applied over {@link Rarity#colour()}'s RGB.
	 *
	 * <p>A pure render dial, not balance data — see the class javadoc. It is meaningful only
	 * because {@code ENTITY_TRANSLUCENT_EMISSIVE} blends {@code TRANSLUCENT}: on a pipeline
	 * that blends {@code ONE, ONE} source alpha is not a factor at all, which is the
	 * measured trap {@link EliteGlowRenderType} documents for the port.
	 *
	 * <p>{@code 0x66} is "clearly a different mob at a glance, still recognisably a zombie" —
	 * low enough that the skin reads through it, high enough to spot down a corridor. Raise it
	 * and elites start looking like solid coloured statues.
	 */
	private static final int GLOW_ALPHA = 0x66;

	/**
	 * Submit order. The base model is order 0 ({@code SubmitNodeStorage} routes every
	 * un-ordered submit through {@code this.order(0)}), and every vanilla overlay layer —
	 * eyes, breeze eyes, wolf collar, horse markings, the emissive layer — uses 1. The glow
	 * belongs with them.
	 *
	 * <p>Order barely matters for correctness here, and that is worth writing down: this pass
	 * does not write depth, and armour is drawn opaque at a depth strictly in front of the
	 * body, so an armour-covered part of the body fails the {@code GREATER_THAN_OR_EQUAL}
	 * test whichever order the two land in. An armoured elite glows on its bare parts, which
	 * is the honest answer.
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

		collector.order(GLOW_ORDER).submitModel(
				this.getParentModel(),
				state,
				poseStack,
				EliteGlowRenderType.forEntityTexture(this.renderer.getTextureLocation(state)),
				// Ignored: ENTITY_EMISSIVE_SNIPPET binds SAMPLER0 only and takes no lightmap,
				// which is what makes an elite readable at the bottom of a cave. Passed anyway
				// so the call reads like every other submitModel in the tree.
				lightCoords,
				// NO_OVERLAY, not getOverlayCoords: the base model already flashes red when
				// hurt, and a rarity indicator that flashes with it is harder to read, not
				// easier. Vanilla's EyesLayer makes the same choice.
				OverlayTexture.NO_OVERLAY,
				ARGB.color(GLOW_ALPHA, rarity.colour()),
				null,
				// NO_OUTLINE, and this is the second half of the occlusion guarantee. The
				// render type already declines the outline pass (affectsOutline=false); a
				// non-zero outline colour here would be the other way into entityOutlineTarget,
				// whose blit ignores scene depth. Both doors stay shut.
				EntityRenderState.NO_OUTLINE,
				null);
	}
}
