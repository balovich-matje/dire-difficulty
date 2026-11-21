package com.diredifficulty.client;

import com.diredifficulty.Tuning;
import com.diredifficulty.affix.Affix;
import com.diredifficulty.affix.Affixes;
import com.diredifficulty.client.mixin.BossHealthOverlayAccessor;
import com.diredifficulty.config.ConfigManager;
import com.diredifficulty.elite.EliteData;
import com.diredifficulty.elite.Rarity;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

/**
 * The elite plate: name, health bar and affix list for the elite under the crosshair, drawn
 * top-centre below any real boss bars.
 *
 * <pre>
 *                    Zombie                 &lt;- the elite's own name, in the rarity colour
 *     [==================        ]          &lt;- vanilla boss-bar sprites, rarity-matched
 *                   Vampiric                &lt;- the drawn affixes, container grey
 *                  Shieldbreaker
 *                    Quaking
 * </pre>
 *
 * <h2>Self-contained, and that is a requirement rather than an accident</h2>
 * No WTHIT, no Jade, no soft dependency on either — this draws with fabric-api's HUD registry
 * and vanilla sprites, and it works on a vanilla+fabric-api install. A WTHIT/Jade plugin that
 * put the same information in THEIR overlay is parked as iteration 2 ({@code docs/DESIGN.md}
 * §10.9); it would be an addition to this, never a replacement, because a plate that only
 * appears for players who happen to run a third mod is not a feature.
 *
 * <h2>Vanilla chrome, taken literally</h2>
 * The house rule is "container greys, no custom chrome", and the strongest form of that here
 * is to draw nothing of our own at all:
 *
 * <ul>
 * <li><b>The bar is vanilla's boss bar.</b> Same {@code boss_bar/*} sprite ids, same 182x5
 *     geometry, same {@code Mth.lerpDiscrete} fill, same {@code RenderPipelines.GUI_TEXTURED}
 *     — read off {@code BossHealthOverlay.extractBar} and reused rather than reimplemented.
 *     There is no new texture in this mod.</li>
 * <li><b>The palette matches by construction.</b> Vanilla ships seven boss-bar colours and
 *     four of them are the four rarity colours: green, blue, purple and yellow against
 *     {@code Tuning.RARITY_COLOUR}'s green, blue, purple and orange. The rarity ladder
 *     indexes {@link #BAR_BACKGROUND}/{@link #BAR_PROGRESS} directly, so the bar cannot
 *     disagree with the glow and the name tag.</li>
 * <li><b>No panel, no border, no backdrop.</b> Vanilla boss-bar names are drawn as bare text
 *     with a drop shadow over whatever is behind them, and so is everything here.</li>
 * <li><b>The geometry is vanilla's.</b> Name at {@code barY - 9}, bar 91 px either side of
 *     centre — the plate is positioned exactly where the next boss bar would have gone, which
 *     is why it reads as part of the game rather than as an overlay.</li>
 * </ul>
 *
 * <h2>Everything is read live</h2>
 * {@link ElitePlateTracker} holds an entity reference and nothing else. Health, max health,
 * rarity and the affix list are all pulled at draw time — health straight off the entity, the
 * rest out of the synced {@code EliteData} attachment through {@link EliteClientState}. There
 * is no snapshot to go stale during the one-second linger, which is the same reasoning
 * {@code EliteClientState} gives for having no id-to-rarity cache at all.
 *
 * <p><b>No wire change was needed for any of this.</b> {@code EliteData} already carries the
 * affix ids as strings and already syncs to every tracker
 * ({@code FabricEliteStore} registers it with {@code AttachmentSyncPredicate.all()}), so the
 * affix names the plate lists have been on the client since iteration 1 — unread. The names
 * themselves come from {@link Affix#nameKey()}, a lang key derived from the frozen id, so a
 * server running an affix this client does not have contributes nothing rather than a raw
 * key over the bar.
 *
 * <h2>What it deliberately does not show</h2>
 * No numeric health read-out. Vanilla boss bars have none, Diablo's elite plate has none, and
 * a "68 / 68" over a 5-pixel bar is the first piece of custom chrome that would make the rest
 * look like an overlay. The debug numbers live in {@code /dire info}, which is where a
 * number belongs.
 */
public final class ElitePlate {
	/**
	 * Vanilla boss-bar background sprites, in {@link Rarity} order.
	 *
	 * <p>Uncommon green, Rare blue, Epic purple, Legendary yellow — four of vanilla's own
	 * seven, chosen because they are the closest thing vanilla has to
	 * {@code Tuning.RARITY_COLOUR}. Legendary's {@code 0xFFAA00} orange has no boss-bar
	 * equivalent; yellow is the nearest, and the name above the bar carries the exact colour.
	 */
	private static final Identifier[] BAR_BACKGROUND = {
			Identifier.withDefaultNamespace("boss_bar/green_background"),
			Identifier.withDefaultNamespace("boss_bar/blue_background"),
			Identifier.withDefaultNamespace("boss_bar/purple_background"),
			Identifier.withDefaultNamespace("boss_bar/yellow_background"),
			Identifier.withDefaultNamespace("boss_bar/red_background")};

	private static final Identifier[] BAR_PROGRESS = {
			Identifier.withDefaultNamespace("boss_bar/green_progress"),
			Identifier.withDefaultNamespace("boss_bar/blue_progress"),
			Identifier.withDefaultNamespace("boss_bar/purple_progress"),
			Identifier.withDefaultNamespace("boss_bar/yellow_progress"),
			Identifier.withDefaultNamespace("boss_bar/red_progress")};

	private ElitePlate() {
	}

	/**
	 * The {@code HudElement} functional method. Attached AFTER
	 * {@code VanillaHudElements.BOSS_BAR}, which matters for one reason that is measured
	 * rather than assumed: fabric wraps {@code Hud.extractBossOverlay}, and vanilla calls
	 * that method unconditionally — the "are there any boss bars" test lives INSIDE
	 * {@code BossHealthOverlay.extractRenderState}, not around the call. So this element runs
	 * on every frame the HUD is drawn, boss bars or not. (Skill Proficiencies learned the
	 * other half of this rule the hard way: an element attached to
	 * {@code EXPERIENCE_LEVEL} is skipped whenever vanilla skips ITS anchor, which it does at
	 * zero XP levels.)
	 *
	 * <p>Being inside the {@code !isHidden} branch is also what makes F1 hide the plate with
	 * the rest of the HUD, at no cost here.
	 */
	public static void render(final GuiGraphicsExtractor graphics, final DeltaTracker deltaTracker) {
		if (!ConfigManager.get().elitePlate) {
			return;
		}

		LivingEntity target = ElitePlateTracker.target();

		if (target == null) {
			return;
		}

		// Re-read, every frame: the linger holds a reference, never a snapshot.
		EliteData elite = EliteClientState.eliteOf(target);

		if (elite == null) {
			return;
		}

		Rarity rarity = elite.rarity();

		if (rarity == null) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		Font font = client.font;

		graphics.nextStratum();

		int centreX = graphics.guiWidth() / 2;
		int barX = centreX - Tuning.PLATE_BAR_WIDTH / 2;
		int barY = belowBossBars(graphics, client);

		// The name the SERVER stamped (EliteNameTag wraps the mob's own translatable type
		// name in the rarity colour), so every client resolves it in its own language and this
		// mod ships no string for any of the several hundred promotable mob types. The rarity
		// colour is passed as the base anyway: a style-less name — an elite whose custom name
		// was set by something else — then still arrives in the right colour instead of white.
		graphics.centeredText(font, target.getName(), centreX, barY - Tuning.PLATE_LINE_HEIGHT,
				ARGB.opaque(rarity.colour()));

		drawHealthBar(graphics, target, rarity, barX, barY);
		drawAffixes(graphics, font, elite, centreX, barY);
	}

	/** Vanilla's own two-blit bar: the full-width background, then the filled portion over it. */
	private static void drawHealthBar(final GuiGraphicsExtractor graphics, final LivingEntity target,
			final Rarity rarity, final int barX, final int barY) {
		int index = rarity.ordinal();

		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_BACKGROUND[index],
				Tuning.PLATE_BAR_WIDTH, Tuning.PLATE_BAR_HEIGHT, 0, 0,
				barX, barY, Tuning.PLATE_BAR_WIDTH, Tuning.PLATE_BAR_HEIGHT);

		// getMaxHealth() is never 0 on a live entity, but the guard costs nothing and this runs
		// in the render loop where a divide-by-zero would be a crash rather than a bad frame.
		float maxHealth = target.getMaxHealth();
		float progress = maxHealth > 0.0F
				? (float) Tuning.clamp01(target.getHealth() / maxHealth)
				: 0.0F;
		int filled = Mth.lerpDiscrete(progress, 0, Tuning.PLATE_BAR_WIDTH);

		if (filled > 0) {
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_PROGRESS[index],
					Tuning.PLATE_BAR_WIDTH, Tuning.PLATE_BAR_HEIGHT, 0, 0,
					barX, barY, filled, Tuning.PLATE_BAR_HEIGHT);
		}
	}

	/**
	 * The drawn affixes, one per line under the bar, in registration order.
	 *
	 * <p>An id this build does not know is SKIPPED rather than printed. That is the same call
	 * {@code Affixes.resolve} makes server-side and for the same reason: a client connected to
	 * a server running a newer catalogue should show the affixes it understands, not a column
	 * of {@code affix.dire_difficulty.something} over the health bar.
	 */
	private static void drawAffixes(final GuiGraphicsExtractor graphics, final Font font,
			final EliteData elite, final int centreX, final int barY) {
		int line = barY + Tuning.PLATE_BAR_HEIGHT + Tuning.PLATE_AFFIX_GAP;

		for (final String id : elite.affixes()) {
			Affix affix = Affixes.byId(id);

			if (affix == null) {
				continue;
			}

			graphics.centeredText(font, Component.translatable(affix.nameKey()), centreX, line,
					Tuning.PLATE_AFFIX_COLOUR);
			line += Tuning.PLATE_LINE_HEIGHT;
		}
	}

	/**
	 * The Y the next vanilla boss bar would occupy — {@code 12}, plus {@code 10 + 9} per bar
	 * already on screen, stopping a third of the way down the screen.
	 *
	 * <p>This is {@code BossHealthOverlay.extractRenderState}'s own loop re-run over the bar
	 * COUNT rather than over the bars, which is why {@link BossHealthOverlayAccessor} exists:
	 * the count is the only thing vanilla does not expose. Mirroring the loop (rather than
	 * multiplying) is what reproduces the {@code guiHeight / 3} ceiling, so on a server with a
	 * dozen boss bars the plate stacks up against the same limit the bars do instead of
	 * sliding off the bottom of the screen.
	 */
	private static int belowBossBars(final GuiGraphicsExtractor graphics, final Minecraft client) {
		int bars = ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).direDifficulty$events().size();
		int limit = graphics.guiHeight() / 3;
		int y = Tuning.PLATE_FIRST_BAR_Y;

		for (int i = 0; i < bars; i++) {
			y += Tuning.PLATE_BOSS_BAR_STRIDE;

			if (y >= limit) {
				break;
			}
		}

		return y;
	}
}
