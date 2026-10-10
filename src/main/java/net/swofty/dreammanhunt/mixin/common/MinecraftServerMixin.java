package net.swofty.dreammanhunt.mixin.common;

import net.minecraft.server.MinecraftServer;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Inject(method = "tickConnection", at = @At("TAIL"))
	private void dreammanhunt$pollCoordinator(CallbackInfo ci) {
		SavestateCoordinator coordinator = SavestateCoordinator.peek((MinecraftServer) (Object) this);
		if (coordinator != null) {
			coordinator.poll();
		}
	}

	@Inject(method = "tickServer", at = @At("HEAD"))
	private void dreammanhunt$completeBarrierBeforeTick(BooleanSupplier haveTime, CallbackInfo ci) {
		SavestateCoordinator coordinator = SavestateCoordinator.peek((MinecraftServer) (Object) this);
		if (coordinator != null) {
			coordinator.beforeTick();
		}
	}

	@Inject(method = "tickChildren", at = @At("TAIL"))
	private void dreammanhunt$syncTrackingDuringRestore(BooleanSupplier haveTime, CallbackInfo ci) {
		SavestateCoordinator coordinator = SavestateCoordinator.peek((MinecraftServer) (Object) this);
		if (coordinator != null) {
			coordinator.afterTick();
		}
	}
}
