package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.DeltaTracker;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
	@ModifyVariable(method = "updateFrozenState", at = @At("HEAD"), argsOnly = true)
	private boolean dm$freezeDuringRestore(boolean frozen) {
		return frozen || ClientStateManager.get().isRestoring();
	}

	@Inject(method = "getGameTimeDeltaPartialTick", at = @At("HEAD"), cancellable = true)
	private void dm$holdBarrierPartialTick(boolean ignoreFrozenGame, CallbackInfoReturnable<Float> cir) {
		float held = ClientStateManager.get().barrierPartialTick();
		if (!Float.isNaN(held)) {
			cir.setReturnValue(held);
		}
	}
}
