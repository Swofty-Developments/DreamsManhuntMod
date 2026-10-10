package net.swofty.dreammanhunt.client.mixin.audio;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.audio.Channel;
import net.minecraft.client.sounds.AudioStream;
import net.swofty.dreammanhunt.client.state.audio.AudioClock;
import net.swofty.dreammanhunt.client.state.audio.AudioState;
import net.swofty.dreammanhunt.client.state.audio.ChannelPosition;
import org.jspecify.annotations.Nullable;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Channel.class)
public abstract class ChannelMixin implements ChannelPosition {
	@Shadow
	@Final
	private int source;

	@Shadow
	private @Nullable AudioStream stream;

	@Unique
	private long dma$unqueuedFrames;

	@Unique
	private int dma$pendingOffset = -1;

	@WrapOperation(method = "removeProcessedBuffers", at = @At(value = "INVOKE", target = "Lorg/lwjgl/openal/AL10;alDeleteBuffers([I)V"))
	private void dma$countUnqueued(int[] buffers, Operation<Void> original) {
		for (int buffer : buffers) {
			int bytesPerFrame = AL10.alGetBufferi(buffer, AL10.AL_BITS) / 8 * AL10.alGetBufferi(buffer, AL10.AL_CHANNELS);
			if (bytesPerFrame > 0) {
				this.dma$unqueuedFrames += AL10.alGetBufferi(buffer, AL10.AL_SIZE) / bytesPerFrame;
			}
		}
		original.call(buffers);
	}

	@Unique
	private boolean dma$hold;

	@Unique
	private boolean dma$held;

	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void dma$applyPendingOffset(CallbackInfo ci) {
		if (this.dma$pendingOffset >= 0) {
			AL10.alSourcei(this.source, AL11.AL_SAMPLE_OFFSET, this.dma$pendingOffset);
			this.dma$pendingOffset = -1;
		}
		if (this.dma$hold && AudioState.batchOpen()) {
			this.dma$hold = false;
			this.dma$held = true;
			ci.cancel();
		}
	}

	@Override
	public void dma$holdStart() {
		this.dma$hold = true;
	}

	@Override
	public boolean dma$takeHeldStart() {
		boolean held = this.dma$held;
		this.dma$held = false;
		this.dma$hold = false;
		return held;
	}

	@Override
	public int dma$source() {
		return this.source;
	}

	@Override
	public long[] dma$position() {
		long frames;
		long clock = 0L;
		long[] values = AudioClock.sampleOffsetClock(this.source);
		if (values != null) {
			frames = values[0] >> 32;
			clock = values[1];
		} else {
			frames = AL10.alGetSourcei(this.source, AL11.AL_SAMPLE_OFFSET);
		}
		int rate;
		if (this.stream != null) {
			rate = (int) this.stream.getFormat().getSampleRate();
		} else {
			int buffer = AL10.alGetSourcei(this.source, AL10.AL_BUFFER);
			rate = buffer == 0 ? 0 : AL10.alGetBufferi(buffer, AL10.AL_FREQUENCY);
		}
		boolean playing = AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING;
		float pitch = AL10.alGetSourcef(this.source, AL10.AL_PITCH);
		return new long[]{this.dma$unqueuedFrames + frames, rate, clock, playing ? 1L : 0L, Float.floatToIntBits(pitch)};
	}

	@Override
	public void dma$seekFrames(int frames) {
		int state = AL10.alGetSourcei(this.source, AL10.AL_SOURCE_STATE);
		if (state == AL10.AL_PLAYING || state == AL10.AL_PAUSED) {
			AL10.alSourcei(this.source, AL11.AL_SAMPLE_OFFSET, frames);
		} else {
			this.dma$pendingOffset = frames;
		}
	}

	@Override
	public void dma$addPlayedFrames(long frames) {
		this.dma$unqueuedFrames += frames;
	}
}
