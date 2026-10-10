package net.swofty.dreammanhunt.client.mixin.ui;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.server.IntegratedServer;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@ModifyExpressionValue(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;isPaused()Z"))
	private boolean dreammanhunt$keepTickingDuringRestore(boolean paused) {
		return paused && !SavestateCoordinator.isPlayerTickBlocked((IntegratedServer) (Object) this);
	}
}
