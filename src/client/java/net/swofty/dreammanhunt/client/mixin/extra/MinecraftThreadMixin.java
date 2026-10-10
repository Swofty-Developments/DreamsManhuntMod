package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.Minecraft;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftThreadMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void dm$rememberThread(CallbackInfo ci) {
		SeedStreams.setClientThread(Thread.currentThread());
	}
}
