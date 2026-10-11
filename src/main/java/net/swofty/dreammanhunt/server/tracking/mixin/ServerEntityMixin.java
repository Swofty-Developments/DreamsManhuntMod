package net.swofty.dreammanhunt.server.tracking.mixin;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.UpdateInterval;
import net.swofty.dreammanhunt.server.coordinator.SavestateCoordinator;
import net.swofty.dreammanhunt.server.tracking.TrackingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerEntity.class)
public abstract class ServerEntityMixin {
	@Shadow
	@Final
	private ServerLevel level;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void dreammanhunt$restoreTracking(ServerLevel level, Entity entity, UpdateInterval updateInterval, boolean trackDelta, ServerEntity.Synchronizer synchronizer, CallbackInfo ci) {
		TrackingState.onTrackerCreated((ServerEntity) (Object) this);
	}

	@Inject(method = "sendChanges", at = @At("HEAD"), cancellable = true)
	private void dreammanhunt$holdDuringRestore(CallbackInfo ci) {
		if (SavestateCoordinator.isPlayerTickBlocked(this.level.getServer())) {
			ci.cancel();
		}
	}
}
