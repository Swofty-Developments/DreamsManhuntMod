package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "tickPlayer", at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$skipPlayerTickDuringRestore(CallbackInfoReturnable<Boolean> cir) {
		if (SavestateCoordinator.isPlayerTickBlocked(this.player.level().getServer())) {
			cir.setReturnValue(false);
		}
	}
}
