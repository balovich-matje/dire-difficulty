package com.diredifficulty.client;

import com.diredifficulty.elite.EliteData;
import com.diredifficulty.platform.EliteStore;

import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;

import net.minecraft.world.entity.Entity;

/**
 * How the client learns that an entity is an elite: it asks the seam, live.
 *
 * <h2>There is no client-side cache, and that is the point</h2>
 * {@code docs/DESIGN.md} §6.1 sketches this class as "entityId to rarity, fed by the payload;
 * cleared on stop-tracking". On 26.2 that machinery does not exist and must not be built:
 * {@code FabricEliteStore} registers the {@code EliteData} attachment with a sync predicate
 * covering every tracker, so fabric-api broadcasts it and {@link EliteStore#get} answers on
 * the client exactly as it does on the server. (The attachment API itself is named only
 * inside {@code platform/}, per the seam rule — this class sees a plain interface.)
 * A parallel id-to-rarity map maintained from a packet would be a <b>second copy of
 * synced state</b> — one that can be stale, can leak across worlds as entity ids recycle, and
 * would have to be invalidated on unload and disconnect. Reading through the seam has none of
 * those failure modes because there is nothing to go out of date.
 *
 * <p>The design's payload is not wrong, it is <em>early</em>: below 1.20.5 fabric-api has the
 * persistent attachment builder and no sync at all, so those nodes need a real packet.
 * {@code FabricEliteStore}'s javadoc is explicit that the payload gets written there, behind
 * this interface, when the port reaches them — and that {@code EliteData}'s
 * {@code FriendlyByteBuf} write/read pair is already the bytes it will carry. Nothing in this
 * package changes when that happens, which is what the seam is for.
 *
 * <h2>The one hop that is still needed</h2>
 * A {@code RenderLayer} is handed a render state and nothing else, and
 * {@code EntityRenderState} carries no entity id (verified against the 26.2 sources: it has
 * {@code entityType}, position, dimensions, light, outline colour, name tag and leash state,
 * and nothing identifying the individual). So the entity has to be asked while it is still in
 * hand — during extraction — and the answer stashed on the state under {@link #RENDER_KEY}.
 * {@code com.diredifficulty.client.mixin.LivingEntityRendererMixin} is that hop and does nothing
 * else.
 */
public final class EliteClientState {
	/**
	 * Where an elite's {@link EliteData} rides from extraction to submit.
	 *
	 * <p>{@code RenderStateDataKey} and the {@code FabricRenderState} interface on
	 * {@code EntityRenderState} both come from fabric-rendering-v1 (25.3.1+6988455e9e on
	 * 26.2). The interface is injected by that module's class tweaker, which is why every
	 * call site casts to {@code FabricRenderState} explicitly instead of relying on the
	 * injected supertype being visible to javac — an interface cast compiles either way.
	 *
	 * <p>The whole record is carried rather than just the rarity, because the affix list is
	 * already in hand and a nameplate or a tooltip will want it. It is immutable, so a
	 * consumer cannot write through it into server state.
	 */
	public static final RenderStateDataKey<EliteData> RENDER_KEY = RenderStateDataKey.create();

	private EliteClientState() {
	}

	/**
	 * The elite state of this entity, or {@code null} when it is not an elite.
	 *
	 * <p>Filters on {@link EliteData#isElite()} rather than on nullness alone: an entity can
	 * legitimately carry {@code EliteData} while <em>not</em> being an elite — the
	 * {@code SUPPRESSED} value stamped on summons and split copies is exactly that, and so is
	 * a rarity ordinal written by a newer build than this client, which
	 * {@code EliteData.rarity()} resolves to {@code null} on purpose. Both must render as an
	 * ordinary mob.
	 */
	public static EliteData eliteOf(final Entity entity) {
		EliteData data = EliteStore.INSTANCE.get(entity);
		return data != null && data.isElite() ? data : null;
	}
}
