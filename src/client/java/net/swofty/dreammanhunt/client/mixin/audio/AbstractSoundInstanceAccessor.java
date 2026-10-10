package net.swofty.dreammanhunt.client.mixin.audio;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {
	@Accessor("volume")
	float dma$getVolume();

	@Accessor("volume")
	void dma$setVolume(float volume);

	@Accessor("pitch")
	float dma$getPitch();

	@Accessor("pitch")
	void dma$setPitch(float pitch);

	@Accessor("x")
	void dma$setX(double x);

	@Accessor("y")
	void dma$setY(double y);

	@Accessor("z")
	void dma$setZ(double z);

	@Accessor("looping")
	void dma$setLooping(boolean looping);

	@Accessor("delay")
	void dma$setDelay(int delay);
}
