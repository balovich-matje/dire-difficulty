package com.diredifficulty.affix.impl;

import net.minecraft.world.entity.player.Player;

/**
 * The one predicate every aura, shockwave and mark uses to decide whether a player counts.
 *
 * <p>Shared rather than repeated because the failure mode of getting it wrong is silent and
 * specific: an affix that forgets {@code isCreative} makes a build-mode player take chip
 * damage from across a wall, and an affix that forgets {@code isSpectator} makes an
 * unreachable observer hold a Summoner's target slot forever.
 */
final class AuraTargets {
	private AuraTargets() {
	}

	/** Alive, in survival or adventure, and actually present in the world. */
	static boolean isAffectable(final Player player) {
		return player.isAlive() && !player.isSpectator() && !player.isCreative();
	}
}
