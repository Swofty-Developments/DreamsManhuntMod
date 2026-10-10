package net.swofty.dreammanhunt.server.tracking.mixin;

import java.nio.file.Path;
import net.minecraft.server.level.ServerPlayer;
import net.swofty.dreammanhunt.api.SavestateInfo;
import net.swofty.dreammanhunt.server.engine.SavestateEngine;
import net.swofty.dreammanhunt.server.tracking.TrackingState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SavestateEngine.class, remap = false)
public abstract class SavestateEngineMixin {
	@Shadow
	@Final
	private net.minecraft.server.MinecraftServer server;

	@Shadow
	@Final
	private Path statesDir;

	@Inject(method = "save", at = @At("HEAD"))
	private void dreammanhunt$captureTracking(String id, String name, ServerPlayer creator, CallbackInfoReturnable<SavestateInfo> cir) {
		TrackingState.capture(this.server, id);
	}

	@Inject(method = "save", at = @At("RETURN"))
	private void dreammanhunt$writeTracking(String id, String name, ServerPlayer creator, CallbackInfoReturnable<SavestateInfo> cir) {
		TrackingState.write(id, this.statesDir.resolve(id));
	}

	@Inject(method = "restore", at = @At("HEAD"))
	private void dreammanhunt$armTracking(String id, CallbackInfo ci) {
		TrackingState.arm(this.server, this.statesDir.resolve(id));
	}
}
