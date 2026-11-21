package com.diredifficulty.mixin.command;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Re-sends the command tree when a player's game mode changes. {@code /dire} is visible only
 * in creative, and vanilla sends the tree on join and on permission changes, not on a game
 * mode switch — so without this, tab-completion for {@code /dire} depended on which mode the
 * player happened to be in when they joined.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerGameModeMixin {
	@Inject(method = "setGameMode", at = @At("RETURN"))
	private void direDifficulty$resendCommands(final GameType mode, final CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValueZ()) {
			return;
		}

		ServerPlayer self = (ServerPlayer) (Object) this;
		MinecraftServer server = self.level().getServer();

		if (server != null) {
			server.getCommands().sendCommands(self);
		}
	}
}
