package com.escalation.progression;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.OptionalDouble;

import com.escalation.Tuning;
import com.escalation.config.EscalationConfig;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerPlayer;

/**
 * Progression measured in completed advancements — the one source that needs no other mod, and
 * therefore the one that keeps this mod meaningful standalone ({@code docs/DESIGN.md} §5.1).
 *
 * <p><b>Recipe advancements are excluded.</b> A vanilla world hands out well over a thousand of
 * them for picking up items, so counting them would saturate the score in the first hour and
 * make the curve meaningless. Two filters, deliberately belt-and-braces: an advancement with no
 * {@code display} is not a visible goal (that alone removes every vanilla recipe unlock), and a
 * path under {@code recipes/} is a recipe by convention even if a data pack gave it a display.
 *
 * <p><b>The eligible list is cached per {@link ServerAdvancementManager} instance</b>, not
 * rebuilt per player per refresh. A datapack reload constructs a new manager, so the identity
 * check below rebuilds exactly when the advancement set can actually have changed.
 */
public final class AdvancementScore implements ScoreProvider {
	public static final AdvancementScore INSTANCE = new AdvancementScore();

	/**
	 * Identity of the manager the cache was built from. Written and read only on the server
	 * thread (the score refresh is an END_SERVER_TICK listener), so no synchronisation.
	 */
	private ServerAdvancementManager cachedFor;

	private List<AdvancementHolder> cachedEligible = List.of();

	private AdvancementScore() {
	}

	@Override
	public String id() {
		return "advancements";
	}

	@Override
	public boolean enabled(final EscalationConfig config) {
		return config.scoreAdvancements;
	}

	@Override
	public OptionalDouble score(final ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		if (server == null) {
			return OptionalDouble.empty();
		}

		List<AdvancementHolder> eligible = eligible(server.getAdvancements());
		if (eligible.isEmpty()) {
			// No advancements loaded at all: genuinely unknown rather than genuinely zero, so
			// absent. A datapack that strips advancements must not pin every player to score 0
			// and hold the whole world at the bottom of the curve.
			return OptionalDouble.empty();
		}

		// getOrStartProgress is the only public read there is, and it does what its name says:
		// an advancement with no entry yet gets an empty one created. That is vanilla's own
		// read path — the /advancement command and the advancement-screen visibility pass both
		// go through it — and PlayerAdvancements.save() only writes entries where
		// progress.hasProgress(), so this adds nothing to the player's save file.
		PlayerAdvancements progress = player.getAdvancements();
		int done = 0;
		for (AdvancementHolder holder : eligible) {
			if (progress.getOrStartProgress(holder).isDone()) {
				done++;
			}
		}

		int target = Math.max(1, ProgressionScore.config().advancementTarget);
		return OptionalDouble.of(Tuning.clamp01((double) done / target));
	}

	/** The visible, non-recipe advancements of this datapack set, cached by manager identity. */
	private List<AdvancementHolder> eligible(final ServerAdvancementManager manager) {
		if (manager == this.cachedFor) {
			return this.cachedEligible;
		}

		Collection<AdvancementHolder> all = manager.getAllAdvancements();
		List<AdvancementHolder> out = new ArrayList<>(all.size());

		for (AdvancementHolder holder : all) {
			if (holder.value().display().isEmpty()) {
				continue;
			}

			if (holder.id().getPath().startsWith("recipes/")) {
				continue;
			}

			out.add(holder);
		}

		this.cachedFor = manager;
		this.cachedEligible = List.copyOf(out);
		return this.cachedEligible;
	}
}
