package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.swofty.dreammanhunt.client.state.audio.AudioState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractSoundInstance.class)
public abstract class AbstractSoundInstanceMixin {
	@Shadow
	protected @Nullable Sound sound;

	@Inject(method = "getOrResolve", at = @At("RETURN"))
	private void dma$forceVariant(SoundManager soundManager, CallbackInfoReturnable<WeighedSoundEvents> cir) {
		Sound forced = AudioState.forcedSound(this);
		if (forced != null && cir.getReturnValue() != null) {
			this.sound = forced;
		}
	}
}
