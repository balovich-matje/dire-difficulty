package com.diredifficulty.client.mixin;

import java.util.Map;
import java.util.UUID;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;

/**
 * How many boss bars are on screen, so the elite plate can sit below them.
 *
 * <h2>Why a mixin, when the whole plate is otherwise plain API</h2>
 * The plate is specified to draw "below any real boss bars if present", which means knowing
 * how many there are. {@code Minecraft.getInstance().gui.hud.getBossOverlay()} is public all
 * the way down, but {@code BossHealthOverlay} exposes only {@code shouldPlayMusic},
 * {@code shouldDarkenScreen} and {@code shouldCreateWorldFog} — all three of which are
 * {@code false} for an ordinary boss bar, so none of them can even answer "is there one".
 * The count lives in a private {@code Map<UUID, LerpingBossEvent> events}, verified against
 * the 26.2 sources ({@code BossHealthOverlay.java:54}).
 *
 * <p>An {@code @Accessor} is the smallest thing that answers it. No injector, no game logic,
 * nothing conditional, no balance number — and the alternative, hardcoding "assume one boss
 * bar" or "assume none", is the kind of guess that looks right in every screenshot taken
 * outside a raid.
 *
 * <h2>The arithmetic this feeds, and why it is a mirror rather than a guess</h2>
 * {@code BossHealthOverlay.extractRenderState} lays its bars out as
 * {@code yOffset = 12}, then per bar {@code yOffset += 10 + 9} with a
 * {@code if (yOffset >= guiHeight / 3) break;} — so the bars stop stacking a third of the way
 * down the screen no matter how many events exist. {@link com.diredifficulty.client.ElitePlate}
 * runs the identical loop over this count, off {@link com.diredifficulty.Tuning}'s
 * {@code PLATE_FIRST_BAR_Y} and {@code PLATE_BOSS_BAR_STRIDE}, so the plate lands exactly
 * where the next boss bar would have — including refusing to walk off the screen when a
 * server has spawned a dozen of them.
 *
 * <p><b>Port note.</b> The field name {@code events} and the {@code 12 / 10 + 9 /
 * guiHeight / 3} layout are all that this depends on, and both are long-lived — but they are
 * a VERSION fact, not an API contract, so the port re-reads them per node rather than
 * assuming they carried. If a future version renames the field this fails loudly at mixin
 * apply time, which is the failure mode to want.
 */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {
	/**
	 * The live boss-event map. Read for {@code size()} only — never mutated, never held.
	 *
	 * <p>The field name is given explicitly because {@code direDifficulty$events} does not
	 * follow the JavaBean getter convention Mixin infers from, and a mod-prefixed name is
	 * the house rule for anything added to a vanilla class.
	 */
	@Accessor("events")
	Map<UUID, LerpingBossEvent> direDifficulty$events();
}
