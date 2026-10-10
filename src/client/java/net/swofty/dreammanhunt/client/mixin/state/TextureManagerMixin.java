package net.swofty.dreammanhunt.client.mixin.state;

import net.minecraft.client.renderer.texture.TextureManager;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {
	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void dm$freezeAnimations(CallbackInfo ci) {
		if (ClientStateManager.get().isRestoring()) {
			ci.cancel();
		}
	}
}
