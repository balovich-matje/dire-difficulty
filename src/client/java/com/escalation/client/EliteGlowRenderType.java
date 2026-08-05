package com.escalation.client;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/**
 * The render type the rarity glow draws through, and the record of why it is the right one.
 *
 * <p>This class exists on 26.2 to hold one call. That is on purpose: {@code docs/DESIGN.md}
 * §6.1 names it "the file that forks hardest at port time", and keeping the pipeline choice
 * behind one method means the port forks <em>this</em> file rather than every draw call in
 * {@link EliteGlowLayer}. Archetypes' {@code NightEyeRenderType} is the same shape for the
 * same reason.
 *
 * <h2>The requirement: the glow must be occluded by walls</h2>
 * Vanilla's glowing outline is not, and that is structural rather than a setting. Read off
 * the 26.2 sources:
 * <ul>
 * <li>{@code Entity.setGlowingTag(boolean)} sets shared entity flag 6.</li>
 * <li>Flagged entities are rendered a <b>second time into a separate render target</b>,
 *     {@code LevelRenderer.entityOutlineTarget}, which is blitted over the finished frame
 *     with {@code RenderPipelines.ENTITY_OUTLINE_BLIT} ({@code RenderPipelines.java:806}).</li>
 * <li>That blit does not participate in the scene depth buffer. That <b>is</b> why the
 *     vanilla outline x-rays, and it cannot be configured away.</li>
 * </ul>
 * So the glow is a real geometry pass on the mob's own model instead, and the pipeline it
 * uses has to depth-test.
 *
 * <h2>The measurement — {@code ENTITY_TRANSLUCENT_EMISSIVE}, 26.2</h2>
 * {@code RenderPipelines.java:353-363}, verbatim state:
 * <pre>
 * RenderPipeline.builder(ENTITY_EMISSIVE_SNIPPET)          // core/entity shaders, EMISSIVE define,
 *                                                          // BindGroupLayouts.SAMPLER0 -> NO lightmap
 *     .withLocation("pipeline/entity_translucent_emissive")
 *     .withShaderDefine("ALPHA_CUTOUT", 0.1F)
 *     .withShaderDefine("PER_FACE_LIGHTING")
 *     .withBindGroupLayout(BindGroupLayouts.SAMPLER1)      // overlay sampler
 *     .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
 *     .withCull(false)
 *     .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
 * </pre>
 * Four of those lines are load-bearing here:
 * <ol>
 * <li><b>{@code DepthStencilState(GREATER_THAN_OR_EQUAL, false)}</b> — reversed-Z: depth
 *     test ON, depth write OFF. Geometry in front of the mob has the larger depth value, so
 *     the glow's fragments fail the test behind a wall. <b>Requirement met.</b> Depth write
 *     off is what lets the second pass sit at exactly the same depth as the base model
 *     without z-fighting, which is also why the glow needs no inflation to hug the
 *     silhouette — it <i>is</i> the silhouette.</li>
 * <li><b>{@code BlendFunction.TRANSLUCENT}</b> — source alpha is a real dial. This is the
 *     trap Archetypes' Stage-7 in-game pass paid for: below 1.21.11 vanilla's
 *     {@code RenderType.eyes} blends through {@code ADDITIVE_TRANSPARENCY}, which is
 *     {@code blendFunc(ONE, ONE)}, and source alpha is <b>not a factor in that blend at
 *     all</b> — an alpha dial written against 26.2 comes out silently inert and a soft glow
 *     renders as a solid band. Translucent was chosen partly for that reason, but the port
 *     <b>must re-measure the pipeline state per node</b> rather than assume it carried.
 *     {@code NightEyeRenderType} demonstrates the fix shape if it has not: take vanilla's own
 *     composite and re-apply exactly one state shard on top of it.</li>
 * <li><b>The {@code EMISSIVE} define with no lightmap sampler</b> — the pass is full-bright
 *     regardless of the block light the mob stands in, so an elite reads at the bottom of a
 *     cave. This is not a light source: nothing is placed in the level and no block light is
 *     written. The mob glows; the floor under it does not.</li>
 * <li><b>{@code ALPHA_CUTOUT 0.1}</b> — the glow inherits the model texture's own cutout, so
 *     transparent regions of the skin (a mob's second "hat" layer, a spider's leg gaps) stay
 *     transparent in the glow too. That is the reason the glow samples the entity's real
 *     texture rather than a flat white sheet: a flat sheet would fill those holes in.</li>
 * </ol>
 *
 * <h2>{@code affectsOutline = false}, and it matters</h2>
 * The {@code (texture, affectsOutline)} overload sets
 * {@code RenderSetup.OutlineProperty.NONE} when false ({@code RenderTypes.java:187-198}).
 * Passing {@code false} keeps this pass out of {@code entityOutlineTarget} entirely — the
 * very target whose blit x-rays. Feeding a depth-tested glow into the depth-ignoring outline
 * pass would reintroduce the bug this whole class is written to avoid, so the parameter is
 * not a default worth taking. Vanilla does the same for {@code breezeEyes}.
 *
 * <p>The factory is {@code Util.memoize}d by texture, so this allocates nothing per frame and
 * the returned instance is stable — which matters, because the submit collector keys its
 * buffers on render-type identity.
 */
public final class EliteGlowRenderType {
	private EliteGlowRenderType() {
	}

	/**
	 * The glow pass for a mob wearing {@code texture} — the entity's own texture, from its
	 * renderer's {@code getTextureLocation(state)}.
	 */
	public static RenderType forEntityTexture(final Identifier texture) {
		return RenderTypes.entityTranslucentEmissive(texture, false);
	}
}
