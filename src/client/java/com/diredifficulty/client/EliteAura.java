package com.diredifficulty.client;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.Rarity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The elite rarity aura: a soft additive disc behind the mob and a soft additive light pool
 * on the block tops around its feet. The mob itself is lit in
 * {@code LivingEntityRendererMixin} (block light raised on the render state).
 *
 * <p>Both shapes are drawn with vertex colours only (no texture): a centre vertex at full
 * alpha falling to zero at the rim, interpolated by the GPU, so the edge is soft. Blend is
 * vanilla's lightning blend (SRC_ALPHA, ONE) — additive, so it brightens rather than tints.
 * Depth is TESTED and NOT written: walls hide it (the occlusion rule from the first
 * iteration still holds), and nothing drawn later is cut out by a transparent rectangle.
 * The OIT variant mirrors vanilla's own lightning OIT set so the type works in every
 * transparency mode.
 */
public final class EliteAura {
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(DireDifficulty.MOD_ID, "pipeline/elite_aura"))
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	private static final OitPipelineSet OIT = RenderPipelines.register(
			OitPipelineSet.builder("dire_difficulty_elite_aura",
					RenderPipeline.builder(RenderPipelines.LIGHTNING_SNIPPET).withShaderDefine("OIT_ADDITIVE"))
					.build());

	private static final RenderType TYPE = RenderType.create("dire_difficulty_elite_aura",
			RenderSetup.builder(PIPELINE).setOitPipelines(OIT).sortOnUpload().createRenderSetup());

	/** Block tops lit by the pool, as (x, y, z) triples relative to the entity origin. */
	public static final RenderStateDataKey<float[]> POOL_KEY = RenderStateDataKey.create();

	private static final int SEGMENTS = 20;
	/** Radial stops of the falloff (fraction of radius) and the alpha fraction at each. */
	private static final float[] RING_R = {0.0F, 0.22F, 0.5F, 1.0F};
	private static final float[] RING_A = {1.0F, 0.72F, 0.28F, 0.0F};

	private EliteAura() {
	}

	// ------------------------------------------------------------------ extraction

	/**
	 * Collects the block tops around the elite's feet that the pool lands on. Same shape of
	 * search as vanilla's entity shadow: every column in the radius, a few blocks of depth,
	 * the top face of a full, visible block.
	 */
	public static void extractPool(final Level level, final LivingEntityRenderState state,
			final Rarity rarity) {
		if (state.distanceToCameraSq > Tuning.AURA_POOL_MAX_DISTANCE * Tuning.AURA_POOL_MAX_DISTANCE) {
			return;
		}

		double radius = rarity.auraPoolRadius();
		int x0 = Mth.floor(state.x - radius);
		int x1 = Mth.floor(state.x + radius);
		int z0 = Mth.floor(state.z - radius);
		int z1 = Mth.floor(state.z + radius);
		int y0 = Mth.floor(state.y) - 2;
		int y1 = Mth.floor(state.y) + 1;
		float[] out = new float[(x1 - x0 + 1) * (z1 - z0 + 1) * (y1 - y0 + 1) * 3];
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				pos.set(x, 0, z);
				ChunkAccess chunk = level.getChunk(pos);

				for (int y = y0; y <= y1; y++) {
					pos.setY(y);
					below.set(x, y - 1, z);
					BlockState belowState = chunk.getBlockState(below);

					if (belowState.getRenderShape() == RenderShape.INVISIBLE
							|| !belowState.isCollisionShapeFullBlock(chunk, below)
							|| chunk.getBlockState(pos).isCollisionShapeFullBlock(chunk, pos)) {
						continue;
					}

					out[n++] = (float) (x - state.x);
					out[n++] = (float) (y - state.y);
					out[n++] = (float) (z - state.z);
				}
			}
		}

		if (n > 0) {
			((FabricRenderState) state).setData(POOL_KEY, java.util.Arrays.copyOf(out, n));
		}
	}

	// ------------------------------------------------------------------ submission

	/** Called with the pose stack at the entity's origin, before the model is posed. */
	public static void submit(final LivingEntityRenderState state, final PoseStack poseStack,
			final SubmitNodeCollector collector, final CameraRenderState camera) {
		if (state.isInvisible) {
			return;
		}

		EliteData elite = ((FabricRenderState) state).getData(EliteClientState.RENDER_KEY);
		Rarity rarity = elite == null ? null : elite.rarity();

		if (rarity == null) {
			return;
		}

		double pulse = Tuning.glowPulse(state.ageInTicks);
		int rgb = rarity.colour();
		submitDisc(state, poseStack, collector, camera, rarity, rgb,
				Tuning.glowAlpha(rarity.auraAlpha(), pulse));

		float[] pool = ((FabricRenderState) state).getData(POOL_KEY);

		if (pool != null) {
			submitPool(poseStack, collector, pool, (float) rarity.auraPoolRadius(), rgb,
					Tuning.glowAlpha(rarity.auraPoolAlpha(), pulse));
		}
	}

	private static void submitDisc(final LivingEntityRenderState state, final PoseStack poseStack,
			final SubmitNodeCollector collector, final CameraRenderState camera, final Rarity rarity,
			final int rgb, final int alpha) {
		float halfHeight = state.boundingBoxHeight * 0.5F;
		float radius = (float) ((halfHeight + Tuning.AURA_RADIUS_PAD) * rarity.auraRadiusScale());

		// Push the disc to the far side of the body, so the body covers its middle and the
		// light shows around the silhouette.
		Vec3 toMob = new Vec3(state.x, state.y + halfHeight, state.z).subtract(camera.pos);
		double len = toMob.length();
		Vec3 back = len < 1.0E-4 ? Vec3.ZERO : toMob.scale((state.boundingBoxWidth * 0.6 + 0.05) / len);
		float cx = (float) back.x;
		float cy = (float) (halfHeight + back.y);
		float cz = (float) back.z;

		Vector3f right = camera.orientation.transform(new Vector3f(1.0F, 0.0F, 0.0F));
		Vector3f up = camera.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));

		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			for (int ring = 0; ring < RING_R.length - 1; ring++) {
				float rIn = RING_R[ring] * radius;
				float rOut = RING_R[ring + 1] * radius;
				int cIn = ARGB.color(Math.round(alpha * RING_A[ring]), rgb);
				int cOut = ARGB.color(Math.round(alpha * RING_A[ring + 1]), rgb);

				for (int i = 0; i < SEGMENTS; i++) {
					double a0 = i * (Math.PI * 2.0 / SEGMENTS);
					double a1 = (i + 1) * (Math.PI * 2.0 / SEGMENTS);
					discVertex(buffer, pose, cx, cy, cz, right, up, a0, rIn, cIn);
					discVertex(buffer, pose, cx, cy, cz, right, up, a0, rOut, cOut);
					discVertex(buffer, pose, cx, cy, cz, right, up, a1, rOut, cOut);
					discVertex(buffer, pose, cx, cy, cz, right, up, a1, rIn, cIn);
				}
			}
		});
	}

	private static void discVertex(final VertexConsumer buffer, final PoseStack.Pose pose,
			final float cx, final float cy, final float cz, final Vector3f right, final Vector3f up,
			final double angle, final float r, final int colour) {
		float u = (float) Math.cos(angle) * r;
		float v = (float) Math.sin(angle) * r;
		buffer.addVertex(pose, cx + right.x * u + up.x * v, cy + right.y * u + up.y * v,
				cz + right.z * u + up.z * v).setColor(colour);
	}

	private static void submitPool(final PoseStack poseStack, final SubmitNodeCollector collector,
			final float[] pool, final float radius, final int rgb, final int alpha) {
		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			for (int i = 0; i < pool.length; i += 3) {
				float x = pool[i];
				float y = pool[i + 1] + 0.015F;
				float z = pool[i + 2];
				// Lower block tops fade: the pool is light falling from the mob, not paint.
				float depthFade = Math.max(0.0F, 1.0F - Math.abs(pool[i + 1]) * 0.45F);

				if (depthFade <= 0.0F) {
					continue;
				}

				poolVertex(buffer, pose, x, y, z, radius, rgb, alpha, depthFade);
				poolVertex(buffer, pose, x, y, z + 1.0F, radius, rgb, alpha, depthFade);
				poolVertex(buffer, pose, x + 1.0F, y, z + 1.0F, radius, rgb, alpha, depthFade);
				poolVertex(buffer, pose, x + 1.0F, y, z, radius, rgb, alpha, depthFade);
			}
		});
	}

	private static void poolVertex(final VertexConsumer buffer, final PoseStack.Pose pose,
			final float x, final float y, final float z, final float radius, final int rgb,
			final int alpha, final float depthFade) {
		float d = (float) Math.sqrt(x * x + z * z) / radius;
		float falloff = d >= 1.0F ? 0.0F : (1.0F - d) * (1.0F - d);
		buffer.addVertex(pose, x, y, z).setColor(ARGB.color(Math.round(alpha * falloff * depthFade), rgb));
	}
}
