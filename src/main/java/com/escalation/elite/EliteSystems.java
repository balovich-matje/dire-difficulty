package com.escalation.elite;

import com.escalation.affix.Affixes;
import com.escalation.platform.EliteStore;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerLevel;

/**
 * One call from common init brings up the whole elite half of the mod.
 *
 * <p>The ORDER below is a Tier-2 assertion, not a preference. The store has to register before
 * anything can read state, the catalogue has to exist before the store's persisted affix ids
 * can resolve to behaviours, and the event listeners must not be able to fire against either
 * one before it is ready. A world may load the moment init returns.
 *
 * <p><b>Only the two registration LINES fork at port time.</b> The listener bodies —
 * {@link EliteCombat} and {@link EliteTicker} — are shared, single-implementation balance logic
 * that every target runs unchanged. That is the §5g convention applied to events: a loader's
 * event API is named at the registration site and nowhere else, so the NeoForge and Forge nodes
 * get their own three-line helper and not their own copy of the funnel.
 *
 * <p>The elite roll itself is not here: it enters from a mixin on {@code Mob.finalizeSpawn},
 * because the spawn reason it gates on is an argument no event carries.
 */
public final class EliteSystems {
	private EliteSystems() {
	}

	public static void register() {
		EliteStore.INSTANCE.initialize();
		Affixes.register();

		// AFTER_DAMAGE rather than a second mixin handler: it hands over the damage actually
		// taken, after armour, resistance and blocking. Every consequence of a hit keys off
		// that number, so a fully blocked hit applies no debuff and a lifesteal cannot heal
		// from damage the victim never took.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity.level() instanceof ServerLevel level) {
				EliteCombat.afterDamage(entity, level, source, damageTaken);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level) {
				EliteCombat.afterDeath(entity, level, source);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(EliteTicker::onEndServerTick);
	}
}
