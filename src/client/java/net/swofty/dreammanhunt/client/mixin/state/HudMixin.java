package net.swofty.dreammanhunt.client.mixin.state;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Hud;
import net.minecraft.util.Util;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.client.state.VisualClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "tick(Z)V", at = @At("HEAD"), cancellable = true)
	private void dm$freezeTick(boolean pause, CallbackInfo ci) {
		if (ClientStateManager.get().isRestoring()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Util;getMillis()J"))
	private long dm$visualHealthClock(Operation<Long> original) {
		return VisualClock.millis();
	}
}
