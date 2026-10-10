package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.Minecraft;
import net.swofty.dreammanhunt.client.state.audio.AudioState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftAudioMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void dma$frame(boolean advanceGameTime, CallbackInfo ci) {
		AudioState.onFrame((Minecraft) (Object) this);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void dma$tickHead(CallbackInfo ci) {
		AudioState.onTickStart((Minecraft) (Object) this);
	}
}
