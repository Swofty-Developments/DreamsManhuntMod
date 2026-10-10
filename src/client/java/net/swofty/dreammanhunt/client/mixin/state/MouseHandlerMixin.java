package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.MouseHandler;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void dm$blockTurning(double mousea, CallbackInfo ci) {
		if (ClientStateManager.get().isRestoring()) {
			ci.cancel();
		}
	}
}
