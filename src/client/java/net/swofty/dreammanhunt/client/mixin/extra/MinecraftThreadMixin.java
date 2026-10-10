package net.swofty.dreammanhunt.client.mixin.extra;

import net.minecraft.client.Minecraft;
import net.swofty.dreammanhunt.client.state.ClientStateManager;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftThreadMixin {
	@Unique
	private long dm$glyphTickSeed;

	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void dm$seedGlyphsForFrame(boolean advanceGameTime, CallbackInfo ci) {
		Minecraft minecraft = (Minecraft) (Object) this;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		long mixed = this.dm$glyphTickSeed ^ Float.floatToIntBits(partial) * 0x9E3779B97F4A7C15L;
		((FontAccessor) minecraft.font).dm$getRandom().setSeed(mixed);
	}

	@Inject(method = "<init>", at = @At("RETURN"))
	private void dm$rememberThread(CallbackInfo ci) {
		SeedStreams.setClientThread(Thread.currentThread());
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void dm$applyScheduledStream(CallbackInfo ci) {
		if (!ClientStateManager.get().isRestoring()) {
			SeedStreams.CLIENT.applyScheduled();
			Long seed = SeedStreams.nextSeed();
			if (seed != null) {
				this.dm$glyphTickSeed = seed;
			}
		}
	}
}
