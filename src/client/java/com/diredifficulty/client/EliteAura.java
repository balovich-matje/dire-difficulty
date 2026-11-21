package com.diredifficulty.client;

import com.diredifficulty.DireDifficulty;
import com.diredifficulty.Tuning;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.Rarity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.oit.OitPipelineSet;
*///?} else {
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
//?}

import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

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
	//? if >=26.3 {
	/*private static final RenderPipeline PIPELINE = RenderPipelines.register(
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
	*///?} else {
	// 26.2 has no lightning snippet and no OIT: the same pipeline spelled out from vanilla's
	// LIGHTNING definition, with depth write off.
	private static final RenderPipeline PIPELINE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
					.withLocation(Identifier.fromNamespaceAndPath(DireDifficulty.MOD_ID, "pipeline/elite_aura"))
					.withVertexShader("core/rendertype_lightning")
					.withFragmentShader("core/rendertype_lightning")
					.withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
					.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
					.withPrimitiveTopology(PrimitiveTopology.QUADS)
					.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
					.build());

	private static final RenderType TYPE = RenderType.create("dire_difficulty_elite_aura",
			RenderSetup.builder(PIPELINE).sortOnUpload().createRenderSetup());
	//?}

	/** Block tops lit by the pool, as (x, y, z) triples relative to the entity origin. */
	public static final RenderStateDataKey<float[]> POOL_KEY = RenderStateDataKey.create();

	/**
	 * Solid block boxes near the disc, as (minX, minY, minZ, maxX, maxY, maxZ) relative to the
	 * entity origin. The disc fades out near them, so it never cuts a hard line into a wall.
	 */
	public static final RenderStateDataKey<float[]> WALL_KEY = RenderStateDataKey.create();

	private static final int SEGMENTS = 24;
	/** Ring count of the disc; enough that per-vertex fades interpolate smoothly. */
	private static final int RINGS = 8;

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

	/**
	 * Collects the collision boxes of blocks the disc could pass into: every non-empty block
	 * in a cube around the mob's middle that has at least one open neighbour (buried blocks
	 * can never touch the disc). The disc fades near these on the CPU — a soft intersection
	 * without reading the depth buffer.
	 */
	public static void extractWalls(final Level level, final LivingEntityRenderState state,
			final Rarity rarity) {
		if (state.distanceToCameraSq > Tuning.AURA_POOL_MAX_DISTANCE * Tuning.AURA_POOL_MAX_DISTANCE) {
			return;
		}

		float halfHeight = state.boundingBoxHeight * 0.5F;
		double reach = discRadius(state, rarity) + state.boundingBoxWidth * 0.6 + 0.05
				+ Tuning.AURA_WALL_FADE;
		double cy = state.y + halfHeight;
		int x0 = Mth.floor(state.x - reach);
		int x1 = Mth.floor(state.x + reach);
		int y0 = Math.max(Mth.floor(state.y) - 1, Mth.floor(cy - reach));
		int y1 = Mth.floor(cy + reach);
		int z0 = Mth.floor(state.z - reach);
		int z1 = Mth.floor(state.z + reach);
		float[] out = new float[64 * 6];
		int n = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos side = new BlockPos.MutableBlockPos();

		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					pos.set(x, y, z);
					BlockState block = level.getBlockState(pos);

					if (block.isAir() || block.getRenderShape() == RenderShape.INVISIBLE) {
						continue;
					}

					VoxelShape shape = block.getCollisionShape(level, pos);

					if (shape.isEmpty() || !exposed(level, pos, side)) {
						continue;
					}

					AABB box = shape.bounds();

					if (n + 6 > out.length) {
						out = java.util.Arrays.copyOf(out, out.length * 2);
					}

					out[n++] = (float) (x + box.minX - state.x);
					out[n++] = (float) (y + box.minY - state.y);
					out[n++] = (float) (z + box.minZ - state.z);
					out[n++] = (float) (x + box.maxX - state.x);
					out[n++] = (float) (y + box.maxY - state.y);
					out[n++] = (float) (z + box.maxZ - state.z);
				}
			}
		}

		if (n > 0) {
			((FabricRenderState) state).setData(WALL_KEY, java.util.Arrays.copyOf(out, n));
		}
	}

	private static boolean exposed(final Level level, final BlockPos pos, final BlockPos.MutableBlockPos side) {
		for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
			side.setWithOffset(pos, direction);

			if (!level.getBlockState(side).isCollisionShapeFullBlock(level, side)) {
				return true;
			}
		}

		return false;
	}

	private static float discRadius(final LivingEntityRenderState state, final Rarity rarity) {
		return (float) ((state.boundingBoxHeight * 0.5F + Tuning.AURA_RADIUS_PAD) * rarity.auraRadiusScale());
	}

	// ------------------------------------------------------------------ submission

	/** Called with the pose stack at the entity's origin, before the model is posed. */
	public static void submit(final LivingEntityRenderState state, final PoseStack poseStack,
			final SubmitNodeCollector collector, final CameraRenderState camera) {
		if (state.isInvisible || !com.diredifficulty.config.ConfigManager.get().eliteGlow) {
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
		float radius = discRadius(state, rarity);

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
		float[] walls = ((FabricRenderState) state).getData(WALL_KEY);

		// Every grid point once: position and colour, so the per-vertex wall test is not
		// repeated for the four quads that share it.
		int points = (RINGS + 1) * SEGMENTS;
		float[] px = new float[points];
		float[] py = new float[points];
		float[] pz = new float[points];
		int[] colour = new int[points];

		for (int ring = 0; ring <= RINGS; ring++) {
			float t = (float) ring / RINGS;
			float radial = (float) Math.pow(1.0F - t, 1.6);

			for (int i = 0; i < SEGMENTS; i++) {
				double angle = i * (Math.PI * 2.0 / SEGMENTS);
				float u = (float) Math.cos(angle) * t * radius;
				float v = (float) Math.sin(angle) * t * radius;
				int k = ring * SEGMENTS + i;
				float x = cx + right.x * u + up.x * v;
				float y = cy + right.y * u + up.y * v;
				float z = cz + right.z * u + up.z * v;
				px[k] = x;
				py[k] = y;
				pz[k] = z;
				float fade = radial <= 0.0F ? 0.0F
						: radial * Math.min(smooth(y / (float) Tuning.AURA_GROUND_FADE), wallFade(walls, x, y, z));
				colour[k] = ARGB.color(Math.round(alpha * fade), rgb);
			}
		}

		collector.submitCustomGeometry(poseStack, TYPE, (pose, buffer) -> {
			for (int ring = 0; ring < RINGS; ring++) {
				for (int i = 0; i < SEGMENTS; i++) {
					int j = (i + 1) % SEGMENTS;
					discVertex(buffer, pose, px, py, pz, colour, ring * SEGMENTS + i);
					discVertex(buffer, pose, px, py, pz, colour, (ring + 1) * SEGMENTS + i);
					discVertex(buffer, pose, px, py, pz, colour, (ring + 1) * SEGMENTS + j);
					discVertex(buffer, pose, px, py, pz, colour, ring * SEGMENTS + j);
				}
			}
		});
	}

	private static void discVertex(final VertexConsumer buffer, final PoseStack.Pose pose,
			final float[] px, final float[] py, final float[] pz, final int[] colour, final int k) {
		buffer.addVertex(pose, px[k], py[k], pz[k]).setColor(colour[k]);
	}

	/** Smoothstep of {@code g} clamped to 0..1. */
	private static float smooth(final float g) {
		float c = Mth.clamp(g, 0.0F, 1.0F);
		return c * c * (3.0F - 2.0F * c);
	}

	/**
	 * 0 inside or touching a nearby block, rising smoothly to 1 at {@code AURA_WALL_FADE}
	 * blocks away. This is what keeps the flat disc from cutting a hard line into a wall,
	 * a step or a slope: wherever it meets a block it has already faded out.
	 */
	private static float wallFade(final float[] walls, final float x, final float y, final float z) {
		if (walls == null) {
			return 1.0F;
		}

		float fade = (float) Tuning.AURA_WALL_FADE;
		float nearestSq = fade * fade;

		for (int i = 0; i < walls.length; i += 6) {
			float dx = Math.max(Math.max(walls[i] - x, 0.0F), x - walls[i + 3]);
			float dy = Math.max(Math.max(walls[i + 1] - y, 0.0F), y - walls[i + 4]);
			float dz = Math.max(Math.max(walls[i + 2] - z, 0.0F), z - walls[i + 5]);
			float dSq = dx * dx + dy * dy + dz * dz;

			if (dSq < nearestSq) {
				nearestSq = dSq;

				if (dSq == 0.0F) {
					return 0.0F;
				}
			}
		}

		return smooth((float) Math.sqrt(nearestSq) / fade);
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
