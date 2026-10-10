package net.swofty.dreammanhunt.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.SoundBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.swofty.dreammanhunt.client.state.audio.AudioState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void dma$blockPlay(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (AudioState.blocking()) {
			cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
		}
	}

	@Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
	private void dma$blockDelayed(SoundInstance instance, int delay, CallbackInfo ci) {
		if (AudioState.blocking()) {
			ci.cancel();
		}
	}

	@Inject(method = "queueTickingSound", at = @At("HEAD"), cancellable = true)
	private void dma$blockQueued(TickableSoundInstance instance, CallbackInfo ci) {
		if (AudioState.blocking()) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getCompleteBuffer(Lnet/minecraft/resources/Identifier;)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<SoundBuffer> dma$seekStatic(SoundBufferLibrary library, Identifier path, Operation<CompletableFuture<SoundBuffer>> original,
		@Local(argsOnly = true) SoundInstance instance, @Local ChannelAccess.ChannelHandle handle) {
		AudioState.beforeAttach(instance, handle, false);
		return original.call(library, path);
	}

	@WrapOperation(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundBufferLibrary;getStream(Lnet/minecraft/resources/Identifier;Z)Ljava/util/concurrent/CompletableFuture;"))
	private CompletableFuture<AudioStream> dma$seekStream(SoundBufferLibrary library, Identifier path, boolean looping, Operation<CompletableFuture<AudioStream>> original,
		@Local(argsOnly = true) SoundInstance instance, @Local ChannelAccess.ChannelHandle handle) {
		AudioState.beforeAttach(instance, handle, true);
		CompletableFuture<AudioStream> prepared = AudioState.preparedStream(instance);
		return prepared != null ? prepared : original.call(library, path, looping);
	}
}
