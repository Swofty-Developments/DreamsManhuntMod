package net.swofty.dreammanhunt.mixin.randomroot;

import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import net.swofty.dreammanhunt.randomroot.SeedStreams;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
	@Shadow
	@Final
	private Thread serverThread;

	@Inject(method = "<init>", at = @At("RETURN"))
	private void dm$rememberThread(CallbackInfo ci) {
		SeedStreams.setServerThread(this.serverThread);
	}

	@Inject(method = "tickChildren", at = @At("HEAD"))
	private void dm$applyScheduledStream(BooleanSupplier haveTime, CallbackInfo ci) {
		SeedStreams.onServerTickStart(((MinecraftServer) (Object) this).tickRateManager().isFrozen());
	}
}
