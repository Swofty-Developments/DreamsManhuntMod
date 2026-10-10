package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
	@Inject(method = {"tick", "doTick"}, at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$blockDuringRestore(CallbackInfo ci) {
		if (SavestateCoordinator.isPlayerTickBlocked(((ServerPlayer) (Object) this).level().getServer())) {
			ci.cancel();
		}
	}
}
