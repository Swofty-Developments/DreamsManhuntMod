package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(UnderwaterAmbientSoundHandler.class)
public interface UnderwaterAmbientSoundHandlerAccessor {
	@Accessor("tickDelay")
	int dma$getTickDelay();

	@Accessor("tickDelay")
	void dma$setTickDelay(int delay);
}
